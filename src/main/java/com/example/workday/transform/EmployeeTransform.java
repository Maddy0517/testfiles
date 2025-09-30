package com.example.workday.transform;

import com.example.workday.model.Employee;
import org.apache.beam.sdk.transforms.DoFn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transform function for Employee data
 * Applies data validation, cleansing, and enrichment
 */
public class EmployeeTransform extends DoFn<Employee, Employee> {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(EmployeeTransform.class);
    
    @ProcessElement
    public void processElement(ProcessContext c) {
        Employee employee = c.element();
        
        try {
            // Data validation
            if (employee.getEmployeeId() == null || employee.getEmployeeId().isEmpty()) {
                LOG.warn("Employee with missing ID, skipping: {}", employee);
                return;
            }
            
            // Data cleansing
            employee = cleanseEmployee(employee);
            
            // Data enrichment
            employee = enrichEmployee(employee);
            
            // Output the transformed employee
            c.output(employee);
            
        } catch (Exception e) {
            LOG.error("Error processing employee: {}", employee, e);
            // You might want to output to a dead letter queue here
        }
    }
    
    private Employee cleanseEmployee(Employee employee) {
        // Trim whitespace from string fields
        if (employee.getFirstName() != null) {
            employee.setFirstName(employee.getFirstName().trim());
        }
        if (employee.getLastName() != null) {
            employee.setLastName(employee.getLastName().trim());
        }
        if (employee.getEmail() != null) {
            employee.setEmail(employee.getEmail().trim().toLowerCase());
        }
        if (employee.getDepartment() != null) {
            employee.setDepartment(employee.getDepartment().trim());
        }
        if (employee.getLocation() != null) {
            employee.setLocation(employee.getLocation().trim());
        }
        if (employee.getJobTitle() != null) {
            employee.setJobTitle(employee.getJobTitle().trim());
        }
        
        // Standardize phone numbers (remove non-numeric characters)
        if (employee.getPhoneNumber() != null) {
            String cleanPhone = employee.getPhoneNumber().replaceAll("[^0-9+]", "");
            employee.setPhoneNumber(cleanPhone);
        }
        
        // Standardize employment status
        if (employee.getEmploymentStatus() != null) {
            String status = employee.getEmploymentStatus().toUpperCase();
            if (status.contains("ACTIVE") || status.contains("EMPLOYED")) {
                employee.setEmploymentStatus("ACTIVE");
            } else if (status.contains("TERMINATED") || status.contains("INACTIVE")) {
                employee.setEmploymentStatus("TERMINATED");
            } else if (status.contains("LEAVE")) {
                employee.setEmploymentStatus("ON_LEAVE");
            }
        }
        
        // Handle null values for numeric fields
        if (employee.getSalary() != null && employee.getSalary() < 0) {
            employee.setSalary(null); // Invalid salary
        }
        
        return employee;
    }
    
    private Employee enrichEmployee(Employee employee) {
        // Generate preferred name if missing
        if (employee.getPreferredName() == null || employee.getPreferredName().isEmpty()) {
            if (employee.getFirstName() != null) {
                employee.setPreferredName(employee.getFirstName());
            }
        }
        
        // Set default currency if missing
        if (employee.getCurrency() == null || employee.getCurrency().isEmpty()) {
            employee.setCurrency("USD");
        }
        
        // Derive employee type from other fields if missing
        if (employee.getEmployeeType() == null || employee.getEmployeeType().isEmpty()) {
            if (employee.getPayFrequency() != null) {
                if (employee.getPayFrequency().contains("HOURLY")) {
                    employee.setEmployeeType("HOURLY");
                } else if (employee.getPayFrequency().contains("SALARY") || 
                          employee.getPayFrequency().contains("MONTHLY")) {
                    employee.setEmployeeType("SALARIED");
                } else {
                    employee.setEmployeeType("CONTRACTOR");
                }
            }
        }
        
        // Add data quality flags or scores if needed
        // This could be expanded to include more sophisticated data quality checks
        
        return employee;
    }
}