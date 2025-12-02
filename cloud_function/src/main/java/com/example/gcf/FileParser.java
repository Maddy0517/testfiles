package com.example.gcf;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for parsing CSV and JSON files to extract employee IDs.
 */
public class FileParser {

    private static final Logger logger = Logger.getLogger(FileParser.class.getName());

    // Possible column names for employee ID (case-insensitive matching)
    private static final List<String> EMPLOYEE_ID_COLUMNS = Arrays.asList(
            "employee_id", "employeeid", "emp_id", "empid", "id",
            "employee-id", "emp-id", "staff_id", "staffid", "worker_id"
    );

    // Possible JSON field names for employee ID
    private static final List<String> EMPLOYEE_ID_FIELDS = Arrays.asList(
            "employee_id", "employeeId", "emp_id", "empId", "id",
            "employee-id", "emp-id", "staffId", "workerId"
    );

    /**
     * Extracts employee IDs from file content based on file type.
     *
     * @param content  The file content as a string
     * @param fileName The name of the file (used to determine format)
     * @return List of employee IDs found in the file
     */
    public List<String> extractEmployeeIds(String content, String fileName) {
        String lowerFileName = fileName.toLowerCase();

        if (lowerFileName.endsWith(".csv")) {
            return extractFromCsv(content);
        } else if (lowerFileName.endsWith(".json")) {
            return extractFromJson(content);
        } else {
            // Try CSV first, then JSON
            List<String> result = extractFromCsv(content);
            if (result.isEmpty()) {
                result = extractFromJson(content);
            }
            return result;
        }
    }

    /**
     * Extracts employee IDs from CSV content.
     *
     * @param content CSV file content
     * @return List of employee IDs
     */
    public List<String> extractFromCsv(String content) {
        List<String> employeeIds = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            // Read header line
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.trim().isEmpty()) {
                return employeeIds;
            }

            // Parse header to find employee ID column index
            String[] headers = parseCsvLine(headerLine);
            int employeeIdIndex = findEmployeeIdColumnIndex(headers);

            if (employeeIdIndex == -1) {
                logger.warning("No employee ID column found in CSV headers: " + Arrays.toString(headers));
                return employeeIds;
            }

            logger.info("Found employee ID column at index " + employeeIdIndex + ": " + headers[employeeIdIndex]);

            // Read data rows
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] values = parseCsvLine(line);
                if (values.length > employeeIdIndex) {
                    String employeeId = values[employeeIdIndex].trim();
                    if (!employeeId.isEmpty()) {
                        employeeIds.add(employeeId);
                    }
                }
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error parsing CSV content", e);
        }

        return employeeIds;
    }

    /**
     * Extracts employee IDs from JSON content.
     *
     * @param content JSON file content
     * @return List of employee IDs
     */
    public List<String> extractFromJson(String content) {
        List<String> employeeIds = new ArrayList<>();

        try {
            JsonElement rootElement = JsonParser.parseString(content);

            if (rootElement.isJsonArray()) {
                // Direct array of objects
                extractFromJsonArray(rootElement.getAsJsonArray(), employeeIds);
            } else if (rootElement.isJsonObject()) {
                JsonObject rootObject = rootElement.getAsJsonObject();

                // Try common container field names
                String[] containerFields = {"employees", "data", "records", "items", "results"};
                boolean found = false;

                for (String field : containerFields) {
                    if (rootObject.has(field) && rootObject.get(field).isJsonArray()) {
                        extractFromJsonArray(rootObject.getAsJsonArray(field), employeeIds);
                        found = true;
                        break;
                    }
                }

                // If no container found, try to extract from the root object itself
                if (!found) {
                    extractEmployeeIdFromObject(rootObject, employeeIds);
                }
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error parsing JSON content", e);
        }

        return employeeIds;
    }

    /**
     * Extracts employee IDs from a JSON array of objects.
     */
    private void extractFromJsonArray(JsonArray array, List<String> employeeIds) {
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                extractEmployeeIdFromObject(element.getAsJsonObject(), employeeIds);
            }
        }
    }

    /**
     * Extracts employee ID from a single JSON object.
     */
    private void extractEmployeeIdFromObject(JsonObject obj, List<String> employeeIds) {
        for (String fieldName : EMPLOYEE_ID_FIELDS) {
            if (obj.has(fieldName)) {
                JsonElement value = obj.get(fieldName);
                if (value != null && !value.isJsonNull()) {
                    String employeeId = value.getAsString().trim();
                    if (!employeeId.isEmpty()) {
                        employeeIds.add(employeeId);
                        return; // Found, exit
                    }
                }
            }
        }
    }

    /**
     * Finds the index of the employee ID column in CSV headers.
     *
     * @param headers Array of header names
     * @return Index of the employee ID column, or -1 if not found
     */
    private int findEmployeeIdColumnIndex(String[] headers) {
        for (int i = 0; i < headers.length; i++) {
            String normalizedHeader = normalizeColumnName(headers[i]);
            if (EMPLOYEE_ID_COLUMNS.contains(normalizedHeader)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Normalizes a column name for comparison.
     */
    private String normalizeColumnName(String columnName) {
        return columnName.toLowerCase()
                .trim()
                .replace(" ", "_")
                .replace("-", "_");
    }

    /**
     * Parses a CSV line, handling quoted values.
     *
     * @param line The CSV line to parse
     * @return Array of values
     */
    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder currentValue = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                // Check for escaped quote
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    currentValue.append('"');
                    i++; // Skip next quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(currentValue.toString().trim());
                currentValue = new StringBuilder();
            } else {
                currentValue.append(c);
            }
        }

        // Add last value
        values.add(currentValue.toString().trim());

        return values.toArray(new String[0]);
    }
}
