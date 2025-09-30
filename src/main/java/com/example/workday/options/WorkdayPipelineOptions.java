package com.example.workday.options;

import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.Validation;

/**
 * Pipeline options for Workday data extraction
 */
public interface WorkdayPipelineOptions extends DataflowPipelineOptions {
    
    @Description("Workday SOAP API endpoint URL")
    @Validation.Required
    String getWorkdayEndpoint();
    void setWorkdayEndpoint(String value);
    
    @Description("Workday username")
    @Validation.Required
    String getWorkdayUsername();
    void setWorkdayUsername(String value);
    
    @Description("Workday password")
    @Validation.Required
    String getWorkdayPassword();
    void setWorkdayPassword(String value);
    
    @Description("Workday tenant ID")
    @Validation.Required
    String getWorkdayTenant();
    void setWorkdayTenant(String value);
    
    @Description("BigQuery dataset name")
    @Validation.Required
    String getBigQueryDataset();
    void setBigQueryDataset(String value);
    
    @Description("BigQuery table name")
    @Default.String("workday_employees")
    String getBigQueryTable();
    void setBigQueryTable(String value);
    
    @Description("Load type: HISTORICAL or INCREMENTAL")
    @Default.String("INCREMENTAL")
    String getLoadType();
    void setLoadType(String value);
    
    @Description("Effective date for data extraction (format: yyyy-MM-dd)")
    String getEffectiveDate();
    void setEffectiveDate(String value);
    
    @Description("Last modified from datetime for incremental loads (format: yyyy-MM-dd'T'HH:mm:ss)")
    String getLastModifiedFrom();
    void setLastModifiedFrom(String value);
    
    @Description("Use proxy for Workday connection")
    @Default.Boolean(false)
    Boolean getUseProxy();
    void setUseProxy(Boolean value);
    
    @Description("Proxy host")
    String getProxyHost();
    void setProxyHost(String value);
    
    @Description("Proxy port")
    @Default.Integer(8080)
    Integer getProxyPort();
    void setProxyPort(Integer value);
    
    @Description("Write disposition: WRITE_TRUNCATE, WRITE_APPEND, or WRITE_EMPTY")
    @Default.String("WRITE_APPEND")
    String getWriteDisposition();
    void setWriteDisposition(String value);
    
    @Description("Create disposition: CREATE_IF_NEEDED or CREATE_NEVER")
    @Default.String("CREATE_IF_NEEDED")
    String getCreateDisposition();
    void setCreateDisposition(String value);
    
    @Description("BigQuery partition field")
    @Default.String("effective_date")
    String getPartitionField();
    void setPartitionField(String value);
    
    @Description("BigQuery clustering fields (comma-separated)")
    @Default.String("employee_id,department,location")
    String getClusteringFields();
    void setClusteringFields(String value);
    
    @Description("Enable BigQuery table partitioning")
    @Default.Boolean(true)
    Boolean getEnablePartitioning();
    void setEnablePartitioning(Boolean value);
    
    @Description("BigQuery temporary GCS bucket for staging")
    String getTempGcsBucket();
    void setTempGcsBucket(String value);
}