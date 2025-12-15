package com.example.dataflow.transform;

import com.example.dataflow.model.PageRequest;
import org.apache.beam.sdk.transforms.DoFn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DoFn to generate page requests for parallel processing
 * Takes total count and generates individual page requests
 */
public class GeneratePageRequestsFn extends DoFn<Integer, PageRequest> {
    
    private static final Logger LOG = LoggerFactory.getLogger(GeneratePageRequestsFn.class);
    private static final int PAGE_SIZE = 999;
    
    private final String effectiveDate;
    
    public GeneratePageRequestsFn(String effectiveDate) {
        this.effectiveDate = effectiveDate;
    }
    
    @ProcessElement
    public void processElement(@Element Integer totalCount, OutputReceiver<PageRequest> out) {
        // Calculate number of pages needed
        int totalPages = (int) Math.ceil((double) totalCount / PAGE_SIZE);
        
        LOG.info("Generating {} page requests for {} total records with effective date {}", 
                totalPages, totalCount, effectiveDate);
        
        // Generate page requests (pages are 1-indexed in Workday)
        for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
            PageRequest pageRequest = new PageRequest(pageNum, effectiveDate, PAGE_SIZE);
            out.output(pageRequest);
            LOG.debug("Generated page request: {}", pageRequest);
        }
        
        LOG.info("Successfully generated {} page requests", totalPages);
    }
}
