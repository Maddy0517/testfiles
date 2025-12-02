package com.example.gcf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileParser class.
 */
class FileParserTest {

    private FileParser fileParser;

    @BeforeEach
    void setUp() {
        fileParser = new FileParser();
    }

    // ==================== CSV Parsing Tests ====================

    @Test
    @DisplayName("Should extract employee IDs from standard CSV")
    void testExtractFromCsv_StandardFormat() {
        String csvContent = """
                employee_id,name,department
                EMP001,John Smith,Engineering
                EMP002,Jane Doe,Marketing
                EMP003,Bob Johnson,Sales
                """;

        List<String> result = fileParser.extractFromCsv(csvContent);

        assertEquals(3, result.size());
        assertEquals("EMP001", result.get(0));
        assertEquals("EMP002", result.get(1));
        assertEquals("EMP003", result.get(2));
    }

    @Test
    @DisplayName("Should handle emp_id column name")
    void testExtractFromCsv_EmpIdColumn() {
        String csvContent = """
                emp_id,name,status
                E1,John,Active
                E2,Jane,Active
                """;

        List<String> result = fileParser.extractFromCsv(csvContent);

        assertEquals(2, result.size());
        assertEquals("E1", result.get(0));
        assertEquals("E2", result.get(1));
    }

    @Test
    @DisplayName("Should handle ID column name")
    void testExtractFromCsv_IdColumn() {
        String csvContent = """
                ID,name,department
                101,John,IT
                102,Jane,HR
                """;

        List<String> result = fileParser.extractFromCsv(csvContent);

        assertEquals(2, result.size());
        assertEquals("101", result.get(0));
        assertEquals("102", result.get(1));
    }

    @Test
    @DisplayName("Should handle CSV with quoted values")
    void testExtractFromCsv_QuotedValues() {
        String csvContent = """
                employee_id,name,address
                EMP001,"Smith, John","123 Main St, City"
                EMP002,"Doe, Jane","456 Oak Ave"
                """;

        List<String> result = fileParser.extractFromCsv(csvContent);

        assertEquals(2, result.size());
        assertEquals("EMP001", result.get(0));
        assertEquals("EMP002", result.get(1));
    }

    @Test
    @DisplayName("Should return empty list when no employee ID column found")
    void testExtractFromCsv_NoEmployeeIdColumn() {
        String csvContent = """
                name,department,salary
                John,Engineering,50000
                Jane,Marketing,55000
                """;

        List<String> result = fileParser.extractFromCsv(csvContent);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should handle empty CSV content")
    void testExtractFromCsv_EmptyContent() {
        List<String> result = fileParser.extractFromCsv("");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should skip empty lines in CSV")
    void testExtractFromCsv_SkipEmptyLines() {
        String csvContent = """
                employee_id,name
                EMP001,John
                
                EMP002,Jane
                
                """;

        List<String> result = fileParser.extractFromCsv(csvContent);

        assertEquals(2, result.size());
    }

    // ==================== JSON Parsing Tests ====================

    @Test
    @DisplayName("Should extract employee IDs from JSON array")
    void testExtractFromJson_Array() {
        String jsonContent = """
                [
                    {"employee_id": "E1", "name": "John"},
                    {"employee_id": "E2", "name": "Jane"},
                    {"employee_id": "E3", "name": "Bob"}
                ]
                """;

        List<String> result = fileParser.extractFromJson(jsonContent);

        assertEquals(3, result.size());
        assertEquals("E1", result.get(0));
        assertEquals("E2", result.get(1));
        assertEquals("E3", result.get(2));
    }

    @Test
    @DisplayName("Should extract employee IDs from nested JSON with 'employees' field")
    void testExtractFromJson_NestedEmployees() {
        String jsonContent = """
                {
                    "employees": [
                        {"employee_id": "EMP101", "name": "Sarah"},
                        {"employee_id": "EMP102", "name": "Mike"}
                    ]
                }
                """;

        List<String> result = fileParser.extractFromJson(jsonContent);

        assertEquals(2, result.size());
        assertEquals("EMP101", result.get(0));
        assertEquals("EMP102", result.get(1));
    }

    @Test
    @DisplayName("Should extract employee IDs from nested JSON with 'data' field")
    void testExtractFromJson_NestedData() {
        String jsonContent = """
                {
                    "data": [
                        {"emp_id": "D1"},
                        {"emp_id": "D2"}
                    ]
                }
                """;

        List<String> result = fileParser.extractFromJson(jsonContent);

        assertEquals(2, result.size());
        assertEquals("D1", result.get(0));
        assertEquals("D2", result.get(1));
    }

    @Test
    @DisplayName("Should handle camelCase employeeId field")
    void testExtractFromJson_CamelCase() {
        String jsonContent = """
                [
                    {"employeeId": "CC1"},
                    {"employeeId": "CC2"}
                ]
                """;

        List<String> result = fileParser.extractFromJson(jsonContent);

        assertEquals(2, result.size());
        assertEquals("CC1", result.get(0));
        assertEquals("CC2", result.get(1));
    }

    @Test
    @DisplayName("Should return empty list for invalid JSON")
    void testExtractFromJson_InvalidJson() {
        String jsonContent = "not valid json";

        List<String> result = fileParser.extractFromJson(jsonContent);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list for empty JSON array")
    void testExtractFromJson_EmptyArray() {
        String jsonContent = "[]";

        List<String> result = fileParser.extractFromJson(jsonContent);

        assertTrue(result.isEmpty());
    }

    // ==================== Auto-Detection Tests ====================

    @Test
    @DisplayName("Should auto-detect CSV file format")
    void testExtractEmployeeIds_CsvFile() {
        String content = "employee_id,name\nEMP001,John";

        List<String> result = fileParser.extractEmployeeIds(content, "data.csv");

        assertEquals(1, result.size());
        assertEquals("EMP001", result.get(0));
    }

    @Test
    @DisplayName("Should auto-detect JSON file format")
    void testExtractEmployeeIds_JsonFile() {
        String content = "[{\"employee_id\": \"EMP001\"}]";

        List<String> result = fileParser.extractEmployeeIds(content, "data.json");

        assertEquals(1, result.size());
        assertEquals("EMP001", result.get(0));
    }

    @Test
    @DisplayName("Should try CSV then JSON for unknown file type")
    void testExtractEmployeeIds_UnknownFileType() {
        // CSV content with unknown extension
        String csvContent = "employee_id,name\nEMP001,John";
        List<String> result1 = fileParser.extractEmployeeIds(csvContent, "data.txt");
        assertEquals(1, result1.size());
        assertEquals("EMP001", result1.get(0));

        // JSON content with unknown extension
        String jsonContent = "[{\"employee_id\": \"EMP002\"}]";
        List<String> result2 = fileParser.extractEmployeeIds(jsonContent, "data.dat");
        assertEquals(1, result2.size());
        assertEquals("EMP002", result2.get(0));
    }
}
