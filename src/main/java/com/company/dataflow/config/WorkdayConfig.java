package com.company.dataflow.config;

import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.Validation;

/**
 * Configuration options for the Workday to BigQuery pipeline.
 */
public interface WorkdayConfig extends PipelineOptions {

    @Description("Workday SOAP API endpoint URL")
    @Validation.Required
    String getWorkdayEndpoint();
    void setWorkdayEndpoint(String value);

    @Description("Workday username for authentication")
    @Validation.Required
    String getWorkdayUsername();
    void setWorkdayUsername(String value);

    @Description("Workday password for authentication")
    @Validation.Required
    String getWorkdayPassword();
    void setWorkdayPassword(String value);

    @Description("BigQuery dataset ID")
    @Validation.Required
    String getBigQueryDataset();
    void setBigQueryDataset(String value);

    @Description("BigQuery table ID")
    @Validation.Required
    String getBigQueryTable();
    void setBigQueryTable(String value);

    @Description("Google Cloud Project ID")
    @Validation.Required
    String getProject();
    void setProject(String value);

    @Description("Batch size for API calls")
    Integer getBatchSize();
    void setBatchSize(Integer value);

    @Description("Maximum number of retries for failed API calls")
    Integer getMaxRetries();
    void setMaxRetries(Integer value);

    @Description("Workday tenant name")
    String getWorkdayTenant();
    void setWorkdayTenant(String value);

    @Description("Workday service version")
    String getWorkdayVersion();
    void setWorkdayVersion(String value);

    @Description("BigQuery write disposition (WRITE_TRUNCATE, WRITE_APPEND, WRITE_EMPTY)")
    String getWriteDisposition();
    void setWriteDisposition(String value);

    @Description("BigQuery create disposition (CREATE_IF_NEEDED, CREATE_NEVER)")
    String getCreateDisposition();
    void setCreateDisposition(String value);

    @Description("Enable streaming inserts to BigQuery")
    Boolean getUseStreaming();
    void setUseStreaming(Boolean value);

    @Description("Workday service namespace")
    String getServiceNamespace();
    void setServiceNamespace(String value);

    @Description("Number of parallel workers for API calls")
    Integer getNumWorkers();
    void setNumWorkers(Integer value);
}