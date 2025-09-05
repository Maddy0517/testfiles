package com.company.dataflow.util;

import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionTuple;
import org.apache.beam.sdk.values.TupleTag;
import org.apache.beam.sdk.values.TupleTagList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Utility class for error handling and data quality monitoring.
 */
public class ErrorHandlingUtils {
    
    private static final Logger LOG = LoggerFactory.getLogger(ErrorHandlingUtils.class);
    
    public static final TupleTag<TableRow> SUCCESSFUL_RECORDS = new TupleTag<TableRow>() {};
    public static final TupleTag<TableRow> FAILED_RECORDS = new TupleTag<TableRow>() {};
    
    /**
     * Transform to validate and separate good/bad records.
     */
    public static class ValidateRecords extends PTransform<PCollection<TableRow>, PCollectionTuple> {
        
        @Override
        public PCollectionTuple expand(PCollection<TableRow> input) {
            return input.apply("Validate Records", 
                    ParDo.of(new ValidateRecordsFn())
                    .withOutputTags(SUCCESSFUL_RECORDS, TupleTagList.of(FAILED_RECORDS)));
        }
    }
    
    /**
     * DoFn to validate TableRow records and separate good from bad.
     */
    private static class ValidateRecordsFn extends DoFn<TableRow, TableRow> {
        
        @ProcessElement
        public void processElement(@Element TableRow row, MultiOutputReceiver out) {
            try {
                if (isValidRecord(row)) {
                    out.get(SUCCESSFUL_RECORDS).output(row);
                } else {
                    TableRow errorRow = createValidationErrorRecord(row, "Record validation failed");
                    out.get(FAILED_RECORDS).output(errorRow);
                }
            } catch (Exception e) {
                LOG.error("Error during record validation", e);
                TableRow errorRow = createValidationErrorRecord(row, "Validation exception: " + e.getMessage());
                out.get(FAILED_RECORDS).output(errorRow);
            }
        }
        
        /**
         * Validates if a record has all required fields.
         */
        private boolean isValidRecord(TableRow row) {
            // Check required fields
            Object employeeId = row.get("employee_id");
            if (employeeId == null || employeeId.toString().trim().isEmpty()) {
                return false;
            }
            
            // Check data quality
            Object isComplete = row.get("is_complete");
            if (isComplete instanceof Boolean && !((Boolean) isComplete)) {
                LOG.warn("Record marked as incomplete: employee_id={}", employeeId);
            }
            
            return true;
        }
        
        /**
         * Creates an error record for validation failures.
         */
        private TableRow createValidationErrorRecord(TableRow originalRow, String errorMessage) {
            TableRow errorRow = new TableRow();
            
            // Preserve identifiable information
            errorRow.set("employee_id", originalRow.get("employee_id"));
            errorRow.set("first_name", originalRow.get("first_name"));
            errorRow.set("last_name", originalRow.get("last_name"));
            
            // Set error information
            errorRow.set("status", "VALIDATION_ERROR");
            errorRow.set("error_message", errorMessage);
            errorRow.set("ingestion_timestamp", Instant.now().toString());
            errorRow.set("is_complete", false);
            errorRow.set("data_source", "workday_soap_api");
            
            return errorRow;
        }
    }
    
    /**
     * Transform to add monitoring metrics to records.
     */
    public static class AddMonitoringMetrics extends PTransform<PCollection<TableRow>, PCollection<TableRow>> {
        
        @Override
        public PCollection<TableRow> expand(PCollection<TableRow> input) {
            return input.apply("Add Monitoring Metrics", ParDo.of(new AddMetricsFn()));
        }
    }
    
    /**
     * DoFn to add monitoring and audit fields.
     */
    private static class AddMetricsFn extends DoFn<TableRow, TableRow> {
        
        @ProcessElement
        public void processElement(@Element TableRow row, OutputReceiver<TableRow> out) {
            // Add processing timestamp
            row.set("processing_timestamp", Instant.now().toString());
            
            // Add data freshness indicator
            Object lastModified = row.get("last_modified");
            if (lastModified != null) {
                try {
                    Instant lastMod = Instant.parse(lastModified.toString());
                    Instant now = Instant.now();
                    long daysSinceModified = java.time.Duration.between(lastMod, now).toDays();
                    row.set("data_age_days", daysSinceModified);
                } catch (Exception e) {
                    row.set("data_age_days", null);
                }
            } else {
                row.set("data_age_days", null);
            }
            
            // Add record hash for deduplication
            String recordHash = generateRecordHash(row);
            row.set("record_hash", recordHash);
            
            out.output(row);
        }
        
        /**
         * Generates a hash for the record to help with deduplication.
         */
        private String generateRecordHash(TableRow row) {
            StringBuilder sb = new StringBuilder();
            sb.append(row.get("employee_id"));
            sb.append("|");
            sb.append(row.get("first_name"));
            sb.append("|");
            sb.append(row.get("last_name"));
            sb.append("|");
            sb.append(row.get("email"));
            
            return String.valueOf(sb.toString().hashCode());
        }
    }
    
    /**
     * Transform to log pipeline statistics.
     */
    public static class LogPipelineStats extends PTransform<PCollection<TableRow>, PCollection<TableRow>> {
        
        private final String stageName;
        
        public LogPipelineStats(String stageName) {
            this.stageName = stageName;
        }
        
        @Override
        public PCollection<TableRow> expand(PCollection<TableRow> input) {
            return input.apply("Log Stats: " + stageName, ParDo.of(new LogStatsFn(stageName)));
        }
    }
    
    /**
     * DoFn to log processing statistics.
     */
    private static class LogStatsFn extends DoFn<TableRow, TableRow> {
        
        private final String stageName;
        private long recordCount = 0;
        private long errorCount = 0;
        
        public LogStatsFn(String stageName) {
            this.stageName = stageName;
        }
        
        @ProcessElement
        public void processElement(@Element TableRow row, OutputReceiver<TableRow> out) {
            recordCount++;
            
            Object status = row.get("status");
            if (status != null && status.toString().contains("ERROR")) {
                errorCount++;
            }
            
            // Log every 1000 records
            if (recordCount % 1000 == 0) {
                LOG.info("Stage '{}': Processed {} records, {} errors", stageName, recordCount, errorCount);
            }
            
            out.output(row);
        }
        
        @FinishBundle
        public void finishBundle() {
            LOG.info("Stage '{}' completed: Total {} records, {} errors", stageName, recordCount, errorCount);
        }
    }
}