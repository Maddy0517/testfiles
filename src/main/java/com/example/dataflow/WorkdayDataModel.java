package com.example.dataflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.DefaultCoder;

import java.io.Serializable;
import java.time.Instant;

/**
 * Data model classes for Workday integration
 */
public class WorkdayDataModel {
    
    /**
     * Employee data model
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
            return "Employee{id='" + employeeId + "', name='" + firstName + " " + lastName + "'}";
        }
    }
    
    /**
     * Page request for pagination
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
            return "PageRequest{page=" + pageNumber + ", date='" + effectiveDate + "'}";
        }
    }
    
    /**
     * Workday configuration
     */
    public static class WorkdayConfig implements Serializable {
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
    }
}
