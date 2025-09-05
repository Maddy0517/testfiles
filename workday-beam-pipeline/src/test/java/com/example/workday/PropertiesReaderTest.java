package com.example.workday;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests for PropertiesReader
 */
public class PropertiesReaderTest {
    
    @TempDir
    Path tempDir;
    
    private Path propertiesFile;
    
    @BeforeEach
    public void setUp() throws IOException {
        propertiesFile = tempDir.resolve("test.properties");
        
        String propertiesContent = 
            "workdayEndpoint=https://test.workday.com\n" +
            "workdayUsername=testuser\n" +
            "workdayPassword=testpass\n" +
            "pageSize=100\n" +
            "maxRetries=3\n" +
            "enableDebug=true\n";
            
        Files.write(propertiesFile, propertiesContent.getBytes());
    }
    
    @Test
    public void testLoadProperties() throws IOException {
        PropertiesReader reader = new PropertiesReader(propertiesFile.toString());
        
        assertEquals("https://test.workday.com", reader.getProperty("workdayEndpoint"));
        assertEquals("testuser", reader.getProperty("workdayUsername"));
        assertEquals("testpass", reader.getProperty("workdayPassword"));
    }
    
    @Test
    public void testGetPropertyWithDefault() throws IOException {
        PropertiesReader reader = new PropertiesReader(propertiesFile.toString());
        
        assertEquals("testuser", reader.getProperty("workdayUsername", "default"));
        assertEquals("default", reader.getProperty("nonexistent", "default"));
    }
    
    @Test
    public void testGetIntProperty() throws IOException {
        PropertiesReader reader = new PropertiesReader(propertiesFile.toString());
        
        assertEquals(Integer.valueOf(100), reader.getIntProperty("pageSize"));
        assertEquals(Integer.valueOf(3), reader.getIntProperty("maxRetries"));
        assertNull(reader.getIntProperty("nonexistent"));
    }
    
    @Test
    public void testGetIntPropertyWithDefault() throws IOException {
        PropertiesReader reader = new PropertiesReader(propertiesFile.toString());
        
        assertEquals(100, reader.getIntProperty("pageSize", 50));
        assertEquals(50, reader.getIntProperty("nonexistent", 50));
    }
    
    @Test
    public void testGetBooleanProperty() throws IOException {
        PropertiesReader reader = new PropertiesReader(propertiesFile.toString());
        
        assertEquals(Boolean.TRUE, reader.getBooleanProperty("enableDebug"));
        assertNull(reader.getBooleanProperty("nonexistent"));
    }
    
    @Test
    public void testGetBooleanPropertyWithDefault() throws IOException {
        PropertiesReader reader = new PropertiesReader(propertiesFile.toString());
        
        assertTrue(reader.getBooleanProperty("enableDebug", false));
        assertFalse(reader.getBooleanProperty("nonexistent", false));
    }
    
    @Test
    public void testHasProperty() throws IOException {
        PropertiesReader reader = new PropertiesReader(propertiesFile.toString());
        
        assertTrue(reader.hasProperty("workdayEndpoint"));
        assertFalse(reader.hasProperty("nonexistent"));
    }
    
    @Test
    public void testGetRequiredProperty() throws IOException {
        PropertiesReader reader = new PropertiesReader(propertiesFile.toString());
        
        assertEquals("https://test.workday.com", reader.getRequiredProperty("workdayEndpoint"));
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> reader.getRequiredProperty("nonexistent")
        );
        assertTrue(exception.getMessage().contains("Required property 'nonexistent' is missing"));
    }
    
    @Test
    public void testValidateRequiredProperties() throws IOException {
        PropertiesReader reader = new PropertiesReader(propertiesFile.toString());
        
        // Should not throw exception
        assertDoesNotThrow(() -> reader.validateRequiredProperties("workdayEndpoint", "workdayUsername"));
        
        // Should throw exception for missing property
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> reader.validateRequiredProperties("workdayEndpoint", "nonexistent")
        );
        assertTrue(exception.getMessage().contains("Required property 'nonexistent' is missing"));
    }
    
    @Test
    public void testToCommandLineArgs() throws IOException {
        PropertiesReader reader = new PropertiesReader(propertiesFile.toString());
        
        String[] args = reader.toCommandLineArgs();
        
        assertTrue(args.length > 0);
        
        // Check that arguments are in correct format
        for (String arg : args) {
            assertTrue(arg.startsWith("--"));
            assertTrue(arg.contains("="));
        }
        
        // Check specific arguments
        boolean foundEndpoint = false;
        for (String arg : args) {
            if (arg.equals("--workdayEndpoint=https://test.workday.com")) {
                foundEndpoint = true;
                break;
            }
        }
        assertTrue(foundEndpoint);
    }
    
    @Test
    public void testFileNotFound() {
        Path nonexistentFile = tempDir.resolve("nonexistent.properties");
        
        IOException exception = assertThrows(
            IOException.class,
            () -> new PropertiesReader(nonexistentFile.toString())
        );
        assertTrue(exception.getMessage().contains("Cannot load properties file"));
    }
}