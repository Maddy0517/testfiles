package com.example.dataflow.client;

import com.example.dataflow.config.WorkdayConfig;
import com.example.dataflow.model.Employee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.soap.*;
import javax.xml.ws.BindingProvider;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * SOAP client for interacting with Workday API
 * Handles authentication, pagination, and data extraction
 */
public class WorkdaySoapClient implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(WorkdaySoapClient.class);
    
    private final WorkdayConfig config;
    private static final String WORKDAY_NS = "urn:com.workday/bsvc";
    
    public WorkdaySoapClient(WorkdayConfig config) {
        this.config = config;
    }
    
    /**
     * Fetch employees for a specific page
     * 
     * @param effectiveDate The effective date for the query
     * @param pageNumber The page number to fetch
     * @return List of employees
     */
    public List<Employee> fetchEmployeePage(String effectiveDate, int pageNumber) {
        List<Employee> employees = new ArrayList<>();
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < config.getMaxRetries()) {
            try {
                LOG.info("Fetching employee page {} for effective date {} (attempt {})", 
                        pageNumber, effectiveDate, attempts + 1);
                
                SOAPMessage response = callWorkdayAPI(effectiveDate, pageNumber);
                employees = parseEmployeeResponse(response, effectiveDate);
                
                LOG.info("Successfully fetched {} employees from page {}", 
                        employees.size(), pageNumber);
                return employees;
                
            } catch (Exception e) {
                lastException = e;
                attempts++;
                LOG.warn("Error fetching page {} (attempt {}): {}", 
                        pageNumber, attempts, e.getMessage());
                
                if (attempts < config.getMaxRetries()) {
                    try {
                        Thread.sleep(config.getRetryDelaySeconds() * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry delay", ie);
                    }
                }
            }
        }
        
        throw new RuntimeException("Failed to fetch page " + pageNumber + 
                " after " + config.getMaxRetries() + " attempts", lastException);
    }
    
    /**
     * Call Workday SOAP API to get worker data
     */
    private SOAPMessage callWorkdayAPI(String effectiveDate, int pageNumber) throws Exception {
        // Create SOAP Connection
        SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();
        SOAPConnection soapConnection = soapConnectionFactory.createConnection();
        
        try {
            // Create SOAP Message
            MessageFactory messageFactory = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);
            SOAPMessage soapMessage = messageFactory.createMessage();
            
            // Set authentication
            addAuthentication(soapMessage);
            
            // Build SOAP Body
            SOAPPart soapPart = soapMessage.getSOAPPart();
            SOAPEnvelope envelope = soapPart.getEnvelope();
            envelope.addNamespaceDeclaration("bsvc", WORKDAY_NS);
            
            SOAPBody soapBody = envelope.getBody();
            
            // Create Get_Workers request
            SOAPElement getWorkersRequest = soapBody.addChildElement("Get_Workers_Request", "bsvc");
            
            // Add Request Criteria
            SOAPElement requestCriteria = getWorkersRequest.addChildElement("Request_Criteria", "bsvc");
            
            // Add effective date if provided
            if (effectiveDate != null && !effectiveDate.isEmpty()) {
                SOAPElement transactionLogCriteria = requestCriteria.addChildElement(
                        "Transaction_Log_Criteria_Data", "bsvc");
                SOAPElement transactionDateFrom = transactionLogCriteria.addChildElement(
                        "Transaction_Date_Range_Data", "bsvc");
                transactionDateFrom.addChildElement("Effective_Date", "bsvc")
                        .addTextNode(effectiveDate);
            }
            
            // Add Response Filter for pagination
            SOAPElement responseFilter = getWorkersRequest.addChildElement("Response_Filter", "bsvc");
            responseFilter.addChildElement("Page", "bsvc").addTextNode(String.valueOf(pageNumber));
            responseFilter.addChildElement("Count", "bsvc").addTextNode("999");
            
            // Add Response Group for fields to return
            SOAPElement responseGroup = getWorkersRequest.addChildElement("Response_Group", "bsvc");
            responseGroup.addChildElement("Include_Reference", "bsvc").addTextNode("true");
            responseGroup.addChildElement("Include_Personal_Information", "bsvc").addTextNode("true");
            responseGroup.addChildElement("Include_Employment_Information", "bsvc").addTextNode("true");
            responseGroup.addChildElement("Include_Organizations", "bsvc").addTextNode("true");
            
            soapMessage.saveChanges();
            
            // Log SOAP request for debugging
            LOG.debug("SOAP Request: {}", soapMessage);
            
            // Call SOAP service
            SOAPMessage soapResponse = soapConnection.call(soapMessage, config.getSoapUrl());
            
            // Check for SOAP faults
            if (soapResponse.getSOAPBody().hasFault()) {
                SOAPFault fault = soapResponse.getSOAPBody().getFault();
                throw new RuntimeException("SOAP Fault: " + fault.getFaultString());
            }
            
            return soapResponse;
            
        } finally {
            soapConnection.close();
        }
    }
    
    /**
     * Add Basic Authentication to SOAP message
     */
    private void addAuthentication(SOAPMessage soapMessage) throws SOAPException {
        String auth = config.getUsername() + "@" + 
                     (config.getTenantId() != null ? config.getTenantId() : "tenant") + 
                     ":" + config.getPassword();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        
        MimeHeaders headers = soapMessage.getMimeHeaders();
        headers.addHeader("Authorization", "Basic " + encodedAuth);
    }
    
    /**
     * Parse SOAP response and extract employee data
     */
    private List<Employee> parseEmployeeResponse(SOAPMessage response, String effectiveDate) throws Exception {
        List<Employee> employees = new ArrayList<>();
        
        try {
            SOAPBody responseBody = response.getSOAPBody();
            
            // Navigate through the response structure
            NodeList workerNodes = responseBody.getElementsByTagNameNS(WORKDAY_NS, "Worker");
            
            LOG.info("Found {} worker nodes in response", workerNodes.getLength());
            
            for (int i = 0; i < workerNodes.getLength(); i++) {
                try {
                    Element workerElement = (Element) workerNodes.item(i);
                    Employee employee = parseWorkerElement(workerElement, effectiveDate);
                    employees.add(employee);
                } catch (Exception e) {
                    LOG.error("Error parsing worker element {}: {}", i, e.getMessage(), e);
                    // Continue processing other workers
                }
            }
            
        } catch (Exception e) {
            LOG.error("Error parsing SOAP response: {}", e.getMessage(), e);
            throw e;
        }
        
        return employees;
    }
    
    /**
     * Parse individual worker element to Employee object
     */
    private Employee parseWorkerElement(Element workerElement, String effectiveDate) {
        Employee employee = new Employee();
        
        try {
            // Extract Worker Reference ID (Employee ID)
            NodeList workerRefNodes = workerElement.getElementsByTagNameNS(WORKDAY_NS, "Worker_Reference");
            if (workerRefNodes.getLength() > 0) {
                Element workerRef = (Element) workerRefNodes.item(0);
                NodeList idNodes = workerRef.getElementsByTagNameNS(WORKDAY_NS, "ID");
                for (int i = 0; i < idNodes.getLength(); i++) {
                    Element idElement = (Element) idNodes.item(i);
                    String type = idElement.getAttribute("type");
                    if ("Employee_ID".equals(type) || "WID".equals(type)) {
                        employee.setEmployeeId(idElement.getTextContent());
                        break;
                    }
                }
            }
            
            // Extract Worker Data
            NodeList workerDataNodes = workerElement.getElementsByTagNameNS(WORKDAY_NS, "Worker_Data");
            if (workerDataNodes.getLength() > 0) {
                Element workerData = (Element) workerDataNodes.item(0);
                
                // Personal Information
                NodeList personalNodes = workerData.getElementsByTagNameNS(WORKDAY_NS, "Personal_Data");
                if (personalNodes.getLength() > 0) {
                    Element personalData = (Element) personalNodes.item(0);
                    
                    employee.setFirstName(getElementText(personalData, "Legal_First_Name"));
                    employee.setLastName(getElementText(personalData, "Legal_Last_Name"));
                    
                    // Email
                    NodeList emailNodes = personalData.getElementsByTagNameNS(WORKDAY_NS, "Email_Address");
                    if (emailNodes.getLength() > 0) {
                        employee.setEmail(emailNodes.item(0).getTextContent());
                    }
                    
                    // Phone
                    NodeList phoneNodes = personalData.getElementsByTagNameNS(WORKDAY_NS, "Phone_Number");
                    if (phoneNodes.getLength() > 0) {
                        employee.setPhone(phoneNodes.item(0).getTextContent());
                    }
                }
                
                // Employment Information
                NodeList employmentNodes = workerData.getElementsByTagNameNS(WORKDAY_NS, "Employment_Data");
                if (employmentNodes.getLength() > 0) {
                    Element employmentData = (Element) employmentNodes.item(0);
                    
                    employee.setHireDate(getElementText(employmentData, "Hire_Date"));
                    employee.setEmploymentStatus(getElementText(employmentData, "Worker_Status"));
                    
                    // Position Information
                    NodeList positionNodes = employmentData.getElementsByTagNameNS(WORKDAY_NS, "Position_Data");
                    if (positionNodes.getLength() > 0) {
                        Element positionData = (Element) positionNodes.item(0);
                        employee.setJobTitle(getElementText(positionData, "Business_Title"));
                    }
                }
                
                // Organization Data
                NodeList orgNodes = workerData.getElementsByTagNameNS(WORKDAY_NS, "Organization_Data");
                if (orgNodes.getLength() > 0) {
                    Element orgData = (Element) orgNodes.item(0);
                    employee.setDepartment(getElementText(orgData, "Organization_Name"));
                    employee.setLocation(getElementText(orgData, "Location"));
                }
                
                // Manager Information
                NodeList managerNodes = workerData.getElementsByTagNameNS(WORKDAY_NS, "Manager_Reference");
                if (managerNodes.getLength() > 0) {
                    Element managerRef = (Element) managerNodes.item(0);
                    NodeList managerIdNodes = managerRef.getElementsByTagNameNS(WORKDAY_NS, "ID");
                    if (managerIdNodes.getLength() > 0) {
                        employee.setManagerId(managerIdNodes.item(0).getTextContent());
                    }
                }
            }
            
            // Set metadata
            employee.setEffectiveDate(effectiveDate);
            employee.setIngestionTimestamp(Instant.now().toString());
            
        } catch (Exception e) {
            LOG.error("Error parsing worker element: {}", e.getMessage(), e);
        }
        
        return employee;
    }
    
    /**
     * Helper method to safely extract element text
     */
    private String getElementText(Element parent, String tagName) {
        try {
            NodeList nodes = parent.getElementsByTagNameNS(WORKDAY_NS, tagName);
            if (nodes.getLength() > 0) {
                return nodes.item(0).getTextContent();
            }
        } catch (Exception e) {
            LOG.debug("Could not find element: {}", tagName);
        }
        return null;
    }
    
    /**
     * Get total count of workers (for determining number of pages)
     * This makes an initial call to get the total count
     */
    public int getTotalWorkerCount(String effectiveDate) {
        try {
            SOAPMessage response = callWorkdayAPI(effectiveDate, 1);
            SOAPBody responseBody = response.getSOAPBody();
            
            // Look for Response_Results with total count
            NodeList resultNodes = responseBody.getElementsByTagNameNS(WORKDAY_NS, "Response_Results");
            if (resultNodes.getLength() > 0) {
                Element resultElement = (Element) resultNodes.item(0);
                NodeList totalNodes = resultElement.getElementsByTagNameNS(WORKDAY_NS, "Total_Results");
                if (totalNodes.getLength() > 0) {
                    String totalStr = totalNodes.item(0).getTextContent();
                    return Integer.parseInt(totalStr);
                }
            }
            
            // If we can't get the total, return a conservative estimate
            LOG.warn("Could not determine total worker count, using default estimate");
            return 10000; // Default estimate
            
        } catch (Exception e) {
            LOG.error("Error getting total worker count: {}", e.getMessage(), e);
            return 10000; // Default estimate on error
        }
    }
}
