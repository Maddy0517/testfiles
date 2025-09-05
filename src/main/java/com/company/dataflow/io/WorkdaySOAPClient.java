package com.company.dataflow.io;

import com.company.dataflow.model.WorkdayEmployee;
import org.apache.http.HttpEntity;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * SOAP client for connecting to Workday API and retrieving employee data.
 */
public class WorkdaySOAPClient implements Serializable {
    
    private static final Logger LOG = LoggerFactory.getLogger(WorkdaySOAPClient.class);
    
    private final String endpoint;
    private final String username;
    private final String password;
    private final String tenant;
    private final String version;
    
    public WorkdaySOAPClient(String endpoint, String username, String password, String tenant, String version) {
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
        this.tenant = tenant != null ? tenant : "tenant";
        this.version = version != null ? version : "v35.0";
    }
    
    /**
     * Retrieves employee data from Workday SOAP API.
     */
    public List<WorkdayEmployee> getEmployees(int offset, int limit) throws Exception {
        String soapRequest = buildGetWorkersSOAPRequest(offset, limit);
        String response = makeSOAPRequest(soapRequest);
        return parseEmployeeResponse(response);
    }
    
    /**
     * Builds SOAP request for getting workers from Workday.
     */
    private String buildGetWorkersSOAPRequest(int offset, int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" ");
        sb.append("xmlns:bsvc=\"urn:com.workday/bsvc\">");
        sb.append("<soapenv:Header>");
        sb.append("<bsvc:Workday_Common_Header>");
        sb.append("<bsvc:Include_Reference_Descriptors_In_Response>true</bsvc:Include_Reference_Descriptors_In_Response>");
        sb.append("</bsvc:Workday_Common_Header>");
        sb.append("</soapenv:Header>");
        sb.append("<soapenv:Body>");
        sb.append("<bsvc:Get_Workers_Request version=\"").append(version).append("\">");
        sb.append("<bsvc:Request_Criteria>");
        sb.append("<bsvc:Exclude_Inactive_Workers>true</bsvc:Exclude_Inactive_Workers>");
        sb.append("<bsvc:Exclude_Contingent_Workers>false</bsvc:Exclude_Contingent_Workers>");
        sb.append("</bsvc:Request_Criteria>");
        sb.append("<bsvc:Response_Filter>");
        sb.append("<bsvc:Page>").append((offset / limit) + 1).append("</bsvc:Page>");
        sb.append("<bsvc:Count>").append(limit).append("</bsvc:Count>");
        sb.append("</bsvc:Response_Filter>");
        sb.append("<bsvc:Response_Group>");
        sb.append("<bsvc:Include_Reference>true</bsvc:Include_Reference>");
        sb.append("<bsvc:Include_Personal_Information>true</bsvc:Include_Personal_Information>");
        sb.append("<bsvc:Include_Employment_Information>true</bsvc:Include_Employment_Information>");
        sb.append("<bsvc:Include_Compensation>true</bsvc:Include_Compensation>");
        sb.append("<bsvc:Include_Organizations>true</bsvc:Include_Organizations>");
        sb.append("</bsvc:Response_Group>");
        sb.append("</bsvc:Get_Workers_Request>");
        sb.append("</soapenv:Body>");
        sb.append("</soapenv:Envelope>");
        
