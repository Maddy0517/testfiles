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
        InputStream input = null;
        try {
            // Try to load from file system first
            try {
                input = new FileInputStream(propertiesFilePath);
                LOG.info("Loading properties from file system: {}", propertiesFilePath);
            } catch (IOException e) {
                // If file system fails, try to load from classpath
                input = this.getClass().getClassLoader().getResourceAsStream(propertiesFilePath);
                if (input != null) {
                    LOG.info("Loading properties from classpath: {}", propertiesFilePath);
                } else {
                    // Try without leading path separators
                    String fileName = propertiesFilePath.replaceAll("^[/\\\\]+", "");
                    input = this.getClass().getClassLoader().getResourceAsStream(fileName);
                    if (input != null) {
                        LOG.info("Loading properties from classpath: {}", fileName);
                    } else {
                        LOG.error("Properties file not found in file system or classpath: {}", propertiesFilePath);
                        throw new IOException("Properties file not found: " + propertiesFilePath + 
                            ". Please check the file path or place the file in src/main/resources/");
                    }
                }
            }
            
            properties.load(input);
            LOG.info("Successfully loaded properties from: {}", propertiesFilePath);
            
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    LOG.warn("Failed to close input stream", e);
                }
            }
        }
    }

    /**
     * Apply loaded properties to pipeline options
     */
    public void applyToOptions(WorkdayToBigQueryOptions options) {
        // Workday SOAP API Configuration
        if (properties.containsKey("workday.soap.url")) {
            String soapUrl = properties.getProperty("workday.soap.url");
            options.setSoapApiUrl(soapUrl);
            LOG.info("Loaded SOAP URL: {}", soapUrl);
        }
        if (properties.containsKey("workday.username")) {
            String username = properties.getProperty("workday.username");
            options.setUsername(username);
            LOG.info("Loaded username: {}", username);
        }
        if (properties.containsKey("workday.password")) {
            String password = properties.getProperty("workday.password");
            options.setPassword(password);
            LOG.info("Loaded password: {}", (password != null && !password.trim().isEmpty()) ? "***PROVIDED***" : "NULL/EMPTY");
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

        // Date Filter Configuration
        if (properties.containsKey("workday.effective.from.date")) {
            options.setEffectiveFromDate(properties.getProperty("workday.effective.from.date"));
        }
        if (properties.containsKey("workday.effective.to.date")) {
            options.setEffectiveToDate(properties.getProperty("workday.effective.to.date"));
        }
        if (properties.containsKey("workday.include.effective.from.date")) {
            options.setIncludeEffectiveFromDate(Boolean.valueOf(properties.getProperty("workday.include.effective.from.date")));
        }
        if (properties.containsKey("workday.include.effective.to.date")) {
            options.setIncludeEffectiveToDate(Boolean.valueOf(properties.getProperty("workday.include.effective.to.date")));
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