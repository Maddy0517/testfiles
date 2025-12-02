package com.example.gcf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GcsFileToBigQueryFunction class.
 * Tests the HUM code extraction logic.
 */
class GcsFileToBigQueryFunctionTest {

    private GcsFileToBigQueryFunction function;

    @BeforeEach
    void setUp() {
        // Create function with null dependencies (only testing extraction logic)
        function = new GcsFileToBigQueryFunction(null, null, null);
    }

    @ParameterizedTest
    @DisplayName("Should extract HUM code from various filename formats")
    @CsvSource({
            "employees_HUM-100_report.csv, HUM-100",
            "data_HUM-200_quarterly.json, HUM-200",
            "HUM-300_employees.csv, HUM-300",
            "report_hum-150_2024.csv, HUM-150",
            "HUM-999_test_HUM-100.csv, HUM-999",
            "path/to/HUM-456_file.csv, HUM-456",
            "uploads/2024/HUM-789_data.json, HUM-789",
            "HUM-1234_large_number.csv, HUM-1234"
    })
    void testExtractHumCode_ValidPatterns(String filename, String expectedCode) {
        String result = function.extractHumCode(filename);
        assertEquals(expectedCode, result);
    }

    @ParameterizedTest
    @DisplayName("Should return UNKNOWN for filenames without HUM code")
    @CsvSource({
            "employees_report.csv",
            "data_quarterly.json",
            "no_code_here.txt",
            "HUM_100_wrong_format.csv",
            "HUM100_missing_dash.csv",
            "report_ABC-100.csv"
    })
    void testExtractHumCode_NoMatch(String filename) {
        String result = function.extractHumCode(filename);
        assertEquals("UNKNOWN", result);
    }

    @Test
    @DisplayName("Should handle case-insensitive HUM code matching")
    void testExtractHumCode_CaseInsensitive() {
        assertEquals("HUM-100", function.extractHumCode("hum-100_file.csv"));
        assertEquals("HUM-200", function.extractHumCode("Hum-200_file.csv"));
        assertEquals("HUM-300", function.extractHumCode("HUM-300_file.csv"));
    }

    @Test
    @DisplayName("Should return first HUM code when multiple are present")
    void testExtractHumCode_MultipleMatches() {
        String result = function.extractHumCode("HUM-100_and_HUM-200_combined.csv");
        assertEquals("HUM-100", result);
    }

    @Test
    @DisplayName("Should extract HUM code from deeply nested paths")
    void testExtractHumCode_NestedPath() {
        String result = function.extractHumCode("uploads/2024/01/department/HUM-500_employees.csv");
        assertEquals("HUM-500", result);
    }
}
