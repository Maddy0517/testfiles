package com.workday.dataflow;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.bigquery.WriteResult;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Apache Beam transform to write worker data to Google Cloud BigQuery.
 */
public class BigQuerySinkTransform extends PTransform<PCollection<TableRow>, WriteResult> {
    private static final Logger LOG = LoggerFactory.getLogger(BigQuerySinkTransform.class);
    
    private final WorkdayToBigQueryOptions options;

    public BigQuerySinkTransform(WorkdayToBigQueryOptions options) {
        this.options = options;
    }

    @Override
    public WriteResult expand(PCollection<TableRow> input) {
        String tableSpec = String.format("%s:%s.%s", 
            options.getBigQueryProject(),
            options.getBigQueryDataset(),
            options.getBigQueryTable()
        );

        LOG.info("Writing to BigQuery table: {}", tableSpec);

        BigQueryIO.Write<TableRow> bigQueryWrite = BigQueryIO.writeTableRows()
            .to(tableSpec)
            .withSchema(getTableSchema())
            .withCreateDisposition(getCreateDisposition())
            .withWriteDisposition(getWriteDisposition())
            .withMethod(BigQueryIO.Write.Method.STREAMING_INSERTS)
            .withFailedInsertRetryPolicy(BigQueryIO.Write.RetryPolicy.retryTransientErrors());

        // Add custom table description if needed
        if (options.getBigQuerySchema() != null) {
            LOG.info("Using custom schema from: {}", options.getBigQuerySchema());
            // In a real implementation, you would load the schema from the file
        }

        return input.apply("Write to BigQuery", bigQueryWrite);
    }

    /**
     * Define the BigQuery table schema
     */
    private TableSchema getTableSchema() {
        List<TableFieldSchema> fields = Arrays.asList(
            new TableFieldSchema().setName("worker_id").setType("STRING").setMode("REQUIRED")
                .setDescription("Unique identifier for the worker in Workday"),
            
            new TableFieldSchema().setName("employee_id").setType("STRING").setMode("REQUIRED")
                .setDescription("Employee ID number"),
            
            new TableFieldSchema().setName("first_name").setType("STRING").setMode("NULLABLE")
                .setDescription("Employee first name"),
            
            new TableFieldSchema().setName("last_name").setType("STRING").setMode("NULLABLE")
                .setDescription("Employee last name"),
            
            new TableFieldSchema().setName("full_name").setType("STRING").setMode("NULLABLE")
                .setDescription("Employee full name (computed field)"),
            
            new TableFieldSchema().setName("email").setType("STRING").setMode("NULLABLE")
                .setDescription("Employee email address"),
            
            new TableFieldSchema().setName("job_title").setType("STRING").setMode("NULLABLE")
                .setDescription("Employee job title"),
            
            new TableFieldSchema().setName("department").setType("STRING").setMode("NULLABLE")
                .setDescription("Employee department"),
            
            new TableFieldSchema().setName("hire_date").setType("DATE").setMode("NULLABLE")
                .setDescription("Employee hire date"),
            
            new TableFieldSchema().setName("status").setType("STRING").setMode("NULLABLE")
                .setDescription("Employee status (Active, Inactive, etc.)"),
            
            new TableFieldSchema().setName("salary").setType("FLOAT").setMode("NULLABLE")
                .setDescription("Employee salary"),
            
            new TableFieldSchema().setName("location").setType("STRING").setMode("NULLABLE")
                .setDescription("Employee work location"),
            
            new TableFieldSchema().setName("manager_id").setType("STRING").setMode("NULLABLE")
                .setDescription("Manager's worker ID"),
            
            new TableFieldSchema().setName("last_modified").setType("TIMESTAMP").setMode("NULLABLE")
                .setDescription("Last modified timestamp from Workday"),
            
            new TableFieldSchema().setName("processed_timestamp").setType("TIMESTAMP").setMode("REQUIRED")
                .setDescription("Timestamp when the record was processed by the pipeline"),
            
            new TableFieldSchema().setName("years_of_service").setType("INTEGER").setMode("NULLABLE")
                .setDescription("Calculated years of service (computed field)"),
            
            new TableFieldSchema().setName("salary_band").setType("STRING").setMode("NULLABLE")
                .setDescription("Salary band category (computed field)")
        );

        return new TableSchema().setFields(fields);
    }

    /**
     * Get BigQuery create disposition from options
     */
    private BigQueryIO.Write.CreateDisposition getCreateDisposition() {
        String disposition = options.getCreateDisposition();
        if (disposition == null) {
            return BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED;
        }

        switch (disposition.toUpperCase()) {
            case "CREATE_IF_NEEDED":
                return BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED;
            case "CREATE_NEVER":
                return BigQueryIO.Write.CreateDisposition.CREATE_NEVER;
            default:
                LOG.warn("Unknown create disposition: {}. Using CREATE_IF_NEEDED", disposition);
                return BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED;
        }
    }

    /**
     * Get BigQuery write disposition from options
     */
    private BigQueryIO.Write.WriteDisposition getWriteDisposition() {
        String disposition = options.getWriteDisposition();
        if (disposition == null) {
            return BigQueryIO.Write.WriteDisposition.WRITE_APPEND;
        }

        switch (disposition.toUpperCase()) {
            case "WRITE_TRUNCATE":
                return BigQueryIO.Write.WriteDisposition.WRITE_TRUNCATE;
            case "WRITE_APPEND":
                return BigQueryIO.Write.WriteDisposition.WRITE_APPEND;
            case "WRITE_EMPTY":
                return BigQueryIO.Write.WriteDisposition.WRITE_EMPTY;
            default:
                LOG.warn("Unknown write disposition: {}. Using WRITE_APPEND", disposition);
                return BigQueryIO.Write.WriteDisposition.WRITE_APPEND;
        }
    }
}