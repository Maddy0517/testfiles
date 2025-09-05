package com.workday.dataflow;

import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Apache Beam transform to process and enrich worker data for BigQuery.
 */
public class WorkerDataTransform extends PTransform<PCollection<WorkerData>, PCollection<TableRow>> {
    private static final Logger LOG = LoggerFactory.getLogger(WorkerDataTransform.class);

    @Override
    public PCollection<TableRow> expand(PCollection<WorkerData> input) {
        return input
            .apply("Transform to TableRow", ParDo.of(new WorkerDataToTableRowDoFn()))
            .apply("Validate and Clean Data", ParDo.of(new DataValidationDoFn()));
    }

    /**
     * DoFn to convert WorkerData to BigQuery TableRow
     */
    private static class WorkerDataToTableRowDoFn extends DoFn<WorkerData, TableRow> {
        
        @ProcessElement
        public void processElement(@Element WorkerData worker, OutputReceiver<TableRow> out) {
            try {
                TableRow row = new TableRow();
                
                row.set("worker_id", worker.getWorkerId());
                row.set("employee_id", worker.getEmployeeId());
                row.set("first_name", worker.getFirstName());
                row.set("last_name", worker.getLastName());
                row.set("full_name", buildFullName(worker.getFirstName(), worker.getLastName()));
                row.set("email", worker.getEmail());
                row.set("job_title", worker.getJobTitle());
                row.set("department", worker.getDepartment());
                row.set("hire_date", formatDate(worker.getHireDate()));
                row.set("status", worker.getStatus());
                row.set("salary", worker.getSalary());
                row.set("location", worker.getLocation());
                row.set("manager_id", worker.getManagerId());
                row.set("last_modified", formatTimestamp(worker.getLastModified()));
                row.set("processed_timestamp", Instant.now().toString());
                
                // Add computed fields
                row.set("years_of_service", calculateYearsOfService(worker.getHireDate()));
                row.set("salary_band", categorizeSalary(worker.getSalary()));
                
                out.output(row);
                
            } catch (Exception e) {
                LOG.error("Failed to transform worker data: {}", worker.getWorkerId(), e);
                // In production, you might want to output to a dead letter queue instead
                throw new RuntimeException("Failed to transform worker data", e);
            }
        }

        private String buildFullName(String firstName, String lastName) {
            if (firstName == null && lastName == null) return null;
            if (firstName == null) return lastName;
            if (lastName == null) return firstName;
            return firstName + " " + lastName;
        }

        private String formatDate(String dateString) {
            if (dateString == null || dateString.trim().isEmpty()) {
                return null;
            }
            
            try {
                // Assuming input format is YYYY-MM-DD
                LocalDate date = LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE);
                return date.toString();
            } catch (DateTimeParseException e) {
                LOG.warn("Failed to parse date: {}", dateString);
                return dateString; // Return original if parsing fails
            }
        }

        private String formatTimestamp(Long timestamp) {
            if (timestamp == null) {
                return Instant.now().toString();
            }
            return Instant.ofEpochMilli(timestamp).toString();
        }

        private Integer calculateYearsOfService(String hireDateString) {
            if (hireDateString == null || hireDateString.trim().isEmpty()) {
                return null;
            }
            
            try {
                LocalDate hireDate = LocalDate.parse(hireDateString, DateTimeFormatter.ISO_LOCAL_DATE);
                LocalDate currentDate = LocalDate.now();
                return currentDate.getYear() - hireDate.getYear();
            } catch (DateTimeParseException e) {
                LOG.warn("Failed to calculate years of service for date: {}", hireDateString);
                return null;
            }
        }

        private String categorizeSalary(Double salary) {
            if (salary == null) {
                return "Unknown";
            }
            
            if (salary < 50000) {
                return "Entry Level";
            } else if (salary < 75000) {
                return "Mid Level";
            } else if (salary < 100000) {
                return "Senior Level";
            } else if (salary < 150000) {
                return "Lead Level";
            } else {
                return "Executive Level";
            }
        }
    }

    /**
     * DoFn to validate and clean data before sending to BigQuery
     */
    private static class DataValidationDoFn extends DoFn<TableRow, TableRow> {
        
        @ProcessElement
        public void processElement(@Element TableRow row, OutputReceiver<TableRow> out) {
            // Validate required fields
            if (row.get("worker_id") == null || row.get("worker_id").toString().trim().isEmpty()) {
                LOG.warn("Skipping row with missing worker_id");
                return;
            }

            if (row.get("employee_id") == null || row.get("employee_id").toString().trim().isEmpty()) {
                LOG.warn("Skipping row with missing employee_id for worker: {}", row.get("worker_id"));
                return;
            }

            // Clean and validate data
            cleanStringFields(row);
            validateNumericFields(row);
            
            out.output(row);
        }

        private void cleanStringFields(TableRow row) {
            // Clean string fields by trimming whitespace and handling nulls
            String[] stringFields = {"first_name", "last_name", "full_name", "email", 
                                   "job_title", "department", "status", "location", "manager_id"};
            
            for (String field : stringFields) {
                Object value = row.get(field);
                if (value != null) {
                    String cleanValue = value.toString().trim();
                    row.set(field, cleanValue.isEmpty() ? null : cleanValue);
                }
            }
        }

        private void validateNumericFields(TableRow row) {
            // Validate salary
            Object salaryObj = row.get("salary");
            if (salaryObj != null) {
                try {
                    Double salary = Double.valueOf(salaryObj.toString());
                    if (salary < 0) {
                        LOG.warn("Invalid negative salary for worker: {}", row.get("worker_id"));
                        row.set("salary", null);
                    } else if (salary > 1000000) {
                        LOG.warn("Unusually high salary for worker: {}", row.get("worker_id"));
                        // Keep the value but log the warning
                    }
                } catch (NumberFormatException e) {
                    LOG.warn("Invalid salary format for worker: {}", row.get("worker_id"));
                    row.set("salary", null);
                }
            }

            // Validate years of service
            Object yearsObj = row.get("years_of_service");
            if (yearsObj != null) {
                try {
                    Integer years = Integer.valueOf(yearsObj.toString());
                    if (years < 0 || years > 70) {
                        LOG.warn("Invalid years of service for worker: {}", row.get("worker_id"));
                        row.set("years_of_service", null);
                    }
                } catch (NumberFormatException e) {
                    LOG.warn("Invalid years of service format for worker: {}", row.get("worker_id"));
                    row.set("years_of_service", null);
                }
            }
        }
    }
}