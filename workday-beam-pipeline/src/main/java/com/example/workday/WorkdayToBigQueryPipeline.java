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
        try {
            // Parse initial options to get properties file path
            WorkdayPipelineOptions initialOptions = PipelineOptionsFactory.fromArgs(args)
                    .withValidation()
                    .as(WorkdayPipelineOptions.class);
            
            // Check if properties file is specified
            WorkdayPipelineOptions options;
            if (initialOptions.getPropertiesFile() != null && initialOptions.getPropertiesFile().isAccessible()) {
                String propertiesFilePath = initialOptions.getPropertiesFile().get();
                LOG.info("Loading configuration from properties file: {}", propertiesFilePath);
                
                // Load properties and merge with command line args
                options = loadOptionsFromProperties(propertiesFilePath, args);
            } else {
                LOG.info("No properties file specified, using command line arguments only");
                options = initialOptions;
            }
            
            // Create pipeline
            Pipeline pipeline = Pipeline.create(options);
            
            // Build and run pipeline
            runPipeline(pipeline, options);
            
            // Execute pipeline
            pipeline.run().waitUntilFinish();
            
        } catch (Exception e) {
            LOG.error("Pipeline execution failed", e);
            System.exit(1);
        }
    }
    
    /**
     * Load pipeline options from properties file and merge with command line args
     */
    private static WorkdayPipelineOptions loadOptionsFromProperties(String propertiesFilePath, String[] originalArgs) {
        try {
            // Load properties from file
            PropertiesReader propertiesReader = new PropertiesReader(propertiesFilePath);
            
            // Validate required properties
            validateRequiredProperties(propertiesReader);
            
            // Convert properties to command line arguments
            String[] propertyArgs = propertiesReader.toCommandLineArgs();
            
            // Merge original args with property args (original args take precedence)
            String[] mergedArgs = mergeArguments(propertyArgs, originalArgs);
            
            // Create options from merged arguments
            return PipelineOptionsFactory.fromArgs(mergedArgs)
                    .withValidation()
                    .as(WorkdayPipelineOptions.class);
                    
        } catch (Exception e) {
            LOG.error("Failed to load configuration from properties file: {}", propertiesFilePath, e);
            throw new RuntimeException("Configuration loading failed", e);
        }
    }
    
    /**
     * Validate required properties
     */
    private static void validateRequiredProperties(PropertiesReader propertiesReader) {
        String[] requiredProperties = {
            "workdayEndpoint",
            "workdayUsername", 
            "workdayPassword",
            "workdayTenant",
            "serviceName",
            "operationName",
            "bigQueryProject",
            "bigQueryDataset",
            "bigQueryTable"
        };
        
        propertiesReader.validateRequiredProperties(requiredProperties);
    }
    
    /**
     * Merge command line arguments, giving precedence to original args
     */
    private static String[] mergeArguments(String[] propertyArgs, String[] originalArgs) {
        // Convert arrays to lists for easier manipulation
        java.util.List<String> mergedList = new java.util.ArrayList<>();
        java.util.Set<String> originalArgKeys = new java.util.HashSet<>();
        
        // First, add all original arguments and track their keys
        for (String arg : originalArgs) {
            mergedList.add(arg);
            if (arg.startsWith("--")) {
                String key = arg.split("=")[0];
                originalArgKeys.add(key);
            }
        }
        
        // Then add property arguments that don't conflict with original args
        for (String arg : propertyArgs) {
            if (arg.startsWith("--")) {
                String key = arg.split("=")[0];
                if (!originalArgKeys.contains(key)) {
                    mergedList.add(arg);
                }
            }
        }
        
        return mergedList.toArray(new String[0]);
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