package com.example.workday.model;

import com.google.api.services.bigquery.model.TableRow;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Employee model representing Workday employee data
 */
public class Employee implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String employeeId;
    private String workerReferenceId;
    private String firstName;
    private String lastName;
    private String preferredName;
    private String email;
    private String phoneNumber;
    private String jobTitle;
    private String department;
    private String location;
    private String managerId;
    private String managerName;
    private String employmentStatus;
    private LocalDate hireDate;
    private LocalDate terminationDate;
    private String employeeType;
    private String costCenter;
    private String businessUnit;
    private Double salary;
    private String currency;
    private String payFrequency;
    private LocalDate effectiveDate;
    private LocalDateTime lastModifiedDate;
    private String dataSource;
    private LocalDateTime extractedAt;
    
    // Constructor
    public Employee() {
        this.extractedAt = LocalDateTime.now();
        this.dataSource = "Workday";
    }
    
    // Convert to BigQuery TableRow
    public TableRow toBigQueryRow() {
        TableRow row = new TableRow();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE;
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        
        row.set("employee_id", employeeId);
        row.set("worker_reference_id", workerReferenceId);
        row.set("first_name", firstName);
        row.set("last_name", lastName);
        row.set("preferred_name", preferredName);
        row.set("email", email);
        row.set("phone_number", phoneNumber);
        row.set("job_title", jobTitle);
        row.set("department", department);
        row.set("location", location);
        row.set("manager_id", managerId);
        row.set("manager_name", managerName);
        row.set("employment_status", employmentStatus);
        row.set("hire_date", hireDate != null ? hireDate.format(dateFormatter) : null);
        row.set("termination_date", terminationDate != null ? terminationDate.format(dateFormatter) : null);
        row.set("employee_type", employeeType);
        row.set("cost_center", costCenter);
        row.set("business_unit", businessUnit);
        row.set("salary", salary);
        row.set("currency", currency);
        row.set("pay_frequency", payFrequency);
        row.set("effective_date", effectiveDate != null ? effectiveDate.format(dateFormatter) : null);
        row.set("last_modified_date", lastModifiedDate != null ? lastModifiedDate.format(dateTimeFormatter) : null);
        row.set("data_source", dataSource);
        row.set("extracted_at", extractedAt.format(dateTimeFormatter));
        
        return row;
    }
    
    // Getters and Setters
    public String getEmployeeId() {
        return employeeId;
    }
    
    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }
    
    public String getWorkerReferenceId() {
        return workerReferenceId;
    }
    
    public void setWorkerReferenceId(String workerReferenceId) {
        this.workerReferenceId = workerReferenceId;
    }
    
    public String getFirstName() {
        return firstName;
    }
    
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    public String getLastName() {
        return lastName;
    }
    
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    
    public String getPreferredName() {
        return preferredName;
    }
    
    public void setPreferredName(String preferredName) {
        this.preferredName = preferredName;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public String getJobTitle() {
        return jobTitle;
    }
    
    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }
    
    public String getDepartment() {
        return department;
    }
    
    public void setDepartment(String department) {
        this.department = department;
    }
    
    public String getLocation() {
        return location;
    }
    
    public void setLocation(String location) {
        this.location = location;
    }
    
    public String getManagerId() {
        return managerId;
    }
    
    public void setManagerId(String managerId) {
        this.managerId = managerId;
    }
    
    public String getManagerName() {
        return managerName;
    }
    
    public void setManagerName(String managerName) {
        this.managerName = managerName;
    }
    
    public String getEmploymentStatus() {
        return employmentStatus;
    }
    
    public void setEmploymentStatus(String employmentStatus) {
        this.employmentStatus = employmentStatus;
    }
    
    public LocalDate getHireDate() {
        return hireDate;
    }
    
    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }
    
    public LocalDate getTerminationDate() {
        return terminationDate;
    }
    
    public void setTerminationDate(LocalDate terminationDate) {
        this.terminationDate = terminationDate;
    }
    
    public String getEmployeeType() {
        return employeeType;
    }
    
    public void setEmployeeType(String employeeType) {
        this.employeeType = employeeType;
    }
    
    public String getCostCenter() {
        return costCenter;
    }
    
    public void setCostCenter(String costCenter) {
        this.costCenter = costCenter;
    }
    
    public String getBusinessUnit() {
        return businessUnit;
    }
    
    public void setBusinessUnit(String businessUnit) {
        this.businessUnit = businessUnit;
    }
    
    public Double getSalary() {
        return salary;
    }
    
    public void setSalary(Double salary) {
        this.salary = salary;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public String getPayFrequency() {
        return payFrequency;
    }
    
    public void setPayFrequency(String payFrequency) {
        this.payFrequency = payFrequency;
    }
    
    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }
    
    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }
    
    public LocalDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }
    
    public void setLastModifiedDate(LocalDateTime lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }
    
    public String getDataSource() {
        return dataSource;
    }
    
    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }
    
    public LocalDateTime getExtractedAt() {
        return extractedAt;
    }
    
    public void setExtractedAt(LocalDateTime extractedAt) {
        this.extractedAt = extractedAt;
    }
}