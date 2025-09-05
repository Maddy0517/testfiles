package com.example.workday;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WorkdayConfiguration
 */
public class WorkdayConfigurationTest {
    
    private WorkdayConfiguration config;
    
    @BeforeEach
    public void setUp() {
        config = new WorkdayConfiguration();
    }
    
    @Test
    public void testDefaultValues() {
        assertEquals("v41.2", config.getVersion());
        assertEquals(100, config.getPageSize());
        assertEquals(3, config.getMaxRetries());
        assertEquals(1000L, config.getRetryDelayMs());
    }
    
    @Test
    public void testConstructorWithParameters() {
        String endpoint = "https://test.workday.com";
        String username = "testuser";
        String password = "testpass";
        String tenant = "testtenant";
        
        WorkdayConfiguration configWithParams = new WorkdayConfiguration(endpoint, username, password, tenant);
        
        assertEquals(endpoint, configWithParams.getWorkdayEndpoint());
        assertEquals(username, configWithParams.getUsername());
        assertEquals(password, configWithParams.getPassword());
        assertEquals(tenant, configWithParams.getTenantName());
    }
    
    @Test
    public void testGetServiceUrl() {
        config.setWorkdayEndpoint("https://test.workday.com");
        config.setTenantName("testtenant");
        config.setVersion("v41.2");
        
        String serviceUrl = config.getServiceUrl("Human_Resources");
        String expected = "https://test.workday.com/ccx/service/testtenant/Human_Resources/v41.2";
        
        assertEquals(expected, serviceUrl);
    }
    
    @Test
    public void testValidationSuccess() {
        config.setWorkdayEndpoint("https://test.workday.com");
        config.setUsername("testuser");
        config.setPassword("testpass");
        config.setTenantName("testtenant");
        config.setPageSize(50);
        config.setMaxRetries(2);
        
        assertDoesNotThrow(() -> config.validate());
    }
    
    @Test
    public void testValidationFailsWithNullEndpoint() {
        config.setUsername("testuser");
        config.setPassword("testpass");
        config.setTenantName("testtenant");
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class, 
            () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("Workday endpoint is required"));
    }
    
    @Test
    public void testValidationFailsWithEmptyUsername() {
        config.setWorkdayEndpoint("https://test.workday.com");
        config.setUsername("");
        config.setPassword("testpass");
        config.setTenantName("testtenant");
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class, 
            () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("Username is required"));
    }
    
    @Test
    public void testValidationFailsWithInvalidPageSize() {
        config.setWorkdayEndpoint("https://test.workday.com");
        config.setUsername("testuser");
        config.setPassword("testpass");
        config.setTenantName("testtenant");
        config.setPageSize(-1);
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class, 
            () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("Page size must be positive"));
    }
    
    @Test
    public void testValidationFailsWithNegativeMaxRetries() {
        config.setWorkdayEndpoint("https://test.workday.com");
        config.setUsername("testuser");
        config.setPassword("testpass");
        config.setTenantName("testtenant");
        config.setMaxRetries(-1);
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class, 
            () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("Max retries cannot be negative"));
    }
}