package com.company.workday;

import org.apache.beam.sdk.transforms.DoFn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * DoFn to extract employee data from Workday SOAP API for a specific page.
 * This function handles the actual SOAP API calls with proper error handling and retry logic.
 */
public class ExtractWorkdayEmployeesFn extends DoFn<Integer, WorkdayEmployee> {
    
    private static final Logger LOG = LoggerFactory.getLogger(ExtractWorkdayEmployeesFn.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;
    
    private final WorkdayConfig config;
    private transient WorkdaySOAPClient soapClient;
    
    public ExtractWorkdayEmployeesFn(WorkdayConfig config) {
        this.config = config;
    }
    
    @Setup
    public void setup() {
        this.soapClient = new WorkdaySOAPClient(config);
        LOG.info("Initialized Workday SOAP client for worker");
    }
    
    @ProcessElement
    public void processElement(@Element Integer pageNumber, OutputReceiver<WorkdayEmployee> out) {
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < MAX_RETRIES) {
            try {
                LOG.info("Processing page {} (attempt {})", pageNumber, attempt + 1);
                
                WorkdayResponse response = soapClient.getEmployees(pageNumber, config.getPageSize());
                
                if (response == null) {
                    LOG.warn("Received null response for page {}", pageNumber);
                    return;
                }
                
                List<WorkdayEmployee> employees = response.getEmployees();
                if (employees == null || employees.isEmpty()) {
                    LOG.info("No employees found on page {}", pageNumber);
                    return;
                }
                
                LOG.info("Successfully retrieved {} employees from page {}", employees.size(), pageNumber);
                
                // Output each employee
                for (WorkdayEmployee employee : employees) {
                    out.output(employee);
                }
                
                return; // Success, exit retry loop
                
            } catch (Exception e) {
                attempt++;
                lastException = e;
                LOG.warn("Attempt {} failed for page {}: {}", attempt, pageNumber, e.getMessage());
                
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry delay", ie);
                    }
                } else {
                    LOG.error("All {} attempts failed for page {}", MAX_RETRIES, pageNumber, lastException);
                    throw new RuntimeException("Failed to extract employees from page " + pageNumber, lastException);
                }
            }
        }
    }
    
    @Teardown
    public void teardown() {
        if (soapClient != null) {
            soapClient.close();
            LOG.info("Closed Workday SOAP client");
        }
    }
}