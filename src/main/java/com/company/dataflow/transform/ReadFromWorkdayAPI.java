package com.company.dataflow.transform;

import com.company.dataflow.io.WorkdaySOAPClient;
import com.company.dataflow.model.WorkdayEmployee;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.values.PBegin;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Apache Beam transform to read data from Workday SOAP API.
 */
public class ReadFromWorkdayAPI extends PTransform<PBegin, PCollection<WorkdayEmployee>> {
    
    private static final Logger LOG = LoggerFactory.getLogger(ReadFromWorkdayAPI.class);
    
    private final String endpoint;
    private final String username;
    private final String password;
    private final String tenant;
    private final String version;
    private final int batchSize;
    private final int maxRetries;
    
    public ReadFromWorkdayAPI(String endpoint, String username, String password, 
                             String tenant, String version, int batchSize, int maxRetries) {
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
        this.tenant = tenant;
        this.version = version;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
    }
    
    @Override
    public PCollection<WorkdayEmployee> expand(PBegin input) {
        // Create initial collection with batch parameters
        List<BatchParams> batches = createBatches();
        
        return input.getPipeline()
            .apply("Create Batch Parameters", Create.of(batches))
            .apply("Fetch Employee Data", ParDo.of(new FetchEmployeeDataFn()));
    }
    
    /**
     * Creates batch parameters for parallel processing.
     */
    private List<BatchParams> createBatches() {
        List<BatchParams> batches = new ArrayList<>();
        
        try {
            WorkdaySOAPClient client = new WorkdaySOAPClient(endpoint, username, password, tenant, version);
            int totalCount = client.getTotalWorkerCount();
            
            LOG.info("Total workers to process: {}", totalCount);
            
            for (int offset = 0; offset < totalCount; offset += batchSize) {
                int currentBatchSize = Math.min(batchSize, totalCount - offset);
                batches.add(new BatchParams(offset, currentBatchSize));
            }
            
        } catch (Exception e) {
            LOG.error("Failed to get total worker count, creating single batch", e);
            // Fallback to single batch
            batches.add(new BatchParams(0, batchSize));
        }
        
        LOG.info("Created {} batches for processing", batches.size());
        return batches;
    }
    
    /**
     * DoFn to fetch employee data from Workday API.
     */
    private class FetchEmployeeDataFn extends DoFn<BatchParams, WorkdayEmployee> {
        
        @ProcessElement
        public void processElement(@Element BatchParams batchParams, OutputReceiver<WorkdayEmployee> out) {
            WorkdaySOAPClient client = new WorkdaySOAPClient(endpoint, username, password, tenant, version);
            
            int retryCount = 0;
            Exception lastException = null;
            
            while (retryCount <= maxRetries) {
                try {
                    LOG.info("Fetching batch: offset={}, limit={}, attempt={}", 
                            batchParams.offset, batchParams.limit, retryCount + 1);
                    
                    List<WorkdayEmployee> employees = client.getEmployees(batchParams.offset, batchParams.limit);
                    
                    for (WorkdayEmployee employee : employees) {
                        out.output(employee);
                    }
                    
                    LOG.info("Successfully processed batch: offset={}, count={}", 
                            batchParams.offset, employees.size());
                    return; // Success, exit retry loop
                    
                } catch (Exception e) {
                    lastException = e;
                    retryCount++;
                    
                    LOG.warn("Failed to fetch batch (attempt {}/{}): offset={}, error={}", 
                            retryCount, maxRetries + 1, batchParams.offset, e.getMessage());
                    
                    if (retryCount <= maxRetries) {
                        try {
                            // Exponential backoff
                            Thread.sleep(1000 * (1L << (retryCount - 1)));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted during retry backoff", ie);
                        }
                    }
                }
            }
            
            // All retries exhausted
            throw new RuntimeException("Failed to fetch batch after " + (maxRetries + 1) + 
                    " attempts: offset=" + batchParams.offset, lastException);
        }
    }
    
    /**
     * Helper class to hold batch parameters.
     */
    private static class BatchParams {
        final int offset;
        final int limit;
        
        BatchParams(int offset, int limit) {
            this.offset = offset;
            this.limit = limit;
        }
        
        @Override
        public String toString() {
            return "BatchParams{offset=" + offset + ", limit=" + limit + '}';
        }
    }
}