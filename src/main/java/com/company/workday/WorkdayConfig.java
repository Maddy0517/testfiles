package com.company.workday;

import java.io.Serializable;

/**
 * Configuration class for Workday SOAP API connection and processing parameters.
 */
public class WorkdayConfig implements Serializable {
    
    public enum LoadMode {
        HISTORICAL,  // Full data load
        INCREMENTAL  // Delta load based on effective date
    }
    
    private final String endpoint;
    private final String username;
    private final String password;
    private final String tenant;
    private final String effectiveDate;
    private final LoadMode loadMode;
    private final Integer incrementalDays;
    private final Integer maxParallelRequests;
    private final Integer pageSize;
    
    private WorkdayConfig(Builder builder) {
        this.endpoint = builder.endpoint;
        this.username = builder.username;
        this.password = builder.password;
        this.tenant = builder.tenant;
        this.effectiveDate = builder.effectiveDate;
        this.loadMode = builder.loadMode;
        this.incrementalDays = builder.incrementalDays;
        this.maxParallelRequests = builder.maxParallelRequests;
        this.pageSize = 999; // Workday SOAP API maximum
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getEndpoint() { return endpoint; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getTenant() { return tenant; }
    public String getEffectiveDate() { return effectiveDate; }
    public LoadMode getLoadMode() { return loadMode; }
    public Integer getIncrementalDays() { return incrementalDays; }
    public Integer getMaxParallelRequests() { return maxParallelRequests; }
    public Integer getPageSize() { return pageSize; }
    
    public static class Builder {
        private String endpoint;
        private String username;
        private String password;
        private String tenant;
        private String effectiveDate;
        private LoadMode loadMode = LoadMode.INCREMENTAL;
        private Integer incrementalDays = 1;
        private Integer maxParallelRequests = 5;
        
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }
        
        public Builder username(String username) {
            this.username = username;
            return this;
        }
        
        public Builder password(String password) {
            this.password = password;
            return this;
        }
        
        public Builder tenant(String tenant) {
            this.tenant = tenant;
            return this;
        }
        
        public Builder effectiveDate(String effectiveDate) {
            this.effectiveDate = effectiveDate;
            return this;
        }
        
        public Builder loadMode(LoadMode loadMode) {
            this.loadMode = loadMode;
            return this;
        }
        
        public Builder incrementalDays(Integer incrementalDays) {
            this.incrementalDays = incrementalDays;
            return this;
        }
        
        public Builder maxParallelRequests(Integer maxParallelRequests) {
            this.maxParallelRequests = maxParallelRequests;
            return this;
        }
        
        public WorkdayConfig build() {
            return new WorkdayConfig(this);
        }
    }
}