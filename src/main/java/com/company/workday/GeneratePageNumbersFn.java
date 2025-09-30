package com.company.workday;

import org.apache.beam.sdk.transforms.DoFn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DoFn to generate page numbers for parallel processing of Workday SOAP API pagination.
 * This function first makes a test call to determine the total number of records,
 * then generates page numbers for parallel processing.
 */
public class GeneratePageNumbersFn extends DoFn<Integer, Integer> {
    
    private static final Logger LOG = LoggerFactory.getLogger(GeneratePageNumbersFn.class);
    
    private final WorkdayConfig config;
    private transient WorkdaySOAPClient soapClient;
    
    public GeneratePageNumbersFn(WorkdayConfig config) {
        this.config = config;
    }
    
    @Setup
    public void setup() {
        this.soapClient = new WorkdaySOAPClient(config);
    }
    
    @ProcessElement
    public void processElement(ProcessContext context) {
        try {
            LOG.info("Determining total number of pages for parallel processing");
            
            // Make initial call to get total count
            WorkdayResponse initialResponse = soapClient.getEmployees(1, 1); // Get just 1 record to check total
            
            if (initialResponse == null || initialResponse.getTotalResults() == 0) {
                LOG.warn("No employees found for the given criteria");
                return;
            }
            
            int totalRecords = initialResponse.getTotalResults();
            int pageSize = config.getPageSize();
            int totalPages = (int) Math.ceil((double) totalRecords / pageSize);
            
            LOG.info("Total records: {}, Page size: {}, Total pages: {}", 
                    totalRecords, pageSize, totalPages);
            
            // Generate page numbers for parallel processing
            for (int page = 1; page <= totalPages; page++) {
                context.output(page);
                LOG.debug("Generated page number: {}", page);
            }
            
        } catch (Exception e) {
            LOG.error("Error generating page numbers", e);
            throw new RuntimeException("Failed to generate page numbers", e);
        }
    }
    
    @Teardown
    public void teardown() {
        if (soapClient != null) {
            soapClient.close();
        }
    }
}