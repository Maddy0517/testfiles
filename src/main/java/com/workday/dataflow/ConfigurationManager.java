package com.workday.dataflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration manager for loading properties from file and applying them to pipeline options.
 */
public class ConfigurationManager {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationManager.class);
    
    private final Properties properties;

    public ConfigurationManager(String propertiesFilePath) throws IOException {
        this.properties = new Properties();
        loadProperties(propertiesFilePath);
    }

    private void loadProperties(String propertiesFilePath) throws IOException {
        try (InputStream input = new FileInputStream(propertiesFilePath)) {
            properties.load(input);
            LOG.info("Successfully loaded properties from: {}", propertiesFilePath);
        } catch (IOException e) {
            LOG.error("Failed to load properties from: {}", propertiesFilePath, e);
            throw e;
        }
    }

    /**
     * Apply loaded properties to pipeline options
     */
    public void applyToOptions(WorkdayToBigQueryOptions options) {
        // Workday SOAP API Configuration
        if (properties.containsKey("workday.soap.url")) {
            options.setSoapApiUrl(properties.getProperty("workday.soap.url"));
        }
        if (properties.containsKey("workday.username")) {
            options.setUsername(properties.getProperty("workday.username"));
        }
        if (properties.containsKey("workday.password")) {
            options.setPassword(properties.getProperty("workday.password"));
        }
        if (properties.containsKey("workday.tenant")) {
            options.setTenantName(properties.getProperty("workday.tenant"));
        }
        if (properties.containsKey("workday.api.version")) {
            options.setApiVersion(properties.getProperty("workday.api.version"));
        }

        // BigQuery Configuration
        if (properties.containsKey("bigquery.project")) {
            options.setBigQueryProject(properties.getProperty("bigquery.project"));
        }
        if (properties.containsKey("bigquery.dataset")) {
            options.setBigQueryDataset(properties.getProperty("bigquery.dataset"));
        }
        if (properties.containsKey("bigquery.table")) {
            options.setBigQueryTable(properties.getProperty("bigquery.table"));
        }
        if (properties.containsKey("bigquery.schema")) {
            options.setBigQuerySchema(properties.getProperty("bigquery.schema"));
        }
        if (properties.containsKey("bigquery.write.disposition")) {
            options.setWriteDisposition(properties.getProperty("bigquery.write.disposition"));
        }
        if (properties.containsKey("bigquery.create.disposition")) {
            options.setCreateDisposition(properties.getProperty("bigquery.create.disposition"));
        }

        // API Configuration
        if (properties.containsKey("api.batch.size")) {
            options.setBatchSize(Integer.valueOf(properties.getProperty("api.batch.size")));
        }
        if (properties.containsKey("api.max.retries")) {
            options.setMaxRetries(Integer.valueOf(properties.getProperty("api.max.retries")));
        }
        if (properties.containsKey("api.request.timeout")) {
            options.setRequestTimeout(Integer.valueOf(properties.getProperty("api.request.timeout")));
        }
        if (properties.containsKey("api.enable.pagination")) {
            options.setEnablePagination(Boolean.valueOf(properties.getProperty("api.enable.pagination")));
        }
        if (properties.containsKey("api.page.size")) {
            options.setPageSize(Integer.valueOf(properties.getProperty("api.page.size")));
        }

        LOG.info("Applied configuration properties to pipeline options");
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public boolean containsKey(String key) {
        return properties.containsKey(key);
    }
}