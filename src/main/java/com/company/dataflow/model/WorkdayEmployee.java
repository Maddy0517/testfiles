package com.company.dataflow.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.Instant;

/**
 * Data model representing a Workday employee record.
 * This is a sample model - adjust fields based on your actual Workday schema.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkdayEmployee implements Serializable {
    
    @JsonProperty("employee_id")
    private String employeeId;
    
    @JsonProperty("first_name")
    private String firstName;
    
    @JsonProperty("last_name")
    private String lastName;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("hire_date")
    private String hireDate;
    
    @JsonProperty("department")
    private String department;
    
    @JsonProperty("job_title")
    private String jobTitle;
    
    @JsonProperty("manager_id")
    private String managerId;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("location")
    private String location;
    
    @JsonProperty("salary")
    private Double salary;
    
    @JsonProperty("last_modified")
    private String lastModified;
    
    @JsonProperty("ingestion_timestamp")
    private Instant ingestionTimestamp;

    // Default constructor
    public WorkdayEmployee() {
        this.ingestionTimestamp = Instant.now();
    }

    // Constructor with basic fields
    public WorkdayEmployee(String employeeId, String firstName, String lastName, String email) {
        this();
        this.employeeId = employeeId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    // Getters and Setters
    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getHireDate() {
        return hireDate;
    }

    public void setHireDate(String hireDate) {
        this.hireDate = hireDate;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getManagerId() {
        return managerId;
    }

    public void setManagerId(String managerId) {
        this.managerId = managerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Double getSalary() {
        return salary;
    }

    public void setSalary(Double salary) {
        this.salary = salary;
    }

    public String getLastModified() {
        return lastModified;
    }

    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }

    public Instant getIngestionTimestamp() {
        return ingestionTimestamp;
    }

    public void setIngestionTimestamp(Instant ingestionTimestamp) {
        this.ingestionTimestamp = ingestionTimestamp;
    }

    @Override
    public String toString() {
        return "WorkdayEmployee{" +
                "employeeId='" + employeeId + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                ", department='" + department + '\'' +
                ", jobTitle='" + jobTitle + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}