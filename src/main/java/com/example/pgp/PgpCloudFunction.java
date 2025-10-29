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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Simple Google Cloud Function for PGP file encryption.
 */
public class PgpCloudFunction implements HttpFunction {
    
    private static final Logger logger = Logger.getLogger(PgpCloudFunction.class.getName());
    private static final Gson gson = new Gson();
    
    private final PgpFileEncryptor encryptor = new PgpFileEncryptor();

    /**
     * Request payload structure
     */
    public static class PgpRequest {
        public String Src_Bucket;
        public String Tgt_Bucket;
        public String Src_File;
        public String Gcs_ProjectID;
        public String passphrase;
        public String Private_encrypt_Key;
    }

    /**
     * Response payload structure
     */
    public static class PgpResponse {
        public boolean success;
        public String message;
        public String processedFile;
        public String error;
        
        public PgpResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public PgpResponse(boolean success, String message, String processedFile) {
            this.success = success;
            this.message = message;
            this.processedFile = processedFile;
        }
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        // Set CORS headers
        response.getHeaders().set("Access-Control-Allow-Origin", "*");
        response.getHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.getHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equals(request.getMethod())) {
            response.setStatusCode(200);
            return;
        }

        if (!"POST".equals(request.getMethod())) {
            sendErrorResponse(response, 405, "Method not allowed. Use POST.");
            return;
        }

        try {
            // Parse request
            PgpRequest pgpRequest = parseRequest(request);
            
            // Validate request
            validateRequest(pgpRequest);
            
            // Process the file
            String processedFileName = processFile(pgpRequest);
            
            // Send success response
            PgpResponse pgpResponse = new PgpResponse(true, "File encrypted successfully", processedFileName);
            sendResponse(response, 200, pgpResponse);
            
        } catch (IllegalArgumentException e) {
            logger.severe("Invalid request: " + e.getMessage());
            sendErrorResponse(response, 400, "Invalid request: " + e.getMessage());
        } catch (Exception e) {
            logger.severe("Error processing file: " + e.getMessage());
            e.printStackTrace();
            sendErrorResponse(response, 500, "Internal server error: " + e.getMessage());
        }
    }

    private PgpRequest parseRequest(HttpRequest request) throws IOException {
        String requestBody = request.getReader().lines()
            .reduce("", (accumulator, actual) -> accumulator + actual);
        
        if (requestBody.trim().isEmpty()) {
            throw new IllegalArgumentException("Request body is empty");
        }
        
        return gson.fromJson(requestBody, PgpRequest.class);
    }

    private void validateRequest(PgpRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (isNullOrEmpty(request.Src_Bucket)) {
            throw new IllegalArgumentException("Src_Bucket is required");
        }
        if (isNullOrEmpty(request.Tgt_Bucket)) {
            throw new IllegalArgumentException("Tgt_Bucket is required");
        }
        if (isNullOrEmpty(request.Src_File)) {
            throw new IllegalArgumentException("Src_File is required");
        }
        if (isNullOrEmpty(request.Gcs_ProjectID)) {
            throw new IllegalArgumentException("Gcs_ProjectID is required");
        }
        if (isNullOrEmpty(request.passphrase)) {
            throw new IllegalArgumentException("passphrase is required");
        }
        if (isNullOrEmpty(request.Private_encrypt_Key)) {
            throw new IllegalArgumentException("Private_encrypt_Key is required");
        }
    }

    private boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    private String processFile(PgpRequest request) throws Exception {
        logger.info("Starting file processing for: " + request.Src_File);
        
        // Initialize Google Cloud Storage client
        Storage storage = StorageOptions.newBuilder()
            .setProjectId(request.Gcs_ProjectID)
            .build()
            .getService();

        // Download the file from source bucket
        logger.info("Downloading file from GCS: " + request.Src_Bucket + "/" + request.Src_File);
        Blob sourceBlob = storage.get(BlobId.of(request.Src_Bucket, request.Src_File));
        
        if (sourceBlob == null) {
            throw new IllegalArgumentException("Source file not found: " + request.Src_File);
        }

        byte[] fileBytes = sourceBlob.getContent();
        
        // Get the private key from Secret Manager
        logger.info("Retrieving private key from Secret Manager: " + request.Private_encrypt_Key);
        String privateKeyContent = getSecretValue(request.Gcs_ProjectID, request.Private_encrypt_Key);
        
        // Encrypt the file
        logger.info("Encrypting file...");
        ByteArrayOutputStream encryptedOutput = new ByteArrayOutputStream();
        
        try (ByteArrayInputStream fileInput = new ByteArrayInputStream(fileBytes);
             ByteArrayInputStream privateKeyInput = new ByteArrayInputStream(privateKeyContent.getBytes(StandardCharsets.UTF_8))) {
            
            String fileName = extractFileName(request.Src_File);
            encryptor.encryptFile(fileInput, encryptedOutput, privateKeyInput, 
                                request.passphrase.toCharArray(), fileName, true);
        }
        
        byte[] encryptedBytes = encryptedOutput.toByteArray();
        logger.info("File encrypted successfully. Size: " + encryptedBytes.length + " bytes");
        
        // Upload the encrypted file to target bucket
        String targetFileName = generateTargetFileName(request.Src_File);
        logger.info("Uploading encrypted file to GCS: " + request.Tgt_Bucket + "/" + targetFileName);
        
        BlobId targetBlobId = BlobId.of(request.Tgt_Bucket, targetFileName);
        BlobInfo targetBlobInfo = BlobInfo.newBuilder(targetBlobId)
            .setContentType("application/octet-stream")
            .build();
        
        storage.create(targetBlobInfo, encryptedBytes);
        
        logger.info("File processing completed successfully: " + targetFileName);
        return targetFileName;
    }

    private String getSecretValue(String projectId, String secretName) throws Exception {
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretVersionName secretVersionName = SecretVersionName.of(projectId, secretName, "latest");
            AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
            return response.getPayload().getData().toStringUtf8();
        }
    }

    private String extractFileName(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "file";
        }
        
        String fileName = filePath;
        int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
        }
        
        return fileName.isEmpty() ? "file" : fileName;
    }

    private String generateTargetFileName(String sourceFileName) {
        String baseName = extractFileName(sourceFileName);
        return baseName + ".pgp";
    }

    private void sendResponse(HttpResponse response, int statusCode, PgpResponse pgpResponse) throws IOException {
        response.setStatusCode(statusCode);
        response.setContentType("application/json");
        
        try (PrintWriter writer = response.getWriter()) {
            writer.write(gson.toJson(pgpResponse));
        }
    }

    private void sendErrorResponse(HttpResponse response, int statusCode, String errorMessage) throws IOException {
        PgpResponse errorResponse = new PgpResponse(false, errorMessage);
        errorResponse.error = errorMessage;
        sendResponse(response, statusCode, errorResponse);
    }
}