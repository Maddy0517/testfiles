package com.company.workday;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableSchema;

import java.util.Arrays;
import java.util.List;

/**
 * Utility class for defining BigQuery table schemas for Workday employee data.
 */
public class BigQuerySchemaUtil {
    
    /**
     * Returns the BigQuery table schema for employee data.
     * This schema matches the structure of the WorkdayEmployee class.
     */
    public static TableSchema getEmployeeTableSchema() {
        List<TableFieldSchema> fields = Arrays.asList(
            // Basic Information
            new TableFieldSchema().setName("worker_id").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("employee_id").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("first_name").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("last_name").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("full_name").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("email").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("phone_number").setType("STRING").setMode("NULLABLE"),
            
            // Employment Information
            new TableFieldSchema().setName("employment_status").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("job_title").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("department").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("location").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("manager").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("manager_id").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("cost_center").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("business_unit").setType("STRING").setMode("NULLABLE"),
            
            // Compensation Information
            new TableFieldSchema().setName("annual_salary").setType("FLOAT").setMode("NULLABLE"),
            new TableFieldSchema().setName("currency").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("pay_group").setType("STRING").setMode("NULLABLE"),
            
            // Date Information
            new TableFieldSchema().setName("hire_date").setType("DATE").setMode("NULLABLE"),
            new TableFieldSchema().setName("termination_date").setType("DATE").setMode("NULLABLE"),
            new TableFieldSchema().setName("effective_date").setType("DATE").setMode("NULLABLE"),
            
            // Audit Information
            new TableFieldSchema().setName("last_modified_by").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("last_modified_date").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("processed_timestamp").setType("TIMESTAMP").setMode("NULLABLE")
        );
        
        return new TableSchema().setFields(fields);
    }
    
    /**
     * Returns the BigQuery table schema for error/dead letter records.
     */
    public static TableSchema getErrorTableSchema() {
        List<TableFieldSchema> fields = Arrays.asList(
            new TableFieldSchema().setName("error_timestamp").setType("TIMESTAMP").setMode("REQUIRED"),
            new TableFieldSchema().setName("worker_id").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("error_message").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("error_details").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("raw_data").setType("STRING").setMode("NULLABLE"),
            new TableFieldSchema().setName("pipeline_job_id").setType("STRING").setMode("NULLABLE")
        );
        
        return new TableSchema().setFields(fields);
    }
    
    /**
     * Returns the DDL statement for creating the employee table in BigQuery.
     */
    public static String getCreateEmployeeTableDDL(String projectId, String datasetId, String tableId) {
        return String.format("""
            CREATE TABLE IF NOT EXISTS `%s.%s.%s` (
              worker_id STRING,
              employee_id STRING,
              first_name STRING,
              last_name STRING,
              full_name STRING,
              email STRING,
              phone_number STRING,
              employment_status STRING,
              job_title STRING,
              department STRING,
              location STRING,
              manager STRING,
              manager_id STRING,
              cost_center STRING,
              business_unit STRING,
              annual_salary FLOAT64,
              currency STRING,
              pay_group STRING,
              hire_date DATE,
              termination_date DATE,
              effective_date DATE,
              last_modified_by STRING,
              last_modified_date STRING,
              processed_timestamp TIMESTAMP
            )
            PARTITION BY DATE(processed_timestamp)
            CLUSTER BY department, location
            OPTIONS (
              description = "Workday employee data extracted via SOAP API",
              partition_expiration_days = 7300,  -- 20 years
              require_partition_filter = false
            )
            """, projectId, datasetId, tableId);
    }
}