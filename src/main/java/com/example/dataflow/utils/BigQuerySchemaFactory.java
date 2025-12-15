package com.example.dataflow.utils;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory class to create BigQuery table schemas
 */
public class BigQuerySchemaFactory {
    
    /**
     * Create schema for employee data table
     */
    public static TableSchema createEmployeeSchema() {
        List<TableFieldSchema> fields = new ArrayList<>();
        
        fields.add(new TableFieldSchema()
                .setName("employee_id")
                .setType("STRING")
                .setMode("REQUIRED")
                .setDescription("Unique employee identifier"));
        
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
        
        fields.add(new TableFieldSchema()
                .setName("phone")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Employee phone number"));
        
        fields.add(new TableFieldSchema()
                .setName("hire_date")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Employee hire date"));
        
        fields.add(new TableFieldSchema()
                .setName("job_title")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Employee job title"));
        
        fields.add(new TableFieldSchema()
                .setName("department")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Employee department"));
        
        fields.add(new TableFieldSchema()
                .setName("manager_id")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Manager employee ID"));
        
        fields.add(new TableFieldSchema()
                .setName("location")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Employee work location"));
        
        fields.add(new TableFieldSchema()
                .setName("employment_status")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Employment status (Active, Terminated, etc.)"));
        
        fields.add(new TableFieldSchema()
                .setName("effective_date")
                .setType("STRING")
                .setMode("NULLABLE")
                .setDescription("Effective date for the data"));
        
        fields.add(new TableFieldSchema()
                .setName("ingestion_timestamp")
                .setType("TIMESTAMP")
                .setMode("REQUIRED")
                .setDescription("Timestamp when data was ingested"));
        
        return new TableSchema().setFields(fields);
    }
}
