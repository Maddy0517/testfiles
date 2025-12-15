package com.example.dataflow;

import com.example.dataflow.client.WorkdaySoapClient;
import com.example.dataflow.config.WorkdayConfig;
import com.example.dataflow.model.Employee;
import com.example.dataflow.model.PageRequest;
import com.example.dataflow.transform.EmployeeToTableRowFn;
import com.example.dataflow.transform.FetchEmployeePageFn;
import com.example.dataflow.transform.GeneratePageRequestsFn;
import com.example.dataflow.utils.BigQuerySchemaFactory;
import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.options.*;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dataflow pipeline to ingest Workday employee data via SOAP API
 * and load into BigQuery with parallel page processing for optimal performance
 * 
 * Pipeline Flow:
 * 1. Get total employee count from Workday API
 * 2. Generate page requests for parallel processing
 * 3. Fetch employee data from each page in parallel
 * 4. Transform employees to BigQuery TableRow format
 * 5. Write to BigQuery table
 * 
 * Key Features:
 * - Parallel page processing (999 records per page)
 * - Configurable effective date filtering
 * - Automatic retry logic with exponential backoff
 * - Error handling and logging
 * - Scalable architecture using Dataflow's distributed processing
 */
public class WorkdayEmployeeDataflowPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(WorkdayEmployeeDataflowPipeline.class);
    
    /**
     * Pipeline options interface
     */
    public interface WorkdayPipelineOptions extends PipelineOptions {
        
        @Description("Workday SOAP API URL")
        @Validation.Required
        String getWorkdaySoapUrl();
        void setWorkdaySoapUrl(String value);
        
        @Description("Workday username")
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
        
        @Description("Effective date for data extraction (YYYY-MM-DD format)")
        @Validation.Required
        String getEffectiveDate();
        void setEffectiveDate(String value);
        
        @Description("BigQuery output table (project:dataset.table)")
        @Validation.Required
        String getBigQueryTable();
        void setBigQueryTable(String value);
        
        @Description("BigQuery write disposition (WRITE_TRUNCATE, WRITE_APPEND, WRITE_EMPTY)")
        @Default.String("WRITE_APPEND")
        String getWriteDisposition();
        void setWriteDisposition(String value);
        
        @Description("BigQuery create disposition (CREATE_IF_NEEDED, CREATE_NEVER)")
        @Default.String("CREATE_IF_NEEDED")
        String getCreateDisposition();
        void setCreateDisposition(String value);
        
        @Description("Maximum number of retries for API calls")
        @Default.Integer(3)
        Integer getMaxRetries();
        void setMaxRetries(Integer value);
        
        @Description("Estimated total employee count (0 to fetch dynamically)")
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
     * Build and run the pipeline
     */
    public static void runPipeline(WorkdayPipelineOptions options) {
        LOG.info("Starting Workday Employee Dataflow Pipeline");
        LOG.info("Effective Date: {}", options.getEffectiveDate());
        LOG.info("BigQuery Table: {}", options.getBigQueryTable());
        
        // Create Workday configuration
        WorkdayConfig workdayConfig = new WorkdayConfig(
                options.getWorkdaySoapUrl(),
                options.getWorkdayUsername(),
                options.getWorkdayPassword()
        );
        workdayConfig.setTenantId(options.getWorkdayTenantId());
        workdayConfig.setMaxRetries(options.getMaxRetries());
        
        // Get total count if not provided
        int totalCount = options.getEstimatedTotalCount();
        if (totalCount <= 0) {
            LOG.info("Fetching total employee count from Workday API...");
            WorkdaySoapClient client = new WorkdaySoapClient(workdayConfig);
            totalCount = client.getTotalWorkerCount(options.getEffectiveDate());
            LOG.info("Total employee count: {}", totalCount);
        }
        
        // Create pipeline
        Pipeline pipeline = Pipeline.create(options);
        
        // Step 1: Create a single element with the total count
        PCollection<Integer> totalCountPCollection = pipeline.apply(
                "CreateTotalCount",
                Create.of(totalCount)
        );
        
        // Step 2: Generate page requests for parallel processing
        PCollection<PageRequest> pageRequests = totalCountPCollection.apply(
                "GeneratePageRequests",
                ParDo.of(new GeneratePageRequestsFn(options.getEffectiveDate()))
        );
        
        // Step 3: Fetch employees from each page in parallel
        // This is where the magic happens - Dataflow will distribute these across workers
        PCollection<Employee> employees = pageRequests.apply(
                "FetchEmployeePages",
                ParDo.of(new FetchEmployeePageFn(workdayConfig))
        );
        
        // Step 4: Convert employees to BigQuery TableRow format
        PCollection<TableRow> tableRows = employees.apply(
                "ConvertToTableRow",
                ParDo.of(new EmployeeToTableRowFn())
        );
        
        // Step 5: Write to BigQuery
        tableRows.apply(
                "WriteToBigQuery",
                BigQueryIO.writeTableRows()
                        .to(options.getBigQueryTable())
                        .withSchema(BigQuerySchemaFactory.createEmployeeSchema())
                        .withWriteDisposition(
                                BigQueryIO.Write.WriteDisposition.valueOf(options.getWriteDisposition())
                        )
                        .withCreateDisposition(
                                BigQueryIO.Write.CreateDisposition.valueOf(options.getCreateDisposition())
                        )
                        .withMethod(BigQueryIO.Write.Method.FILE_LOADS) // Better for large batches
                        .withCustomGcsTempLocation(
                                ValueProvider.StaticValueProvider.of(
                                        options.getTempLocation() + "/bq-loads"
                                )
                        )
        );
        
        LOG.info("Pipeline configured successfully. Starting execution...");
        
        // Run the pipeline
        pipeline.run().waitUntilFinish();
        
        LOG.info("Pipeline execution completed successfully!");
    }
}
