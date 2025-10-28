# Simple PGP Encryption Cloud Function

A simple Google Cloud Function that encrypts files using PGP and stores them in Google Cloud Storage.

## What it does

1. Downloads a file from source GCS bucket
2. Gets PGP private key from Secret Manager
3. Encrypts the file using PGP
4. Uploads encrypted file to target GCS bucket

## Request Format

```json
{
    "Src_Bucket": "source-bucket",
    "Tgt_Bucket": "target-bucket", 
    "Src_File": "path/to/file.txt",
    "Gcs_ProjectID": "your-project-id",
    "passphrase": "your-key-passphrase",
    "Private_encrypt_Key": "PULSE_BYOD_FILE_ENCRYPTION_KEY"
}
```

## Response Format

Success:
```json
{
    "success": true,
    "message": "File encrypted successfully",
    "encryptedFile": "path/to/file.txt.pgp"
}
```

Error:
```json
{
    "success": false,
    "message": "Error: description"
}
```

## Setup

1. Enable APIs:
```bash
gcloud services enable cloudfunctions.googleapis.com
gcloud services enable storage.googleapis.com
gcloud services enable secretmanager.googleapis.com
```

2. Create secret:
```bash
gcloud secrets create PULSE_BYOD_FILE_ENCRYPTION_KEY --data-file=private_key.asc
```

3. Deploy:
```bash
./deploy.sh
```

## Test

```bash
curl -X POST https://REGION-PROJECT.cloudfunctions.net/pgp-file-processor \
  -H 'Content-Type: application/json' \
  -d '{
    "Src_Bucket": "my-source-bucket",
    "Tgt_Bucket": "my-target-bucket",
    "Src_File": "data.csv",
    "Gcs_ProjectID": "my-project",
    "passphrase": "my-passphrase",
    "Private_encrypt_Key": "PULSE_BYOD_FILE_ENCRYPTION_KEY"
  }'
```