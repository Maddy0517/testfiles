package com.example.workday;

import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.ValueProvider;
import org.apache.beam.sdk.transforms.SerializableFunction;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Main pipeline class for Workday to BigQuery data transfer
 */
public class WorkdayToBigQueryPipeline {
    private static final Logger LOG = LoggerFactory.getLogger(WorkdayToBigQueryPipeline.class);
    
    public static void main(String[] args) {
        // Parse pipeline options
        WorkdayPipelineOptions options = PipelineOptionsFactory.fromArgs(args)
                .withValidation()
                .as(WorkdayPipelineOptions.class);
        
        // Create pipeline
        Pipeline pipeline = Pipeline.create(options);
        
        // Build and run pipeline
        runPipeline(pipeline, options);
        
        // Execute pipeline
        pipeline.run().waitUntilFinish();
    }
    
    /**
     * Build and execute the pipeline
     */
    public static void runPipeline(Pipeline pipeline, WorkdayPipelineOptions options) {
        LOG.info("Starting Workday to BigQuery pipeline");
        
        // Create Workday configuration
        WorkdayConfiguration config = createWorkdayConfiguration(options);
        
        // Read data from Workday
        PCollection<WorkdayRecord> workdayRecords = pipeline
                .apply("Read from Workday", 
                       WorkdayIO.read()
                               .withConfiguration(config)
                               .withServiceName(options.getServiceName().get())
                               .withOperationName(options.getOperationName().get())
                               .withRequestParams(createRequestParams(options)));
        
        // Transform to BigQuery format
        PCollection<TableRow> bigQueryRows = workdayRecords
                .apply("Transform to BigQuery", 
                       new WorkdayToBigQueryTransform(options.getTransformationType().get()));
        
        // Write to BigQuery
        bigQueryRows.apply("Write to BigQuery",
                BigQueryIO.writeTableRows()
                        .to(getTableSpec(options))
                        .withSchema(getTableSchema(options))
                        .withWriteDisposition(BigQueryIO.Write.WriteDisposition.valueOf(
                                options.getWriteDisposition().get()))
                        .withCreateDisposition(BigQueryIO.Write.CreateDisposition.valueOf(
                                options.getCreateDisposition().get()))
                        .withCustomGcsTempLocation(ValueProvider.StaticValueProvider.of(
                                "gs://" + options.getBigQueryProject().get() + "-temp/workday-pipeline")));
        
        LOG.info("Pipeline configured successfully");
    }
    
    /**
     * Create Workday configuration from pipeline options
     */
    private static WorkdayConfiguration createWorkdayConfiguration(WorkdayPipelineOptions options) {
        WorkdayConfiguration config = new WorkdayConfiguration();
        config.setWorkdayEndpoint(options.getWorkdayEndpoint().get());
        config.setUsername(options.getWorkdayUsername().get());
        config.setPassword(options.getWorkdayPassword().get());
        config.setTenantName(options.getWorkdayTenant().get());
        config.setVersion(options.getWorkdayVersion().get());
        config.setPageSize(options.getPageSize().get());
        config.setMaxRetries(options.getMaxRetries().get());
        
        return config;
    }
    
    /**
     * Create request parameters for Workday API call
     */
    private static Map<String, Object> createRequestParams(WorkdayPipelineOptions options) {
        Map<String, Object> params = new HashMap<>();
        
        // Add common parameters
        params.put("Include_Reference_Data", "true");
        params.put("Include_Personal_Information", "true");
        
        // You can add more specific parameters based on the operation
        String operationName = options.getOperationName().get();
        if ("Get_Workers".equals(operationName)) {
            params.put("Include_Inactive_Workers", "false");
            params.put("Include_Subordinate_Organizations", "true");
        } else if ("Get_Organizations".equals(operationName)) {
            params.put("Include_Inactive_Organizations", "false");
        }
        
        return params;
    }
    
    /**
     * Get BigQuery table specification
     */
    private static ValueProvider<String> getTableSpec(WorkdayPipelineOptions options) {
        return ValueProvider.NestedValueProvider.of(
                ValueProvider.NestedValueProvider.of(
                        ValueProvider.NestedValueProvider.of(
                                options.getBigQueryProject(),
                                project -> project + ":"),
                        dataset -> dataset + options.getBigQueryDataset().get()),
                table -> table + "." + options.getBigQueryTable().get());
    }
    
    /**
     * Get table schema based on transformation type
     */
    private static SerializableFunction<String, TableSchema> getTableSchema(WorkdayPipelineOptions options) {
        return (String input) -> {
            String transformationType = options.getTransformationType().get();
            switch (transformationType.toLowerCase()) {
                case "worker":
                    return BigQuerySchemaGenerator.generateWorkerSchema();
                case "organization":
                    return BigQuerySchemaGenerator.generateOrganizationSchema();
                default:
                    return BigQuerySchemaGenerator.generateWorkdaySchema();
            }
        };
    }
}