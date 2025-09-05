package com.example.workday;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for WorkdayRecord
 */
public class WorkdayRecordTest {
    
    private WorkdayRecord record;
    private Map<String, Object> testData;
    
    @BeforeEach
    public void setUp() {
        testData = new HashMap<>();
        testData.put("name", "John Doe");
        testData.put("id", "12345");
        testData.put("active", true);
        
        record = new WorkdayRecord("test-id", testData);
    }
    
    @Test
    public void testConstructorWithParameters() {
        assertEquals("test-id", record.getId());
        assertEquals(testData, record.getData());
        assertEquals("WORKDAY", record.getSourceSystem());
        assertNotNull(record.getTimestamp());
    }
    
    @Test
    public void testDefaultConstructor() {
        WorkdayRecord emptyRecord = new WorkdayRecord();
        assertNull(emptyRecord.getId());
        assertNull(emptyRecord.getData());
        assertEquals("WORKDAY", emptyRecord.getSourceSystem());
    }
    
    @Test
    public void testSettersAndGetters() {
        WorkdayRecord newRecord = new WorkdayRecord();
        
        newRecord.setId("new-id");
        newRecord.setData(testData);
        newRecord.setSourceSystem("TEST_SYSTEM");
        newRecord.setTimestamp("2023-01-01T00:00:00Z");
        
        assertEquals("new-id", newRecord.getId());
        assertEquals(testData, newRecord.getData());
        assertEquals("TEST_SYSTEM", newRecord.getSourceSystem());
        assertEquals("2023-01-01T00:00:00Z", newRecord.getTimestamp());
    }
    
    @Test
    public void testToString() {
        String toString = record.toString();
        
        assertTrue(toString.contains("WorkdayRecord"));
        assertTrue(toString.contains("id='test-id'"));
        assertTrue(toString.contains("sourceSystem='WORKDAY'"));
        assertTrue(toString.contains("dataKeys=["));
    }
    
    @Test
    public void testToStringWithNullData() {
        WorkdayRecord recordWithNullData = new WorkdayRecord("test-id", null);
        String toString = recordWithNullData.toString();
        
        assertTrue(toString.contains("dataKeys=null"));
    }
}