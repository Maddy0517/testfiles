package com.company.workday;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Data model representing a Workday employee.
 * This class contains the core employee information extracted from Workday SOAP API.
 */
public class WorkdayEmployee implements Serializable {
    
    private String workerId;
    private String employeeId;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String phoneNumber;
    private LocalDate hireDate;
    private LocalDate terminationDate;
    private String employmentStatus;
    private String jobTitle;
    private String department;
    private String location;
    private String manager;
    private String managerId;
    private String costCenter;
    private String businessUnit;
    private Double annualSalary;
    private String currency;
    private String payGroup;
    private LocalDate effectiveDate;
    private String lastModifiedBy;
    private String lastModifiedDate;
    
    // Default constructor
    public WorkdayEmployee() {}
    
    // Builder pattern for easy construction
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters and Setters
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }
    
    public LocalDate getTerminationDate() { return terminationDate; }
    public void setTerminationDate(LocalDate terminationDate) { this.terminationDate = terminationDate; }
    
    public String getEmploymentStatus() { return employmentStatus; }
    public void setEmploymentStatus(String employmentStatus) { this.employmentStatus = employmentStatus; }
    
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    
    public String getManager() { return manager; }
    public void setManager(String manager) { this.manager = manager; }
    
    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }
    
    public String getCostCenter() { return costCenter; }
    public void setCostCenter(String costCenter) { this.costCenter = costCenter; }
    
    public String getBusinessUnit() { return businessUnit; }
    public void setBusinessUnit(String businessUnit) { this.businessUnit = businessUnit; }
    
    public Double getAnnualSalary() { return annualSalary; }
    public void setAnnualSalary(Double annualSalary) { this.annualSalary = annualSalary; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public String getPayGroup() { return payGroup; }
    public void setPayGroup(String payGroup) { this.payGroup = payGroup; }
    
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    
    public String getLastModifiedBy() { return lastModifiedBy; }
    public void setLastModifiedBy(String lastModifiedBy) { this.lastModifiedBy = lastModifiedBy; }
    
    public String getLastModifiedDate() { return lastModifiedDate; }
    public void setLastModifiedDate(String lastModifiedDate) { this.lastModifiedDate = lastModifiedDate; }
    
    @Override
    public String toString() {
        return "WorkdayEmployee{" +
                "workerId='" + workerId + '\'' +
                ", employeeId='" + employeeId + '\'' +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", jobTitle='" + jobTitle + '\'' +
                ", department='" + department + '\'' +
                ", employmentStatus='" + employmentStatus + '\'' +
                '}';
    }
    
    public static class Builder {
        private final WorkdayEmployee employee = new WorkdayEmployee();
        
        public Builder workerId(String workerId) {
            employee.setWorkerId(workerId);
            return this;
        }
        
        public Builder employeeId(String employeeId) {
            employee.setEmployeeId(employeeId);
            return this;
        }
        
        public Builder firstName(String firstName) {
            employee.setFirstName(firstName);
            return this;
        }
        
        public Builder lastName(String lastName) {
            employee.setLastName(lastName);
            return this;
        }
        
        public Builder fullName(String fullName) {
            employee.setFullName(fullName);
            return this;
        }
        
        public Builder email(String email) {
            employee.setEmail(email);
            return this;
        }
        
        public Builder phoneNumber(String phoneNumber) {
            employee.setPhoneNumber(phoneNumber);
            return this;
        }
        
        public Builder hireDate(LocalDate hireDate) {
            employee.setHireDate(hireDate);
            return this;
        }
        
        public Builder terminationDate(LocalDate terminationDate) {
            employee.setTerminationDate(terminationDate);
            return this;
        }
        
        public Builder employmentStatus(String employmentStatus) {
            employee.setEmploymentStatus(employmentStatus);
            return this;
        }
        
        public Builder jobTitle(String jobTitle) {
            employee.setJobTitle(jobTitle);
            return this;
        }
        
        public Builder department(String department) {
            employee.setDepartment(department);
            return this;
        }
        
        public Builder location(String location) {
            employee.setLocation(location);
            return this;
        }
        
        public Builder manager(String manager) {
            employee.setManager(manager);
            return this;
        }
        
        public Builder managerId(String managerId) {
            employee.setManagerId(managerId);
            return this;
        }
        
        public Builder costCenter(String costCenter) {
            employee.setCostCenter(costCenter);
            return this;
        }
        
        public Builder businessUnit(String businessUnit) {
            employee.setBusinessUnit(businessUnit);
            return this;
        }
        
        public Builder annualSalary(Double annualSalary) {
            employee.setAnnualSalary(annualSalary);
            return this;
        }
        
        public Builder currency(String currency) {
            employee.setCurrency(currency);
            return this;
        }
        
        public Builder payGroup(String payGroup) {
            employee.setPayGroup(payGroup);
            return this;
        }
        
        public Builder effectiveDate(LocalDate effectiveDate) {
            employee.setEffectiveDate(effectiveDate);
            return this;
        }
        
        public Builder lastModifiedBy(String lastModifiedBy) {
            employee.setLastModifiedBy(lastModifiedBy);
            return this;
        }
        
        public Builder lastModifiedDate(String lastModifiedDate) {
            employee.setLastModifiedDate(lastModifiedDate);
            return this;
        }
        
        public WorkdayEmployee build() {
            return employee;
        }
    }
}