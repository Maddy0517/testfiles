package com.example.workday;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableSchema;

import java.util.Arrays;
import java.util.List;

/**
 * Utility class for generating BigQuery table schemas
 */
public class BigQuerySchemaGenerator {
    
    /**
     * Generate a generic schema for Workday records
     */
    public static TableSchema generateWorkdaySchema() {
        List<TableFieldSchema> fields = Arrays.asList(
            new TableFieldSchema()
                .setName("id")
                .setType("STRING")
                .setMode("REQUIRED")
                .setDescription("Unique identifier from Workday"),
            
            new TableFieldSchema()
                .setName("data")
                .setType("JSON")
                .setMode("NULLABLE")
                .setDescription("JSON representation of the Workday record data"),
            
            new TableFieldSchema()
                .setName("timestamp")
                .setType("TIMESTAMP")
                .setMode("REQUIRED")
                .setDescription("Timestamp when the record was processed"),
            
            new TableFieldSchema()
                .setName("source_system")
                .setType("STRING")
                .setMode("REQUIRED")
                .setDescription("Source system identifier"),
            
            new TableFieldSchema()
                .setName("load_date")
                .setType("DATE")
                .setMode("REQUIRED")
                .setDescription("Date when the record was loaded"),
            
            new TableFieldSchema()
                .setName("load_timestamp")
                .setType("TIMESTAMP")
                .setMode("REQUIRED")
                .setDescription("Timestamp when the record was loaded into BigQuery")
        );
        
        return new TableSchema().setFields(fields);
    }
    
    /**
     * Generate schema for worker data specifically
     */
    public static TableSchema generateWorkerSchema() {
        List<TableFieldSchema> fields = Arrays.asList(
            new TableFieldSchema()
                .setName("worker_id")
                .setType("STRING")
                .setMode("REQUIRED")
                .setDescription("Unique worker identifier"),
            
            new TableFieldSchema()
                .setName("employee_id")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Employee ID"),
            
            new TableFieldSchema()
                .setName("first_name")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("First name"),
            
            new TableFieldSchema()
                .setName("last_name")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Last name"),
            
            new TableFieldSchema()
                .setName("email")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Email address"),
            
            new TableFieldSchema()
                .setName("hire_date")
                .setType("DATE")
                .setMode("NULLABLE")
                .setDescription("Hire date"),
            
            new TableFieldSchema()
                .setName("job_title")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Job title"),
            
            new TableFieldSchema()
                .setName("department")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Department"),
            
            new TableFieldSchema()
                .setName("manager_id")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Manager's worker ID"),
            
            new TableFieldSchema()
                .setName("status")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Employment status"),
            
            new TableFieldSchema()
                .setName("raw_data")
                .setType("JSON")
                .setMode("NULLABLE")
                .setDescription("Complete raw data from Workday"),
            
            new TableFieldSchema()
                .setName("load_timestamp")
                .setType("TIMESTAMP")
                .setMode("REQUIRED")
                .setDescription("Timestamp when the record was loaded")
        );
        
        return new TableSchema().setFields(fields);
    }
    
    /**
     * Generate schema for organization data
     */
    public static TableSchema generateOrganizationSchema() {
        List<TableFieldSchema> fields = Arrays.asList(
            new TableFieldSchema()
                .setName("organization_id")
                .setType("STRING")
                .setMode("REQUIRED")
                .setDescription("Unique organization identifier"),
            
            new TableFieldSchema()
                .setName("organization_name")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Organization name"),
            
            new TableFieldSchema()
                .setName("organization_type")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Type of organization"),
            
            new TableFieldSchema()
                .setName("parent_organization_id")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Parent organization ID"),
            
            new TableFieldSchema()
                .setName("organization_code")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Organization code"),
            
            new TableFieldSchema()
                .setName("effective_date")
                .setType("DATE")
                .setMode("NULLABLE")
                .setDescription("Effective date"),
            
            new TableFieldSchema()
                .setName("raw_data")
                .setType("JSON")
                .setMode("NULLABLE")
                .setDescription("Complete raw data from Workday"),
            
            new TableFieldSchema()
                .setName("load_timestamp")
                .setType("TIMESTAMP")
                .setMode("REQUIRED")
                .setDescription("Timestamp when the record was loaded")
        );
        
        return new TableSchema().setFields(fields);
    }
}