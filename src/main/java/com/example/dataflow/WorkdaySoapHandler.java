package com.example.dataflow;

import com.example.dataflow.WorkdayDataModel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.soap.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Handles all SOAP API interactions and data parsing for Workday
 */
public class WorkdaySoapHandler {
    
    private static final Logger LOG = LoggerFactory.getLogger(WorkdaySoapHandler.class);
    private static final String WORKDAY_NS = "urn:com.workday/bsvc";
    
    private final WorkdayConfig config;
    
    public WorkdaySoapHandler(WorkdayConfig config) {
        this.config = config;
    }
    
    /**
     * Fetch employees for a specific page with retry logic (Java 21 enhanced)
     */
    public List<Employee> fetchEmployeePage(String effectiveDate, int pageNumber) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= config.maxRetries; attempt++) {
            try {
                LOG.info(STR."Fetching page \{pageNumber} for date \{effectiveDate} (attempt \{attempt})");
                
                SOAPMessage response = callWorkdayAPI(effectiveDate, pageNumber);
                List<Employee> employees = parseEmployeeResponse(response, effectiveDate);
                
                LOG.info(STR."Successfully fetched \{employees.size()} employees from page \{pageNumber}");
                return employees;
                
            } catch (Exception e) {
                lastException = e;
                LOG.warn(STR."Error fetching page \{pageNumber} (attempt \{attempt}): \{e.getMessage()}");
                
                if (attempt < config.maxRetries) {
                    try {
                        Thread.sleep(config.retryDelaySeconds * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                }
            }
        }
        
        throw new RuntimeException(
            STR."Failed to fetch page \{pageNumber} after \{config.maxRetries} attempts", 
            lastException
        );
    }
    
    /**
     * Get total worker count for pagination (Java 21 pattern matching)
     */
    public int getTotalWorkerCount(String effectiveDate) {
        try {
            SOAPMessage response = callWorkdayAPI(effectiveDate, 1);
            SOAPBody responseBody = response.getSOAPBody();
            
            NodeList resultNodes = responseBody.getElementsByTagNameNS(WORKDAY_NS, "Response_Results");
            if (resultNodes.getLength() > 0 && resultNodes.item(0) instanceof Element resultElement) {
                NodeList totalNodes = resultElement.getElementsByTagNameNS(WORKDAY_NS, "Total_Results");
                if (totalNodes.getLength() > 0) {
                    return Integer.parseInt(totalNodes.item(0).getTextContent());
                }
            }
            
            LOG.warn("Could not determine total count, using default estimate");
            return 10000;
            
        } catch (Exception e) {
            LOG.error(STR."Error getting total count: \{e.getMessage()}");
            return 10000;
        }
    }
    
    /**
     * Call Workday SOAP API
     */
    private SOAPMessage callWorkdayAPI(String effectiveDate, int pageNumber) throws Exception {
        SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();
        SOAPConnection soapConnection = soapConnectionFactory.createConnection();
        
        try {
            MessageFactory messageFactory = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);
            SOAPMessage soapMessage = messageFactory.createMessage();
            
            // Add authentication
            String auth = config.username + "@" + (config.tenantId != null ? config.tenantId : "tenant") + ":" + config.password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            MimeHeaders headers = soapMessage.getMimeHeaders();
            headers.addHeader("Authorization", "Basic " + encodedAuth);
            
            // Build SOAP request
            SOAPPart soapPart = soapMessage.getSOAPPart();
            SOAPEnvelope envelope = soapPart.getEnvelope();
            envelope.addNamespaceDeclaration("bsvc", WORKDAY_NS);
            
            SOAPBody soapBody = envelope.getBody();
            SOAPElement getWorkersRequest = soapBody.addChildElement("Get_Workers_Request", "bsvc");
            
            // Add request criteria with effective date
            SOAPElement requestCriteria = getWorkersRequest.addChildElement("Request_Criteria", "bsvc");
            if (effectiveDate != null && !effectiveDate.isEmpty()) {
                SOAPElement transactionLogCriteria = requestCriteria.addChildElement("Transaction_Log_Criteria_Data", "bsvc");
                SOAPElement transactionDateFrom = transactionLogCriteria.addChildElement("Transaction_Date_Range_Data", "bsvc");
                transactionDateFrom.addChildElement("Effective_Date", "bsvc").addTextNode(effectiveDate);
            }
            
            // Add pagination
            SOAPElement responseFilter = getWorkersRequest.addChildElement("Response_Filter", "bsvc");
            responseFilter.addChildElement("Page", "bsvc").addTextNode(String.valueOf(pageNumber));
            responseFilter.addChildElement("Count", "bsvc").addTextNode("999");
            
            // Add response group
            SOAPElement responseGroup = getWorkersRequest.addChildElement("Response_Group", "bsvc");
            responseGroup.addChildElement("Include_Reference", "bsvc").addTextNode("true");
            responseGroup.addChildElement("Include_Personal_Information", "bsvc").addTextNode("true");
            responseGroup.addChildElement("Include_Employment_Information", "bsvc").addTextNode("true");
            responseGroup.addChildElement("Include_Organizations", "bsvc").addTextNode("true");
            
            soapMessage.saveChanges();
            
            // Call SOAP service
            SOAPMessage soapResponse = soapConnection.call(soapMessage, config.soapUrl);
            
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
     * Parse SOAP response and extract employees
     */
    private List<Employee> parseEmployeeResponse(SOAPMessage response, String effectiveDate) throws Exception {
        List<Employee> employees = new ArrayList<>();
        
        try {
            SOAPBody responseBody = response.getSOAPBody();
            NodeList workerNodes = responseBody.getElementsByTagNameNS(WORKDAY_NS, "Worker");
            
            LOG.info("Found {} worker nodes in response", workerNodes.getLength());
            
            for (int i = 0; i < workerNodes.getLength(); i++) {
                try {
                    if (workerNodes.item(i) instanceof Element workerElement) {
                        Employee employee = parseWorkerElement(workerElement, effectiveDate);
                        employees.add(employee);
                    }
                } catch (Exception e) {
                    LOG.error(STR."Error parsing worker element \{i}: \{e.getMessage()}");
                }
            }
            
        } catch (Exception e) {
            LOG.error(STR."Error parsing SOAP response: \{e.getMessage()}", e);
            throw e;
        }
        
        return employees;
    }
    
    /**
     * Parse individual worker element to Employee
     */
    private Employee parseWorkerElement(Element workerElement, String effectiveDate) {
        Employee employee = new Employee();
        
        try {
            // Extract Worker Reference ID (Java 21 pattern matching)
            NodeList workerRefNodes = workerElement.getElementsByTagNameNS(WORKDAY_NS, "Worker_Reference");
            if (workerRefNodes.getLength() > 0 && workerRefNodes.item(0) instanceof Element workerRef) {
                NodeList idNodes = workerRef.getElementsByTagNameNS(WORKDAY_NS, "ID");
                for (int i = 0; i < idNodes.getLength(); i++) {
                    if (idNodes.item(i) instanceof Element idElement) {
                        String type = idElement.getAttribute("type");
                        if ("Employee_ID".equals(type) || "WID".equals(type)) {
                            employee.employeeId = idElement.getTextContent();
                            break;
                        }
                    }
                }
            }
            
            // Extract Worker Data (Java 21 pattern matching)
            NodeList workerDataNodes = workerElement.getElementsByTagNameNS(WORKDAY_NS, "Worker_Data");
            if (workerDataNodes.getLength() > 0 && workerDataNodes.item(0) instanceof Element workerData) {
                
                // Personal Information
                NodeList personalNodes = workerData.getElementsByTagNameNS(WORKDAY_NS, "Personal_Data");
                if (personalNodes.getLength() > 0 && personalNodes.item(0) instanceof Element personalData) {
                    employee.firstName = getElementText(personalData, "Legal_First_Name");
                    employee.lastName = getElementText(personalData, "Legal_Last_Name");
                    
                    NodeList emailNodes = personalData.getElementsByTagNameNS(WORKDAY_NS, "Email_Address");
                    if (emailNodes.getLength() > 0) {
                        employee.email = emailNodes.item(0).getTextContent();
                    }
                    
                    NodeList phoneNodes = personalData.getElementsByTagNameNS(WORKDAY_NS, "Phone_Number");
                    if (phoneNodes.getLength() > 0) {
                        employee.phone = phoneNodes.item(0).getTextContent();
                    }
                }
                
                // Employment Information
                NodeList employmentNodes = workerData.getElementsByTagNameNS(WORKDAY_NS, "Employment_Data");
                if (employmentNodes.getLength() > 0 && employmentNodes.item(0) instanceof Element employmentData) {
                    employee.hireDate = getElementText(employmentData, "Hire_Date");
                    employee.employmentStatus = getElementText(employmentData, "Worker_Status");
                    
                    NodeList positionNodes = employmentData.getElementsByTagNameNS(WORKDAY_NS, "Position_Data");
                    if (positionNodes.getLength() > 0 && positionNodes.item(0) instanceof Element positionData) {
                        employee.jobTitle = getElementText(positionData, "Business_Title");
                    }
                }
                
                // Organization Data
                NodeList orgNodes = workerData.getElementsByTagNameNS(WORKDAY_NS, "Organization_Data");
                if (orgNodes.getLength() > 0 && orgNodes.item(0) instanceof Element orgData) {
                    employee.department = getElementText(orgData, "Organization_Name");
                    employee.location = getElementText(orgData, "Location");
                }
                
                // Manager Information
                NodeList managerNodes = workerData.getElementsByTagNameNS(WORKDAY_NS, "Manager_Reference");
                if (managerNodes.getLength() > 0 && managerNodes.item(0) instanceof Element managerRef) {
                    NodeList managerIdNodes = managerRef.getElementsByTagNameNS(WORKDAY_NS, "ID");
                    if (managerIdNodes.getLength() > 0) {
                        employee.managerId = managerIdNodes.item(0).getTextContent();
                    }
                }
            }
            
            employee.effectiveDate = effectiveDate;
            employee.ingestionTimestamp = Instant.now().toString();
            
        } catch (Exception e) {
            LOG.error(STR."Error parsing worker element: \{e.getMessage()}");
        }
        
        return employee;
    }
    
    /**
     * Helper to safely extract element text (Java 21 enhanced)
     */
    private String getElementText(Element parent, String tagName) {
        try {
            NodeList nodes = parent.getElementsByTagNameNS(WORKDAY_NS, tagName);
            if (nodes.getLength() > 0) {
                return nodes.item(0).getTextContent();
            }
        } catch (Exception e) {
            LOG.debug(STR."Could not find element: \{tagName}");
        }
        return null;
    }
}
