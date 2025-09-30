package com.company.workday;

import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.sdk.transforms.DoFn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;

/**
 * DoFn to transform WorkdayEmployee objects to BigQuery TableRow format.
 * Handles data type conversions and null value handling for BigQuery compatibility.
 */
public class TransformToBigQueryFn extends DoFn<WorkdayEmployee, TableRow> {
    
    private static final Logger LOG = LoggerFactory.getLogger(TransformToBigQueryFn.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    
    @ProcessElement
    public void processElement(@Element WorkdayEmployee employee, OutputReceiver<TableRow> out) {
        try {
            TableRow row = new TableRow();
            
            // Basic Information
            row.set("worker_id", employee.getWorkerId());
            row.set("employee_id", employee.getEmployeeId());
            row.set("first_name", employee.getFirstName());
            row.set("last_name", employee.getLastName());
            row.set("full_name", employee.getFullName());
            row.set("email", employee.getEmail());
            row.set("phone_number", employee.getPhoneNumber());
            
            // Employment Information
            row.set("employment_status", employee.getEmploymentStatus());
            row.set("job_title", employee.getJobTitle());
            row.set("department", employee.getDepartment());
            row.set("location", employee.getLocation());
            row.set("manager", employee.getManager());
            row.set("manager_id", employee.getManagerId());
            row.set("cost_center", employee.getCostCenter());
            row.set("business_unit", employee.getBusinessUnit());
            
            // Compensation Information
            row.set("annual_salary", employee.getAnnualSalary());
            row.set("currency", employee.getCurrency());
            row.set("pay_group", employee.getPayGroup());
            
            // Date Information
            if (employee.getHireDate() != null) {
                row.set("hire_date", employee.getHireDate().format(DATE_FORMATTER));
            }
            if (employee.getTerminationDate() != null) {
                row.set("termination_date", employee.getTerminationDate().format(DATE_FORMATTER));
            }
            if (employee.getEffectiveDate() != null) {
                row.set("effective_date", employee.getEffectiveDate().format(DATE_FORMATTER));
            }
            
            // Audit Information
            row.set("last_modified_by", employee.getLastModifiedBy());
            row.set("last_modified_date", employee.getLastModifiedDate());
            
            // Add processing timestamp
            row.set("processed_timestamp", java.time.Instant.now().toString());
            
            out.output(row);
            
        } catch (Exception e) {
            LOG.error("Error transforming employee to TableRow: {}", employee, e);
            // You might want to output to a dead letter queue here
            throw new RuntimeException("Failed to transform employee: " + employee.getWorkerId(), e);
        }
    }
}