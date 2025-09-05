package com.workday.dataflow;

import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.Validation;

/**
 * Pipeline options for Workday to BigQuery data ingestion pipeline.
 * All configuration parameters are loaded from properties file.
 */
public interface WorkdayToBigQueryOptions extends PipelineOptions {

    @Description("Properties file path containing all configuration parameters")
    @Validation.Required
    String getPropertiesFile();
    void setPropertiesFile(String propertiesFile);

    @Description("Workday SOAP API URL")
    String getSoapApiUrl();
    void setSoapApiUrl(String soapApiUrl);

    @Description("Workday API username")
    String getUsername();
    void setUsername(String username);

    @Description("Workday API password")
    String getPassword();
    void setPassword(String password);

    @Description("BigQuery project ID")
    String getBigQueryProject();
    void setBigQueryProject(String bigQueryProject);

    @Description("BigQuery dataset ID")
    String getBigQueryDataset();
    void setBigQueryDataset(String bigQueryDataset);

    @Description("BigQuery table ID")
    String getBigQueryTable();
    void setBigQueryTable(String bigQueryTable);

    @Description("BigQuery table schema file path")
    String getBigQuerySchema();
    void setBigQuerySchema(String bigQuerySchema);

    @Description("Batch size for SOAP API requests")
    Integer getBatchSize();
    void setBatchSize(Integer batchSize);

    @Description("Maximum number of retries for failed requests")
    Integer getMaxRetries();
    void setMaxRetries(Integer maxRetries);

    @Description("Request timeout in milliseconds")
    Integer getRequestTimeout();
    void setRequestTimeout(Integer requestTimeout);

    @Description("Enable pagination for large datasets")
    Boolean getEnablePagination();
    void setEnablePagination(Boolean enablePagination);

    @Description("Page size for pagination")
    Integer getPageSize();
    void setPageSize(Integer pageSize);

    @Description("Workday tenant name")
    String getTenantName();
    void setTenantName(String tenantName);

    @Description("Workday API version")
    String getApiVersion();
    void setApiVersion(String apiVersion);

    @Description("Write disposition for BigQuery (WRITE_TRUNCATE, WRITE_APPEND, WRITE_EMPTY)")
    String getWriteDisposition();
    void setWriteDisposition(String writeDisposition);

    @Description("Create disposition for BigQuery (CREATE_IF_NEEDED, CREATE_NEVER)")
    String getCreateDisposition();
    void setCreateDisposition(String createDisposition);
}