package com.example.workday.client;

import com.example.workday.model.Employee;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.soap.*;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * SOAP client for Workday API with pagination support
 */
public class WorkdaySoapClient implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(WorkdaySoapClient.class);
    
    private static final String WORKDAY_NAMESPACE = "urn:com.workday/bsvc";
    private static final int PAGE_SIZE = 999; // Workday max page size
    
    private final String workdayEndpoint;
    private final String username;
    private final String password;
    private final String tenant;
    private final boolean useProxy;
    private final String proxyHost;
    private final int proxyPort;
    
    public WorkdaySoapClient(String workdayEndpoint, String username, String password, 
                            String tenant, boolean useProxy, String proxyHost, int proxyPort) {
        this.workdayEndpoint = workdayEndpoint;
        this.username = username;
        this.password = password;
        this.tenant = tenant;
        this.useProxy = useProxy;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }
    
    /**
     * Fetch employees with pagination support
     * @param effectiveDate The effective as-of date for data retrieval
     * @param lastModifiedFrom For incremental loads, fetch only modified records
     * @param pageNumber The page number to fetch (1-based)
     * @return WorkdayResponse containing employees and pagination info
     */
    public WorkdayResponse fetchEmployees(LocalDate effectiveDate, 
                                         LocalDateTime lastModifiedFrom, 
                                         int pageNumber) throws Exception {
        LOG.info("Fetching employees - Page: {}, Effective Date: {}, Last Modified From: {}", 
                pageNumber, effectiveDate, lastModifiedFrom);
        
        try {
            // Create SOAP Connection
            SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();
            SOAPConnection soapConnection = soapConnectionFactory.createConnection();
            
            // Create SOAP Message
            MessageFactory messageFactory = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
            SOAPMessage soapMessage = messageFactory.createMessage();
            
            // Build SOAP Request
            buildSoapRequest(soapMessage, effectiveDate, lastModifiedFrom, pageNumber);
            
            // Add authentication
            addAuthentication(soapMessage);
            
            // Send SOAP Message
            SOAPMessage soapResponse = soapConnection.call(soapMessage, workdayEndpoint);
            
            // Parse Response
            WorkdayResponse response = parseResponse(soapResponse);
            
            soapConnection.close();
            
            return response;
            
        } catch (Exception e) {
            LOG.error("Error fetching employees from Workday", e);
            throw new RuntimeException("Failed to fetch employees from Workday", e);
        }
    }
    
    private void buildSoapRequest(SOAPMessage soapMessage, LocalDate effectiveDate, 
                                 LocalDateTime lastModifiedFrom, int pageNumber) throws Exception {
        SOAPPart soapPart = soapMessage.getSOAPPart();
        SOAPEnvelope envelope = soapPart.getEnvelope();
        envelope.addNamespaceDeclaration("bsvc", WORKDAY_NAMESPACE);
        
        SOAPBody soapBody = envelope.getBody();
        
        // Create Get_Workers_Request
        SOAPElement getWorkersRequest = soapBody.addChildElement("Get_Workers_Request", "bsvc");
        getWorkersRequest.setAttribute("version", "v41.0");
        
        // Add Request_References (optional - for specific workers)
        // Skip if we want all workers
        
        // Add Request_Criteria
        SOAPElement requestCriteria = getWorkersRequest.addChildElement("Request_Criteria", "bsvc");
        
        // Add Transaction Log criteria for incremental loads
        if (lastModifiedFrom != null) {
            SOAPElement transactionLogCriteria = requestCriteria.addChildElement(
                "Transaction_Log_Criteria_Data", "bsvc");
            SOAPElement transactionDateRange = transactionLogCriteria.addChildElement(
                "Transaction_Date_Range_Data", "bsvc");
            
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            SOAPElement updatedFrom = transactionDateRange.addChildElement("Updated_From", "bsvc");
            updatedFrom.addTextNode(lastModifiedFrom.format(formatter));
            
            SOAPElement updatedThrough = transactionDateRange.addChildElement("Updated_Through", "bsvc");
            updatedThrough.addTextNode(LocalDateTime.now().format(formatter));
        }
        
        // Add Response_Filter
        SOAPElement responseFilter = getWorkersRequest.addChildElement("Response_Filter", "bsvc");
        
        // Pagination
        SOAPElement pageNumber_elem = responseFilter.addChildElement("Page", "bsvc");
        pageNumber_elem.addTextNode(String.valueOf(pageNumber));
        
        SOAPElement count = responseFilter.addChildElement("Count", "bsvc");
        count.addTextNode(String.valueOf(PAGE_SIZE));
        
        // As_Of_Effective_Date
        if (effectiveDate != null) {
            SOAPElement asOfDate = responseFilter.addChildElement("As_Of_Effective_Date", "bsvc");
            asOfDate.addTextNode(effectiveDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        } else {
            SOAPElement asOfDate = responseFilter.addChildElement("As_Of_Entry_DateTime", "bsvc");
            asOfDate.addTextNode(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        
        // Add Response_Group to specify what data to include
        SOAPElement responseGroup = getWorkersRequest.addChildElement("Response_Group", "bsvc");
        
        // Include various data elements
        SOAPElement includeReference = responseGroup.addChildElement("Include_Reference", "bsvc");
        includeReference.addTextNode("true");
        
        SOAPElement includePersonalInfo = responseGroup.addChildElement("Include_Personal_Information", "bsvc");
        includePersonalInfo.addTextNode("true");
        
        SOAPElement includeEmploymentInfo = responseGroup.addChildElement("Include_Employment_Information", "bsvc");
        includeEmploymentInfo.addTextNode("true");
        
        SOAPElement includeCompensation = responseGroup.addChildElement("Include_Compensation", "bsvc");
        includeCompensation.addTextNode("true");
        
        SOAPElement includeOrganizations = responseGroup.addChildElement("Include_Organizations", "bsvc");
        includeOrganizations.addTextNode("true");
        
        SOAPElement includeRoles = responseGroup.addChildElement("Include_Roles", "bsvc");
        includeRoles.addTextNode("true");
        
        SOAPElement includeManagement = responseGroup.addChildElement("Include_Management_Chain_Data", "bsvc");
        includeManagement.addTextNode("true");
        
        soapMessage.saveChanges();
    }
    
    private void addAuthentication(SOAPMessage soapMessage) throws Exception {
        SOAPHeader header = soapMessage.getSOAPHeader();
        if (header == null) {
            SOAPEnvelope envelope = soapMessage.getSOAPPart().getEnvelope();
            header = envelope.addHeader();
        }
        
        // Add WS-Security header
        String wsseNamespace = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
        String wsuNamespace = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
        
        SOAPElement security = header.addChildElement("Security", "wsse", wsseNamespace);
        security.addAttribute(new QName("mustUnderstand"), "1");
        
        SOAPElement usernameToken = security.addChildElement("UsernameToken", "wsse");
        usernameToken.addAttribute(new QName(wsuNamespace, "Id", "wsu"), "UsernameToken-1");
        
        SOAPElement usernameElement = usernameToken.addChildElement("Username", "wsse");
        usernameElement.addTextNode(username + "@" + tenant);
        
        SOAPElement passwordElement = usernameToken.addChildElement("Password", "wsse");
        passwordElement.setAttribute("Type", 
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText");
        passwordElement.addTextNode(password);
    }
    
    private WorkdayResponse parseResponse(SOAPMessage soapResponse) throws Exception {
        WorkdayResponse response = new WorkdayResponse();
        List<Employee> employees = new ArrayList<>();
        
        try {
            SOAPBody body = soapResponse.getSOAPBody();
            
            // Check for SOAP Fault
            if (body.hasFault()) {
                SOAPFault fault = body.getFault();
                throw new RuntimeException("SOAP Fault: " + fault.getFaultString());
            }
            
            // Parse the response
            NodeList workerNodes = body.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Worker");
            
            for (int i = 0; i < workerNodes.getLength(); i++) {
                Node workerNode = workerNodes.item(i);
                Employee employee = parseWorkerNode(workerNode);
                if (employee != null) {
                    employees.add(employee);
                }
            }
            
            // Get pagination info
            NodeList responseResultsNode = body.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Response_Results");
            if (responseResultsNode.getLength() > 0) {
                Element resultsElement = (Element) responseResultsNode.item(0);
                
                String totalPages = getElementTextContent(resultsElement, "Total_Pages");
                String totalResults = getElementTextContent(resultsElement, "Total_Results");
                String pageResults = getElementTextContent(resultsElement, "Page_Results");
                String page = getElementTextContent(resultsElement, "Page");
                
                response.setTotalPages(totalPages != null ? Integer.parseInt(totalPages) : 1);
                response.setTotalResults(totalResults != null ? Integer.parseInt(totalResults) : employees.size());
                response.setPageResults(pageResults != null ? Integer.parseInt(pageResults) : employees.size());
                response.setCurrentPage(page != null ? Integer.parseInt(page) : 1);
            }
            
            response.setEmployees(employees);
            
        } catch (Exception e) {
            LOG.error("Error parsing SOAP response", e);
            throw e;
        }
        
        return response;
    }
    
    private Employee parseWorkerNode(Node workerNode) {
        try {
            Employee employee = new Employee();
            Element workerElement = (Element) workerNode;
            
            // Parse Worker Reference
            NodeList referenceNodes = workerElement.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Worker_Reference");
            if (referenceNodes.getLength() > 0) {
                Element referenceElement = (Element) referenceNodes.item(0);
                NodeList idNodes = referenceElement.getElementsByTagNameNS(WORKDAY_NAMESPACE, "ID");
                for (int i = 0; i < idNodes.getLength(); i++) {
                    Element idElement = (Element) idNodes.item(i);
                    String type = idElement.getAttribute("type");
                    String value = idElement.getTextContent();
                    
                    if ("Employee_ID".equals(type)) {
                        employee.setEmployeeId(value);
                    } else if ("Worker_ID".equals(type)) {
                        employee.setWorkerReferenceId(value);
                    }
                }
            }
            
            // Parse Worker Data
            NodeList workerDataNodes = workerElement.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Worker_Data");
            if (workerDataNodes.getLength() > 0) {
                Element workerData = (Element) workerDataNodes.item(0);
                
                // Personal Data
                NodeList personalDataNodes = workerData.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Personal_Data");
                if (personalDataNodes.getLength() > 0) {
                    Element personalData = (Element) personalDataNodes.item(0);
                    
                    // Name Data
                    NodeList nameDataNodes = personalData.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Name_Data");
                    if (nameDataNodes.getLength() > 0) {
                        Element nameData = (Element) nameDataNodes.item(0);
                        
                        // Legal Name
                        NodeList legalNameNodes = nameData.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Legal_Name_Data");
                        if (legalNameNodes.getLength() > 0) {
                            Element legalName = (Element) legalNameNodes.item(0);
                            employee.setFirstName(getElementTextContent(legalName, "First_Name"));
                            employee.setLastName(getElementTextContent(legalName, "Last_Name"));
                        }
                        
                        // Preferred Name
                        NodeList preferredNameNodes = nameData.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Preferred_Name_Data");
                        if (preferredNameNodes.getLength() > 0) {
                            Element preferredName = (Element) preferredNameNodes.item(0);
                            String preferred = getElementTextContent(preferredName, "Name_Detail_Data");
                            employee.setPreferredName(preferred);
                        }
                    }
                    
                    // Contact Data
                    NodeList contactDataNodes = personalData.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Contact_Data");
                    if (contactDataNodes.getLength() > 0) {
                        Element contactData = (Element) contactDataNodes.item(0);
                        
                        // Email
                        NodeList emailNodes = contactData.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Email_Address_Data");
                        if (emailNodes.getLength() > 0) {
                            Element emailData = (Element) emailNodes.item(0);
                            employee.setEmail(getElementTextContent(emailData, "Email_Address"));
                        }
                        
                        // Phone
                        NodeList phoneNodes = contactData.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Phone_Data");
                        if (phoneNodes.getLength() > 0) {
                            Element phoneData = (Element) phoneNodes.item(0);
                            employee.setPhoneNumber(getElementTextContent(phoneData, "Phone_Number"));
                        }
                    }
                }
                
                // Employment Data
                NodeList employmentDataNodes = workerData.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Employment_Data");
                if (employmentDataNodes.getLength() > 0) {
                    Element employmentData = (Element) employmentDataNodes.item(0);
                    
                    // Worker Status
                    NodeList statusNodes = employmentData.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Worker_Status_Data");
                    if (statusNodes.getLength() > 0) {
                        Element statusData = (Element) statusNodes.item(0);
                        employee.setEmploymentStatus(getElementTextContent(statusData, "Active_Status_Date"));
                        
                        String hireDateStr = getElementTextContent(statusData, "Hire_Date");
                        if (hireDateStr != null) {
                            employee.setHireDate(LocalDate.parse(hireDateStr));
                        }
                        
                        String termDateStr = getElementTextContent(statusData, "Termination_Date");
                        if (termDateStr != null) {
                            employee.setTerminationDate(LocalDate.parse(termDateStr));
                        }
                    }
                    
                    // Worker Job Data
                    NodeList jobDataNodes = employmentData.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Worker_Job_Data");
                    if (jobDataNodes.getLength() > 0) {
                        Element jobData = (Element) jobDataNodes.item(0);
                        
                        // Position Data
                        NodeList positionNodes = jobData.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Position_Data");
                        if (positionNodes.getLength() > 0) {
                            Element positionData = (Element) positionNodes.item(0);
                            employee.setJobTitle(getElementTextContent(positionData, "Position_Title"));
                            
                            // Business Title
                            NodeList businessTitleNodes = positionData.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Business_Title");
                            if (businessTitleNodes.getLength() > 0) {
                                employee.setJobTitle(businessTitleNodes.item(0).getTextContent());
                            }
                        }
                    }
                }
                
                // Organization Data
                NodeList orgDataNodes = workerData.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Organization_Data");
                if (orgDataNodes.getLength() > 0) {
                    Element orgData = (Element) orgDataNodes.item(0);
                    
                    NodeList orgAssignments = orgData.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Worker_Organization_Data");
                    for (int i = 0; i < orgAssignments.getLength(); i++) {
                        Element orgAssignment = (Element) orgAssignments.item(i);
                        
                        NodeList orgRefNodes = orgAssignment.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Organization_Reference");
                        if (orgRefNodes.getLength() > 0) {
                            Element orgRef = (Element) orgRefNodes.item(0);
                            NodeList orgIdNodes = orgRef.getElementsByTagNameNS(WORKDAY_NAMESPACE, "ID");
                            
                            for (int j = 0; j < orgIdNodes.getLength(); j++) {
                                Element orgId = (Element) orgIdNodes.item(j);
                                String type = orgId.getAttribute("type");
                                String value = orgId.getTextContent();
                                
                                if ("Company".equals(type)) {
                                    employee.setBusinessUnit(value);
                                } else if ("Cost_Center".equals(type)) {
                                    employee.setCostCenter(value);
                                } else if ("Department".equals(type) || "Organization_Reference_ID".equals(type)) {
                                    employee.setDepartment(value);
                                } else if ("Location".equals(type)) {
                                    employee.setLocation(value);
                                }
                            }
                        }
                    }
                }
                
                // Management Chain Data
                NodeList mgmtDataNodes = workerData.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Management_Chain_Data");
                if (mgmtDataNodes.getLength() > 0) {
                    Element mgmtData = (Element) mgmtDataNodes.item(0);
                    
                    NodeList managerNodes = mgmtData.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Manager");
                    if (managerNodes.getLength() > 0) {
                        // Get immediate manager (first in chain)
                        Element manager = (Element) managerNodes.item(0);
                        
                        NodeList managerRefNodes = manager.getElementsByTagNameNS(WORKDAY_NAMESPACE, "Manager_Reference");
                        if (managerRefNodes.getLength() > 0) {
                            Element managerRef = (Element) managerRefNodes.item(0);
                            NodeList managerIdNodes = managerRef.getElementsByTagNameNS(WORKDAY_NAMESPACE, "ID");
                            
                            for (int i = 0; i < managerIdNodes.getLength(); i++) {
                                Element managerId = (Element) managerIdNodes.item(i);
                                String type = managerId.getAttribute("type");
                                String value = managerId.getTextContent();
                                
                                if ("Employee_ID".equals(type)) {
                                    employee.setManagerId(value);
                                }
                            }
                        }
                    }
                }
                
                // Set effective date to today if not specified
                employee.setEffectiveDate(LocalDate.now());
            }
            
            return employee;
            
        } catch (Exception e) {
            LOG.error("Error parsing worker node", e);
            return null;
        }
    }
    
    private String getElementTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagNameNS(WORKDAY_NAMESPACE, tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }
    
    /**
     * Response wrapper class
     */
    public static class WorkdayResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private List<Employee> employees;
        private int totalPages;
        private int totalResults;
        private int pageResults;
        private int currentPage;
        
        public WorkdayResponse() {
            this.employees = new ArrayList<>();
        }
        
        public List<Employee> getEmployees() {
            return employees;
        }
        
        public void setEmployees(List<Employee> employees) {
            this.employees = employees;
        }
        
        public int getTotalPages() {
            return totalPages;
        }
        
        public void setTotalPages(int totalPages) {
            this.totalPages = totalPages;
        }
        
        public int getTotalResults() {
            return totalResults;
        }
        
        public void setTotalResults(int totalResults) {
            this.totalResults = totalResults;
        }
        
        public int getPageResults() {
            return pageResults;
        }
        
        public void setPageResults(int pageResults) {
            this.pageResults = pageResults;
        }
        
        public int getCurrentPage() {
            return currentPage;
        }
        
        public void setCurrentPage(int currentPage) {
            this.currentPage = currentPage;
        }
        
        public boolean hasMorePages() {
            return currentPage < totalPages;
        }
    }
}