package com.example.workday;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * SOAP client for Workday API with pagination support
 */
public class WorkdaySOAPClient implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(WorkdaySOAPClient.class);
    
    private final WorkdayConfiguration config;
    private final XmlMapper xmlMapper;
    private final ObjectMapper jsonMapper;
    
    public WorkdaySOAPClient(WorkdayConfiguration config) {
        this.config = config;
        this.xmlMapper = new XmlMapper();
        this.jsonMapper = new ObjectMapper();
        config.validate();
    }
    
    /**
     * Fetch all data from a Workday service with pagination
     */
    public List<WorkdayRecord> fetchAllData(String serviceName, String operationName, 
                                          Map<String, Object> requestParams) throws IOException {
        List<WorkdayRecord> allRecords = new ArrayList<>();
        int page = 1;
        boolean hasMoreData = true;
        
        LOG.info("Starting to fetch data from service: {}, operation: {}", serviceName, operationName);
        
        while (hasMoreData) {
            try {
                LOG.info("Fetching page {} with page size {}", page, config.getPageSize());
                
                List<WorkdayRecord> pageRecords = fetchPage(serviceName, operationName, 
                                                          requestParams, page, config.getPageSize());
                
                if (pageRecords.isEmpty()) {
                    hasMoreData = false;
                    LOG.info("No more data found. Total records fetched: {}", allRecords.size());
                } else {
                    allRecords.addAll(pageRecords);
                    LOG.info("Fetched {} records from page {}. Total so far: {}", 
                           pageRecords.size(), page, allRecords.size());
                    
                    // Check if we got less than page size, indicating last page
                    if (pageRecords.size() < config.getPageSize()) {
                        hasMoreData = false;
                        LOG.info("Last page reached. Final total: {} records", allRecords.size());
                    } else {
                        page++;
                    }
                }
            } catch (Exception e) {
                LOG.error("Error fetching page {}: {}", page, e.getMessage(), e);
                
                // Retry logic
                if (shouldRetry(e)) {
                    LOG.info("Retrying page {} after delay", page);
                    try {
                        Thread.sleep(config.getRetryDelayMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted during retry delay", ie);
                    }
                } else {
                    throw new IOException("Failed to fetch page " + page, e);
                }
            }
        }
        
        return allRecords;
    }
    
    /**
     * Fetch a single page of data
     */
    private List<WorkdayRecord> fetchPage(String serviceName, String operationName,
                                        Map<String, Object> requestParams, int page, int pageSize) 
                                        throws IOException {
        String soapRequest = buildSOAPRequest(serviceName, operationName, requestParams, page, pageSize);
        String serviceUrl = config.getServiceUrl(serviceName);
        
        try (CloseableHttpClient httpClient = createHttpClient()) {
            HttpPost httpPost = new HttpPost(serviceUrl);
            httpPost.setHeader("Content-Type", "text/xml; charset=utf-8");
            httpPost.setHeader("SOAPAction", "");
            httpPost.setEntity(new StringEntity(soapRequest, "UTF-8"));
            
            HttpResponse response = httpClient.execute(httpPost);
            String responseBody = EntityUtils.toString(response.getEntity());
            
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new IOException(String.format("HTTP error %d: %s", 
                                    response.getStatusLine().getStatusCode(), responseBody));
            }
            
            return parseSOAPResponse(responseBody);
        }
    }
    
    /**
     * Create HTTP client with authentication
     */
    private CloseableHttpClient createHttpClient() {
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
            AuthScope.ANY,
            new UsernamePasswordCredentials(config.getUsername(), config.getPassword())
        );
        
        return HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .build();
    }
    
    /**
     * Build SOAP request XML
     */
    private String buildSOAPRequest(String serviceName, String operationName,
                                  Map<String, Object> requestParams, int page, int pageSize) {
        StringBuilder soapRequest = new StringBuilder();
        
        soapRequest.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        soapRequest.append("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" ");
        soapRequest.append("xmlns:bsvc=\"urn:com.workday/bsvc\">\n");
        soapRequest.append("  <soap:Header>\n");
        soapRequest.append("    <bsvc:Workday_Common_Header>\n");
        soapRequest.append("      <bsvc:Include_Reference_Descriptors_In_Response>true</bsvc:Include_Reference_Descriptors_In_Response>\n");
        soapRequest.append("    </bsvc:Workday_Common_Header>\n");
        soapRequest.append("  </soap:Header>\n");
        soapRequest.append("  <soap:Body>\n");
        soapRequest.append("    <bsvc:").append(operationName).append("_Request>\n");
        
        // Add request parameters
        if (requestParams != null) {
            for (Map.Entry<String, Object> entry : requestParams.entrySet()) {
                soapRequest.append("      <bsvc:").append(entry.getKey()).append(">");
                soapRequest.append(entry.getValue());
                soapRequest.append("</bsvc:").append(entry.getKey()).append(">\n");
            }
        }
        
        // Add pagination
        soapRequest.append("      <bsvc:Response_Filter>\n");
        soapRequest.append("        <bsvc:Page>").append(page).append("</bsvc:Page>\n");
        soapRequest.append("        <bsvc:Count>").append(pageSize).append("</bsvc:Count>\n");
        soapRequest.append("      </bsvc:Response_Filter>\n");
        
        soapRequest.append("    </bsvc:").append(operationName).append("_Request>\n");
        soapRequest.append("  </soap:Body>\n");
        soapRequest.append("</soap:Envelope>");
        
        return soapRequest.toString();
    }
    
    /**
     * Parse SOAP response and extract data
     */
    private List<WorkdayRecord> parseSOAPResponse(String responseBody) throws IOException {
        List<WorkdayRecord> records = new ArrayList<>();
        
        try {
            // Parse XML response
            JsonNode rootNode = xmlMapper.readTree(responseBody);
            
            // Navigate through SOAP envelope structure
            JsonNode body = rootNode.path("Body");
            if (body.isMissingNode()) {
                body = rootNode.path("soap:Body");
            }
            
            // Look for response data - this will vary based on the specific Workday service
            JsonNode responseData = findResponseData(body);
            
            if (responseData != null && responseData.isArray()) {
                for (JsonNode dataNode : responseData) {
                    WorkdayRecord record = convertToWorkdayRecord(dataNode);
                    if (record != null) {
                        records.add(record);
                    }
                }
            } else if (responseData != null) {
                // Single record response
                WorkdayRecord record = convertToWorkdayRecord(responseData);
                if (record != null) {
                    records.add(record);
                }
            }
            
        } catch (Exception e) {
            LOG.error("Error parsing SOAP response: {}", e.getMessage(), e);
            throw new IOException("Failed to parse SOAP response", e);
        }
        
        return records;
    }
    
    /**
     * Find response data in the SOAP response structure
     */
    private JsonNode findResponseData(JsonNode body) {
        // Common patterns for Workday responses
        String[] possiblePaths = {
            "Get_Workers_Response/Response_Data/Worker",
            "Get_Organizations_Response/Response_Data/Organization",
            "Response_Data",
            "Worker",
            "Organization"
        };
        
        for (String path : possiblePaths) {
            JsonNode node = body.at("/" + path.replace("/", "/"));
            if (!node.isMissingNode()) {
                return node;
            }
        }
        
        // If no known pattern found, return the body itself
        return body;
    }
    
    /**
     * Convert JSON node to WorkdayRecord
     */
    private WorkdayRecord convertToWorkdayRecord(JsonNode dataNode) {
        try {
            Map<String, Object> data = new HashMap<>();
            String recordId = null;
            
            // Extract ID from various possible locations
            JsonNode idNode = dataNode.path("Worker_ID");
            if (idNode.isMissingNode()) {
                idNode = dataNode.path("ID");
            }
            if (idNode.isMissingNode()) {
                idNode = dataNode.path("Reference_ID");
            }
            
            if (!idNode.isMissingNode()) {
                recordId = idNode.asText();
            }
            
            // Convert all fields to map
            Iterator<Map.Entry<String, JsonNode>> fields = dataNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                JsonNode value = field.getValue();
                
                if (value.isTextual()) {
                    data.put(key, value.asText());
                } else if (value.isNumber()) {
                    data.put(key, value.asDouble());
                } else if (value.isBoolean()) {
                    data.put(key, value.asBoolean());
                } else {
                    data.put(key, value.toString());
                }
            }
            
            return new WorkdayRecord(recordId != null ? recordId : "unknown", data);
            
        } catch (Exception e) {
            LOG.error("Error converting data node to WorkdayRecord: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Determine if an exception should trigger a retry
     */
    private boolean shouldRetry(Exception e) {
        // Retry on network issues, timeouts, and temporary server errors
        return e instanceof IOException || 
               e.getMessage().contains("timeout") ||
               e.getMessage().contains("connection");
    }
}