package com.example.pgp;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.protobuf.ByteString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Google Cloud Function to decrypt PGP files in Google Cloud Storage.
 * Decrypts files using private key from Secret Manager and stores the result back to GCS.
 */
public class PgpGcsDecryptionFunction implements HttpFunction {
    
    private static final Logger logger = Logger.getLogger(PgpGcsDecryptionFunction.class.getName());

    public static String accessSecret(String projectName, String keyName) {
        ByteString secretValue = ByteString.fromHex("");
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            String secretName = "projects/" + projectName + "/secrets/" + keyName + "/versions/latest";
            SecretPayload secretPayload = client.accessSecretVersion(secretName).getPayload();
            secretValue = secretPayload.getData();
        } catch (IOException e) {
            // Handle exception
            e.printStackTrace();
        }
        return secretValue.toStringUtf8();
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        // Get the file details from the request parameters
        String srcBucketName = request.getFirstQueryParameter("Src_Bucket").orElseThrow(() -> new Exception("Src_Bucket is a required parameter"));
        String tgtBucketName = request.getFirstQueryParameter("Tgt_Bucket").orElseThrow(() -> new Exception("Tgt_Bucket is a required parameter"));
        String srcFileName = request.getFirstQueryParameter("Src_File").orElseThrow(() -> new Exception("Src_File is a required parameter"));
        String gcsProjectId = request.getFirstQueryParameter("Gcs_ProjectID").orElseThrow(() -> new Exception("Gcs_ProjectID is a required parameter"));
        String passphrase = request.getFirstQueryParameter("passphrase").orElseThrow(() -> new Exception("passphrase is a required parameter"));
        String privateEncryptKey = request.getFirstQueryParameter("Private_encrypt_Key").orElseThrow(() -> new Exception("Private_encrypt_Key is a required parameter"));

        logger.info("Processing PGP decryption for file: " + srcFileName);

        // Initialize the GCS client
        Storage storage = StorageOptions.newBuilder().setProjectId(gcsProjectId).build().getService();
        Bucket sourceBucket = storage.get(srcBucketName);
        Bucket targetBucket = storage.get(tgtBucketName);
        Blob receivedBlob = sourceBucket.get(srcFileName);
        
        if (receivedBlob == null) {
            throw new Exception("Source file not found: " + srcFileName);
        }
        
        byte[] encryptedFileBytes = receivedBlob.getContent();

        // Load the private key from Secret Manager
        logger.info("Retrieving private key from Secret Manager: " + privateEncryptKey);
        String privateKeyContent = accessSecret(gcsProjectId, privateEncryptKey);

        // Decrypt the file using PGP
        logger.info("Decrypting PGP file");
        byte[] decryptedBytes = decryptPgpFile(encryptedFileBytes, privateKeyContent, passphrase);
        
        System.out.println("Decrypted file size: " + decryptedBytes.length + " bytes");

        // Generate output file name (remove .pgp extension if present, add .decrypted)
        String outputFileName = generateOutputFileName(srcFileName);

        // Store the decrypted file in target GCS bucket
        BlobId decryptedBlobId = BlobId.of(tgtBucketName, outputFileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(decryptedBlobId).build();
        storage.create(blobInfo, decryptedBytes);
        
        logger.info("Source file decrypted and saved as: " + outputFileName);
        
        // Optionally delete the source encrypted file (uncomment if needed)
        // BlobId receivedBlobId = BlobId.of(srcBucketName, srcFileName);
        // storage.delete(receivedBlobId);
        // logger.info("Source encrypted file deleted: " + srcFileName);
    }

    private byte[] decryptPgpFile(byte[] encryptedContent, String privateKeyContent, String passphrase) throws Exception {
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
        
        // Add .decrypted extension (or just return without .pgp if you prefer)
        return baseName + ".decrypted";
    }
}