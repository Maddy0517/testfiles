package com.example.pgp;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.storage.*;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Google Cloud Function to encrypt files using PGP encryption.
 * 
 * This function reads a decrypted file from GCS, encrypts it using a PGP public key
 * (extracted from a private key stored in Secret Manager), and writes the encrypted
 * file back to GCS.
 */
public class PgpEncryptionFunction implements HttpFunction {
    
    private static final Logger logger = Logger.getLogger(PgpEncryptionFunction.class.getName());

    /**
     * Access a secret from Google Cloud Secret Manager.
     *
     * @param projectName Project ID
     * @param keyName     Secret name
     * @return Secret value as a string
     */
    public static String accessSecret(String projectName, String keyName) {
        ByteString secretValue = ByteString.fromHex("");
        
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            String secretName = "projects/" + projectName + "/secrets/" + keyName + "/versions/latest";
            SecretPayload secretPayload = client.accessSecretVersion(secretName).getPayload();
            secretValue = secretPayload.getData();
            logger.info("Successfully retrieved secret: " + keyName);
        } catch (IOException e) {
            logger.severe("Error accessing secret: " + e.getMessage());
            e.printStackTrace();
        }
        
        return secretValue.toStringUtf8();
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        
        try {
            // Extract parameters from the request
            String srcBucketName = request.getFirstQueryParameter("Src_Bucket")
                .orElseThrow(() -> new Exception("Src_Bucket is a required parameter"));
            
            String tgtBucketName = request.getFirstQueryParameter("Tgt_Bucket")
                .orElseThrow(() -> new Exception("Tgt_Bucket is a required parameter"));
            
            String srcFileName = request.getFirstQueryParameter("Src_File")
                .orElseThrow(() -> new Exception("Src_File is a required parameter"));
            
            String gcsProjectId = request.getFirstQueryParameter("Gcs_ProjectID")
                .orElseThrow(() -> new Exception("Gcs_ProjectID is a required parameter"));
            
            String passphrase = request.getFirstQueryParameter("passphrase")
                .orElseThrow(() -> new Exception("passphrase is a required parameter"));
            
            String privateEncryptKeySecretName = request.getFirstQueryParameter("Private_encrypt_Key")
                .orElseThrow(() -> new Exception("Private_encrypt_Key is a required parameter"));

            logger.info("Processing file: " + srcFileName);
            logger.info("Source bucket: " + srcBucketName);
            logger.info("Target bucket: " + tgtBucketName);

            // Initialize the GCS client
            Storage storage = StorageOptions.newBuilder()
                .setProjectId(gcsProjectId)
                .build()
                .getService();

            // Get the source file from GCS
            Bucket sourceBucket = storage.get(srcBucketName);
            if (sourceBucket == null) {
                throw new Exception("Source bucket not found: " + srcBucketName);
            }

            Blob sourceBlob = sourceBucket.get(srcFileName);
            if (sourceBlob == null) {
                throw new Exception("Source file not found: " + srcFileName);
            }

            // Read the file content
            byte[] fileBytes = sourceBlob.getContent();
            logger.info("Successfully read file: " + srcFileName + " (" + fileBytes.length + " bytes)");

            // Retrieve the private key from Secret Manager
            logger.info("Retrieving encryption key from Secret Manager...");
            String privateKeyString = accessSecret(gcsProjectId, privateEncryptKeySecretName);
            
            if (privateKeyString == null || privateKeyString.isEmpty()) {
                throw new Exception("Failed to retrieve private key from Secret Manager");
            }

            // Initialize PGP encryptor
            PgpFileEncryptor encryptor = new PgpFileEncryptor();

            // Extract public key from private key
            // (Note: For PGP encryption, we need the public key. If you have a private key,
            // we extract the public key from it. If you already have a public key in Secret Manager,
            // you can skip this step and use it directly)
            logger.info("Extracting public key from private key...");
            String publicKeyString;
            try {
                publicKeyString = encryptor.extractPublicKeyFromPrivateKey(privateKeyString, passphrase);
                logger.info("Successfully extracted public key");
            } catch (Exception e) {
                // If extraction fails, assume the secret already contains a public key
                logger.info("Could not extract public key, assuming secret contains public key directly");
                publicKeyString = privateKeyString;
            }

            // Encrypt the file
            logger.info("Encrypting file...");
            String originalFileName = srcFileName.substring(srcFileName.lastIndexOf('/') + 1);
            byte[] encryptedBytes = encryptor.encryptData(
                fileBytes, 
                publicKeyString, 
                originalFileName, 
                true  // Use ASCII armor
            );
            
            logger.info("Successfully encrypted file (" + encryptedBytes.length + " bytes)");

            // Determine the target file name
            String targetFileName = srcFileName;
            if (!targetFileName.endsWith(".pgp") && !targetFileName.endsWith(".gpg")) {
                targetFileName = targetFileName + ".pgp";
            }

            // Store the encrypted file in the target bucket
            logger.info("Writing encrypted file to target bucket: " + targetFileName);
            BlobId encryptedBlobId = BlobId.of(tgtBucketName, targetFileName);
            BlobInfo blobInfo = BlobInfo.newBuilder(encryptedBlobId)
                .setContentType("application/pgp-encrypted")
                .build();
            
            storage.create(blobInfo, encryptedBytes);
            logger.info("Successfully wrote encrypted file to: " + tgtBucketName + "/" + targetFileName);

            // Optionally delete the source file (only if source and target buckets are different)
            if (!srcBucketName.equals(tgtBucketName) || !srcFileName.equals(targetFileName)) {
                logger.info("Deleting source file: " + srcFileName);
                BlobId sourceBlobId = BlobId.of(srcBucketName, srcFileName);
                boolean deleted = storage.delete(sourceBlobId);
                
                if (deleted) {
                    logger.info("Successfully deleted source file");
                } else {
                    logger.warning("Could not delete source file: " + srcFileName);
                }
            }

            // Send success response
            response.setStatusCode(200);
            response.getWriter().write("File successfully encrypted: " + targetFileName);
            logger.info("Encryption process completed successfully");

        } catch (Exception e) {
            logger.severe("Error during encryption process: " + e.getMessage());
            e.printStackTrace();
            
            response.setStatusCode(500);
            response.getWriter().write("Error: " + e.getMessage());
            throw e;
        }
    }
}
