package com.example.gcf;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Simple CSV parser to extract employee IDs from CSV files.
 */
public class CsvFileParser {

    private static final Logger logger = Logger.getLogger(CsvFileParser.class.getName());

    // Supported column names for employee ID (lowercase)
    private static final String[] EMPLOYEE_ID_COLUMNS = {
        "employee_id", "employeeid", "emp_id", "empid", "id", "staff_id", "worker_id"
    };

    /**
     * Extracts employee IDs from CSV content.
     */
    public List<String> extractEmployeeIds(String content) {
        List<String> employeeIds = new ArrayList<>();

        if (content == null || content.trim().isEmpty()) {
            return employeeIds;
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            // Read header line
            String headerLine = reader.readLine();
            if (headerLine == null) return employeeIds;

            // Find employee ID column index
            String[] headers = parseLine(headerLine);
            int empIdIndex = findEmployeeIdColumn(headers);

            if (empIdIndex == -1) {
                logger.warning("No employee ID column found in headers");
                return employeeIds;
            }

            // Read data rows
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                String[] values = parseLine(line);
                if (values.length > empIdIndex) {
                    String empId = values[empIdIndex].trim();
                    if (!empId.isEmpty()) {
                        employeeIds.add(empId);
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error parsing CSV: " + e.getMessage());
        }

        return employeeIds;
    }

    /**
     * Finds employee ID column index.
     */
    private int findEmployeeIdColumn(String[] headers) {
        for (int i = 0; i < headers.length; i++) {
            String normalized = headers[i].toLowerCase().trim().replace("-", "_").replace(" ", "_");
            for (String col : EMPLOYEE_ID_COLUMNS) {
                if (normalized.equals(col)) return i;
            }
        }
        return -1;
    }

    /**
     * Parses a CSV line handling quoted values.
     */
    private String[] parseLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString().trim());
        return values.toArray(new String[0]);
    }
}
