package com.workday.dataflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Workday SOAP API client with authentication and pagination support.
 */
public class WorkdayApiClient implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(WorkdayApiClient.class);
    
    private final String soapApiUrl;
    private final String username;
    private final String password;
    private final String tenantName;
    private final String apiVersion;
    private final int requestTimeout;
    private final int maxRetries;
    private final boolean enablePagination;
    private final int pageSize;
    private final String effectiveFromDate;
    private final String effectiveToDate;
    private final boolean includeEffectiveFromDate;
    private final boolean includeEffectiveToDate;
    
    private transient CloseableHttpClient httpClient;
    private transient ObjectMapper objectMapper;

    public WorkdayApiClient(WorkdayToBigQueryOptions options) {
        this.soapApiUrl = options.getSoapApiUrl();
        this.username = options.getUsername();
        this.password = options.getPassword();
        this.tenantName = options.getTenantName();
        this.apiVersion = options.getApiVersion() != null ? options.getApiVersion() : "v40.0";
        this.requestTimeout = options.getRequestTimeout() != null ? options.getRequestTimeout() : 30000;
        this.maxRetries = options.getMaxRetries() != null ? options.getMaxRetries() : 3;
        this.enablePagination = options.getEnablePagination() != null ? options.getEnablePagination() : true;
        this.pageSize = options.getPageSize() != null ? options.getPageSize() : 100;
        this.effectiveFromDate = options.getEffectiveFromDate();
        this.effectiveToDate = options.getEffectiveToDate();
        this.includeEffectiveFromDate = options.getIncludeEffectiveFromDate() != null ? options.getIncludeEffectiveFromDate() : true;
        this.includeEffectiveToDate = options.getIncludeEffectiveToDate() != null ? options.getIncludeEffectiveToDate() : true;
        
        initializeHttpClient();
        this.objectMapper = new ObjectMapper();
        
        // Validate date formats if provided
        validateDateFormats();
    }

    private void initializeHttpClient() {
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
            AuthScope.ANY,
            new UsernamePasswordCredentials(username, password)
        );

        RequestConfig requestConfig = RequestConfig.custom()
            .setSocketTimeout(requestTimeout)
            .setConnectTimeout(requestTimeout)
            .setConnectionRequestTimeout(requestTimeout)
            .build();

        this.httpClient = HttpClientBuilder.create()
            .setDefaultCredentialsProvider(credentialsProvider)
            .setDefaultRequestConfig(requestConfig)
            .build();
    }

    /**
     * Validate date formats for effective date filters
     */
    private void validateDateFormats() {
        if (effectiveFromDate != null && !effectiveFromDate.trim().isEmpty()) {
            if (!isValidDateFormat(effectiveFromDate)) {
                throw new IllegalArgumentException("Invalid effective from date format: " + effectiveFromDate + 
                    ". Expected format: YYYY-MM-DD");
            }
            LOG.info("Using effective from date filter: {}", effectiveFromDate);
        }
        
        if (effectiveToDate != null && !effectiveToDate.trim().isEmpty()) {
            if (!isValidDateFormat(effectiveToDate)) {
                throw new IllegalArgumentException("Invalid effective to date format: " + effectiveToDate + 
                    ". Expected format: YYYY-MM-DD");
            }
            LOG.info("Using effective to date filter: {}", effectiveToDate);
        }
        
        // Validate that from date is not after to date
        if (effectiveFromDate != null && !effectiveFromDate.trim().isEmpty() &&
            effectiveToDate != null && !effectiveToDate.trim().isEmpty()) {
            if (effectiveFromDate.compareTo(effectiveToDate) > 0) {
                throw new IllegalArgumentException("Effective from date (" + effectiveFromDate + 
                    ") cannot be after effective to date (" + effectiveToDate + ")");
            }
        }
    }

    /**
     * Validate if the date string is in YYYY-MM-DD format
     */
    private boolean isValidDateFormat(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return false;
        }
        
        try {
            // Check if it matches YYYY-MM-DD pattern
            if (!dateString.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return false;
            }
            
            // Try to parse the date to ensure it's valid
            java.time.LocalDate.parse(dateString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Fetch all worker data with pagination support
     */
    public List<WorkerData> getAllWorkers() throws Exception {
        List<WorkerData> allWorkers = new ArrayList<>();
        int page = 1;
        boolean hasMoreData = true;

        while (hasMoreData) {
            LOG.info("Fetching worker data page: {}", page);
            
            List<WorkerData> pageWorkers = getWorkersPage(page);
            allWorkers.addAll(pageWorkers);
            
            if (!enablePagination || pageWorkers.size() < pageSize) {
                hasMoreData = false;
            } else {
                page++;
            }
        }

        LOG.info("Total workers fetched: {}", allWorkers.size());
        return allWorkers;
    }

    /**
     * Fetch a specific page of worker data
     */
    private List<WorkerData> getWorkersPage(int page) throws Exception {
        String soapRequest = buildGetWorkersRequest(page);
        String response = executeWithRetry(soapRequest);
        return parseWorkersResponse(response);
    }

    /**
     * Build SOAP request for getting workers with date filters
     */
    private String buildGetWorkersRequest(int page) {
        int offset = (page - 1) * pageSize;
        
        StringBuilder requestBuilder = new StringBuilder();
        requestBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            .append("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" \n")
            .append("               xmlns:wd=\"urn:com.workday/bsvc\">\n")
            .append("  <soap:Header>\n")
            .append("    <wd:Workday_Common_Header>\n")
            .append("      <wd:Include_Reference_Descriptors_In_Response>true</wd:Include_Reference_Descriptors_In_Response>\n")
            .append("    </wd:Workday_Common_Header>\n")
            .append("  </soap:Header>\n")
            .append("  <soap:Body>\n")
            .append("    <wd:Get_Workers_Request version=\"").append(apiVersion).append("\">\n")
            .append("      <wd:Request_Criteria>\n")
            .append("        <wd:Exclude_Inactive_Workers>true</wd:Exclude_Inactive_Workers>\n")
            .append("        <wd:Exclude_Contingent_Workers>false</wd:Exclude_Contingent_Workers>\n");
        
        // Add date filters if provided
        if (effectiveFromDate != null && !effectiveFromDate.trim().isEmpty()) {
            requestBuilder.append("        <wd:Transaction_Log_Criteria_Data>\n")
                .append("          <wd:Transaction_Date_Range_Data>\n")
                .append("            <wd:Updated_From>").append(effectiveFromDate).append("</wd:Updated_From>\n");
            
            if (effectiveToDate != null && !effectiveToDate.trim().isEmpty()) {
                requestBuilder.append("            <wd:Updated_Through>").append(effectiveToDate).append("</wd:Updated_Through>\n");
            }
            
            requestBuilder.append("          </wd:Transaction_Date_Range_Data>\n")
                .append("        </wd:Transaction_Log_Criteria_Data>\n");
        } else if (effectiveToDate != null && !effectiveToDate.trim().isEmpty()) {
            requestBuilder.append("        <wd:Transaction_Log_Criteria_Data>\n")
                .append("          <wd:Transaction_Date_Range_Data>\n")
                .append("            <wd:Updated_Through>").append(effectiveToDate).append("</wd:Updated_Through>\n")
                .append("          </wd:Transaction_Date_Range_Data>\n")
                .append("        </wd:Transaction_Log_Criteria_Data>\n");
        }
        
        // Alternative approach using As_Of_Effective_Date for point-in-time data
        if (effectiveFromDate != null && !effectiveFromDate.trim().isEmpty() && 
            effectiveToDate != null && !effectiveToDate.trim().isEmpty() && 
            effectiveFromDate.equals(effectiveToDate)) {
            // Point-in-time query
            requestBuilder.append("        <wd:As_Of_Effective_Date>").append(effectiveFromDate).append("</wd:As_Of_Effective_Date>\n");
        }
        
        requestBuilder.append("      </wd:Request_Criteria>\n")
            .append("      <wd:Response_Filter>\n")
            .append("        <wd:Page>").append(page).append("</wd:Page>\n")
            .append("        <wd:Count>").append(pageSize).append("</wd:Count>\n");
        
        // Add As_Of_Entry_DateTime for effective date filtering if specified
        if (effectiveFromDate != null && !effectiveFromDate.trim().isEmpty() && 
            !effectiveFromDate.equals(effectiveToDate)) {
            requestBuilder.append("        <wd:As_Of_Entry_DateTime>").append(effectiveFromDate).append("T00:00:00</wd:As_Of_Entry_DateTime>\n");
        }
        
        requestBuilder.append("      </wd:Response_Filter>\n")
            .append("      <wd:Response_Group>\n")
            .append("        <wd:Include_Reference>true</wd:Include_Reference>\n")
            .append("        <wd:Include_Personal_Information>true</wd:Include_Personal_Information>\n")
            .append("        <wd:Include_Employment_Information>true</wd:Include_Employment_Information>\n")
            .append("        <wd:Include_Compensation>true</wd:Include_Compensation>\n")
            .append("        <wd:Include_Organizations>true</wd:Include_Organizations>\n")
            .append("        <wd:Include_Roles>true</wd:Include_Roles>\n")
            .append("      </wd:Response_Group>\n")
            .append("    </wd:Get_Workers_Request>\n")
            .append("  </soap:Body>\n")
            .append("</soap:Envelope>");
        
        String soapRequest = requestBuilder.toString();
        LOG.debug("Built SOAP request with date filters - From: {}, To: {}", effectiveFromDate, effectiveToDate);
        return soapRequest;
    }

    /**
     * Execute SOAP request with retry logic
     */
    private String executeWithRetry(String soapRequest) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return executeSoapRequest(soapRequest);
            } catch (Exception e) {
                lastException = e;
                LOG.warn("Request attempt {} failed: {}", attempt, e.getMessage());
                
                if (attempt < maxRetries) {
                    Thread.sleep(1000 * attempt); // Exponential backoff
                }
            }
        }
        
        throw new Exception("Failed after " + maxRetries + " attempts", lastException);
    }

    /**
     * Execute SOAP request
     */
    private String executeSoapRequest(String soapRequest) throws IOException {
        if (httpClient == null) {
            initializeHttpClient();
        }

        HttpPost httpPost = new HttpPost(soapApiUrl);
        httpPost.setHeader("Content-Type", "text/xml; charset=utf-8");
        httpPost.setHeader("SOAPAction", "");
        httpPost.setEntity(new StringEntity(soapRequest, "UTF-8"));

        HttpResponse response = httpClient.execute(httpPost);
        HttpEntity entity = response.getEntity();
        
        if (entity != null) {
            String responseString = EntityUtils.toString(entity);
            int statusCode = response.getStatusLine().getStatusCode();
            
            if (statusCode >= 200 && statusCode < 300) {
                return responseString;
            } else {
                throw new IOException("HTTP error code: " + statusCode + ", Response: " + responseString);
            }
        } else {
            throw new IOException("Empty response from Workday API");
        }
    }

    /**
     * Parse workers from SOAP response
     */
    private List<WorkerData> parseWorkersResponse(String response) throws Exception {
        List<WorkerData> workers = new ArrayList<>();
        
        // This is a simplified parser. In a real implementation, you would use
        // proper XML parsing libraries like JAXB or DOM parser
        // For this example, we'll create sample worker data
        
        // Extract worker count from response (simplified)
        int workerCount = extractWorkerCount(response);
        
        for (int i = 0; i < workerCount && i < pageSize; i++) {
            WorkerData worker = parseWorkerFromResponse(response, i);
            if (worker != null) {
                workers.add(worker);
            }
        }
        
        return workers;
    }

    /**
     * Extract worker count from SOAP response (simplified implementation)
     */
    private int extractWorkerCount(String response) {
        // In a real implementation, you would parse the XML properly
        // This is a simplified version for demonstration
        if (response.contains("Worker")) {
            // Return a sample count based on response content
            return Math.min(pageSize, 10); // Sample implementation
        }
        return 0;
    }

    /**
     * Parse individual worker from SOAP response (simplified implementation)
     */
    private WorkerData parseWorkerFromResponse(String response, int index) {
        // In a real implementation, you would parse the XML properly using JAXB or DOM
        // This is a simplified version for demonstration
        
        WorkerData worker = new WorkerData();
        worker.setWorkerId("WD-" + System.currentTimeMillis() + "-" + index);
        worker.setEmployeeId("EMP-" + (1000 + index));
        worker.setFirstName("FirstName" + index);
        worker.setLastName("LastName" + index);
        worker.setEmail("employee" + index + "@company.com");
        worker.setJobTitle("Software Engineer");
        worker.setDepartment("Engineering");
        worker.setHireDate("2023-01-15");
        worker.setStatus("Active");
        worker.setSalary(75000.0 + (index * 1000));
        worker.setLocation("New York");
        worker.setManagerId("MGR-001");
        worker.setLastModified(System.currentTimeMillis());
        
        return worker;
    }

    /**
     * Close HTTP client resources
     */
    public void close() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }
}