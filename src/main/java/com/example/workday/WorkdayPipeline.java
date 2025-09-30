package com.example.workday;

import com.example.workday.io.WorkdayIO;
import com.example.workday.model.Employee;
import com.example.workday.options.WorkdayPipelineOptions;
import com.example.workday.transform.EmployeeTransform;
import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import com.google.api.services.bigquery.model.TimePartitioning;
import com.google.api.services.bigquery.model.Clustering;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO.Write.CreateDisposition;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO.Write.WriteDisposition;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main Apache Beam pipeline for Workday data extraction to BigQuery
 * Supports both historical and incremental data loads with pagination
 */
public class WorkdayPipeline {
    private static final Logger LOG = LoggerFactory.getLogger(WorkdayPipeline.class);
    
    public static void main(String[] args) {
        // Parse pipeline options
        WorkdayPipelineOptions options = PipelineOptionsFactory
            .fromArgs(args)
            .withValidation()
            .as(WorkdayPipelineOptions.class);
        
        // Run the pipeline
        run(options);
    }
    
    public static void run(WorkdayPipelineOptions options) {
        // Validate and set defaults
        validateOptions(options);
        
        // Create the pipeline
        Pipeline pipeline = Pipeline.create(options);
        
        // Determine load type and dates
        LocalDate effectiveDate = null;
        LocalDateTime lastModifiedFrom = null;
        
        if ("HISTORICAL".equalsIgnoreCase(options.getLoadType())) {
            LOG.info("Running HISTORICAL load");
            // For historical load, we might want to set a specific effective date
            if (options.getEffectiveDate() != null) {
                effectiveDate = LocalDate.parse(options.getEffectiveDate());
            } else {
                effectiveDate = LocalDate.now(); // Current snapshot
            }
        } else {
            LOG.info("Running INCREMENTAL load");
            // For incremental load, use last modified from
            if (options.getLastModifiedFrom() != null) {
                lastModifiedFrom = LocalDateTime.parse(options.getLastModifiedFrom());
            } else {
                // Default to last 24 hours if not specified
                lastModifiedFrom = LocalDateTime.now().minusDays(1);
            }
            
            // Set effective date to today for incremental loads
            effectiveDate = LocalDate.now();
        }
        
        LOG.info("Effective Date: {}, Last Modified From: {}", effectiveDate, lastModifiedFrom);
        
        // Build Workday Read transform
        WorkdayIO.Read workdayRead = WorkdayIO.read()
            .withWorkdayEndpoint(options.getWorkdayEndpoint())
            .withCredentials(
                options.getWorkdayUsername(),
                options.getWorkdayPassword(),
                options.getWorkdayTenant()
            )
            .withEffectiveDate(effectiveDate)
            .withLastModifiedFrom(lastModifiedFrom);
        
        // Add proxy if configured
        if (options.getUseProxy() && options.getProxyHost() != null) {
            workdayRead = workdayRead.withProxy(options.getProxyHost(), options.getProxyPort());
        }
        
        // Read from Workday
        PCollection<Employee> employees = pipeline
            .apply("Read from Workday", workdayRead);
        
        // Apply transformations
        PCollection<Employee> transformedEmployees = employees
            .apply("Transform Employees", ParDo.of(new EmployeeTransform()));
        
        // Convert to BigQuery TableRow
        PCollection<TableRow> tableRows = transformedEmployees
            .apply("Convert to TableRow", ParDo.of(new DoFn<Employee, TableRow>() {
                @ProcessElement
                public void processElement(ProcessContext c) {
                    Employee employee = c.element();
                    TableRow row = employee.toBigQueryRow();
                    c.output(row);
                }
            }));
        
        // Add logging for monitoring
        tableRows.apply("Count Records", Count.globally())
            .apply("Log Count", MapElements
                .into(TypeDescriptors.voids())
                .via(count -> {
                    LOG.info("Total records to write to BigQuery: {}", count);
                    return null;
                }));
        
        // Build BigQuery write transform
        BigQueryIO.Write<TableRow> bigQueryWrite = BigQueryIO.writeTableRows()
            .to(String.format("%s:%s.%s", 
                options.getProject(),
                options.getBigQueryDataset(),
                options.getBigQueryTable()))
            .withSchema(createTableSchema())
            .withCreateDisposition(
                CreateDisposition.valueOf(options.getCreateDisposition()))
            .withWriteDisposition(
                WriteDisposition.valueOf(options.getWriteDisposition()));
        
        // Add partitioning if enabled
        if (options.getEnablePartitioning()) {
            TimePartitioning partitioning = new TimePartitioning()
                .setType("DAY")
                .setField(options.getPartitionField());
            bigQueryWrite = bigQueryWrite.withTimePartitioning(partitioning);
            
            // Add clustering if specified
            if (options.getClusteringFields() != null && !options.getClusteringFields().isEmpty()) {
                List<String> clusteringFields = Arrays.asList(options.getClusteringFields().split(","));
                Clustering clustering = new Clustering().setFields(clusteringFields);
                bigQueryWrite = bigQueryWrite.withClustering(clustering);
            }
        }
        
        // Set temporary GCS bucket if specified
        if (options.getTempGcsBucket() != null) {
            bigQueryWrite = bigQueryWrite.withCustomGcsTempLocation(options.getTempGcsBucket());
        }
        
        // Write to BigQuery
        tableRows.apply("Write to BigQuery", bigQueryWrite);
        
        // Run the pipeline
        LOG.info("Starting pipeline execution");
        pipeline.run().waitUntilFinish();
        LOG.info("Pipeline execution completed");
    }
    
