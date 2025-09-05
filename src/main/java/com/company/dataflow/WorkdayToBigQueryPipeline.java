package com.company.dataflow;

import com.company.dataflow.config.WorkdayConfig;
import com.company.dataflow.model.WorkdayEmployee;
import com.company.dataflow.transform.ReadFromWorkdayAPI;
import com.company.dataflow.transform.TransformEmployeeData;
import com.company.dataflow.util.BigQuerySchemaUtils;
import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.PCollection;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Apache Beam pipeline to ingest data from Workday SOAP API and load into BigQuery.
 * 
 * This pipeline:
 * 1. Reads employee data from Workday SOAP API in batches
 * 2. Transforms the data to BigQuery format
 * 3. Writes the data to BigQuery with proper error handling
 * 4. Supports both batch and streaming modes
 * 
 * Usage:
 * mvn compile exec:java -Dexec.mainClass=com.company.dataflow.WorkdayToBigQueryPipeline \
 *   -Dexec.args="--project=your-project \
 *                --workdayEndpoint=https://wd2-impl-services1.workday.com/ccx/service/tenant/Human_Resources/v35.0 \
 *                --workdayUsername=username@tenant \
 *                --workdayPassword=password \
 *                --workdayTenant=tenant \
 *                --bigQueryDataset=workday_data \
 *                --bigQueryTable=employees \
 *                --runner=DataflowRunner \
 *                --region=us-central1"
 */
public class WorkdayToBigQueryPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(WorkdayToBigQueryPipeline.class);
    
    public static void main(String[] args) {
        // Parse command line arguments
        WorkdayConfig options = PipelineOptionsFactory.fromArgs(args)
                .withValidation()
                .as(WorkdayConfig.class);
        
        // Set default values
        setDefaultOptions(options);
        
        // Log configuration (without sensitive data)
        logConfiguration(options);
        
        // Create and run pipeline
        runPipeline(options);
    }
    
    /**
     * Sets default values for pipeline options.
     */
    private static void setDefaultOptions(WorkdayConfig options) {
        if (options.getBatchSize() == null) {
            options.setBatchSize(100);
        }
        if (options.getMaxRetries() == null) {
            options.setMaxRetries(3);
        }
        if (options.getWorkdayVersion() == null) {
            options.setWorkdayVersion("v35.0");
        }
        if (options.getWriteDisposition() == null) {
            options.setWriteDisposition("WRITE_TRUNCATE");
        }
        if (options.getCreateDisposition() == null) {
            options.setCreateDisposition("CREATE_IF_NEEDED");
        }
        if (options.getUseStreaming() == null) {
            options.setUseStreaming(false);
        }
        if (options.getNumWorkers() == null) {
            options.setNumWorkers(5);
        }
    }
    
    /**
     * Logs pipeline configuration.
     */
    private static void logConfiguration(WorkdayConfig options) {
        LOG.info("Pipeline Configuration:");
        LOG.info("  Project: {}", options.getProject());
        LOG.info("  Workday Endpoint: {}", options.getWorkdayEndpoint());
        LOG.info("  Workday Tenant: {}", options.getWorkdayTenant());
        LOG.info("  Workday Version: {}", options.getWorkdayVersion());
        LOG.info("  BigQuery Dataset: {}", options.getBigQueryDataset());
        LOG.info("  BigQuery Table: {}", options.getBigQueryTable());
        LOG.info("  Batch Size: {}", options.getBatchSize());
        LOG.info("  Max Retries: {}", options.getMaxRetries());
        LOG.info("  Write Disposition: {}", options.getWriteDisposition());
        LOG.info("  Use Streaming: {}", options.getUseStreaming());
        LOG.info("  Num Workers: {}", options.getNumWorkers());
    }
    
    /**
     * Creates and runs the Apache Beam pipeline.
     */
    private static void runPipeline(WorkdayConfig options) {
        Pipeline pipeline = Pipeline.create(options);
        
        // Build BigQuery table reference
        String tableSpec = String.format("%s:%s.%s", 
                options.getProject(), 
                options.getBigQueryDataset(), 
                options.getBigQueryTable());
        
        // Create the pipeline
        PCollection<WorkdayEmployee> employees = pipeline
                .apply("Read from Workday API", 
                       new ReadFromWorkdayAPI(
                               options.getWorkdayEndpoint(),
                               options.getWorkdayUsername(),
                               options.getWorkdayPassword(),
                               options.getWorkdayTenant(),
                               options.getWorkdayVersion(),
                               options.getBatchSize(),
                               options.getMaxRetries()));
        
        // Transform data
        PCollection<TableRow> tableRows = employees
                .apply("Transform Employee Data", new TransformEmployeeData());
        
        // Add windowing for streaming mode
        if (options.getUseStreaming()) {
            tableRows = tableRows
                    .apply("Window into Fixed Windows", 
                           Window.<TableRow>into(FixedWindows.of(Duration.standardMinutes(5))));
        }
        
        // Add data quality monitoring
        PCollection<TableRow> enrichedRows = tableRows
                .apply("Add Pipeline Metadata", ParDo.of(new AddPipelineMetadataFn()));
        
        // Write to BigQuery
        BigQueryIO.Write<TableRow> bigQueryWrite = BigQueryIO.writeTableRows()
                .to(tableSpec)
                .withSchema(BigQuerySchemaUtils.createEmployeeTableSchema())
                .withWriteDisposition(BigQueryIO.Write.WriteDisposition.valueOf(options.getWriteDisposition()))
                .withCreateDisposition(BigQueryIO.Write.CreateDisposition.valueOf(options.getCreateDisposition()));
        
        // Configure for streaming or batch
        if (options.getUseStreaming()) {
            bigQueryWrite = bigQueryWrite
                    .withMethod(BigQueryIO.Write.Method.STREAMING_INSERTS)
                    .withFailedInsertRetryPolicy(BigQueryIO.Write.RetryPolicy.alwaysRetry());
        } else {
            bigQueryWrite = bigQueryWrite
                    .withMethod(BigQueryIO.Write.Method.FILE_LOADS)
                    .withNumFileShards(options.getNumWorkers());
        }
        
        enrichedRows.apply("Write to BigQuery", bigQueryWrite);
        
        // Run the pipeline
        LOG.info("Starting Workday to BigQuery pipeline...");
        pipeline.run().waitUntilFinish();
        LOG.info("Pipeline completed successfully!");
    }
    
    /**
     * DoFn to add pipeline metadata to each record.
     */
    private static class AddPipelineMetadataFn extends DoFn<TableRow, TableRow> {
        
        @ProcessElement
        public void processElement(@Element TableRow row, OutputReceiver<TableRow> out) {
            // Add pipeline execution metadata
            row.set("pipeline_version", "1.0.0");
            row.set("pipeline_execution_time", System.currentTimeMillis());
            
            // Add partition date for partitioned tables
            String partitionDate = java.time.LocalDate.now().toString();
            row.set("partition_date", partitionDate);
            
            out.output(row);
        }
    }
}