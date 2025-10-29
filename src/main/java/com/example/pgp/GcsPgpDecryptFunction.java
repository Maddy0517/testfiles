package com.example.pgp;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.storage.*;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GcsPgpDecryptFunction implements HttpFunction {
    private static final Logger logger = Logger.getLogger(GcsPgpDecryptFunction.class.getName());

    private static String accessSecret(String projectName, String keyName) throws IOException {
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            String secretName = "projects/" + projectName + "/secrets/" + keyName + "/versions/latest";
            SecretPayload secretPayload = client.accessSecretVersion(secretName).getPayload();
            ByteString secretValue = secretPayload.getData();
            return secretValue.toStringUtf8();
        }
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        response.setContentType("text/plain");
        Writer writer = response.getWriter();

        // Required params
        String srcBucketName = request.getFirstQueryParameter("Src_Bucket").orElseThrow(() -> new Exception("Src_Bucket is required"));
        String tgtBucketName = request.getFirstQueryParameter("Tgt_Bucket").orElseThrow(() -> new Exception("Tgt_Bucket is required"));
        String srcFileName = request.getFirstQueryParameter("Src_File").orElseThrow(() -> new Exception("Src_File is required"));
        String gcsProjectId = request.getFirstQueryParameter("Gcs_ProjectID").orElseThrow(() -> new Exception("Gcs_ProjectID is required"));
        String passphrase = request.getFirstQueryParameter("passphrase").orElseThrow(() -> new Exception("passphrase is required"));
        String privateKeySecretName = request.getFirstQueryParameter("Private_encrypt_Key").orElseThrow(() -> new Exception("Private_encrypt_Key is required"));

        logger.info(String.format("Starting PGP decryption for gs://%s/%s -> bucket %s", srcBucketName, srcFileName, tgtBucketName));

        // Init clients
        Storage storage = StorageOptions.newBuilder().setProjectId(gcsProjectId).build().getService();

        // Fetch encrypted file
        Bucket sourceBucket = storage.get(srcBucketName);
        if (sourceBucket == null) {
            throw new IllegalArgumentException("Source bucket not found: " + srcBucketName);
        }
        Blob encryptedBlob = sourceBucket.get(srcFileName);
        if (encryptedBlob == null) {
            throw new IllegalArgumentException("Source file not found: " + srcFileName);
        }
        byte[] encryptedBytes = encryptedBlob.getContent();

        // Fetch private key from Secret Manager (expected ASCII-armored PGP private key)
        String privateKeyArmored = accessSecret(gcsProjectId, privateKeySecretName);
        if (privateKeyArmored == null || privateKeyArmored.trim().isEmpty()) {
            throw new IllegalStateException("Private key from Secret Manager is empty");
        }

        // Decrypt
        PgpFileDecryptor decryptor = new PgpFileDecryptor();
        byte[] decryptedBytes = decryptor.decryptBytes(encryptedBytes, privateKeyArmored, passphrase.toCharArray());

        // Determine output name
        String outputObjectName = deriveOutputName(srcFileName);

        // Write decrypted content to target bucket
        Bucket targetBucket = storage.get(tgtBucketName);
        if (targetBucket == null) {
            throw new IllegalArgumentException("Target bucket not found: " + tgtBucketName);
        }
        BlobId decryptedBlobId = BlobId.of(tgtBucketName, outputObjectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(decryptedBlobId).setContentType("application/octet-stream").build();
        storage.create(blobInfo, decryptedBytes);

        // Optionally delete the original encrypted file
        try {
            storage.delete(BlobId.of(srcBucketName, srcFileName));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to delete source encrypted blob: " + srcFileName, e);
        }

        writer.write(String.format("Decrypted %s to %s in bucket %s", srcFileName, outputObjectName, tgtBucketName));
        logger.info(String.format("Decryption complete: gs://%s/%s -> gs://%s/%s", srcBucketName, srcFileName, tgtBucketName, outputObjectName));
    }

    private static String deriveOutputName(String srcFileName) {
        // Goal: original base name + "_decrypted" before original extension.
        // Example: xyz_20251028.csv.pgp -> xyz_20251028_decrypted.csv

        // Preserve path prefix if present
        int lastSlash = srcFileName.lastIndexOf('/') + 1; // 0 if none, else index after '/'
        String dirPrefix = lastSlash > 0 ? srcFileName.substring(0, lastSlash) : "";
        String fileNameOnly = lastSlash > 0 ? srcFileName.substring(lastSlash) : srcFileName;

        // Strip PGP layer extension first
        String lower = fileNameOnly.toLowerCase();
        if (lower.endsWith(".pgp") || lower.endsWith(".gpg") || lower.endsWith(".asc")) {
            fileNameOnly = fileNameOnly.substring(0, fileNameOnly.lastIndexOf('.'));
        }

        // Insert _decrypted before the original (now exposed) extension if any
        int lastDot = fileNameOnly.lastIndexOf('.');
        String outputName;
        if (lastDot > 0) {
            String base = fileNameOnly.substring(0, lastDot);
            String ext = fileNameOnly.substring(lastDot); // includes dot
            outputName = base + "_decrypted" + ext;
        } else {
            // No extension; just append suffix
            outputName = fileNameOnly + "_decrypted";
        }

        return dirPrefix + outputName;
    }
}