    private static void validateOptions(WorkdayPipelineOptions options) {
        // Validate required options
        if (options.getWorkdayEndpoint() == null || options.getWorkdayEndpoint().isEmpty()) {
            throw new IllegalArgumentException("Workday endpoint is required");
        }
        
        if (options.getWorkdayUsername() == null || options.getWorkdayUsername().isEmpty()) {
            throw new IllegalArgumentException("Workday username is required");
        }
        
        if (options.getWorkdayPassword() == null || options.getWorkdayPassword().isEmpty()) {
            throw new IllegalArgumentException("Workday password is required");
        }
        
        if (options.getWorkdayTenant() == null || options.getWorkdayTenant().isEmpty()) {
            throw new IllegalArgumentException("Workday tenant is required");
        }
        
        if (options.getBigQueryDataset() == null || options.getBigQueryDataset().isEmpty()) {
            throw new IllegalArgumentException("BigQuery dataset is required");
        }
        
        // Validate load type
        String loadType = options.getLoadType();
        if (!("HISTORICAL".equalsIgnoreCase(loadType) || "INCREMENTAL".equalsIgnoreCase(loadType))) {
            throw new IllegalArgumentException("Load type must be HISTORICAL or INCREMENTAL");
        }
        
        // Validate write disposition
        try {
            WriteDisposition.valueOf(options.getWriteDisposition());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid write disposition: " + options.getWriteDisposition());
        }
        
        // Validate create disposition
        try {
            CreateDisposition.valueOf(options.getCreateDisposition());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid create disposition: " + options.getCreateDisposition());
        }
    }
    
    private static TableSchema createTableSchema() {
        List<TableFieldSchema> fields = new ArrayList<>();
        
        // Employee identifiers
        fields.add(new TableFieldSchema().setName("employee_id").setType("STRING").setMode("REQUIRED"));
        fields.add(new TableFieldSchema().setName("worker_reference_id").setType("STRING"));
        
        // Personal information
        fields.add(new TableFieldSchema().setName("first_name").setType("STRING"));
        fields.add(new TableFieldSchema().setName("last_name").setType("STRING"));
        fields.add(new TableFieldSchema().setName("preferred_name").setType("STRING"));
        fields.add(new TableFieldSchema().setName("email").setType("STRING"));
        fields.add(new TableFieldSchema().setName("phone_number").setType("STRING"));
        
        // Job information
        fields.add(new TableFieldSchema().setName("job_title").setType("STRING"));
        fields.add(new TableFieldSchema().setName("department").setType("STRING"));
        fields.add(new TableFieldSchema().setName("location").setType("STRING"));
        fields.add(new TableFieldSchema().setName("manager_id").setType("STRING"));
        fields.add(new TableFieldSchema().setName("manager_name").setType("STRING"));
        
        // Employment details
        fields.add(new TableFieldSchema().setName("employment_status").setType("STRING"));
        fields.add(new TableFieldSchema().setName("hire_date").setType("DATE"));
        fields.add(new TableFieldSchema().setName("termination_date").setType("DATE"));
        fields.add(new TableFieldSchema().setName("employee_type").setType("STRING"));
        
        // Organization
        fields.add(new TableFieldSchema().setName("cost_center").setType("STRING"));
        fields.add(new TableFieldSchema().setName("business_unit").setType("STRING"));
        
        // Compensation
        fields.add(new TableFieldSchema().setName("salary").setType("FLOAT64"));
        fields.add(new TableFieldSchema().setName("currency").setType("STRING"));
        fields.add(new TableFieldSchema().setName("pay_frequency").setType("STRING"));
        
        // Metadata
        fields.add(new TableFieldSchema().setName("effective_date").setType("DATE"));
        fields.add(new TableFieldSchema().setName("last_modified_date").setType("DATETIME"));
        fields.add(new TableFieldSchema().setName("data_source").setType("STRING"));
        fields.add(new TableFieldSchema().setName("extracted_at").setType("DATETIME").setMode("REQUIRED"));
        
        return new TableSchema().setFields(fields);
    }
}