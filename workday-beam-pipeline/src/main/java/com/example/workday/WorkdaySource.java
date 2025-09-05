package com.example.workday;

import org.apache.beam.sdk.io.BoundedSource;
import org.apache.beam.sdk.options.ValueProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Apache Beam BoundedSource for reading data from Workday SOAP API
 */
public class WorkdaySource extends BoundedSource<WorkdayRecord> {
    private static final Logger LOG = LoggerFactory.getLogger(WorkdaySource.class);
    
    private final WorkdayConfiguration config;
    private final String serviceName;
    private final String operationName;
    private final Map<String, Object> requestParams;
    
    public WorkdaySource(WorkdayConfiguration config, String serviceName, 
                        String operationName, Map<String, Object> requestParams) {
        this.config = config;
        this.serviceName = serviceName;
        this.operationName = operationName;
        this.requestParams = requestParams;
    }
    
    @Override
    public List<? extends BoundedSource<WorkdayRecord>> split(long desiredBundleSizeBytes, 
                                                            PipelineOptions options) {
        // For simplicity, return single source. In production, you might want to
        // split based on date ranges or other partitioning strategies
        return List.of(this);
    }
    
    @Override
    public long getEstimatedSizeBytes(PipelineOptions options) {
        // Estimate based on typical record size and expected count
        // This is a rough estimate - adjust based on your data
        return 1024L * 1024L; // 1MB estimate
    }
    
    @Override
    public BoundedReader<WorkdayRecord> createReader(PipelineOptions options) {
        return new WorkdayReader(this);
    }
    
    /**
     * Reader implementation for Workday data
     */
    public static class WorkdayReader extends BoundedSource.BoundedReader<WorkdayRecord> {
        private final WorkdaySource source;
        private WorkdaySOAPClient client;
        private List<WorkdayRecord> records;
        private int currentIndex;
        
        public WorkdayReader(WorkdaySource source) {
            this.source = source;
            this.currentIndex = -1;
        }
        
        @Override
        public boolean start() throws IOException {
            LOG.info("Starting Workday reader for service: {}, operation: {}", 
                   source.serviceName, source.operationName);
            
            client = new WorkdaySOAPClient(source.config);
            records = client.fetchAllData(source.serviceName, source.operationName, 
                                        source.requestParams);
            
            LOG.info("Fetched {} records from Workday", records.size());
            currentIndex = 0;
            
            return !records.isEmpty();
        }
        
        @Override
        public boolean advance() {
            currentIndex++;
            return currentIndex < records.size();
        }
        
        @Override
        public WorkdayRecord getCurrent() {
            if (currentIndex >= 0 && currentIndex < records.size()) {
                return records.get(currentIndex);
            }
            throw new IllegalStateException("No current record available");
        }
        
        @Override
        public void close() {
            // Cleanup if needed
            LOG.info("Closing Workday reader");
        }
        
        @Override
        public BoundedSource<WorkdayRecord> getCurrentSource() {
            return source;
        }
    }
}