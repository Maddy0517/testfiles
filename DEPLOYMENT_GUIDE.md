# Quick Deployment Guide

## Overview

This Cloud Function encrypts decrypted files in Google Cloud Storage using PGP encryption. The encrypted files are then stored back in GCS.

## Files Created

1. **PgpFileEncryptor.java** - Core PGP encryption utility
2. **PgpEncryptionFunction.java** - Cloud Function implementation
3. **pom.xml** - Maven dependencies and build configuration
4. **deploy.sh** - Automated deployment script
5. **example_airflow_dag.py** - Example Airflow DAG for integration
6. **.gcloudignore** - Files to exclude from deployment

## Quick Start

### 1. Prerequisites

```bash
# Install gcloud CLI if not already installed
# https://cloud.google.com/sdk/docs/install

# Authenticate
gcloud auth login

# Set your project
export GCP_PROJECT_ID="your-project-id"
```

### 2. Store Your PGP Key in Secret Manager

```bash
# Option 1: From a file
gcloud secrets create PULSE_BYOD_FILE_ENCRYPTION_KEY \
    --data-file=your_pgp_key.asc \
    --project=$GCP_PROJECT_ID

# Option 2: From stdin
cat your_pgp_key.asc | gcloud secrets create PULSE_BYOD_FILE_ENCRYPTION_KEY \
    --data-file=- \
    --project=$GCP_PROJECT_ID
```

**Important Notes:**
- You can store either a **private key** or **public key** in Secret Manager
- If you store a **private key**, the function will extract the public key from it
- For encryption, only the **public key** is actually used
- The passphrase is needed if your private key is password-protected

### 3. Deploy the Function

#### Option A: Using the deployment script (recommended)

```bash
# Make the script executable (already done)
chmod +x deploy.sh

# Deploy
export GCP_PROJECT_ID="your-project-id"
export SERVICE_ACCOUNT="your-service-account@your-project.iam.gserviceaccount.com"  # Optional
./deploy.sh
```

#### Option B: Manual deployment

```bash
gcloud functions deploy pgp-encryption-function \
    --gen2 \
    --runtime=java11 \
    --region=us-central1 \
    --source=. \
    --entry-point=com.example.pgp.PgpEncryptionFunction \
    --trigger-http \
    --allow-unauthenticated \
    --memory=512MB \
    --timeout=540s \
    --project=$GCP_PROJECT_ID
```

**For production:** Remove `--allow-unauthenticated` and add:
```bash
--service-account=your-service-account@your-project.iam.gserviceaccount.com
```

### 4. Test the Function

```bash
# Get the function URL
FUNCTION_URL=$(gcloud functions describe pgp-encryption-function \
    --region=us-central1 \
    --gen2 \
    --format="value(serviceConfig.uri)" \
    --project=$GCP_PROJECT_ID)

# Test the function
curl -X POST "${FUNCTION_URL}?Src_Bucket=source-bucket&Tgt_Bucket=target-bucket&Src_File=path/to/file.csv&Gcs_ProjectID=${GCP_PROJECT_ID}&passphrase=your-passphrase&Private_encrypt_Key=PULSE_BYOD_FILE_ENCRYPTION_KEY" \
  -H "Authorization: Bearer $(gcloud auth print-identity-token)"
```

### 5. Integrate with Airflow

1. Copy `example_airflow_dag.py` to your Airflow DAGs folder
2. Update the configuration variables:
   - `GCP_PROJECT_ID`
   - `CLOUD_FUNCTION_NAME`
   - `SOURCE_BUCKET`
   - `TARGET_BUCKET`
   - `SOURCE_FILE_PATH`
   - `PGP_KEY_PASSPHRASE`
3. Trigger the DAG manually or on a schedule

## Required IAM Permissions

The Cloud Function's service account needs:

```
# Secret Manager
secretmanager.versions.access

# Cloud Storage (Source Bucket)
storage.objects.get
storage.objects.delete  # If deleting source files

# Cloud Storage (Target Bucket)
storage.objects.create
```

### Grant permissions:

```bash
# Secret Manager access
gcloud secrets add-iam-policy-binding PULSE_BYOD_FILE_ENCRYPTION_KEY \
    --member="serviceAccount:your-service-account@your-project.iam.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor" \
    --project=$GCP_PROJECT_ID

# Storage access (repeat for each bucket)
gsutil iam ch serviceAccount:your-service-account@your-project.iam.gserviceaccount.com:objectViewer \
    gs://source-bucket

gsutil iam ch serviceAccount:your-service-account@your-project.iam.gserviceaccount.com:objectCreator \
    gs://target-bucket
```

## Input Parameters

| Parameter | Description | Example |
|-----------|-------------|---------|
| `Src_Bucket` | Source bucket name | `my-source-bucket` |
| `Tgt_Bucket` | Target bucket name | `my-target-bucket` |
| `Src_File` | File path in source bucket | `data/file.csv` |
| `Gcs_ProjectID` | GCP Project ID | `my-project-123` |
| `passphrase` | PGP key passphrase | `my-secure-passphrase` |
| `Private_encrypt_Key` | Secret Manager key name | `PULSE_BYOD_FILE_ENCRYPTION_KEY` |

## Expected Behavior

1. Function reads the decrypted file from `Src_Bucket/Src_File`
2. Retrieves PGP key from Secret Manager
3. Encrypts the file using PGP encryption
4. Writes encrypted file to `Tgt_Bucket/Src_File.pgp`
5. Deletes the original file from source (if different location)

## Troubleshooting

### Function deployment fails

```bash
# Check logs
gcloud functions logs read pgp-encryption-function \
    --region=us-central1 \
    --limit=50 \
    --project=$GCP_PROJECT_ID
```

### "Permission denied" errors

- Verify service account has necessary IAM roles
- Check Secret Manager permissions
- Verify bucket permissions

### "Secret not found"

- Confirm secret name matches exactly (case-sensitive)
- Verify secret exists: `gcloud secrets list --project=$GCP_PROJECT_ID`

### "Can't find encryption key in key ring"

- Ensure the PGP key is valid
- Try storing the public key directly instead of private key
- Verify the key has encryption capabilities

## Key Differences from Original Code

This implementation differs from your original Java code in several ways:

1. **Encryption vs Decryption**: 
   - Original: Decrypts files using a private key
   - This: Encrypts files using a public key

2. **Cloud Function**: 
   - Integrated with GCS, Secret Manager, and HTTP triggers
   - Follows the pattern of your existing `GlintSurveyEncryptionFunction`

3. **PGP vs AES**:
   - Original example used AES encryption
   - This uses PGP encryption with Bouncy Castle

4. **Key Handling**:
   - Retrieves keys from Secret Manager
   - Automatically extracts public key from private key if needed

## Next Steps

1. ✅ Deploy the function
2. ✅ Test with a sample file
3. ✅ Integrate with your Airflow/Composer DAGs
4. ✅ Set up monitoring and alerting
5. ✅ Configure proper authentication (remove `--allow-unauthenticated`)
6. ✅ Review and adjust memory/timeout settings based on file sizes

## Support

For questions or issues:
- Review Cloud Function logs
- Check Google Cloud documentation
- Refer to README.md for detailed information
