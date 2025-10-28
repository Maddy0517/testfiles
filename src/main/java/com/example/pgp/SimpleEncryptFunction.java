package com.example.pgp;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.gson.Gson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class SimpleEncryptFunction implements HttpFunction {
    
    private final Gson gson = new Gson();

    public static class Request {
        public String Src_Bucket;
        public String Tgt_Bucket;
        public String Src_File;
        public String Gcs_ProjectID;
        public String passphrase;
        public String Private_encrypt_Key;
    }

    public static class Response {
        public boolean success;
        public String message;
        public String encryptedFile;
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        
        try {
            // Parse request
            String body = request.getReader().lines().reduce("", String::concat);
            Request req = gson.fromJson(body, Request.class);
            
            // Initialize Storage
            Storage storage = StorageOptions.newBuilder()
                .setProjectId(req.Gcs_ProjectID)
                .build()
                .getService();

            // Download source file
            byte[] fileData = storage.get(BlobId.of(req.Src_Bucket, req.Src_File)).getContent();
            
            // Get private key from Secret Manager
            String privateKey = getSecret(req.Gcs_ProjectID, req.Private_encrypt_Key);
            
            // Encrypt file
            byte[] encryptedData = encryptFile(fileData, privateKey, req.passphrase, req.Src_File);
            
            // Upload encrypted file
            String targetFile = req.Src_File + ".pgp";
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(req.Tgt_Bucket, targetFile)).build();
            storage.create(blobInfo, encryptedData);
            
            // Send response
            Response resp = new Response();
            resp.success = true;
            resp.message = "File encrypted successfully";
            resp.encryptedFile = targetFile;
            
            response.setContentType("application/json");
            response.getWriter().write(gson.toJson(resp));
            
        } catch (Exception e) {
            Response resp = new Response();
            resp.success = false;
            resp.message = "Error: " + e.getMessage();
            
            response.setContentType("application/json");
            response.getWriter().write(gson.toJson(resp));
        }
    }
    
    private String getSecret(String projectId, String secretName) throws Exception {
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretVersionName name = SecretVersionName.of(projectId, secretName, "latest");
            return client.accessSecretVersion(name).getPayload().getData().toStringUtf8();
        }
    }
    
    private byte[] encryptFile(byte[] fileData, String privateKey, String passphrase, String fileName) throws Exception {
        PgpFileEncryptor encryptor = new PgpFileEncryptor();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        try (ByteArrayInputStream fileInput = new ByteArrayInputStream(fileData);
             ByteArrayInputStream keyInput = new ByteArrayInputStream(privateKey.getBytes())) {
            
            encryptor.encryptFile(fileInput, output, keyInput, passphrase.toCharArray(), fileName, true);
        }
        
        return output.toByteArray();
    }
}