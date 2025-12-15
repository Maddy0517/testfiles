package com.example.dataflow.config;

import java.io.Serializable;

/**
 * Configuration class for Workday SOAP API credentials and settings
 */
public class WorkdayConfig implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String soapUrl;
    private String username;
    private String password;
    private String tenantId;
    private int maxRetries;
    private int retryDelaySeconds;
    private int connectionTimeoutSeconds;
    private int readTimeoutSeconds;
    
    public WorkdayConfig() {
        this.maxRetries = 3;
        this.retryDelaySeconds = 5;
        this.connectionTimeoutSeconds = 60;
        this.readTimeoutSeconds = 120;
    }
    
    public WorkdayConfig(String soapUrl, String username, String password) {
        this();
        this.soapUrl = soapUrl;
        this.username = username;
        this.password = password;
    }
    
    public String getSoapUrl() {
        return soapUrl;
    }
    
    public void setSoapUrl(String soapUrl) {
        this.soapUrl = soapUrl;
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
    
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    public int getRetryDelaySeconds() {
        return retryDelaySeconds;
    }
    
    public void setRetryDelaySeconds(int retryDelaySeconds) {
        this.retryDelaySeconds = retryDelaySeconds;
    }
    
    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }
    
    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }
    
    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }
    
    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }
}
