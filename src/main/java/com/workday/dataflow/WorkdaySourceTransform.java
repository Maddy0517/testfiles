package com.workday.dataflow;

import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.values.PBegin;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Apache Beam transform to read worker data from Workday SOAP API.
 */
public class WorkdaySourceTransform extends PTransform<PBegin, PCollection<WorkerData>> {
    private static final Logger LOG = LoggerFactory.getLogger(WorkdaySourceTransform.class);
    
    // Extract serializable values from options instead of storing the entire options object
    private final String soapApiUrl;
    private final String username;
    private final String password;
    private final String tenantName;
    private final String apiVersion;
    private final Integer requestTimeout;
    private final Integer maxRetries;
    private final Boolean enablePagination;
    private final Integer pageSize;
    private final String effectiveFromDate;
    private final String effectiveToDate;
    private final Boolean includeEffectiveFromDate;
    private final Boolean includeEffectiveToDate;

    public WorkdaySourceTransform(WorkdayToBigQueryOptions options) {
        // Extract all needed values from options to avoid serialization issues
        this.soapApiUrl = options.getSoapApiUrl();
        this.username = options.getUsername();
        this.password = options.getPassword();
        this.tenantName = options.getTenantName();
        this.apiVersion = options.getApiVersion();
        this.requestTimeout = options.getRequestTimeout();
        this.maxRetries = options.getMaxRetries();
        this.enablePagination = options.getEnablePagination();
        this.pageSize = options.getPageSize();
        this.effectiveFromDate = options.getEffectiveFromDate();
        this.effectiveToDate = options.getEffectiveToDate();
        this.includeEffectiveFromDate = options.getIncludeEffectiveFromDate();
        this.includeEffectiveToDate = options.getIncludeEffectiveToDate();
    }

    @Override
    public PCollection<WorkerData> expand(PBegin input) {
        return input
            .apply("Create Trigger", Create.of(Collections.singletonList("trigger")))
            .apply("Fetch Workers from Workday", ParDo.of(new FetchWorkersDoFn(
                soapApiUrl, username, password, tenantName, apiVersion,
                requestTimeout, maxRetries, enablePagination, pageSize,
                effectiveFromDate, effectiveToDate, includeEffectiveFromDate, includeEffectiveToDate)));
    }

    /**
     * DoFn to fetch workers from Workday API
     */
    private static class FetchWorkersDoFn extends DoFn<String, WorkerData> {
        // Store individual serializable fields instead of the entire options object
        private final String soapApiUrl;
        private final String username;
        private final String password;
        private final String tenantName;
        private final String apiVersion;
        private final Integer requestTimeout;
        private final Integer maxRetries;
        private final Boolean enablePagination;
        private final Integer pageSize;
        private final String effectiveFromDate;
        private final String effectiveToDate;
        private final Boolean includeEffectiveFromDate;
        private final Boolean includeEffectiveToDate;
        
        private transient WorkdayApiClient apiClient;

        public FetchWorkersDoFn(String soapApiUrl, String username, String password, String tenantName, String apiVersion,
                               Integer requestTimeout, Integer maxRetries, Boolean enablePagination, Integer pageSize,
                               String effectiveFromDate, String effectiveToDate, 
                               Boolean includeEffectiveFromDate, Boolean includeEffectiveToDate) {
            this.soapApiUrl = soapApiUrl;
            this.username = username;
            this.password = password;
            this.tenantName = tenantName;
            this.apiVersion = apiVersion;
            this.requestTimeout = requestTimeout;
            this.maxRetries = maxRetries;
            this.enablePagination = enablePagination;
            this.pageSize = pageSize;
            this.effectiveFromDate = effectiveFromDate;
            this.effectiveToDate = effectiveToDate;
            this.includeEffectiveFromDate = includeEffectiveFromDate;
            this.includeEffectiveToDate = includeEffectiveToDate;
        }

        @Setup
        public void setup() {
            LOG.info("Setting up Workday API client");
            // Use the new constructor that accepts individual parameters
            this.apiClient = new WorkdayApiClient(
                soapApiUrl, username, password, tenantName, apiVersion,
                requestTimeout, maxRetries, enablePagination, pageSize,
                effectiveFromDate, effectiveToDate, includeEffectiveFromDate, includeEffectiveToDate
            );
        }

        @ProcessElement
        public void processElement(@Element String trigger, OutputReceiver<WorkerData> out) {
            try {
                LOG.info("Starting to fetch workers from Workday API");
                List<WorkerData> workers = apiClient.getAllWorkers();
                
                LOG.info("Successfully fetched {} workers", workers.size());
                for (WorkerData worker : workers) {
                    out.output(worker);
                }
            } catch (Exception e) {
                LOG.error("Failed to fetch workers from Workday API", e);
                throw new RuntimeException("Failed to fetch workers from Workday API", e);
            }
        }

        @Teardown
        public void teardown() {
            if (apiClient != null) {
                try {
                    apiClient.close();
                    LOG.info("Closed Workday API client");
                } catch (Exception e) {
                    LOG.warn("Failed to close Workday API client", e);
                }
            }
        }
    }
}