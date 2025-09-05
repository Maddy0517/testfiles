package com.example.workday;

import java.io.Serializable;

/**
 * Configuration class for Workday SOAP API connection
 */
public class WorkdayConfiguration implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String workdayEndpoint;
    private String username;
    private String password;
    private String tenantName;
    private String version;
    private int pageSize;
    private int maxRetries;
    private long retryDelayMs;
    
    public WorkdayConfiguration() {
        // Default values
        this.version = "v41.2";
        this.pageSize = 100;
        this.maxRetries = 3;
        this.retryDelayMs = 1000;
    }
    
    public WorkdayConfiguration(String workdayEndpoint, String username, String password, 
                               String tenantName) {
        this();
        this.workdayEndpoint = workdayEndpoint;
        this.username = username;
        this.password = password;
        this.tenantName = tenantName;
    }
    
    // Getters and Setters
    public String getWorkdayEndpoint() {
        return workdayEndpoint;
    }
    
    public void setWorkdayEndpoint(String workdayEndpoint) {
        this.workdayEndpoint = workdayEndpoint;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getTenantName() {
        return tenantName;
    }
    
    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public int getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    public long getRetryDelayMs() {
        return retryDelayMs;
    }
    
    public void setRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }
    
    /**
     * Get the full service URL for a specific service
     */
    public String getServiceUrl(String serviceName) {
        return String.format("%s/ccx/service/%s/%s/%s", 
                           workdayEndpoint, tenantName, serviceName, version);
    }
    
    /**
     * Validate configuration
     */
    public void validate() {
        if (workdayEndpoint == null || workdayEndpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("Workday endpoint is required");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (tenantName == null || tenantName.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant name is required");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be positive");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("Max retries cannot be negative");
        }
    }
}