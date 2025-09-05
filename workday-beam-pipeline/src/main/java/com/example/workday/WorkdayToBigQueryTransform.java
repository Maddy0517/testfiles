package com.example.workday;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Transform to convert WorkdayRecord to BigQuery TableRow
 */
public class WorkdayToBigQueryTransform extends PTransform<PCollection<WorkdayRecord>, PCollection<TableRow>> {
    private static final Logger LOG = LoggerFactory.getLogger(WorkdayToBigQueryTransform.class);
    
    private final String transformationType;
    
    public WorkdayToBigQueryTransform(String transformationType) {
        this.transformationType = transformationType != null ? transformationType : "generic";
    }
    
    @Override
    public PCollection<TableRow> expand(PCollection<WorkdayRecord> input) {
        return input.apply("Convert to BigQuery TableRow", 
                          ParDo.of(new WorkdayToBigQueryFn(transformationType)));
    }
    
    /**
     * DoFn to convert WorkdayRecord to TableRow
     */
    public static class WorkdayToBigQueryFn extends DoFn<WorkdayRecord, TableRow> {
        private static final Logger LOG = LoggerFactory.getLogger(WorkdayToBigQueryFn.class);
        
        private final String transformationType;
        private transient ObjectMapper objectMapper;
        
        public WorkdayToBigQueryFn(String transformationType) {
            this.transformationType = transformationType;
        }
        
        @Setup
        public void setup() {
            objectMapper = new ObjectMapper();
        }
        
        @ProcessElement
        public void processElement(@Element WorkdayRecord record, OutputReceiver<TableRow> out) {
            try {
                TableRow row;
                
                switch (transformationType.toLowerCase()) {
                    case "worker":
                        row = transformWorkerRecord(record);
                        break;
                    case "organization":
                        row = transformOrganizationRecord(record);
                        break;
                    default:
                        row = transformGenericRecord(record);
                        break;
                }
                
                if (row != null) {
                    out.output(row);
                }
                
            } catch (Exception e) {
                LOG.error("Error transforming record {}: {}", record.getId(), e.getMessage(), e);
                // Optionally output to dead letter queue or error collection
            }
        }
        
        /**
         * Transform generic WorkdayRecord to TableRow
         */
        private TableRow transformGenericRecord(WorkdayRecord record) throws Exception {
            TableRow row = new TableRow();
            
            row.set("id", record.getId());
            row.set("data", objectMapper.writeValueAsString(record.getData()));
            row.set("timestamp", record.getTimestamp());
            row.set("source_system", record.getSourceSystem());
            row.set("load_date", LocalDate.now().toString());
            row.set("load_timestamp", Instant.now().toString());
            
            return row;
        }
        
        /**
         * Transform worker-specific data
         */
        private TableRow transformWorkerRecord(WorkdayRecord record) throws Exception {
            TableRow row = new TableRow();
            Map<String, Object> data = record.getData();
            
            row.set("worker_id", record.getId());
            row.set("employee_id", getStringValue(data, "Employee_ID"));
            row.set("first_name", getStringValue(data, "First_Name"));
            row.set("last_name", getStringValue(data, "Last_Name"));
            row.set("email", getStringValue(data, "Email_Address"));
            row.set("hire_date", formatDate(getStringValue(data, "Hire_Date")));
            row.set("job_title", getStringValue(data, "Job_Title"));
            row.set("department", getStringValue(data, "Department"));
            row.set("manager_id", getStringValue(data, "Manager_ID"));
            row.set("status", getStringValue(data, "Employment_Status"));
            row.set("raw_data", objectMapper.writeValueAsString(data));
            row.set("load_timestamp", Instant.now().toString());
            
            return row;
        }
        
        /**
         * Transform organization-specific data
         */
        private TableRow transformOrganizationRecord(WorkdayRecord record) throws Exception {
            TableRow row = new TableRow();
            Map<String, Object> data = record.getData();
            
            row.set("organization_id", record.getId());
            row.set("organization_name", getStringValue(data, "Organization_Name"));
            row.set("organization_type", getStringValue(data, "Organization_Type"));
            row.set("parent_organization_id", getStringValue(data, "Parent_Organization_ID"));
            row.set("organization_code", getStringValue(data, "Organization_Code"));
            row.set("effective_date", formatDate(getStringValue(data, "Effective_Date")));
            row.set("raw_data", objectMapper.writeValueAsString(data));
            row.set("load_timestamp", Instant.now().toString());
            
            return row;
        }
        
        /**
         * Safely get string value from map
         */
        private String getStringValue(Map<String, Object> data, String key) {
            Object value = data.get(key);
            return value != null ? value.toString() : null;
        }
        
        /**
         * Format date string to YYYY-MM-DD format
         */
        private String formatDate(String dateString) {
            if (dateString == null || dateString.trim().isEmpty()) {
                return null;
            }
            
            try {
                // Try parsing different date formats commonly used by Workday
                String[] formats = {
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS",
                    "yyyy-MM-dd",
                    "MM/dd/yyyy",
                    "dd/MM/yyyy"
                };
                
                for (String format : formats) {
                    try {
                        if (format.contains("T")) {
                            // Parse datetime and extract date
                            Instant instant = Instant.parse(dateString);
                            return instant.toString().substring(0, 10); // Get YYYY-MM-DD part
                        } else {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                            LocalDate date = LocalDate.parse(dateString, formatter);
                            return date.toString();
                        }
                    } catch (Exception e) {
                        // Try next format
                    }
                }
                
                // If no format works, return original string
                return dateString;
                
            } catch (Exception e) {
                LOG.warn("Could not parse date string: {}", dateString);
                return dateString;
            }
        }
    }
}