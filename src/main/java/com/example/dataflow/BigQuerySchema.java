package com.example.dataflow;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * BigQuery schema definition for employee table
 */
public class BigQuerySchema {
    
    /**
     * Create employee table schema
     */
    public static TableSchema getEmployeeSchema() {
        List<TableFieldSchema> fields = new ArrayList<>();
        
        fields.add(new TableFieldSchema()
                .setName("employee_id")
                .setType("STRING")
                .setMode("REQUIRED"));
        
        fields.add(new TableFieldSchema()
                .setName("first_name")
                .setType("STRING")
                .setMode("NULLABLE"));
        
        fields.add(new TableFieldSchema()
                .setName("last_name")
                .setType("STRING")
                .setMode("NULLABLE"));
        
        fields.add(new TableFieldSchema()
                .setName("email")
                .setType("STRING")
                .setMode("NULLABLE"));
        
        fields.add(new TableFieldSchema()
                .setName("phone")
                .setType("STRING")
                .setMode("NULLABLE"));
        
        fields.add(new TableFieldSchema()
                .setName("hire_date")
                .setType("STRING")
                .setMode("NULLABLE"));
        
        fields.add(new TableFieldSchema()
                .setName("job_title")
                .setType("STRING")
                .setMode("NULLABLE"));
        
        fields.add(new TableFieldSchema()
                .setName("department")
                .setType("STRING")
                .setMode("NULLABLE"));
        
        fields.add(new TableFieldSchema()
                .setName("manager_id")
                .setType("STRING")
                .setMode("NULLABLE"));
        
        fields.add(new TableFieldSchema()
                .setName("location")
                .setType("STRING")
                .setMode("NULLABLE"));
        
        fields.add(new TableFieldSchema()
                .setName("employment_status")
                .setType("STRING")
                .setMode("NULLABLE"));
        
        fields.add(new TableFieldSchema()
                .setName("effective_date")
                .setType("STRING")
                .setMode("NULLABLE"));
        
        fields.add(new TableFieldSchema()
                .setName("ingestion_timestamp")
                .setType("TIMESTAMP")
                .setMode("REQUIRED"));
        
        return new TableSchema().setFields(fields);
    }
}
