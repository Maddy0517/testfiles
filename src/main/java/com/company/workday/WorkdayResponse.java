package com.company.workday;

import java.io.Serializable;
import java.util.List;

/**
 * Response wrapper for Workday SOAP API calls.
 * Contains the employee data along with pagination metadata.
 */
public class WorkdayResponse implements Serializable {
    
    private List<WorkdayEmployee> employees;
    private int totalResults;
    private int pageNumber;
    private int pageSize;
    private boolean hasMorePages;
    private String responseTime;
    
    // Default constructor
    public WorkdayResponse() {}
    
    // Constructor with all fields
    public WorkdayResponse(List<WorkdayEmployee> employees, int totalResults, 
                          int pageNumber, int pageSize) {
        this.employees = employees;
        this.totalResults = totalResults;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.hasMorePages = calculateHasMorePages();
    }
    
    // Getters and Setters
    public List<WorkdayEmployee> getEmployees() {
        return employees;
    }
    
    public void setEmployees(List<WorkdayEmployee> employees) {
        this.employees = employees;
    }
    
    public int getTotalResults() {
        return totalResults;
    }
    
    public void setTotalResults(int totalResults) {
        this.totalResults = totalResults;
        this.hasMorePages = calculateHasMorePages();
    }
    
    public int getPageNumber() {
        return pageNumber;
    }
    
    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
        this.hasMorePages = calculateHasMorePages();
    }
    
    public int getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
        this.hasMorePages = calculateHasMorePages();
    }
    
    public boolean hasMorePages() {
        return hasMorePages;
    }
    
    public void setHasMorePages(boolean hasMorePages) {
        this.hasMorePages = hasMorePages;
    }
    
    public String getResponseTime() {
        return responseTime;
    }
    
    public void setResponseTime(String responseTime) {
        this.responseTime = responseTime;
    }
    
    /**
     * Calculate if there are more pages based on current pagination info.
     */
    private boolean calculateHasMorePages() {
        if (totalResults <= 0 || pageSize <= 0) {
            return false;
        }
        
        int totalPages = (int) Math.ceil((double) totalResults / pageSize);
        return pageNumber < totalPages;
    }
    
    /**
     * Get the total number of pages.
     */
    public int getTotalPages() {
        if (totalResults <= 0 || pageSize <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalResults / pageSize);
    }
    
    /**
     * Get the number of employees in the current page.
     */
    public int getCurrentPageSize() {
        return employees != null ? employees.size() : 0;
    }
    
    @Override
    public String toString() {
        return "WorkdayResponse{" +
                "employeeCount=" + (employees != null ? employees.size() : 0) +
                ", totalResults=" + totalResults +
                ", pageNumber=" + pageNumber +
                ", pageSize=" + pageSize +
                ", hasMorePages=" + hasMorePages +
                ", totalPages=" + getTotalPages() +
                '}';
    }
}