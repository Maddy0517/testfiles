package com.example.gcf;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Configuration properties reader for the Cloud Function.
 * 
 * Reads configuration from application.properties file.
 * This allows easy migration between environments (dev, sit, uat, prod)
 * by simply updating the properties file without changing code.
 * 
 * Properties file location: src/main/resources/application.properties
 */
public class ConfigProperties {

    private static final Logger logger = Logger.getLogger(ConfigProperties.class.getName());
    private static final String PROPERTIES_FILE = "application.properties";

    private final Properties properties;

    // Property keys
    private static final String KEY_PROJECT_ID = "gcp.project.id";
    private static final String KEY_BUCKET_NAME = "gcs.bucket.name";
    private static final String KEY_FILE_PREFIX = "gcs.file.prefix";
    private static final String KEY_DATASET_ID = "bigquery.dataset.id";
    private static final String KEY_TABLE_ID = "bigquery.table.id";

    /**
     * Loads properties from application.properties file.
     */
    public ConfigProperties() {
        this.properties = new Properties();
        loadProperties();
    }

    /**
     * Constructor for testing with custom properties.
     */
    public ConfigProperties(Properties properties) {
        this.properties = properties;
    }

    /**
     * Loads properties from the classpath resource.
     */
    private void loadProperties() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (inputStream == null) {
                logger.severe("Properties file not found: " + PROPERTIES_FILE);
                throw new RuntimeException("Configuration file not found: " + PROPERTIES_FILE);
            }
            properties.load(inputStream);
            logger.info("Loaded configuration from " + PROPERTIES_FILE);
            
            // Validate required properties
            validateRequiredProperties();
            
        } catch (IOException e) {
            logger.severe("Error loading properties file: " + e.getMessage());
            throw new RuntimeException("Failed to load configuration", e);
        }
    }

    /**
     * Validates that all required properties are present.
     */
    private void validateRequiredProperties() {
        String[] requiredKeys = {KEY_PROJECT_ID, KEY_BUCKET_NAME, KEY_DATASET_ID, KEY_TABLE_ID};
        
        for (String key : requiredKeys) {
            String value = properties.getProperty(key);
            if (value == null || value.trim().isEmpty()) {
                throw new RuntimeException("Required property missing or empty: " + key);
            }
        }
    }

    /**
     * Gets the GCP Project ID.
     *
     * @return Project ID
     */
    public String getProjectId() {
        return properties.getProperty(KEY_PROJECT_ID).trim();
    }

    /**
     * Gets the GCS Bucket name where files are stored.
     *
     * @return Bucket name
     */
    public String getBucketName() {
        return properties.getProperty(KEY_BUCKET_NAME).trim();
    }

    /**
     * Gets the optional file prefix/folder path to scan within the bucket.
     *
     * @return File prefix or empty string if not set
     */
    public String getFilePrefix() {
        String prefix = properties.getProperty(KEY_FILE_PREFIX);
        return (prefix != null) ? prefix.trim() : "";
    }

    /**
     * Gets the BigQuery Dataset ID.
     *
     * @return Dataset ID
     */
    public String getDatasetId() {
        return properties.getProperty(KEY_DATASET_ID).trim();
    }

    /**
     * Gets the BigQuery Table ID.
     *
     * @return Table ID
     */
    public String getTableId() {
        return properties.getProperty(KEY_TABLE_ID).trim();
    }

    /**
     * Gets any custom property by key.
     *
     * @param key Property key
     * @return Property value or null if not found
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * Gets any custom property with a default value.
     *
     * @param key          Property key
     * @param defaultValue Default value if property not found
     * @return Property value or default value
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    @Override
    public String toString() {
        return "ConfigProperties{" +
                "projectId='" + getProjectId() + '\'' +
                ", bucketName='" + getBucketName() + '\'' +
                ", filePrefix='" + getFilePrefix() + '\'' +
                ", datasetId='" + getDatasetId() + '\'' +
                ", tableId='" + getTableId() + '\'' +
                '}';
    }
}
