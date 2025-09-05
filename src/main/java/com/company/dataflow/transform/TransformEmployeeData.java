package com.company.dataflow.transform;

import com.company.dataflow.model.WorkdayEmployee;
import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Transform to convert WorkdayEmployee objects to BigQuery TableRow format.
 */
public class TransformEmployeeData extends PTransform<PCollection<WorkdayEmployee>, PCollection<TableRow>> {
    
    private static final Logger LOG = LoggerFactory.getLogger(TransformEmployeeData.class);
    
    @Override
    public PCollection<TableRow> expand(PCollection<WorkdayEmployee> input) {
        return input.apply("Transform to TableRow", ParDo.of(new ConvertToTableRowFn()));
    }
    
    /**
     * DoFn to convert WorkdayEmployee to BigQuery TableRow.
     */
    private static class ConvertToTableRowFn extends DoFn<WorkdayEmployee, TableRow> {
        
        @ProcessElement
        public void processElement(@Element WorkdayEmployee employee, OutputReceiver<TableRow> out) {
            try {
                TableRow row = new TableRow();
                
                // Basic employee information
                row.set("employee_id", sanitizeString(employee.getEmployeeId()));
                row.set("first_name", sanitizeString(employee.getFirstName()));
                row.set("last_name", sanitizeString(employee.getLastName()));
                row.set("email", sanitizeString(employee.getEmail()));
                
                // Employment details
                row.set("department", sanitizeString(employee.getDepartment()));
                row.set("job_title", sanitizeString(employee.getJobTitle()));
                row.set("manager_id", sanitizeString(employee.getManagerId()));
                row.set("status", sanitizeString(employee.getStatus()));
                row.set("location", sanitizeString(employee.getLocation()));
                
                // Salary (handle null values)
                if (employee.getSalary() != null) {
                    row.set("salary", employee.getSalary());
                } else {
                    row.set("salary", null);
                }
                
                // Date fields with proper formatting
                row.set("hire_date", formatDate(employee.getHireDate()));
                row.set("last_modified", formatDate(employee.getLastModified()));
                
                // Ingestion timestamp
                if (employee.getIngestionTimestamp() != null) {
                    row.set("ingestion_timestamp", employee.getIngestionTimestamp().toString());
                } else {
                    row.set("ingestion_timestamp", Instant.now().toString());
                }
                
                // Data quality indicators
                row.set("is_complete", isRecordComplete(employee));
                row.set("data_source", "workday_soap_api");
                
                out.output(row);
                
            } catch (Exception e) {
                LOG.error("Error transforming employee record: {}", employee, e);
                // Output error record for monitoring
                TableRow errorRow = createErrorRecord(employee, e);
                out.output(errorRow);
            }
        }
        
        /**
         * Sanitizes string fields to handle null values and trim whitespace.
         */
        private String sanitizeString(String value) {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            return value.trim();
        }
        
        /**
         * Formats date strings to ISO format for BigQuery.
         */
        private String formatDate(String dateStr) {
            if (dateStr == null || dateStr.trim().isEmpty()) {
                return null;
            }
            
            try {
                // Try to parse and reformat the date
                // Adjust parsing logic based on Workday date format
                if (dateStr.contains("T")) {
                    // Already in ISO format
                    return dateStr;
                } else if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    // YYYY-MM-DD format
                    return dateStr + "T00:00:00Z";
                } else {
                    // Return as-is if format is unknown
                    return dateStr;
                }
            } catch (DateTimeParseException e) {
                LOG.warn("Could not parse date: {}", dateStr);
                return dateStr; // Return original if parsing fails
            }
        }
        
        /**
         * Checks if the employee record has all required fields.
         */
        private boolean isRecordComplete(WorkdayEmployee employee) {
            return employee.getEmployeeId() != null && 
                   !employee.getEmployeeId().trim().isEmpty() &&
                   employee.getFirstName() != null && 
                   !employee.getFirstName().trim().isEmpty() &&
                   employee.getLastName() != null && 
                   !employee.getLastName().trim().isEmpty();
        }
        
        /**
         * Creates an error record for failed transformations.
         */
        private TableRow createErrorRecord(WorkdayEmployee employee, Exception error) {
            TableRow errorRow = new TableRow();
            
            // Try to preserve what we can
            errorRow.set("employee_id", sanitizeString(employee.getEmployeeId()));
            errorRow.set("first_name", sanitizeString(employee.getFirstName()));
            errorRow.set("last_name", sanitizeString(employee.getLastName()));
            errorRow.set("email", sanitizeString(employee.getEmail()));
            errorRow.set("department", null);
            errorRow.set("job_title", null);
            errorRow.set("manager_id", null);
            errorRow.set("status", "ERROR");
            errorRow.set("location", null);
            errorRow.set("salary", null);
            errorRow.set("hire_date", null);
            errorRow.set("last_modified", null);
            errorRow.set("ingestion_timestamp", Instant.now().toString());
            errorRow.set("is_complete", false);
            errorRow.set("data_source", "workday_soap_api");
            errorRow.set("error_message", error.getMessage());
            
            return errorRow;
        }
    }
}