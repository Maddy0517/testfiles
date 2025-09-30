package com.example.workday.io;

import com.example.workday.client.WorkdaySoapClient;
import com.example.workday.client.WorkdaySoapClient.WorkdayResponse;
import com.example.workday.model.Employee;
import org.apache.beam.sdk.coders.Coder;
import org.apache.beam.sdk.coders.SerializableCoder;
import org.apache.beam.sdk.io.BoundedSource;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.PBegin;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Custom Apache Beam IO for reading from Workday SOAP API with pagination
 */
public class WorkdayIO {
    private static final Logger LOG = LoggerFactory.getLogger(WorkdayIO.class);
    
    /**
     * Read transform for Workday
     */
    public static Read read() {
        return new Read();
    }
    
    /**
     * Read PTransform
     */
    public static class Read extends PTransform<PBegin, PCollection<Employee>> {
        private String workdayEndpoint;
        private String username;
        private String password;
        private String tenant;
        private LocalDate effectiveDate;
        private LocalDateTime lastModifiedFrom;
        private boolean useProxy;
        private String proxyHost;
        private int proxyPort;
        
        public Read withWorkdayEndpoint(String endpoint) {
            this.workdayEndpoint = endpoint;
            return this;
        }
        
        public Read withCredentials(String username, String password, String tenant) {
            this.username = username;
            this.password = password;
            this.tenant = tenant;
            return this;
        }
        
        public Read withEffectiveDate(LocalDate effectiveDate) {
            this.effectiveDate = effectiveDate;
            return this;
        }
        
        public Read withLastModifiedFrom(LocalDateTime lastModifiedFrom) {
            this.lastModifiedFrom = lastModifiedFrom;
            return this;
        }
        
        public Read withProxy(String proxyHost, int proxyPort) {
            this.useProxy = true;
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            return this;
        }
        
        @Override
        public PCollection<Employee> expand(PBegin input) {
            WorkdaySource source = new WorkdaySource(
                workdayEndpoint, username, password, tenant,
                effectiveDate, lastModifiedFrom,
                useProxy, proxyHost, proxyPort
            );
            
            return input.apply(org.apache.beam.sdk.io.Read.from(source));
        }
    }
    
    /**
     * BoundedSource implementation for Workday
     */
    public static class WorkdaySource extends BoundedSource<Employee> {
        private static final long serialVersionUID = 1L;
        
        private final String workdayEndpoint;
        private final String username;
        private final String password;
        private final String tenant;
        private final LocalDate effectiveDate;
        private final LocalDateTime lastModifiedFrom;
        private final boolean useProxy;
        private final String proxyHost;
        private final int proxyPort;
        private final Integer startPage;
        private final Integer endPage;
        
        public WorkdaySource(String workdayEndpoint, String username, String password, String tenant,
                            LocalDate effectiveDate, LocalDateTime lastModifiedFrom,
                            boolean useProxy, String proxyHost, int proxyPort) {
            this(workdayEndpoint, username, password, tenant, effectiveDate, lastModifiedFrom,
                 useProxy, proxyHost, proxyPort, null, null);
        }
        
        private WorkdaySource(String workdayEndpoint, String username, String password, String tenant,
                             LocalDate effectiveDate, LocalDateTime lastModifiedFrom,
                             boolean useProxy, String proxyHost, int proxyPort,
                             Integer startPage, Integer endPage) {
            this.workdayEndpoint = workdayEndpoint;
            this.username = username;
            this.password = password;
            this.tenant = tenant;
            this.effectiveDate = effectiveDate;
            this.lastModifiedFrom = lastModifiedFrom;
            this.useProxy = useProxy;
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            this.startPage = startPage;
            this.endPage = endPage;
        }
        
