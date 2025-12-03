package com.equinix.it.platform.helix.workdaydataretentioncloudfunctions;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * POJO class representing a single record in the BigQuery data retention audit table.
 * Maps to BigQuery table: stg_workday_data_retention_audit
 */
public class DataRetentionAuditRecord {

    private static final DateTimeFormatter BQ_TIMESTAMP_FORMAT = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Fields matching BigQuery table schema (in order)
    private String employeeId;
    private String retentionPolicyName;
    private String sourceFileName;
    private String fileUploadDate;
    private String deletionDate;

    // Default constructor
    public DataRetentionAuditRecord() {
    }

    // Constructor with all fields
    public DataRetentionAuditRecord(String employeeId, String retentionPolicyName, 
                                     String sourceFileName, String fileUploadDate, 
                                     String deletionDate) {
        this.employeeId = employeeId;
        this.retentionPolicyName = retentionPolicyName;
        this.sourceFileName = sourceFileName;
        this.fileUploadDate = fileUploadDate;
        this.deletionDate = deletionDate;
    }

    // Builder pattern for easier object creation
    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getEmployeeId() {
        return employeeId;
    }

    public String getRetentionPolicyName() {
        return retentionPolicyName;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public String getFileUploadDate() {
        return fileUploadDate;
    }

    public String getDeletionDate() {
        return deletionDate;
    }

    // Setters
    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public void setRetentionPolicyName(String retentionPolicyName) {
        this.retentionPolicyName = retentionPolicyName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public void setFileUploadDate(String fileUploadDate) {
        this.fileUploadDate = fileUploadDate;
    }

    public void setDeletionDate(String deletionDate) {
        this.deletionDate = deletionDate;
    }

    // Converts record to CSV row (order matches BigQuery schema)
    public String toCsvRow() {
        return String.join(",",
                escapeCSV(employeeId),
                escapeCSV(retentionPolicyName),
                escapeCSV(sourceFileName),
                fileUploadDate,
                deletionDate
        );
    }

    // Escapes special characters for CSV format
    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // Utility method to format timestamp for BigQuery
    public static String formatTimestamp(Long epochMillis) {
        if (epochMillis == null || epochMillis == 0) {
            epochMillis = System.currentTimeMillis();
        }
        return Instant.ofEpochMilli(epochMillis)
                .atOffset(ZoneOffset.UTC)
                .format(BQ_TIMESTAMP_FORMAT);
    }

    @Override
    public String toString() {
        return "DataRetentionAuditRecord{" +
                "employeeId='" + employeeId + '\'' +
                ", retentionPolicyName='" + retentionPolicyName + '\'' +
                ", sourceFileName='" + sourceFileName + '\'' +
                ", fileUploadDate='" + fileUploadDate + '\'' +
                ", deletionDate='" + deletionDate + '\'' +
                '}';
    }

    // Builder class for fluent object creation
    public static class Builder {
        private String employeeId;
        private String retentionPolicyName;
        private String sourceFileName;
        private String fileUploadDate;
        private String deletionDate;

        public Builder employeeId(String employeeId) {
            this.employeeId = employeeId;
            return this;
        }

        public Builder retentionPolicyName(String retentionPolicyName) {
            this.retentionPolicyName = retentionPolicyName;
            return this;
        }

        public Builder sourceFileName(String sourceFileName) {
            this.sourceFileName = sourceFileName;
            return this;
        }

        public Builder fileUploadDate(String fileUploadDate) {
            this.fileUploadDate = fileUploadDate;
            return this;
        }

        public Builder fileUploadDate(Long epochMillis) {
            this.fileUploadDate = DataRetentionAuditRecord.formatTimestamp(epochMillis);
            return this;
        }

        public Builder deletionDate(String deletionDate) {
            this.deletionDate = deletionDate;
            return this;
        }

        public Builder deletionDateNow() {
            this.deletionDate = DataRetentionAuditRecord.formatTimestamp(System.currentTimeMillis());
            return this;
        }

        public DataRetentionAuditRecord build() {
            return new DataRetentionAuditRecord(
                    employeeId, retentionPolicyName, sourceFileName, 
                    fileUploadDate, deletionDate
            );
        }
    }
}
