package com.company.workday;

import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.options.*;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Main pipeline class for extracting employee data from Workday SOAP API
 * and loading it into BigQuery using Apache Beam on Google Cloud Dataflow.
 */
public class WorkdayEmployeePipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(WorkdayEmployeePipeline.class);

    /**
     * Pipeline options interface for Workday employee data extraction.
     */
    public interface WorkdayPipelineOptions extends PipelineOptions {
        
        @Description("Workday SOAP API endpoint URL")
        @Validation.Required
        String getWorkdayEndpoint();
        void setWorkdayEndpoint(String endpoint);

        @Description("Workday username for authentication")
        @Validation.Required
        String getWorkdayUsername();
        void setWorkdayUsername(String username);

        @Description("Workday password for authentication")
        @Validation.Required
        String getWorkdayPassword();
        void setWorkdayPassword(String password);

        @Description("Workday tenant name")
        @Validation.Required
        String getWorkdayTenant();
        void setWorkdayTenant(String tenant);

        @Description("BigQuery dataset ID")
        @Validation.Required
        String getBigQueryDataset();
        void setBigQueryDataset(String dataset);

        @Description("BigQuery table ID")
        @Validation.Required
        String getBigQueryTable();
        void setBigQueryTable(String table);

        @Description("Effective as of date (YYYY-MM-DD format). If not provided, uses current date")
        String getEffectiveAsOfDate();
        void setEffectiveAsOfDate(String date);

        @Description("Mode: HISTORICAL (full load) or INCREMENTAL (delta load)")
        @Default.String("INCREMENTAL")
        String getLoadMode();
        void setLoadMode(String mode);

        @Description("Number of days to look back for incremental loads")
        @Default.Integer(1)
        Integer getIncrementalDays();
        void setIncrementalDays(Integer days);

        @Description("Maximum number of parallel requests to Workday API")
        @Default.Integer(5)
        Integer getMaxParallelRequests();
        void setMaxParallelRequests(Integer maxRequests);
    }

    public static void main(String[] args) {
        WorkdayPipelineOptions options = PipelineOptionsFactory.fromArgs(args)
                .withValidation()
                .as(WorkdayPipelineOptions.class);

        run(options);
    }

    public static void run(WorkdayPipelineOptions options) {
        Pipeline pipeline = Pipeline.create(options);

        LOG.info("Starting Workday Employee Data Pipeline");
        LOG.info("Load Mode: {}", options.getLoadMode());
        LOG.info("Effective Date: {}", getEffectiveDate(options));
        LOG.info("Target Table: {}.{}", options.getBigQueryDataset(), options.getBigQueryTable());

        // Create WorkdayConfig from options
        WorkdayConfig config = WorkdayConfig.builder()
                .endpoint(options.getWorkdayEndpoint())
                .username(options.getWorkdayUsername())
                .password(options.getWorkdayPassword())
                .tenant(options.getWorkdayTenant())
                .effectiveDate(getEffectiveDate(options))
                .loadMode(WorkdayConfig.LoadMode.valueOf(options.getLoadMode()))
                .incrementalDays(options.getIncrementalDays())
                .maxParallelRequests(options.getMaxParallelRequests())
                .build();

        // Step 1: Generate page ranges for parallel processing
        PCollection<Integer> pageNumbers = pipeline
                .apply("Create Initial Page", Create.of(1))
                .apply("Generate Page Numbers", ParDo.of(new GeneratePageNumbersFn(config)));

        // Step 2: Extract employee data from Workday SOAP API with pagination
        PCollection<WorkdayEmployee> employees = pageNumbers
                .apply("Extract Employee Data", ParDo.of(new ExtractWorkdayEmployeesFn(config)));

        // Step 3: Transform to BigQuery TableRow format
        PCollection<TableRow> tableRows = employees
                .apply("Transform to TableRow", ParDo.of(new TransformToBigQueryFn()));

        // Step 4: Write to BigQuery
        String tableSpec = String.format("%s:%s.%s", 
                options.getProject(), 
                options.getBigQueryDataset(), 
                options.getBigQueryTable());

        BigQueryIO.Write.CreateDisposition createDisposition = 
                options.getLoadMode().equals("HISTORICAL") ? 
                BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED : 
                BigQueryIO.Write.CreateDisposition.CREATE_NEVER;

        BigQueryIO.Write.WriteDisposition writeDisposition = 
                options.getLoadMode().equals("HISTORICAL") ? 
                BigQueryIO.Write.WriteDisposition.WRITE_TRUNCATE : 
                BigQueryIO.Write.WriteDisposition.WRITE_APPEND;

        tableRows.apply("Write to BigQuery", 
                BigQueryIO.writeTableRows()
                        .to(tableSpec)
                        .withSchema(BigQuerySchemaUtil.getEmployeeTableSchema())
                        .withCreateDisposition(createDisposition)
                        .withWriteDisposition(writeDisposition)
                        .withMethod(BigQueryIO.Write.Method.STREAMING_INSERTS));

        pipeline.run().waitUntilFinish();
        LOG.info("Pipeline completed successfully");
    }

    private static String getEffectiveDate(WorkdayPipelineOptions options) {
        if (options.getEffectiveAsOfDate() != null && !options.getEffectiveAsOfDate().isEmpty()) {
            return options.getEffectiveAsOfDate();
        }
        
        LocalDate date = LocalDate.now();
        if (options.getLoadMode().equals("INCREMENTAL")) {
            date = date.minusDays(options.getIncrementalDays());
        }
        
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}