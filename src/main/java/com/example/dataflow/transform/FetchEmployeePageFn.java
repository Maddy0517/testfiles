package com.example.dataflow.transform;

import com.example.dataflow.client.WorkdaySoapClient;
import com.example.dataflow.config.WorkdayConfig;
import com.example.dataflow.model.Employee;
import com.example.dataflow.model.PageRequest;
import org.apache.beam.sdk.transforms.DoFn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * DoFn to fetch employee data from Workday SOAP API for a specific page
 * This enables parallel processing of pages across multiple workers
 */
public class FetchEmployeePageFn extends DoFn<PageRequest, Employee> {
    
    private static final Logger LOG = LoggerFactory.getLogger(FetchEmployeePageFn.class);
    
    private final WorkdayConfig config;
    private transient WorkdaySoapClient client;
    
    public FetchEmployeePageFn(WorkdayConfig config) {
        this.config = config;
    }
    
    @Setup
    public void setup() {
        LOG.info("Initializing Workday SOAP client for worker");
        this.client = new WorkdaySoapClient(config);
    }
    
    @ProcessElement
    public void processElement(@Element PageRequest pageRequest, OutputReceiver<Employee> out) {
        try {
            LOG.info("Processing page request: {}", pageRequest);
            
            long startTime = System.currentTimeMillis();
            
            // Fetch employees for this page
            List<Employee> employees = client.fetchEmployeePage(
                    pageRequest.getEffectiveDate(), 
                    pageRequest.getPageNumber()
            );
            
            long duration = System.currentTimeMillis() - startTime;
            
            LOG.info("Fetched {} employees from page {} in {} ms", 
                    employees.size(), pageRequest.getPageNumber(), duration);
            
            // Output each employee individually for downstream processing
            for (Employee employee : employees) {
                out.output(employee);
            }
            
        } catch (Exception e) {
            LOG.error("Error processing page {}: {}", pageRequest.getPageNumber(), e.getMessage(), e);
            throw new RuntimeException("Failed to process page " + pageRequest.getPageNumber(), e);
        }
    }
    
    @Teardown
    public void teardown() {
        LOG.info("Tearing down Workday SOAP client");
        // Cleanup if needed
    }
}
