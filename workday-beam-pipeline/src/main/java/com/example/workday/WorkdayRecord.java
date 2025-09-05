package com.example.workday;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.Map;

/**
 * Generic record structure for Workday data
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkdayRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("data")
    private Map<String, Object> data;
    
    @JsonProperty("timestamp")
    private String timestamp;
    
    @JsonProperty("source_system")
    private String sourceSystem = "WORKDAY";
    
    public WorkdayRecord() {}
    
    public WorkdayRecord(String id, Map<String, Object> data) {
        this.id = id;
        this.data = data;
        this.timestamp = java.time.Instant.now().toString();
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public Map<String, Object> getData() {
        return data;
    }
    
    public void setData(Map<String, Object> data) {
        this.data = data;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getSourceSystem() {
        return sourceSystem;
    }
    
    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }
    
    @Override
    public String toString() {
        return String.format("WorkdayRecord{id='%s', sourceSystem='%s', timestamp='%s', dataKeys=%s}", 
                           id, sourceSystem, timestamp, 
                           data != null ? data.keySet() : "null");
    }
}