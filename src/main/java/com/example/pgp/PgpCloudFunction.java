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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

    /**
     * Google Cloud Function for PGP file decryption.
     * 
     * This function:
     * 1. Downloads an encrypted PGP file from Google Cloud Storage
     * 2. Decrypts it using PGP with a private key from Secret Manager
     * 3. Uploads the decrypted file back to Google Cloud Storage
     */
public class PgpCloudFunction implements HttpFunction {
    
    private static final Logger logger = Logger.getLogger(PgpCloudFunction.class.getName());
    private static final Gson gson = new Gson();
    
    private final PgpFileDecryptor decryptor = new PgpFileDecryptor();

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
        
        public PgpResponse(boolean success, String message, String error) {
            this.success = success;
            this.message = message;
            this.error = error;
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
            PgpResponse pgpResponse = new PgpResponse(true, "File processed successfully", processedFileName);
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
        try {
            String requestBody = request.getReader().lines()
                .reduce("", (accumulator, actual) -> accumulator + actual);
            
            if (requestBody.trim().isEmpty()) {
                throw new IllegalArgumentException("Request body is empty");
            }
            
            return gson.fromJson(requestBody, PgpRequest.class);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid JSON in request body: " + e.getMessage());
        }
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

        // Download the encrypted file from source bucket
        logger.info("Downloading file from GCS: " + request.Src_Bucket + "/" + request.Src_File);
        Blob sourceBlob = storage.get(BlobId.of(request.Src_Bucket, request.Src_File));
        
        if (sourceBlob == null) {
            throw new IllegalArgumentException("Source file not found: " + request.Src_File);
        }

        byte[] encryptedFileBytes = sourceBlob.getContent();
        
        // Get the private key from Secret Manager
        logger.info("Retrieving private key from Secret Manager: " + request.Private_encrypt_Key);
        String privateKeyContent = getSecretValue(request.Gcs_ProjectID, request.Private_encrypt_Key);
        
        // Decrypt the file
        logger.info("Decrypting file...");
        ByteArrayOutputStream decryptedOutput = new ByteArrayOutputStream();
        
        try (ByteArrayInputStream encryptedInput = new ByteArrayInputStream(encryptedFileBytes);
             ByteArrayInputStream privateKeyInput = new ByteArrayInputStream(privateKeyContent.getBytes(StandardCharsets.UTF_8))) {
            
            decryptor.decryptFile(encryptedInput, privateKeyInput, 
                                request.passphrase.toCharArray(), decryptedOutput);
        }
        
        byte[] decryptedBytes = decryptedOutput.toByteArray();
        logger.info("File decrypted successfully. Size: " + decryptedBytes.length + " bytes");
        
        // Upload the decrypted file to target bucket
        String targetFileName = generateTargetFileName(request.Src_File);
        logger.info("Uploading decrypted file to GCS: " + request.Tgt_Bucket + "/" + targetFileName);
        
        BlobId targetBlobId = BlobId.of(request.Tgt_Bucket, targetFileName);
        BlobInfo targetBlobInfo = BlobInfo.newBuilder(targetBlobId)
            .setContentType(determineContentType(targetFileName))
            .build();
        
        storage.create(targetBlobInfo, decryptedBytes);
        
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
        
        // Remove path separators and get just the filename
        String fileName = filePath;
        int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
        }
        
        return fileName.isEmpty() ? "file" : fileName;
    }

    private String generateTargetFileName(String sourceFileName) {
        String baseName = extractFileName(sourceFileName);
        
        // Remove .pgp extension if present to get the original filename
        if (baseName.toLowerCase().endsWith(".pgp")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        
        // Add decrypted suffix to indicate this is the decrypted version
        return baseName + "_decrypted";
    }

    private String determineContentType(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        
        if (lowerFileName.endsWith(".csv")) {
            return "text/csv";
        } else if (lowerFileName.endsWith(".txt")) {
            return "text/plain";
        } else if (lowerFileName.endsWith(".json")) {
            return "application/json";
        } else if (lowerFileName.endsWith(".xml")) {
            return "application/xml";
        } else if (lowerFileName.endsWith(".pdf")) {
            return "application/pdf";
        } else {
            return "application/octet-stream";
        }
    }

    private void sendResponse(HttpResponse response, int statusCode, PgpResponse pgpResponse) throws IOException {
        response.setStatusCode(statusCode);
        response.setContentType("application/json");
        
        try (PrintWriter writer = response.getWriter()) {
            writer.write(gson.toJson(pgpResponse));
        }
    }

    private void sendErrorResponse(HttpResponse response, int statusCode, String errorMessage) throws IOException {
        PgpResponse errorResponse = new PgpResponse(false, "Error", errorMessage);
        sendResponse(response, statusCode, errorResponse);
    }
}