        @Override
        public List<? extends BoundedSource<Employee>> split(long desiredBundleSizeBytes, 
                                                            PipelineOptions options) throws Exception {
            // First, get total pages by making an initial request
            WorkdaySoapClient client = new WorkdaySoapClient(
                workdayEndpoint, username, password, tenant,
                useProxy, proxyHost, proxyPort
            );
            
            WorkdayResponse initialResponse = client.fetchEmployees(effectiveDate, lastModifiedFrom, 1);
            int totalPages = initialResponse.getTotalPages();
            
            LOG.info("Total pages to fetch: {}", totalPages);
            
            // Create splits for parallel processing
            List<WorkdaySource> splits = new ArrayList<>();
            int pagesPerSplit = Math.max(1, totalPages / 10); // Create up to 10 splits
            
            for (int i = 1; i <= totalPages; i += pagesPerSplit) {
                int endPage = Math.min(i + pagesPerSplit - 1, totalPages);
                splits.add(new WorkdaySource(
                    workdayEndpoint, username, password, tenant,
                    effectiveDate, lastModifiedFrom,
                    useProxy, proxyHost, proxyPort,
                    i, endPage
                ));
                LOG.info("Created split for pages {} to {}", i, endPage);
            }
            
            return splits;
        }
        
        @Override
        public long getEstimatedSizeBytes(PipelineOptions options) throws Exception {
            // Estimate based on average employee record size
            return 1000 * 1000; // 1MB per page estimate
        }
        
        @Override
        public BoundedReader<Employee> createReader(PipelineOptions options) throws IOException {
            return new WorkdayReader(this);
        }
        
        @Override
        public Coder<Employee> getOutputCoder() {
            return SerializableCoder.of(Employee.class);
        }
    }
    
    /**
     * BoundedReader implementation for Workday
     */
    private static class WorkdayReader extends BoundedSource.BoundedReader<Employee> {
        private final WorkdaySource source;
        private WorkdaySoapClient client;
        private List<Employee> currentPageEmployees;
        private int currentPageIndex;
        private int currentEmployeeIndex;
        private Employee currentEmployee;
        private int totalPages;
        
        public WorkdayReader(WorkdaySource source) {
            this.source = source;
            this.currentPageEmployees = new ArrayList<>();
            this.currentEmployeeIndex = -1;
        }
        
        @Override
        public boolean start() throws IOException {
            try {
                client = new WorkdaySoapClient(
                    source.workdayEndpoint, source.username, source.password, source.tenant,
                    source.useProxy, source.proxyHost, source.proxyPort
                );
                
                currentPageIndex = source.startPage != null ? source.startPage : 1;
                
                // Fetch first page
                return fetchNextPage();
            } catch (Exception e) {
                throw new IOException("Failed to start Workday reader", e);
            }
        }
        
        @Override
        public boolean advance() throws IOException {
            currentEmployeeIndex++;
            
            // Check if we have more employees in current page
            if (currentEmployeeIndex < currentPageEmployees.size()) {
                currentEmployee = currentPageEmployees.get(currentEmployeeIndex);
                return true;
            }
            
            // Try to fetch next page
            currentPageIndex++;
            if (source.endPage != null && currentPageIndex > source.endPage) {
                return false;
            }
            
            try {
                return fetchNextPage();
            } catch (Exception e) {
                throw new IOException("Failed to fetch next page", e);
            }
        }
        
        private boolean fetchNextPage() throws Exception {
            LOG.info("Fetching page {} for effective date {} and last modified from {}", 
                    currentPageIndex, source.effectiveDate, source.lastModifiedFrom);
            
            WorkdayResponse response = client.fetchEmployees(
                source.effectiveDate, 
                source.lastModifiedFrom, 
                currentPageIndex
            );
            
            currentPageEmployees = response.getEmployees();
            totalPages = response.getTotalPages();
            currentEmployeeIndex = 0;
            
            if (!currentPageEmployees.isEmpty()) {
                currentEmployee = currentPageEmployees.get(0);
                LOG.info("Fetched {} employees from page {}", currentPageEmployees.size(), currentPageIndex);
                return true;
            }
            
            return false;
        }
        
        @Override
        public Employee getCurrent() throws NoSuchElementException {
            if (currentEmployee == null) {
                throw new NoSuchElementException();
            }
            return currentEmployee;
        }
        
        @Override
        public void close() throws IOException {
            // Cleanup resources if needed
        }
        
        @Override
        public BoundedSource<Employee> getCurrentSource() {
            return source;
        }
    }
}