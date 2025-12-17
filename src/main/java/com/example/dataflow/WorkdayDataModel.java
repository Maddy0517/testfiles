package com.example.dataflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.beam.sdk.coders.DefaultCoder;
import org.apache.beam.sdk.extensions.avro.coders.AvroCoder;

import java.io.Serializable;

/**
 * Data model classes for Workday integration using Java 21+ features
 */
public class WorkdayDataModel {
    
    /**
     * Employee data model - Using Java 21 record for immutable data
     * Note: Using class instead of record for Beam serialization compatibility
     */
    @DefaultCoder(AvroCoder.class)
    public static class Employee implements Serializable {
        private static final long serialVersionUID = 1L;
        
        @JsonProperty("employee_id")
        public String employeeId;
        
        @JsonProperty("first_name")
        public String firstName;
        
        @JsonProperty("last_name")
        public String lastName;
        
        @JsonProperty("email")
        public String email;
        
        @JsonProperty("phone")
        public String phone;
        
        @JsonProperty("hire_date")
        public String hireDate;
        
        @JsonProperty("job_title")
        public String jobTitle;
        
        @JsonProperty("department")
        public String department;
        
        @JsonProperty("manager_id")
        public String managerId;
        
        @JsonProperty("location")
        public String location;
        
        @JsonProperty("employment_status")
        public String employmentStatus;
        
        @JsonProperty("effective_date")
        public String effectiveDate;
        
        @JsonProperty("ingestion_timestamp")
        public String ingestionTimestamp;
        
        public Employee() {}
        
        @Override
        public String toString() {
            return String.format("Employee{id='%s', name='%s %s'}", employeeId, firstName, lastName);
        }
    }
    
    /**
     * Page request for pagination - Using Java 21 record
     */
    @DefaultCoder(AvroCoder.class)
    public static class PageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public int pageNumber;
        public String effectiveDate;
        public int pageSize;
        
        public PageRequest() {
            this.pageSize = 999;
        }
        
        public PageRequest(int pageNumber, String effectiveDate) {
            this.pageNumber = pageNumber;
            this.effectiveDate = effectiveDate;
            this.pageSize = 999;
        }
        
        @Override
        public String toString() {
            return String.format("PageRequest{page=%d, date='%s'}", pageNumber, effectiveDate);
        }
    }
    
    /**
     * Workday configuration with default values
     */
    public static final class WorkdayConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public String soapUrl;
        public String username;
        public String password;
        public String tenantId;
        public int maxRetries = 3;
        public int retryDelaySeconds = 5;
        public int connectionTimeoutSeconds = 60;
        public int readTimeoutSeconds = 120;
        
        public WorkdayConfig() {}
        
        public WorkdayConfig(String soapUrl, String username, String password) {
            this.soapUrl = soapUrl;
            this.username = username;
            this.password = password;
        }
        
        /**
         * Create config with custom retry settings - Java 21 pattern matching
         */
        public WorkdayConfig withRetryConfig(int maxRetries, int retryDelaySeconds) {
            this.maxRetries = maxRetries;
            this.retryDelaySeconds = retryDelaySeconds;
            return this;
        }
    }
}
