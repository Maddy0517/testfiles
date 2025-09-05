package com.company.dataflow.util;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for BigQuery schema definitions.
 */
public class BigQuerySchemaUtils {
    
    /**
     * Creates the BigQuery table schema for employee data.
     */
    public static TableSchema createEmployeeTableSchema() {
        List<TableFieldSchema> fields = new ArrayList<>();
        
        // Employee identification
        fields.add(new TableFieldSchema()
                .setName("employee_id")
                .setType("STRING")
                .setMode("REQUIRED")
                .setDescription("Unique employee identifier from Workday"));
        
        fields.add(new TableFieldSchema()
                .setName("first_name")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Employee first name"));
        
        fields.add(new TableFieldSchema()
                .setName("last_name")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Employee last name"));
        
        fields.add(new TableFieldSchema()
                .setName("email")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Employee email address"));
        
        // Employment information
        fields.add(new TableFieldSchema()
                .setName("department")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Employee department"));
        
        fields.add(new TableFieldSchema()
                .setName("job_title")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Employee job title"));
        
        fields.add(new TableFieldSchema()
                .setName("manager_id")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Manager employee ID"));
        
        fields.add(new TableFieldSchema()
                .setName("status")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Employee status (Active, Inactive, etc.)"));
        
        fields.add(new TableFieldSchema()
                .setName("location")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Employee work location"));
        
        // Compensation
        fields.add(new TableFieldSchema()
                .setName("salary")
                .setType("NUMERIC")
                .setMode("NULLABLE")
                .setDescription("Employee salary"));
        
        // Dates
        fields.add(new TableFieldSchema()
                .setName("hire_date")
                .setType("TIMESTAMP")
                .setMode("NULLABLE")
                .setDescription("Employee hire date"));
        
        fields.add(new TableFieldSchema()
                .setName("last_modified")
                .setType("TIMESTAMP")
                .setMode("NULLABLE")
                .setDescription("Last modification timestamp in Workday"));
        
        // Metadata
        fields.add(new TableFieldSchema()
                .setName("ingestion_timestamp")
                .setType("TIMESTAMP")
                .setMode("REQUIRED")
                .setDescription("Timestamp when record was ingested"));
        
        fields.add(new TableFieldSchema()
                .setName("is_complete")
                .setType("BOOLEAN")
                .setMode("REQUIRED")
                .setDescription("Indicates if all required fields are present"));
        
        fields.add(new TableFieldSchema()
                .setName("data_source")
                .setType("STRING")
                .setMode("REQUIRED")
                .setDescription("Source system (workday_soap_api)"));
        
        fields.add(new TableFieldSchema()
                .setName("error_message")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Error message if processing failed"));
        
        return new TableSchema().setFields(fields);
    }
    
    /**
     * Creates a partitioned table schema with clustering.
     */
    public static TableSchema createPartitionedEmployeeTableSchema() {
        TableSchema schema = createEmployeeTableSchema();
        
        // Add partition field
        List<TableFieldSchema> fields = new ArrayList<>(schema.getFields());
        fields.add(new TableFieldSchema()
                .setName("partition_date")
                .setType("DATE")
                .setMode("REQUIRED")
                .setDescription("Partition date (YYYY-MM-DD) for table partitioning"));
        
        return schema.setFields(fields);
    }
}