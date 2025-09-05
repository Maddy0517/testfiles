package com.example.workday;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Utility class for reading configuration properties from files
 */
public class PropertiesReader {
    private static final Logger LOG = LoggerFactory.getLogger(PropertiesReader.class);
    
    private final Properties properties;
    
    public PropertiesReader(String propertiesFilePath) throws IOException {
        this.properties = new Properties();
        loadProperties(propertiesFilePath);
    }
    
    /**
     * Load properties from file path
     */
    private void loadProperties(String propertiesFilePath) throws IOException {
        LOG.info("Loading properties from: {}", propertiesFilePath);
        
        try (InputStream input = new FileInputStream(propertiesFilePath)) {
            properties.load(input);
            LOG.info("Successfully loaded {} properties", properties.size());
            
            // Log property keys (not values for security)
            LOG.debug("Loaded property keys: {}", properties.keySet());
            
        } catch (IOException e) {
            LOG.error("Failed to load properties from: {}", propertiesFilePath, e);
            throw new IOException("Cannot load properties file: " + propertiesFilePath, e);
        }
    }
    
    /**
     * Get string property value
     */
    public String getProperty(String key) {
        String value = properties.getProperty(key);
        if (value != null) {
            value = value.trim();
        }
        return value;
    }
    
    /**
     * Get string property value with default
     */
    public String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Get integer property value
     */
    public Integer getIntProperty(String key) {
        String value = getProperty(key);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            LOG.warn("Invalid integer value for property {}: {}", key, value);
            return null;
        }
    }
    
    /**
     * Get integer property value with default
     */
    public int getIntProperty(String key, int defaultValue) {
        Integer value = getIntProperty(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Get boolean property value
     */
    public Boolean getBooleanProperty(String key) {
        String value = getProperty(key);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return Boolean.valueOf(value);
    }
    
    /**
     * Get boolean property value with default
     */
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        Boolean value = getBooleanProperty(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Check if property exists
     */
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }
    
    /**
     * Get required property - throws exception if not found
     */
    public String getRequiredProperty(String key) {
        String value = getProperty(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Required property '" + key + "' is missing or empty");
        }
        return value;
    }
    
    /**
     * Convert properties to command line arguments array
     */
    public String[] toCommandLineArgs() {
        return properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }
    
    /**
     * Validate required properties
     */
    public void validateRequiredProperties(String... requiredKeys) {
        for (String key : requiredKeys) {
            getRequiredProperty(key);
        }
        LOG.info("All required properties validated successfully");
    }
    
    /**
     * Get all properties
     */
    public Properties getProperties() {
        return new Properties(properties);
    }
}