package com.example.gcf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CsvFileParser class.
 */
class CsvFileParserTest {

    private CsvFileParser csvParser;

    @BeforeEach
    void setUp() {
        csvParser = new CsvFileParser();
    }

    @Test
    @DisplayName("Should extract employee IDs from standard CSV with employee_id column")
    void testExtractEmployeeIds_StandardFormat() {
        String csvContent = """
                employee_id,name,department
                EMP001,John Smith,Engineering
                EMP002,Jane Doe,Marketing
                EMP003,Bob Johnson,Sales
                """;

        List<String> result = csvParser.extractEmployeeIds(csvContent);

        assertEquals(3, result.size());
        assertEquals("EMP001", result.get(0));
        assertEquals("EMP002", result.get(1));
        assertEquals("EMP003", result.get(2));
    }

    @Test
    @DisplayName("Should handle emp_id column name")
    void testExtractEmployeeIds_EmpIdColumn() {
        String csvContent = """
                emp_id,name,status
                E1,John,Active
                E2,Jane,Active
                """;

        List<String> result = csvParser.extractEmployeeIds(csvContent);

        assertEquals(2, result.size());
        assertEquals("E1", result.get(0));
        assertEquals("E2", result.get(1));
    }

    @Test
    @DisplayName("Should handle ID column name")
    void testExtractEmployeeIds_IdColumn() {
        String csvContent = """
                ID,name,department
                101,John,IT
                102,Jane,HR
                """;

        List<String> result = csvParser.extractEmployeeIds(csvContent);

        assertEquals(2, result.size());
        assertEquals("101", result.get(0));
        assertEquals("102", result.get(1));
    }

    @Test
    @DisplayName("Should handle employeeid column name (no underscore)")
    void testExtractEmployeeIds_EmployeeIdNoUnderscore() {
        String csvContent = """
                employeeid,name
                EMP100,Alice
                EMP200,Bob
                """;

        List<String> result = csvParser.extractEmployeeIds(csvContent);

        assertEquals(2, result.size());
        assertEquals("EMP100", result.get(0));
        assertEquals("EMP200", result.get(1));
    }

    @Test
    @DisplayName("Should handle CSV with quoted values containing commas")
    void testExtractEmployeeIds_QuotedValues() {
        String csvContent = """
                employee_id,name,address
                EMP001,"Smith, John","123 Main St, City"
                EMP002,"Doe, Jane","456 Oak Ave"
                """;

        List<String> result = csvParser.extractEmployeeIds(csvContent);

        assertEquals(2, result.size());
        assertEquals("EMP001", result.get(0));
        assertEquals("EMP002", result.get(1));
    }

    @Test
    @DisplayName("Should handle CSV with quoted values containing quotes")
    void testExtractEmployeeIds_QuotedValuesWithQuotes() {
        String csvContent = """
                employee_id,name,nickname
                EMP001,John,"Johnny ""The Great"""
                EMP002,Jane,Janey
                """;

        List<String> result = csvParser.extractEmployeeIds(csvContent);

        assertEquals(2, result.size());
        assertEquals("EMP001", result.get(0));
        assertEquals("EMP002", result.get(1));
    }

    @Test
    @DisplayName("Should return empty list when no employee ID column found")
    void testExtractEmployeeIds_NoEmployeeIdColumn() {
        String csvContent = """
                name,department,salary
                John,Engineering,50000
                Jane,Marketing,55000
                """;

        List<String> result = csvParser.extractEmployeeIds(csvContent);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should handle empty CSV content")
    void testExtractEmployeeIds_EmptyContent() {
        List<String> result = csvParser.extractEmployeeIds("");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should handle null CSV content")
    void testExtractEmployeeIds_NullContent() {
        List<String> result = csvParser.extractEmployeeIds(null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should skip empty lines in CSV")
    void testExtractEmployeeIds_SkipEmptyLines() {
        String csvContent = """
                employee_id,name
                EMP001,John
                
                EMP002,Jane
                
                EMP003,Bob
                """;

        List<String> result = csvParser.extractEmployeeIds(csvContent);

        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("Should skip rows with empty employee ID")
    void testExtractEmployeeIds_SkipEmptyIds() {
        String csvContent = """
                employee_id,name
                EMP001,John
                ,Jane
                EMP003,Bob
                """;

        List<String> result = csvParser.extractEmployeeIds(csvContent);

        assertEquals(2, result.size());
        assertEquals("EMP001", result.get(0));
        assertEquals("EMP003", result.get(1));
    }

    @Test
    @DisplayName("Should handle case-insensitive column names")
    void testExtractEmployeeIds_CaseInsensitiveColumns() {
        String csvContent1 = "EMPLOYEE_ID,name\nEMP001,John";
        String csvContent2 = "Employee_Id,name\nEMP002,Jane";
        String csvContent3 = "eMpLoYeE_iD,name\nEMP003,Bob";

        assertEquals("EMP001", csvParser.extractEmployeeIds(csvContent1).get(0));
        assertEquals("EMP002", csvParser.extractEmployeeIds(csvContent2).get(0));
        assertEquals("EMP003", csvParser.extractEmployeeIds(csvContent3).get(0));
    }

    @Test
    @DisplayName("Should handle column names with hyphens")
    void testExtractEmployeeIds_HyphenColumnNames() {
        String csvContent = """
                employee-id,name
                EMP001,John
                EMP002,Jane
                """;

        List<String> result = csvParser.extractEmployeeIds(csvContent);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Should handle CSV with extra whitespace")
    void testExtractEmployeeIds_ExtraWhitespace() {
        String csvContent = """
                employee_id , name , department
                  EMP001  , John Smith , Engineering  
                EMP002,Jane Doe,Marketing
                """;

        List<String> result = csvParser.extractEmployeeIds(csvContent);

        assertEquals(2, result.size());
        assertEquals("EMP001", result.get(0));
        assertEquals("EMP002", result.get(1));
    }

    @Test
    @DisplayName("Should handle large CSV file")
    void testExtractEmployeeIds_LargeFile() {
        StringBuilder csvContent = new StringBuilder("employee_id,name,department\n");
        for (int i = 1; i <= 1000; i++) {
            csvContent.append("EMP").append(String.format("%04d", i))
                     .append(",Employee ").append(i)
                     .append(",Dept").append(i % 10)
                     .append("\n");
        }

        List<String> result = csvParser.extractEmployeeIds(csvContent.toString());

        assertEquals(1000, result.size());
        assertEquals("EMP0001", result.get(0));
        assertEquals("EMP1000", result.get(999));
    }

    @Test
    @DisplayName("Should handle employee ID as first column")
    void testExtractEmployeeIds_FirstColumn() {
        String csvContent = """
                employee_id,name
                EMP001,John
                """;

        List<String> result = csvParser.extractEmployeeIds(csvContent);
        assertEquals("EMP001", result.get(0));
    }

    @Test
    @DisplayName("Should handle employee ID as last column")
    void testExtractEmployeeIds_LastColumn() {
        String csvContent = """
                name,department,employee_id
                John,Engineering,EMP001
                """;

        List<String> result = csvParser.extractEmployeeIds(csvContent);
        assertEquals("EMP001", result.get(0));
    }

    @Test
    @DisplayName("Should handle employee ID as middle column")
    void testExtractEmployeeIds_MiddleColumn() {
        String csvContent = """
                name,employee_id,department
                John,EMP001,Engineering
                """;

        List<String> result = csvParser.extractEmployeeIds(csvContent);
        assertEquals("EMP001", result.get(0));
    }
}
