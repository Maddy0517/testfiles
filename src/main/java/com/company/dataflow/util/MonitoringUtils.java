package com.company.dataflow.util;

import org.apache.beam.sdk.metrics.Counter;
import org.apache.beam.sdk.metrics.Distribution;
import org.apache.beam.sdk.metrics.Gauge;
import org.apache.beam.sdk.metrics.Metrics;
import org.apache.beam.sdk.transforms.DoFn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for monitoring and metrics collection.
 */
public class MonitoringUtils {
    
    private static final Logger LOG = LoggerFactory.getLogger(MonitoringUtils.class);
    
    // Metrics namespace
    private static final String METRICS_NAMESPACE = "WorkdayPipeline";
    
    // Counters
    public static final Counter RECORDS_READ = Metrics.counter(METRICS_NAMESPACE, "records_read");
    public static final Counter RECORDS_TRANSFORMED = Metrics.counter(METRICS_NAMESPACE, "records_transformed");
    public static final Counter RECORDS_WRITTEN = Metrics.counter(METRICS_NAMESPACE, "records_written");
    public static final Counter RECORDS_FAILED = Metrics.counter(METRICS_NAMESPACE, "records_failed");
    public static final Counter API_CALLS_SUCCESS = Metrics.counter(METRICS_NAMESPACE, "api_calls_success");
    public static final Counter API_CALLS_FAILED = Metrics.counter(METRICS_NAMESPACE, "api_calls_failed");
    public static final Counter VALIDATION_ERRORS = Metrics.counter(METRICS_NAMESPACE, "validation_errors");
    
    // Distributions
    public static final Distribution API_CALL_LATENCY = Metrics.distribution(METRICS_NAMESPACE, "api_call_latency_ms");
    public static final Distribution BATCH_SIZE = Metrics.distribution(METRICS_NAMESPACE, "batch_size");
    public static final Distribution RECORD_PROCESSING_TIME = Metrics.distribution(METRICS_NAMESPACE, "record_processing_time_ms");
    
    // Gauges
    public static final Gauge ACTIVE_WORKERS = Metrics.gauge(METRICS_NAMESPACE, "active_workers");
    public static final Gauge MEMORY_USAGE = Metrics.gauge(METRICS_NAMESPACE, "memory_usage_mb");
    
    /**
     * Base class for DoFns that include monitoring.
     */
    public abstract static class MonitoredDoFn<InputT, OutputT> extends DoFn<InputT, OutputT> {
        
        private final String operationName;
        private long startTime;
        
        public MonitoredDoFn(String operationName) {
            this.operationName = operationName;
        }
        
        @StartBundle
        public void startBundle() {
            startTime = System.currentTimeMillis();
            LOG.info("Starting bundle for operation: {}", operationName);
        }
        
        @FinishBundle
        public void finishBundle() {
            long duration = System.currentTimeMillis() - startTime;
            LOG.info("Finished bundle for operation: {} in {}ms", operationName, duration);
            
            // Update memory usage
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
            MEMORY_USAGE.set(usedMemory);
        }
        
        protected void recordProcessingTime(long processingTimeMs) {
            RECORD_PROCESSING_TIME.update(processingTimeMs);
        }
        
        protected void recordSuccess() {
            // Override in specific implementations
        }
        
        protected void recordFailure() {
            RECORDS_FAILED.inc();
        }
    }
    
    /**
     * Utility methods for common monitoring operations.
     */
    public static class MetricsHelper {
        
        /**
         * Records API call metrics.
         */
        public static void recordApiCall(boolean success, long latencyMs, int batchSize) {
            if (success) {
                API_CALLS_SUCCESS.inc();
            } else {
                API_CALLS_FAILED.inc();
            }
            
            API_CALL_LATENCY.update(latencyMs);
            BATCH_SIZE.update(batchSize);
        }
        
        /**
         * Records record processing metrics.
         */
        public static void recordRecordProcessing(String stage, boolean success) {
            switch (stage.toLowerCase()) {
                case "read":
                    RECORDS_READ.inc();
                    break;
                case "transform":
                    RECORDS_TRANSFORMED.inc();
                    break;
                case "write":
                    RECORDS_WRITTEN.inc();
                    break;
            }
            
            if (!success) {
                RECORDS_FAILED.inc();
            }
        }
        
        /**
         * Records validation errors.
         */
        public static void recordValidationError() {
            VALIDATION_ERRORS.inc();
        }
        
        /**
         * Updates active worker count.
         */
        public static void updateActiveWorkers(int count) {
            ACTIVE_WORKERS.set(count);
        }
        
        /**
         * Logs current metrics state.
         */
        public static void logCurrentMetrics() {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
            long totalMemory = runtime.totalMemory() / 1024 / 1024;
            
            LOG.info("=== Pipeline Metrics ===");
            LOG.info("Memory Usage: {} MB / {} MB", usedMemory, totalMemory);
            LOG.info("Available Processors: {}", runtime.availableProcessors());
        }
    }
    
    /**
     * Health check utilities.
     */
    public static class HealthCheck {
        
        private static final long MAX_MEMORY_USAGE_PERCENT = 90;
        private static final long MAX_ERROR_RATE_PERCENT = 5;
        
        /**
         * Checks if the pipeline is healthy.
         */
        public static boolean isPipelineHealthy() {
            return isMemoryHealthy() && isErrorRateHealthy();
        }
        
        /**
         * Checks memory health.
         */
        private static boolean isMemoryHealthy() {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            long usagePercent = (usedMemory * 100) / maxMemory;
            
            boolean healthy = usagePercent < MAX_MEMORY_USAGE_PERCENT;
            if (!healthy) {
                LOG.warn("Memory usage is high: {}%", usagePercent);
            }
            
            return healthy;
        }
        
        /**
         * Checks error rate health.
         */
        private static boolean isErrorRateHealthy() {
            // This would require accessing actual metric values
            // For now, return true - implement based on your monitoring needs
            return true;
        }
        
        /**
         * Triggers garbage collection if memory is high.
         */
        public static void triggerGCIfNeeded() {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            long usagePercent = (usedMemory * 100) / maxMemory;
            
            if (usagePercent > 80) {
                LOG.info("Memory usage is {}%, triggering GC", usagePercent);
                System.gc();
            }
        }
    }
    
    /**
     * Alert utilities.
     */
    public static class AlertManager {
        
        /**
         * Sends alert for high error rate.
         */
        public static void alertHighErrorRate(double errorRate) {
            if (errorRate > 0.1) { // 10% error rate
                LOG.error("ALERT: High error rate detected: {}%", errorRate * 100);
                // Implement actual alerting mechanism (email, Slack, etc.)
            }
        }
        
        /**
         * Sends alert for pipeline failure.
         */
        public static void alertPipelineFailure(String reason) {
            LOG.error("ALERT: Pipeline failure - {}", reason);
            // Implement actual alerting mechanism
        }
        
        /**
         * Sends alert for data quality issues.
         */
        public static void alertDataQualityIssue(String issue) {
            LOG.warn("ALERT: Data quality issue - {}", issue);
            // Implement actual alerting mechanism
        }
    }
}