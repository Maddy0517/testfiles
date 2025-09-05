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
    
    private final WorkdayToBigQueryOptions options;

    public WorkdaySourceTransform(WorkdayToBigQueryOptions options) {
        this.options = options;
    }

    @Override
    public PCollection<WorkerData> expand(PBegin input) {
        return input
            .apply("Create Trigger", Create.of(Collections.singletonList("trigger")))
            .apply("Fetch Workers from Workday", ParDo.of(new FetchWorkersDoFn(options)));
    }

    /**
     * DoFn to fetch workers from Workday API
     */
    private static class FetchWorkersDoFn extends DoFn<String, WorkerData> {
        private final WorkdayToBigQueryOptions options;
        private transient WorkdayApiClient apiClient;

        public FetchWorkersDoFn(WorkdayToBigQueryOptions options) {
            this.options = options;
        }

        @Setup
        public void setup() {
            LOG.info("Setting up Workday API client");
            this.apiClient = new WorkdayApiClient(options);
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