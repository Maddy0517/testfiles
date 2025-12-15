package com.example.dataflow.transform;

import com.example.dataflow.model.Employee;
import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.sdk.transforms.DoFn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DoFn to convert Employee objects to BigQuery TableRow format
 */
public class EmployeeToTableRowFn extends DoFn<Employee, TableRow> {
    
    private static final Logger LOG = LoggerFactory.getLogger(EmployeeToTableRowFn.class);
    
    @ProcessElement
    public void processElement(@Element Employee employee, OutputReceiver<TableRow> out) {
        try {
            TableRow row = new TableRow()
                    .set("employee_id", employee.getEmployeeId())
                    .set("first_name", employee.getFirstName())
                    .set("last_name", employee.getLastName())
                    .set("email", employee.getEmail())
                    .set("phone", employee.getPhone())
                    .set("hire_date", employee.getHireDate())
                    .set("job_title", employee.getJobTitle())
                    .set("department", employee.getDepartment())
                    .set("manager_id", employee.getManagerId())
                    .set("location", employee.getLocation())
                    .set("employment_status", employee.getEmploymentStatus())
                    .set("effective_date", employee.getEffectiveDate())
                    .set("ingestion_timestamp", employee.getIngestionTimestamp());
            
            out.output(row);
            
        } catch (Exception e) {
            LOG.error("Error converting employee to TableRow: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert employee to TableRow", e);
        }
    }
}
