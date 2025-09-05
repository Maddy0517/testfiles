package com.workday.dataflow;

import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.io.gcp.bigquery.WriteResult;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Main Apache Beam pipeline for ingesting worker data from Workday SOAP API 
 * and loading it into Google Cloud BigQuery.
 * 
 * This pipeline:
 * 1. Reads configuration from properties file
 * 2. Fetches worker data from Workday SOAP API with pagination
 * 3. Transforms and validates the data
 * 4. Loads the data into BigQuery
 * 
 * Usage:
 * mvn compile exec:java -Dexec.mainClass=com.workday.dataflow.WorkdayToBigQueryPipeline \
 *   -Dexec.args="--propertiesFile=config/pipeline.properties"
 * 
 * For Dataflow:
 * mvn compile exec:java -Dexec.mainClass=com.workday.dataflow.WorkdayToBigQueryPipeline \
 *   -Dexec.args="--propertiesFile=config/pipeline.properties --runner=DataflowRunner --project=your-project --region=us-central1"
 */
public class WorkdayToBigQueryPipeline {
    private static final Logger LOG = LoggerFactory.getLogger(WorkdayToBigQueryPipeline.class);

    public static void main(String[] args) {
        // Parse command line arguments
        WorkdayToBigQueryOptions options = PipelineOptionsFactory
            .fromArgs(args)
            .withValidation()
            .as(WorkdayToBigQueryOptions.class);

        // Load configuration from properties file
        try {
            loadConfiguration(options);
        } catch (IOException e) {
            LOG.error("Failed to load configuration from properties file", e);
            System.exit(1);
        }

        // Validate required options
        validateOptions(options);

        // Create and run the pipeline
        runPipeline(options);
    }

    /**
     * Load configuration from properties file and apply to options
     */
    private static void loadConfiguration(WorkdayToBigQueryOptions options) throws IOException {
        String propertiesFile = options.getPropertiesFile();
        if (propertiesFile == null || propertiesFile.trim().isEmpty()) {
            throw new IllegalArgumentException("Properties file path is required. Use --propertiesFile=path/to/file.properties");
        }

        LOG.info("Loading configuration from: {}", propertiesFile);
        ConfigurationManager configManager = new ConfigurationManager(propertiesFile);
        configManager.applyToOptions(options);
        LOG.info("Configuration loaded successfully");
    }

    /**
     * Validate required pipeline options
     */
    private static void validateOptions(WorkdayToBigQueryOptions options) {
        StringBuilder errors = new StringBuilder();

        if (options.getSoapApiUrl() == null || options.getSoapApiUrl().trim().isEmpty()) {
            errors.append("SOAP API URL is required. ");
        }
        if (options.getUsername() == null || options.getUsername().trim().isEmpty()) {
            errors.append("Username is required. ");
        }
        if (options.getPassword() == null || options.getPassword().trim().isEmpty()) {
            errors.append("Password is required. ");
        }
        if (options.getBigQueryProject() == null || options.getBigQueryProject().trim().isEmpty()) {
            errors.append("BigQuery project is required. ");
        }
        if (options.getBigQueryDataset() == null || options.getBigQueryDataset().trim().isEmpty()) {
            errors.append("BigQuery dataset is required. ");
        }
        if (options.getBigQueryTable() == null || options.getBigQueryTable().trim().isEmpty()) {
            errors.append("BigQuery table is required. ");
        }

        if (errors.length() > 0) {
            throw new IllegalArgumentException("Validation errors: " + errors.toString());
        }

        LOG.info("Pipeline options validated successfully");
    }

    /**
     * Create and run the Apache Beam pipeline
     */
    private static void runPipeline(WorkdayToBigQueryOptions options) {
        LOG.info("Starting Workday to BigQuery pipeline");
        
        // Create the pipeline
        Pipeline pipeline = Pipeline.create(options);

        // Build the pipeline
        PCollection<WorkerData> workerData = pipeline
            .apply("Read from Workday API", new WorkdaySourceTransform(options));

        PCollection<TableRow> transformedData = workerData
            .apply("Transform Worker Data", new WorkerDataTransform());

        WriteResult writeResult = transformedData
            .apply("Write to BigQuery", new BigQuerySinkTransform(options));

        // Add error handling for failed inserts
        writeResult.getFailedInserts()
            .apply("Log Failed Inserts", ParDo.of(new LogFailedInsertsDoFn()));

        // Run the pipeline
        PipelineResult result = pipeline.run();

        // Wait for completion if running locally (DirectRunner)
        if (isDirectRunner(options)) {
            LOG.info("Running with DirectRunner - waiting for completion");
            PipelineResult.State finalState = result.waitUntilFinish();
            LOG.info("Pipeline completed with state: {}", finalState);
        } else {
            LOG.info("Pipeline submitted to Dataflow. Check the Dataflow console for progress.");
        }
    }

    /**
     * Check if running with DirectRunner (local execution)
     */
    private static boolean isDirectRunner(WorkdayToBigQueryOptions options) {
        String runner = options.getRunner();
        return runner == null || runner.contains("DirectRunner");
    }

    /**
     * DoFn to log failed BigQuery inserts
     */
    private static class LogFailedInsertsDoFn extends DoFn<TableRow, Void> {
        
        @ProcessElement
        public void processElement(@Element TableRow failedRow) {
            LOG.error("Failed to insert row into BigQuery: {}", failedRow);
            // In production, you might want to:
            // 1. Write to a dead letter queue
            // 2. Send alerts
            // 3. Write to a separate error table
        }
    }
}