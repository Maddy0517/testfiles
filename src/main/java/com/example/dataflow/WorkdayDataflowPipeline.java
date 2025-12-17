package com.example.dataflow;

import com.example.dataflow.WorkdayDataModel.*;
import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.options.*;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Main Dataflow pipeline for Workday employee data ingestion
 * Features: Parallel page processing, automatic retries, BigQuery integration
 * Built with Java 21+ features for modern, clean code
 */
public class WorkdayDataflowPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(WorkdayDataflowPipeline.class);
    
    /**
     * Pipeline options
     */
    public interface WorkdayPipelineOptions extends PipelineOptions {
        
        @Description("Workday SOAP API URL")
        @Validation.Required
        String getWorkdaySoapUrl();
        void setWorkdaySoapUrl(String value);
        
        @Description("Workday username (format: user@tenant)")
        @Validation.Required
        String getWorkdayUsername();
        void setWorkdayUsername(String value);
        
        @Description("Workday password")
        @Validation.Required
        String getWorkdayPassword();
        void setWorkdayPassword(String value);
        
        @Description("Workday tenant ID")
        String getWorkdayTenantId();
        void setWorkdayTenantId(String value);
        
        @Description("Effective date (YYYY-MM-DD)")
        @Validation.Required
        String getEffectiveDate();
        void setEffectiveDate(String value);
        
        @Description("BigQuery table (project:dataset.table)")
        @Validation.Required
        String getBigQueryTable();
        void setBigQueryTable(String value);
        
        @Description("Write disposition")
        @Default.String("WRITE_APPEND")
        String getWriteDisposition();
        void setWriteDisposition(String value);
        
        @Description("Max retries")
        @Default.Integer(3)
        Integer getMaxRetries();
        void setMaxRetries(Integer value);
        
        @Description("Estimated total count (0=auto)")
        @Default.Integer(0)
        Integer getEstimatedTotalCount();
        void setEstimatedTotalCount(Integer value);
    }
    
    /**
     * Main entry point
     */
    public static void main(String[] args) {
        WorkdayPipelineOptions options = PipelineOptionsFactory
                .fromArgs(args)
                .withValidation()
                .as(WorkdayPipelineOptions.class);
        
        runPipeline(options);
    }
    
    /**
     * Build and run pipeline
     */
    public static void runPipeline(WorkdayPipelineOptions options) {
        LOG.info("Starting Workday Employee Dataflow Pipeline");
        LOG.info("Effective Date: {}, BigQuery: {}", options.getEffectiveDate(), options.getBigQueryTable());
        
        // Create Workday configuration
        WorkdayConfig config = new WorkdayConfig(
                options.getWorkdaySoapUrl(),
                options.getWorkdayUsername(),
                options.getWorkdayPassword()
        );
        config.tenantId = options.getWorkdayTenantId();
        config.maxRetries = options.getMaxRetries();
        
        // Get total count (Java 21 enhanced switch with pattern matching)
        int totalCount = switch (options.getEstimatedTotalCount()) {
            case int count when count > 0 -> {
                LOG.info("Using estimated total count: {}", count);
                yield count;
            }
            default -> {
                LOG.info("Fetching total employee count from Workday...");
                WorkdaySoapHandler handler = new WorkdaySoapHandler(config);
                int count = handler.getTotalWorkerCount(options.getEffectiveDate());
                LOG.info("Total employees: {}", count);
                yield count;
            }
        };
        
        // Create pipeline
        Pipeline pipeline = Pipeline.create(options);
        
        // Step 1: Create page requests
        PCollection<PageRequest> pageRequests = pipeline
                .apply("CreateTotalCount", Create.of(totalCount))
                .apply("GeneratePages", ParDo.of(new GeneratePagesFn(options.getEffectiveDate())));
        
        // Step 2: Fetch employees in parallel
        PCollection<Employee> employees = pageRequests
                .apply("FetchEmployees", ParDo.of(new FetchEmployeesFn(config)));
        
        // Step 3: Convert to TableRow
        PCollection<TableRow> tableRows = employees
                .apply("ConvertToTableRow", ParDo.of(new ConvertToTableRowFn()));
        
        // Step 4: Write to BigQuery
        tableRows.apply("WriteToBigQuery",
                BigQueryIO.writeTableRows()
                        .to(options.getBigQueryTable())
                        .withSchema(BigQuerySchema.getEmployeeSchema())
                        .withWriteDisposition(BigQueryIO.Write.WriteDisposition.valueOf(options.getWriteDisposition()))
                        .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED)
                        .withMethod(BigQueryIO.Write.Method.FILE_LOADS)
                        .withCustomGcsTempLocation(ValueProvider.StaticValueProvider.of(
                                options.getTempLocation() + "/bq-loads"))
        );
        
        LOG.info("Pipeline configured. Starting execution...");
        pipeline.run().waitUntilFinish();
        LOG.info("Pipeline completed successfully!");
    }
    
    /**
     * Generate page requests for parallel processing
     */
    static class GeneratePagesFn extends DoFn<Integer, PageRequest> {
        private final String effectiveDate;
        
        public GeneratePagesFn(String effectiveDate) {
            this.effectiveDate = effectiveDate;
        }
        
        @ProcessElement
        public void processElement(@Element Integer totalCount, OutputReceiver<PageRequest> out) {
            int totalPages = (int) Math.ceil((double) totalCount / 999);
            LOG.info("Generating {} page requests for {} records", totalPages, totalCount);
            
            for (int page = 1; page <= totalPages; page++) {
                out.output(new PageRequest(page, effectiveDate));
            }
        }
    }
    
    /**
     * Fetch employees from Workday (runs in parallel) - Java 21 with method references
     */
    static class FetchEmployeesFn extends DoFn<PageRequest, Employee> {
        private final WorkdayConfig config;
        private transient WorkdaySoapHandler handler;
        
        public FetchEmployeesFn(WorkdayConfig config) {
            this.config = config;
        }
        
        @Setup
        public void setup() {
            this.handler = new WorkdaySoapHandler(config);
        }
        
        @ProcessElement
        public void processElement(@Element PageRequest request, OutputReceiver<Employee> out) {
            try {
                List<Employee> employees = handler.fetchEmployeePage(request.effectiveDate, request.pageNumber);
                employees.forEach(out::output);  // Java 21 method reference
            } catch (Exception e) {
                LOG.error("Error processing page {}: {}", request.pageNumber, e.getMessage(), e);
                throw new RuntimeException(String.format("Failed to process page %d", request.pageNumber), e);
            }
        }
    }
    
    /**
     * Convert Employee to BigQuery TableRow
     */
    static class ConvertToTableRowFn extends DoFn<Employee, TableRow> {
        
        @ProcessElement
        public void processElement(@Element Employee emp, OutputReceiver<TableRow> out) {
            TableRow row = new TableRow()
                    .set("employee_id", emp.employeeId)
                    .set("first_name", emp.firstName)
                    .set("last_name", emp.lastName)
                    .set("email", emp.email)
                    .set("phone", emp.phone)
                    .set("hire_date", emp.hireDate)
                    .set("job_title", emp.jobTitle)
                    .set("department", emp.department)
                    .set("manager_id", emp.managerId)
                    .set("location", emp.location)
                    .set("employment_status", emp.employmentStatus)
                    .set("effective_date", emp.effectiveDate)
                    .set("ingestion_timestamp", emp.ingestionTimestamp);
            
            out.output(row);
        }
    }
}
