package com.example.workday;

import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.ValueProvider;

/**
 * Pipeline options for Workday to BigQuery pipeline
 */
public interface WorkdayPipelineOptions extends PipelineOptions {
    
    @Description("Path to properties file containing all configuration")
    ValueProvider<String> getPropertiesFile();
    void setPropertiesFile(ValueProvider<String> propertiesFile);
    
    @Description("Workday endpoint URL")
    ValueProvider<String> getWorkdayEndpoint();
    void setWorkdayEndpoint(ValueProvider<String> workdayEndpoint);
    
    @Description("Workday username")
    ValueProvider<String> getWorkdayUsername();
    void setWorkdayUsername(ValueProvider<String> workdayUsername);
    
    @Description("Workday password")
    ValueProvider<String> getWorkdayPassword();
    void setWorkdayPassword(ValueProvider<String> workdayPassword);
    
    @Description("Workday tenant name")
    ValueProvider<String> getWorkdayTenant();
    void setWorkdayTenant(ValueProvider<String> workdayTenant);
    
    @Description("Workday service name (e.g., Human_Resources)")
    ValueProvider<String> getServiceName();
    void setServiceName(ValueProvider<String> serviceName);
    
    @Description("Workday operation name (e.g., Get_Workers)")
    ValueProvider<String> getOperationName();
    void setOperationName(ValueProvider<String> operationName);
    
    @Description("BigQuery project ID")
    ValueProvider<String> getBigQueryProject();
    void setBigQueryProject(ValueProvider<String> bigQueryProject);
    
    @Description("BigQuery dataset ID")
    ValueProvider<String> getBigQueryDataset();
    void setBigQueryDataset(ValueProvider<String> bigQueryDataset);
    
    @Description("BigQuery table ID")
    ValueProvider<String> getBigQueryTable();
    void setBigQueryTable(ValueProvider<String> bigQueryTable);
    
    @Description("Transformation type (generic, worker, organization)")
    @Default.String("generic")
    ValueProvider<String> getTransformationType();
    void setTransformationType(ValueProvider<String> transformationType);
    
    @Description("Page size for Workday API calls")
    @Default.Integer(100)
    ValueProvider<Integer> getPageSize();
    void setPageSize(ValueProvider<Integer> pageSize);
    
    @Description("Maximum number of retries for failed API calls")
    @Default.Integer(3)
    ValueProvider<Integer> getMaxRetries();
    void setMaxRetries(ValueProvider<Integer> maxRetries);
    
    @Description("Workday API version")
    @Default.String("v41.2")
    ValueProvider<String> getWorkdayVersion();
    void setWorkdayVersion(ValueProvider<String> workdayVersion);
    
    @Description("BigQuery write disposition (WRITE_TRUNCATE, WRITE_APPEND, WRITE_EMPTY)")
    @Default.String("WRITE_APPEND")
    ValueProvider<String> getWriteDisposition();
    void setWriteDisposition(ValueProvider<String> writeDisposition);
    
    @Description("BigQuery create disposition (CREATE_IF_NEEDED, CREATE_NEVER)")
    @Default.String("CREATE_IF_NEEDED")
    ValueProvider<String> getCreateDisposition();
    void setCreateDisposition(ValueProvider<String> createDisposition);
}