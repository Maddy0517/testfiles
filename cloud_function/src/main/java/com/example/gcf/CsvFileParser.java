package com.example.gcf;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for parsing CSV files to extract employee IDs.
 * 
 * Supports various column name formats for employee ID:
 * - employee_id, employeeid, emp_id, empid, id
 * - employee-id, emp-id, staff_id, worker_id
 */
public class CsvFileParser {

    private static final Logger logger = Logger.getLogger(CsvFileParser.class.getName());

    // Possible column names for employee ID (normalized - lowercase, underscores)
    private static final List<String> EMPLOYEE_ID_COLUMNS = Arrays.asList(
            "employee_id",
            "employeeid",
            "emp_id",
            "empid",
            "id",
            "employee_id",
            "emp_id",
            "staff_id",
            "staffid",
            "worker_id",
            "workerid"
    );

    /**
     * Extracts employee IDs from CSV content.
     *
     * @param content CSV file content as a string
     * @return List of employee IDs found in the file
     */
    public List<String> extractEmployeeIds(String content) {
        List<String> employeeIds = new ArrayList<>();

        if (content == null || content.trim().isEmpty()) {
            logger.warning("Empty CSV content provided");
            return employeeIds;
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            // Read header line
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.trim().isEmpty()) {
                logger.warning("CSV file has no header row");
                return employeeIds;
            }

            // Parse header to find employee ID column index
            String[] headers = parseCsvLine(headerLine);
            int employeeIdIndex = findEmployeeIdColumnIndex(headers);

            if (employeeIdIndex == -1) {
                logger.warning("No employee ID column found in CSV headers: " + Arrays.toString(headers));
                return employeeIds;
            }

            logger.info("Found employee ID column '" + headers[employeeIdIndex] + 
                       "' at index " + employeeIdIndex);

            // Read data rows
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] values = parseCsvLine(line);
                
                if (values.length <= employeeIdIndex) {
                    logger.fine("Line " + lineNumber + " has fewer columns than expected, skipping");
                    continue;
                }

                String employeeId = values[employeeIdIndex].trim();
                
                // Skip empty values
                if (!employeeId.isEmpty()) {
                    employeeIds.add(employeeId);
                }
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error parsing CSV content", e);
        }

        return employeeIds;
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
     * Converts to lowercase, replaces spaces and hyphens with underscores.
     *
     * @param columnName Original column name
     * @return Normalized column name
     */
    private String normalizeColumnName(String columnName) {
        if (columnName == null) {
            return "";
        }
        return columnName.toLowerCase()
                .trim()
                .replace(" ", "_")
                .replace("-", "_");
    }

    /**
     * Parses a CSV line, handling quoted values containing commas.
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
                // Check for escaped quote (double quote)
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    currentValue.append('"');
                    i++; // Skip next quote
                } else {
                    // Toggle quote state
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // End of field
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

    /**
     * Gets the list of supported employee ID column names.
     *
     * @return List of supported column names
     */
    public List<String> getSupportedColumnNames() {
        return new ArrayList<>(EMPLOYEE_ID_COLUMNS);
    }
}