        return sb.toString();
    }
    
    /**
     * Makes SOAP request to Workday API with authentication.
     */
    private String makeSOAPRequest(String soapRequest) throws Exception {
        // Create credentials provider
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
            AuthScope.ANY,
            new UsernamePasswordCredentials(username, password)
        );
        
        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .build()) {
            
            HttpPost httpPost = new HttpPost(endpoint);
            httpPost.setHeader("Content-Type", "text/xml; charset=utf-8");
            httpPost.setHeader("SOAPAction", "");
            
            StringEntity entity = new StringEntity(soapRequest, "UTF-8");
            httpPost.setEntity(entity);
            
            LOG.info("Making SOAP request to Workday API: {}", endpoint);
            HttpResponse response = httpClient.execute(httpPost);
            
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new RuntimeException("SOAP request failed with status code: " + statusCode);
            }
            
            HttpEntity responseEntity = response.getEntity();
            String responseString = EntityUtils.toString(responseEntity);
            
            LOG.debug("Received SOAP response: {}", responseString);
            return responseString;
        }
    }
    
    /**
     * Parses SOAP response and extracts employee data.
     */
    private List<WorkdayEmployee> parseEmployeeResponse(String xmlResponse) throws Exception {
        List<WorkdayEmployee> employees = new ArrayList<>();
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        
        Document doc = builder.parse(new ByteArrayInputStream(xmlResponse.getBytes()));
        
        // Find all Worker elements
        NodeList workerNodes = doc.getElementsByTagNameNS("urn:com.workday/bsvc", "Worker");
        
        for (int i = 0; i < workerNodes.getLength(); i++) {
            Node workerNode = workerNodes.item(i);
            if (workerNode.getNodeType() == Node.ELEMENT_NODE) {
                WorkdayEmployee employee = parseWorkerElement((Element) workerNode);
                if (employee != null) {
                    employees.add(employee);
                }
            }
        }
        
        LOG.info("Parsed {} employees from SOAP response", employees.size());
        return employees;
    }
    
    /**
     * Parses a single Worker XML element into WorkdayEmployee object.
     */
    private WorkdayEmployee parseWorkerElement(Element workerElement) {
        try {
            WorkdayEmployee employee = new WorkdayEmployee();
            
            // Extract employee ID
            NodeList empIdNodes = workerElement.getElementsByTagNameNS("urn:com.workday/bsvc", "Employee_ID");
            if (empIdNodes.getLength() > 0) {
                employee.setEmployeeId(empIdNodes.item(0).getTextContent());
            }
            
            // Extract personal information
            NodeList personalDataNodes = workerElement.getElementsByTagNameNS("urn:com.workday/bsvc", "Personal_Data");
            if (personalDataNodes.getLength() > 0) {
                Element personalData = (Element) personalDataNodes.item(0);
                
                NodeList firstNameNodes = personalData.getElementsByTagNameNS("urn:com.workday/bsvc", "First_Name");
                if (firstNameNodes.getLength() > 0) {
                    employee.setFirstName(firstNameNodes.item(0).getTextContent());
                }
                
                NodeList lastNameNodes = personalData.getElementsByTagNameNS("urn:com.workday/bsvc", "Last_Name");
                if (lastNameNodes.getLength() > 0) {
                    employee.setLastName(lastNameNodes.item(0).getTextContent());
                }
            }
            
            // Extract employment information
            NodeList employmentDataNodes = workerElement.getElementsByTagNameNS("urn:com.workday/bsvc", "Employment_Data");
            if (employmentDataNodes.getLength() > 0) {
                Element employmentData = (Element) employmentDataNodes.item(0);
                
                // Extract hire date
                NodeList hireDateNodes = employmentData.getElementsByTagNameNS("urn:com.workday/bsvc", "Hire_Date");
                if (hireDateNodes.getLength() > 0) {
                    employee.setHireDate(hireDateNodes.item(0).getTextContent());
                }
                
                // Extract job title
                NodeList jobTitleNodes = employmentData.getElementsByTagNameNS("urn:com.workday/bsvc", "Job_Title");
                if (jobTitleNodes.getLength() > 0) {
                    employee.setJobTitle(jobTitleNodes.item(0).getTextContent());
                }
            }
            
            // Extract organization data (department)
            NodeList orgDataNodes = workerElement.getElementsByTagNameNS("urn:com.workday/bsvc", "Organization_Data");
            if (orgDataNodes.getLength() > 0) {
                Element orgData = (Element) orgDataNodes.item(0);
                
                NodeList deptNodes = orgData.getElementsByTagNameNS("urn:com.workday/bsvc", "Organization_Name");
                if (deptNodes.getLength() > 0) {
                    employee.setDepartment(deptNodes.item(0).getTextContent());
                }
            }
            
            // Extract email from contact information
            NodeList contactNodes = workerElement.getElementsByTagNameNS("urn:com.workday/bsvc", "Email_Address");
            if (contactNodes.getLength() > 0) {
                employee.setEmail(contactNodes.item(0).getTextContent());
            }
            
            // Set status as active (since we're filtering for active workers)
            employee.setStatus("Active");
            
            return employee;
            
        } catch (Exception e) {
            LOG.error("Error parsing worker element", e);
            return null;
        }
    }
    
    /**
     * Gets total count of workers from Workday API.
     */
    public int getTotalWorkerCount() throws Exception {
        String soapRequest = buildGetWorkersCountSOAPRequest();
        String response = makeSOAPRequest(soapRequest);
        return parseWorkerCountResponse(response);
    }
    
    /**
     * Builds SOAP request for getting total worker count.
     */
    private String buildGetWorkersCountSOAPRequest() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" ");
        sb.append("xmlns:bsvc=\"urn:com.workday/bsvc\">");
        sb.append("<soapenv:Header>");
        sb.append("<bsvc:Workday_Common_Header>");
        sb.append("<bsvc:Include_Reference_Descriptors_In_Response>false</bsvc:Include_Reference_Descriptors_In_Response>");
        sb.append("</bsvc:Workday_Common_Header>");
        sb.append("</soapenv:Header>");
        sb.append("<soapenv:Body>");
        sb.append("<bsvc:Get_Workers_Request version=\"").append(version).append("\">");
        sb.append("<bsvc:Request_Criteria>");
        sb.append("<bsvc:Exclude_Inactive_Workers>true</bsvc:Exclude_Inactive_Workers>");
        sb.append("</bsvc:Request_Criteria>");
        sb.append("<bsvc:Response_Filter>");
        sb.append("<bsvc:Count>1</bsvc:Count>");
        sb.append("</bsvc:Response_Filter>");
        sb.append("</bsvc:Get_Workers_Request>");
        sb.append("</soapenv:Body>");
        sb.append("</soapenv:Envelope>");
        
        return sb.toString();
    }
    
    /**
     * Parses worker count from SOAP response.
     */
    private int parseWorkerCountResponse(String xmlResponse) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        
        Document doc = builder.parse(new ByteArrayInputStream(xmlResponse.getBytes()));
        
        // Look for Total_Results element
        NodeList totalNodes = doc.getElementsByTagNameNS("urn:com.workday/bsvc", "Total_Results");
        if (totalNodes.getLength() > 0) {
            String totalStr = totalNodes.item(0).getTextContent();
            return Integer.parseInt(totalStr);
        }
        
        // Fallback: count Worker elements
        NodeList workerNodes = doc.getElementsByTagNameNS("urn:com.workday/bsvc", "Worker");
        return workerNodes.getLength();
    }
}