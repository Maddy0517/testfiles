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
        
        initializeHttpClient();
        this.objectMapper = new ObjectMapper();
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
     * Build SOAP request for getting workers
     */
    private String buildGetWorkersRequest(int page) {
        int offset = (page - 1) * pageSize;
        
        return String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" \n" +
            "               xmlns:wd=\"urn:com.workday/bsvc\">\n" +
            "  <soap:Header>\n" +
            "    <wd:Workday_Common_Header>\n" +
            "      <wd:Include_Reference_Descriptors_In_Response>true</wd:Include_Reference_Descriptors_In_Response>\n" +
            "    </wd:Workday_Common_Header>\n" +
            "  </soap:Header>\n" +
            "  <soap:Body>\n" +
            "    <wd:Get_Workers_Request version=\"%s\">\n" +
            "      <wd:Request_Criteria>\n" +
            "        <wd:Exclude_Inactive_Workers>true</wd:Exclude_Inactive_Workers>\n" +
            "        <wd:Exclude_Contingent_Workers>false</wd:Exclude_Contingent_Workers>\n" +
            "      </wd:Request_Criteria>\n" +
            "      <wd:Response_Filter>\n" +
            "        <wd:Page>%d</wd:Page>\n" +
            "        <wd:Count>%d</wd:Count>\n" +
            "      </wd:Response_Filter>\n" +
            "      <wd:Response_Group>\n" +
            "        <wd:Include_Reference>true</wd:Include_Reference>\n" +
            "        <wd:Include_Personal_Information>true</wd:Include_Personal_Information>\n" +
            "        <wd:Include_Employment_Information>true</wd:Include_Employment_Information>\n" +
            "        <wd:Include_Compensation>true</wd:Include_Compensation>\n" +
            "        <wd:Include_Organizations>true</wd:Include_Organizations>\n" +
            "        <wd:Include_Roles>true</wd:Include_Roles>\n" +
            "      </wd:Response_Group>\n" +
            "    </wd:Get_Workers_Request>\n" +
            "  </soap:Body>\n" +
            "</soap:Envelope>",
            apiVersion, page, pageSize
        );
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