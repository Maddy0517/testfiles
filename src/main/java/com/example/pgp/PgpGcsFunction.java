package com.example.pgp;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Google Cloud Function to process PGP files in Google Cloud Storage.
 * Decrypts files using private key from Secret Manager and stores the result back to GCS.
 */
public class PgpGcsFunction implements HttpFunction {
    
    private static final Logger logger = Logger.getLogger(PgpGcsFunction.class.getName());
    private static final Gson gson = new Gson();
    
    // Request payload class
    public static class PgpRequest {
        public String Src_Bucket;
        public String Tgt_Bucket;
        public String Src_File;
        public String Gcs_ProjectID;
        public String passphrase;
        public String Private_encrypt_Key;
    }
    
    // Response payload class
    public static class PgpResponse {
        public String status;
        public String message;
        public String processedFile;
        
        public PgpResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }
        
        public PgpResponse(String status, String message, String processedFile) {
            this.status = status;
            this.message = message;
            this.processedFile = processedFile;
        }
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        // Set CORS headers
        response.appendHeader("Access-Control-Allow-Origin", "*");
        response.appendHeader("Access-Control-Allow-Methods", "POST");
        response.appendHeader("Access-Control-Allow-Headers", "Content-Type");
        
        if ("OPTIONS".equals(request.getMethod())) {
            response.setStatusCode(204);
            return;
        }
        
        try {
            // Parse request body
            PgpRequest pgpRequest = parseRequest(request);
            
            // Validate request
            validateRequest(pgpRequest);
            
            logger.info("Processing PGP request for file: " + pgpRequest.Src_File);
            
            // Process the file
            String processedFileName = processFile(pgpRequest);
            
            // Send success response
            PgpResponse pgpResponse = new PgpResponse("success", 
                "File processed successfully", processedFileName);
            sendResponse(response, 200, pgpResponse);
            
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Invalid request: " + e.getMessage(), e);
            PgpResponse errorResponse = new PgpResponse("error", "Invalid request: " + e.getMessage());
            sendResponse(response, 400, errorResponse);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing request: " + e.getMessage(), e);
            PgpResponse errorResponse = new PgpResponse("error", "Internal server error: " + e.getMessage());
            sendResponse(response, 500, errorResponse);
        }
    }
    
    private PgpRequest parseRequest(HttpRequest request) throws IOException {
        String requestBody = request.getReader().lines()
            .reduce("", (accumulator, actual) -> accumulator + actual);
            
        if (requestBody.trim().isEmpty()) {
            throw new IllegalArgumentException("Request body is empty");
        }
        
        try {
            return gson.fromJson(requestBody, PgpRequest.class);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage());
        }
    }
    
    private void validateRequest(PgpRequest request) {
        if (request.Src_Bucket == null || request.Src_Bucket.trim().isEmpty()) {
            throw new IllegalArgumentException("Src_Bucket is required");
        }
        if (request.Tgt_Bucket == null || request.Tgt_Bucket.trim().isEmpty()) {
            throw new IllegalArgumentException("Tgt_Bucket is required");
        }
        if (request.Src_File == null || request.Src_File.trim().isEmpty()) {
            throw new IllegalArgumentException("Src_File is required");
        }
        if (request.Gcs_ProjectID == null || request.Gcs_ProjectID.trim().isEmpty()) {
            throw new IllegalArgumentException("Gcs_ProjectID is required");
        }
        if (request.passphrase == null || request.passphrase.trim().isEmpty()) {
            throw new IllegalArgumentException("passphrase is required");
        }
        if (request.Private_encrypt_Key == null || request.Private_encrypt_Key.trim().isEmpty()) {
            throw new IllegalArgumentException("Private_encrypt_Key is required");
        }
    }
    
    private String processFile(PgpRequest request) throws Exception {
        // Initialize Google Cloud Storage client
        Storage storage = StorageOptions.newBuilder()
            .setProjectId(request.Gcs_ProjectID)
            .build()
            .getService();
        
        // Download encrypted file from source bucket
        logger.info("Downloading file from GCS: " + request.Src_Bucket + "/" + request.Src_File);
        BlobId sourceBlobId = BlobId.of(request.Src_Bucket, request.Src_File);
        Blob sourceBlob = storage.get(sourceBlobId);
        
        if (sourceBlob == null) {
            throw new IllegalArgumentException("Source file not found: " + request.Src_File);
        }
        
        // Get encrypted file content
        byte[] encryptedContent = sourceBlob.getContent();
        
        // Get private key from Secret Manager
        logger.info("Retrieving private key from Secret Manager: " + request.Private_encrypt_Key);
        String privateKeyContent = getSecretValue(request.Gcs_ProjectID, request.Private_encrypt_Key);
        
        // Decrypt the file
        logger.info("Decrypting file using PGP");
        byte[] decryptedContent = decryptFile(encryptedContent, privateKeyContent, request.passphrase);
        
        // Generate output file name (remove .pgp extension if present, add .decrypted)
        String outputFileName = generateOutputFileName(request.Src_File);
        
        // Upload decrypted file to target bucket
        logger.info("Uploading decrypted file to GCS: " + request.Tgt_Bucket + "/" + outputFileName);
        BlobId targetBlobId = BlobId.of(request.Tgt_Bucket, outputFileName);
        BlobInfo targetBlobInfo = BlobInfo.newBuilder(targetBlobId)
            .setContentType("application/octet-stream")
            .build();
        
        storage.create(targetBlobInfo, decryptedContent);
        
        logger.info("File processing completed successfully");
        return outputFileName;
    }
    
    private String getSecretValue(String projectId, String secretName) throws Exception {
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretVersionName secretVersionName = SecretVersionName.of(projectId, secretName, "latest");
            AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
            return response.getPayload().getData().toStringUtf8();
        }
    }
    
    private byte[] decryptFile(byte[] encryptedContent, String privateKeyContent, String passphrase) throws Exception {
        PgpFileProcessor processor = new PgpFileProcessor();
        
        try (InputStream encryptedInputStream = new ByteArrayInputStream(encryptedContent);
             InputStream privateKeyInputStream = new ByteArrayInputStream(privateKeyContent.getBytes());
             ByteArrayOutputStream decryptedOutputStream = new ByteArrayOutputStream()) {
            
            processor.decryptFile(encryptedInputStream, privateKeyInputStream, 
                                passphrase.toCharArray(), decryptedOutputStream);
            
            return decryptedOutputStream.toByteArray();
        }
    }
    
    private String generateOutputFileName(String originalFileName) {
        // Remove .pgp extension if present
        String baseName = originalFileName;
        if (baseName.toLowerCase().endsWith(".pgp")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        
        // Add .decrypted extension
        return baseName + ".decrypted";
    }
    
    private void sendResponse(HttpResponse response, int statusCode, PgpResponse pgpResponse) throws IOException {
        response.setStatusCode(statusCode);
        response.setContentType("application/json");
        
        try (BufferedWriter writer = response.getWriter()) {
            gson.toJson(pgpResponse, writer);
        }
    }
}