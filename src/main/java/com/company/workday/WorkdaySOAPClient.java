package com.company.workday;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import javax.xml.soap.*;
import javax.xml.ws.Service;
import javax.xml.ws.soap.SOAPBinding;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * SOAP client for interacting with Workday Human Capital Management API.
 * Handles authentication, request construction, and response parsing.
 */
public class WorkdaySOAPClient implements Serializable {
    
    private static final Logger LOG = LoggerFactory.getLogger(WorkdaySOAPClient.class);
    
    // Workday SOAP namespaces
    private static final String WORKDAY_NAMESPACE = "urn:com.workday/bsvc";
    private static final String WORKDAY_VERSION = "v39.0"; // Adjust based on your Workday version
    
    private final WorkdayConfig config;
    private transient SOAPConnection soapConnection;
    
    public WorkdaySOAPClient(WorkdayConfig config) {
        this.config = config;
        initializeConnection();
    }
    
    private void initializeConnection() {
        try {
            SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();
            this.soapConnection = soapConnectionFactory.createConnection();
            LOG.info("SOAP connection initialized successfully");
        } catch (Exception e) {
            LOG.error("Failed to initialize SOAP connection", e);
            throw new RuntimeException("Failed to initialize SOAP connection", e);
        }
    }
    
    /**
     * Retrieves employees from Workday API with pagination support.
     *
     * @param page     Page number (1-based)
     * @param pageSize Number of records per page (max 999)
     * @return WorkdayResponse containing employee data and metadata
     */
    public WorkdayResponse getEmployees(int page, int pageSize) {
        try {
            LOG.debug("Requesting employees - Page: {}, Size: {}", page, pageSize);
            
            SOAPMessage soapMessage = createGetWorkersRequest(page, pageSize);
            
            // Add authentication headers
            addAuthenticationHeaders(soapMessage);
            
            // Log the request for debugging (remove in production)
            if (LOG.isDebugEnabled()) {
                logSOAPMessage("Request", soapMessage);
            }
            
            // Send the SOAP request
            String endpoint = buildEndpointUrl();
            SOAPMessage response = soapConnection.call(soapMessage, endpoint);
            
            // Log the response for debugging (remove in production)
            if (LOG.isDebugEnabled()) {
                logSOAPMessage("Response", response);
            }
            
            // Parse the response
            return parseGetWorkersResponse(response);
            
        } catch (Exception e) {
            LOG.error("Error retrieving employees from Workday API", e);
            throw new RuntimeException("Failed to retrieve employees", e);
        }
    }
    
    private SOAPMessage createGetWorkersRequest(int page, int pageSize) throws Exception {
        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage soapMessage = messageFactory.createMessage();
        SOAPPart soapPart = soapMessage.getSOAPPart();
        
        SOAPEnvelope envelope = soapPart.getEnvelope();
        envelope.addNamespaceDeclaration("wd", WORKDAY_NAMESPACE);
        
        SOAPHeader header = envelope.getHeader();
        SOAPBody body = envelope.getBody();
        
        // Add Workday version header
        SOAPElement versionElement = header.addChildElement("Workday_Common_Header", "wd");
        versionElement.addChildElement("Include_Reference_Descriptors_In_Response", "wd").addTextNode("true");
        
        // Create Get_Workers request
        SOAPElement getWorkersElement = body.addChildElement("Get_Workers_Request", "wd");
        
        // Add Request_References (optional - can be used to filter specific workers)
        // SOAPElement requestReferences = getWorkersElement.addChildElement("Request_References", "wd");
        
        // Add Request_Criteria for filtering
        SOAPElement requestCriteria = getWorkersElement.addChildElement("Request_Criteria", "wd");
        
        // Add effective date filter for incremental loads
        if (config.getLoadMode() == WorkdayConfig.LoadMode.INCREMENTAL) {
            SOAPElement transactionLogCriteria = requestCriteria.addChildElement("Transaction_Log_Criteria_Data", "wd");
            transactionLogCriteria.addChildElement("Transaction_Date_Range_Data", "wd")
                    .addChildElement("Updated_From", "wd")
                    .addTextNode(config.getEffectiveDate());
            transactionLogCriteria.addChildElement("Transaction_Date_Range_Data", "wd")
                    .addChildElement("Updated_Through", "wd")
                    .addTextNode(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        
        // Add Response_Filter for pagination
        SOAPElement responseFilter = getWorkersElement.addChildElement("Response_Filter", "wd");
        responseFilter.addChildElement("Page", "wd").addTextNode(String.valueOf(page));
        responseFilter.addChildElement("Count", "wd").addTextNode(String.valueOf(pageSize));
        
        // Add Response_Group to specify what data to return
        SOAPElement responseGroup = getWorkersElement.addChildElement("Response_Group", "wd");
        responseGroup.addChildElement("Include_Reference", "wd").addTextNode("true");
        responseGroup.addChildElement("Include_Personal_Information", "wd").addTextNode("true");
        responseGroup.addChildElement("Include_Employment_Information", "wd").addTextNode("true");
        responseGroup.addChildElement("Include_Compensation", "wd").addTextNode("true");
        responseGroup.addChildElement("Include_Organizations", "wd").addTextNode("true");
        responseGroup.addChildElement("Include_Roles", "wd").addTextNode("true");
        
        soapMessage.saveChanges();
        return soapMessage;
    }
    
    private void addAuthenticationHeaders(SOAPMessage soapMessage) throws Exception {
        SOAPHeader header = soapMessage.getSOAPHeader();
        
        // Add WS-Security authentication
        SOAPElement securityElement = header.addChildElement("Security", "wsse", 
                "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd");
        
        SOAPElement usernameTokenElement = securityElement.addChildElement("UsernameToken", "wsse");
        usernameTokenElement.addChildElement("Username", "wsse").addTextNode(config.getUsername());
        usernameTokenElement.addChildElement("Password", "wsse").addTextNode(config.getPassword());
    }
    
    private String buildEndpointUrl() {
        return String.format("%s/Human_Resources/%s", config.getEndpoint(), WORKDAY_VERSION);
    }
    
    private WorkdayResponse parseGetWorkersResponse(SOAPMessage response) throws Exception {
        SOAPBody body = response.getSOAPBody();
        
        // Check for SOAP faults
        if (body.hasFault()) {
            SOAPFault fault = body.getFault();
            throw new RuntimeException("SOAP Fault: " + fault.getFaultString());
        }
        
        WorkdayResponse workdayResponse = new WorkdayResponse();
        List<WorkdayEmployee> employees = new ArrayList<>();
        
        // Parse response - this is a simplified version
        // In a real implementation, you would use proper XML parsing or JAXB
        // to handle the complex Workday response structure
        
        // For now, return a mock response structure
        // You'll need to implement proper XML parsing based on your Workday schema
        
        workdayResponse.setEmployees(employees);
        workdayResponse.setTotalResults(0); // Parse from response
        workdayResponse.setPageNumber(1);   // Parse from response
        workdayResponse.setPageSize(999);   // Parse from response
        
        return workdayResponse;
    }
    
    private void logSOAPMessage(String messageType, SOAPMessage message) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            message.writeTo(outputStream);
            LOG.debug("{} SOAP Message: {}", messageType, outputStream.toString());
        } catch (Exception e) {
            LOG.warn("Failed to log SOAP message", e);
        }
    }
    
    public void close() {
        if (soapConnection != null) {
            try {
                soapConnection.close();
                LOG.info("SOAP connection closed");
            } catch (Exception e) {
                LOG.warn("Error closing SOAP connection", e);
            }
        }
    }
}