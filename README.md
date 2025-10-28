# PGP Cloud Function

A Google Cloud Function that decrypts PGP files from Google Cloud Storage and stores the decrypted files back to GCS.

## Overview

This Cloud Function performs the following operations:
1. Downloads an encrypted PGP file from a source GCS bucket
2. Decrypts the file using PGP decryption with a private key (retrieved from Secret Manager) and passphrase
3. Uploads the decrypted file to a target GCS bucket

## Prerequisites

- Google Cloud Project with the following APIs enabled:
  - Cloud Functions API
  - Cloud Storage API
  - Secret Manager API
- Google Cloud SDK installed and configured
- Maven installed for building the project
- A PGP private key stored in Google Cloud Secret Manager

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
# Create the secret
gcloud secrets create PULSE_BYOD_FILE_ENCRYPTION_KEY --data-file=path/to/your/private_key.asc

# Or create it and add the key content
echo "YOUR_PRIVATE_KEY_CONTENT" | gcloud secrets create PULSE_BYOD_FILE_ENCRYPTION_KEY --data-file=-
```

### 3. Set up Service Account Permissions

The Cloud Function needs the following IAM roles:
- `roles/storage.objectAdmin` (for GCS operations)
- `roles/secretmanager.secretAccessor` (for accessing secrets)

```bash
# Get the default compute service account
PROJECT_ID=$(gcloud config get-value project)
SERVICE_ACCOUNT="$PROJECT_ID@appspot.gserviceaccount.com"

# Grant necessary permissions
gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:$SERVICE_ACCOUNT" \
    --role="roles/storage.objectAdmin"

gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:$SERVICE_ACCOUNT" \
    --role="roles/secretmanager.secretAccessor"
```

## Deployment

### Using the deployment script (recommended):

```bash
./deploy.sh
```

### Manual deployment:

```bash
# Build the project
mvn clean compile

# Deploy the function
gcloud functions deploy pgp-file-processor \
    --gen2 \
    --runtime=java11 \
    --region=us-central1 \
    --source=. \
    --entry-point=com.example.pgp.PgpCloudFunction \
    --memory=512MB \
    --timeout=540s \
    --trigger=http \
    --allow-unauthenticated
```

## Usage

### Request Format

Send a POST request to the Cloud Function endpoint with the following JSON payload:

```json
{
    "Src_Bucket": "source-bucket-name",
    "Tgt_Bucket": "target-bucket-name", 
    "Src_File": "path/to/encrypted/file.pgp",
    "Gcs_ProjectID": "your-project-id",
    "passphrase": "your-pgp-key-passphrase",
    "Private_encrypt_Key": "PULSE_BYOD_FILE_ENCRYPTION_KEY"
}
```

### Parameters

- `Src_Bucket`: Name of the GCS bucket containing the encrypted file
- `Tgt_Bucket`: Name of the GCS bucket where the re-encrypted file will be stored
- `Src_File`: Path to the encrypted file within the source bucket
- `Gcs_ProjectID`: Google Cloud Project ID
- `passphrase`: Passphrase for the PGP private key
- `Private_encrypt_Key`: Name of the secret in Secret Manager containing the private key

### Example Request

```bash
curl -X POST https://us-central1-your-project.cloudfunctions.net/pgp-file-processor \
  -H 'Content-Type: application/json' \
  -d '{
    "Src_Bucket": "my-source-bucket",
    "Tgt_Bucket": "my-target-bucket",
    "Src_File": "encrypted-files/data.pgp",
    "Gcs_ProjectID": "my-project-id",
    "passphrase": "my-secret-passphrase",
    "Private_encrypt_Key": "PULSE_BYOD_FILE_ENCRYPTION_KEY"
  }'
```

### Response Format

Success response:
```json
{
    "success": true,
    "message": "File processed successfully",
    "processedFile": "data_decrypted"
}
```

Error response:
```json
{
    "success": false,
    "message": "Error",
    "error": "Error description"
}
```

## File Processing Flow

1. **Download**: The function downloads the encrypted PGP file from the source GCS bucket
2. **Decrypt**: The file is decrypted using the PGP private key and passphrase
3. **Upload**: The decrypted file is uploaded to the target GCS bucket with "_decrypted" suffix

## Error Handling

The function includes comprehensive error handling for:
- Invalid request parameters
- Missing files in GCS
- PGP decryption failures
- Secret Manager access issues
- GCS upload/download failures

## Monitoring and Logging

View function logs:
```bash
gcloud functions logs read pgp-file-processor --region=us-central1
```

## Security Considerations

- The function requires appropriate IAM permissions for GCS and Secret Manager
- Private keys are securely stored in Secret Manager
- Passphrases are passed as request parameters (consider using Secret Manager for these as well in production)
- The function can be configured to require authentication by removing `--allow-unauthenticated`

## Troubleshooting

### Common Issues

1. **Permission Denied**: Ensure the service account has the required IAM roles
2. **File Not Found**: Verify the source bucket and file path are correct
3. **Secret Access Error**: Check that the secret exists and the service account can access it
4. **Memory Issues**: Increase the memory allocation for large files
5. **Timeout**: Increase the timeout for processing large files

### Debug Mode

Add debug logging by setting the log level in the function configuration or add more detailed logging in the code.

## Dependencies

The project uses the following main dependencies:
- Bouncy Castle PGP libraries for decryption
- Google Cloud Storage client library
- Google Cloud Secret Manager client library
- Google Cloud Functions Framework

See `pom.xml` for the complete list of dependencies and versions.