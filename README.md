# PGP GCS Cloud Function

A Google Cloud Function that decrypts PGP-encrypted files stored in Google Cloud Storage using private keys from Secret Manager.

## Overview

This Cloud Function processes **encrypted PGP files** stored in Google Cloud Storage:

1. **Input**: Downloads an **encrypted PGP file** from a source GCS bucket
2. **Key Retrieval**: Retrieves the **private key** from Google Cloud Secret Manager
3. **Decryption**: **Decrypts the encrypted file** using the private key and provided passphrase
4. **Output**: Uploads the **decrypted file** to a target GCS bucket

**Flow**: Encrypted File (GCS) → Decrypt with Private Key → Decrypted File (GCS)

## Prerequisites

- Google Cloud Project with the following APIs enabled:
  - Cloud Functions API
  - Cloud Storage API
  - Secret Manager API
- Google Cloud SDK (`gcloud`) installed and authenticated
- Java 11 or higher
- Maven 3.6 or higher

## Setup

### 1. Enable Required APIs

```bash
gcloud services enable cloudfunctions.googleapis.com
gcloud services enable storage.googleapis.com
gcloud services enable secretmanager.googleapis.com
```

### 2. Create Secret in Secret Manager

Store your PGP private key in Secret Manager:

```bash
# Create secret from file
gcloud secrets create PULSE_BYOD_FILE_ENCRYPTION_KEY --data-file=path/to/your/private-key.asc

# Or create secret from stdin
echo "-----BEGIN PGP PRIVATE KEY BLOCK-----
...your private key content...
-----END PGP PRIVATE KEY BLOCK-----" | gcloud secrets create PULSE_BYOD_FILE_ENCRYPTION_KEY --data-file=-
```

### 3. Set up Service Account (Optional)

Create a service account with necessary permissions:

```bash
# Create service account
gcloud iam service-accounts create pgp-processor-sa \
    --display-name="PGP Processor Service Account"

# Grant necessary permissions
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
    --member="serviceAccount:pgp-processor-sa@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/storage.objectAdmin"

gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
    --member="serviceAccount:pgp-processor-sa@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor"
```

## Deployment

### Method 1: Using the deployment script

1. Set your project ID:
   ```bash
   export GCP_PROJECT_ID=your-project-id
   ```

2. Run the deployment script:
   ```bash
   ./deploy.sh
   ```

### Method 2: Manual deployment

```bash
# Build the project
mvn clean package

# Deploy the function
gcloud functions deploy pgp-gcs-processor \
    --gen2 \
    --runtime=java11 \
    --region=us-central1 \
    --source=. \
    --entry-point=com.example.pgp.PgpGcsFunction \
    --memory=512MB \
    --timeout=540s \
    --trigger-http \
    --allow-unauthenticated \
    --project=YOUR_PROJECT_ID
```

## Usage

### Request Format

Send a POST request to the function URL with the following JSON payload:

```json
{
    "Src_Bucket": "xyz",
    "Tgt_Bucket": "xyz",
    "Src_File": "gcsfile path",
    "Gcs_ProjectID": "project_id",
    "passphrase": "pqaddddzxx",
    "Private_encrypt_Key": "PULSE_BYOD_FILE_ENCRYPTION_KEY"
}
```

### Parameters

- `Src_Bucket`: Name of the source GCS bucket containing the encrypted file
- `Tgt_Bucket`: Name of the target GCS bucket where the decrypted file will be stored
- `Src_File`: Path to the encrypted file within the source bucket
- `Gcs_ProjectID`: Google Cloud Project ID
- `passphrase`: Passphrase for the private key
- `Private_encrypt_Key`: Name of the secret in Secret Manager containing the private key

### Example Request

```bash
curl -X POST \
  https://us-central1-project_id.cloudfunctions.net/pgp-gcs-processor \
  -H "Content-Type: application/json" \
  -d '{
    "Src_Bucket": "xyz",
    "Tgt_Bucket": "xyz",
    "Src_File": "gcsfile path",
    "Gcs_ProjectID": "project_id",
    "passphrase": "pqaddddzxx",
    "Private_encrypt_Key": "PULSE_BYOD_FILE_ENCRYPTION_KEY"
  }'
```

### Response Format

#### Success Response (200)
```json
{
    "status": "success",
    "message": "File processed successfully",
    "processedFile": "data/encrypted-file.decrypted"
}
```

#### Error Response (400/500)
```json
{
    "status": "error",
    "message": "Error description"
}
```

## File Naming Convention

The function automatically generates output file names:
- If the input file ends with `.pgp`, it removes this extension
- Adds `.decrypted` extension to the result
- Example: `data.csv.pgp` → `data.csv.decrypted`

## Security Considerations

1. **Private Key Storage**: Private keys are securely stored in Google Cloud Secret Manager
2. **Access Control**: Use IAM to control access to the function and secrets
3. **Network Security**: Consider using VPC connectors for enhanced network security
4. **Audit Logging**: Enable Cloud Audit Logs to track function invocations

## Monitoring and Logging

- Function logs are available in Google Cloud Logging
- Monitor function performance in Google Cloud Monitoring
- Set up alerts for function failures or performance issues

## Troubleshooting

### Common Issues

1. **"Secret not found"**: Ensure the secret exists in Secret Manager and the function has access
2. **"Source file not found"**: Verify the bucket name and file path are correct
3. **"PGP decryption failed"**: Check that the private key matches the encrypted file and passphrase is correct
4. **"Permission denied"**: Ensure the function's service account has necessary permissions

### Debug Mode

To enable more detailed logging, you can modify the logging level in the function code or check Cloud Logging for detailed error messages.

## Development

### Local Testing

To test the PGP processing logic locally:

```bash
# Compile the project
mvn compile

# Run tests (if any)
mvn test
```

### Dependencies

The function uses the following key dependencies:
- Bouncy Castle for PGP operations
- Google Cloud Storage client library
- Google Cloud Secret Manager client library
- Google Cloud Functions Framework

## License

This project is licensed under the MIT License - see the LICENSE file for details.