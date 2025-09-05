package com.workday.dataflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Data model for Workday Worker information.
 * This class represents the structure of worker data retrieved from Workday SOAP API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkerData implements Serializable {
    
    @JsonProperty("worker_id")
    private String workerId;
    
    @JsonProperty("employee_id")
    private String employeeId;
    
    @JsonProperty("first_name")
    private String firstName;
    
    @JsonProperty("last_name")
    private String lastName;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("job_title")
    private String jobTitle;
    
    @JsonProperty("department")
    private String department;
    
    @JsonProperty("hire_date")
    private String hireDate;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("salary")
    private Double salary;
    
    @JsonProperty("location")
    private String location;
    
    @JsonProperty("manager_id")
    private String managerId;
    
    @JsonProperty("last_modified")
    private Long lastModified;

    // Default constructor
    public WorkerData() {}

    // Getters and Setters
    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

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

    public String getHireDate() {
        return hireDate;
    }

    public void setHireDate(String hireDate) {
        this.hireDate = hireDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getSalary() {
        return salary;
    }

    public void setSalary(Double salary) {
        this.salary = salary;
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

    public Long getLastModified() {
        return lastModified;
    }

    public void setLastModified(Long lastModified) {
        this.lastModified = lastModified;
    }

    @Override
    public String toString() {
        return "WorkerData{" +
                "workerId='" + workerId + '\'' +
                ", employeeId='" + employeeId + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                ", jobTitle='" + jobTitle + '\'' +
                ", department='" + department + '\'' +
                ", hireDate='" + hireDate + '\'' +
                ", status='" + status + '\'' +
                ", salary=" + salary +
                ", location='" + location + '\'' +
                ", managerId='" + managerId + '\'' +
                ", lastModified=" + lastModified +
                '}';
    }
}