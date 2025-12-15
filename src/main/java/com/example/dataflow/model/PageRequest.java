package com.example.dataflow.model;

import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.DefaultCoder;

import java.io.Serializable;

/**
 * Represents a page request for Workday API pagination
 */
@DefaultCoder(AvroCoder.class)
public class PageRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private int pageNumber;
    private String effectiveDate;
    private int pageSize;
    
    // Default constructor required for serialization
    public PageRequest() {
        this.pageSize = 999; // Workday max page size
    }
    
    public PageRequest(int pageNumber, String effectiveDate) {
        this.pageNumber = pageNumber;
        this.effectiveDate = effectiveDate;
        this.pageSize = 999;
    }
    
    public PageRequest(int pageNumber, String effectiveDate, int pageSize) {
        this.pageNumber = pageNumber;
        this.effectiveDate = effectiveDate;
        this.pageSize = pageSize;
    }
    
    public int getPageNumber() {
        return pageNumber;
    }
    
    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }
    
    public String getEffectiveDate() {
        return effectiveDate;
    }
    
    public void setEffectiveDate(String effectiveDate) {
        this.effectiveDate = effectiveDate;
    }
    
    public int getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
    
    @Override
    public String toString() {
        return "PageRequest{" +
                "pageNumber=" + pageNumber +
                ", effectiveDate='" + effectiveDate + '\'' +
                ", pageSize=" + pageSize +
                '}';
    }
}
