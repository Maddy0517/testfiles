# PGP File Encryption Cloud Function

This Google Cloud Function encrypts files in Google Cloud Storage using PGP encryption. It reads a decrypted file from GCS, encrypts it using a PGP public key (stored in Secret Manager), and writes the encrypted file back to GCS.

## Features

- **PGP Encryption**: Uses Bouncy Castle library for robust PGP encryption
- **GCS Integration**: Reads from and writes to Google Cloud Storage buckets
- **Secret Manager**: Securely retrieves encryption keys from Google Cloud Secret Manager
- **Flexible Configuration**: Supports different source and target buckets
- **Automatic File Handling**: Can optionally delete source files after encryption

## Architecture

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────┐
│   Airflow   │────▶│  Cloud Function  │────▶│     GCS     │
│  Composer   │     │  (PGP Encrypt)   │     │  (Target)   │
└─────────────┘     └──────────────────┘     └─────────────┘
                            │
                            ▼
                    ┌──────────────┐
                    │    Secret    │
                    │   Manager    │
                    └──────────────┘
```

## Prerequisites

1. Google Cloud Project with the following APIs enabled:
   - Cloud Functions API
   - Cloud Storage API
   - Secret Manager API
   
2. Service account with appropriate permissions:
   - `cloudfunctions.functions.create`
   - `storage.objects.get` (source bucket)
   - `storage.objects.create` (target bucket)
   - `storage.objects.delete` (source bucket, if deleting)
   - `secretmanager.versions.access`

3. PGP key pair (the public key will be used for encryption)

## Setup

### 1. Store Encryption Key in Secret Manager

First, store your PGP private key (or public key) in Google Cloud Secret Manager:

```bash
# Create a secret with your PGP key
gcloud secrets create PULSE_BYOD_FILE_ENCRYPTION_KEY \
    --data-file=your_pgp_key.asc \
    --project=your-project-id

# Or from stdin
cat your_pgp_key.asc | gcloud secrets create PULSE_BYOD_FILE_ENCRYPTION_KEY \
    --data-file=- \
    --project=your-project-id
```

### 2. Deploy the Cloud Function

#### Using gcloud CLI:

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
    --service-account=your-service-account@your-project.iam.gserviceaccount.com
```

**Note**: Remove `--allow-unauthenticated` for production and use proper authentication.

#### Using Cloud Console:

1. Go to Cloud Functions in the Google Cloud Console
2. Click "Create Function"
3. Configure:
   - **Name**: `pgp-encryption-function`
   - **Region**: Choose your region
   - **Trigger**: HTTP
   - **Runtime**: Java 11
   - **Entry point**: `com.example.pgp.PgpEncryptionFunction`
   - **Source**: Upload zip or use Cloud Source Repositories
4. Upload the code (all .java files and pom.xml)
5. Deploy

### 3. File Structure

Your deployment should include these files:

```
.
├── pom.xml
├── PgpFileEncryptor.java
├── PgpEncryptionFunction.java
└── README.md
```

## Usage

### Calling from Airflow/Composer

Here's an example of how to call this function from an Airflow DAG:

```python
from airflow import DAG
from airflow.providers.google.cloud.operators.functions import CloudFunctionInvokeFunctionOperator
from datetime import datetime

default_args = {
    'start_date': datetime(2024, 1, 1),
}

dag = DAG(
    'pgp_encryption_workflow',
    default_args=default_args,
    schedule_interval=None
)

encrypt_file_task = CloudFunctionInvokeFunctionOperator(
    task_id='encrypt_gcs_file',
    function_name='pgp-encryption-function',
    location='us-central1',
    input_data={
        'Src_Bucket': 'my-source-bucket',
        'Tgt_Bucket': 'my-target-bucket',
        'Src_File': 'path/to/decrypted_file.csv',
        'Gcs_ProjectID': 'my-project-id',
        'passphrase': 'your-key-passphrase',
        'Private_encrypt_Key': 'PULSE_BYOD_FILE_ENCRYPTION_KEY'
    },
    dag=dag
)
```

### Direct HTTP Request

You can also call the function directly via HTTP:

```bash
curl -X POST "https://REGION-PROJECT_ID.cloudfunctions.net/pgp-encryption-function?Src_Bucket=my-source-bucket&Tgt_Bucket=my-target-bucket&Src_File=path/to/file.csv&Gcs_ProjectID=my-project-id&passphrase=your-passphrase&Private_encrypt_Key=PULSE_BYOD_FILE_ENCRYPTION_KEY" \
  -H "Authorization: Bearer $(gcloud auth print-identity-token)"
```

## Input Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `Src_Bucket` | String | Yes | Source GCS bucket name containing the decrypted file |
| `Tgt_Bucket` | String | Yes | Target GCS bucket name where encrypted file will be stored |
| `Src_File` | String | Yes | Path to the source file within the bucket (e.g., `folder/file.csv`) |
| `Gcs_ProjectID` | String | Yes | Google Cloud Project ID |
| `passphrase` | String | Yes | Passphrase for the PGP private key (if applicable) |
| `Private_encrypt_Key` | String | Yes | Secret name in Secret Manager containing the PGP key |

## How It Works

1. **Parameter Extraction**: Reads input parameters from HTTP request query parameters
2. **File Retrieval**: Downloads the decrypted file from the source GCS bucket
3. **Key Retrieval**: Fetches the PGP key from Secret Manager
4. **Public Key Extraction**: If a private key is provided, extracts the corresponding public key
5. **Encryption**: Encrypts the file using PGP encryption with the public key
6. **Upload**: Uploads the encrypted file to the target GCS bucket with `.pgp` extension
7. **Cleanup**: Deletes the source file (if source and target locations differ)

## Output

- The encrypted file will be saved with a `.pgp` extension (unless it already has `.pgp` or `.gpg`)
- The file will be marked with content type `application/pgp-encrypted`
- The original decrypted file will be deleted from the source bucket

## Security Best Practices

1. **Use Secret Manager**: Never hardcode encryption keys in the code
2. **Service Account Permissions**: Use least-privilege principle for service accounts
3. **Authentication**: Require authentication for the Cloud Function (remove `--allow-unauthenticated`)
4. **VPC**: Consider deploying in a VPC for additional network security
5. **Logging**: Monitor Cloud Function logs for any security issues

## Troubleshooting

### Common Issues

1. **"Secret key not found"**
   - Verify the secret name in Secret Manager
   - Check service account has `secretmanager.versions.access` permission

2. **"Source bucket not found"**
   - Verify bucket names are correct
   - Check service account has read access to source bucket

3. **"Can't find encryption key in key ring"**
   - Ensure the PGP key in Secret Manager is valid
   - Verify the key has encryption capabilities

4. **Out of Memory**
   - Increase memory allocation for the function (default is 256MB)
   - Use `--memory=512MB` or higher

### Viewing Logs

```bash
gcloud functions logs read pgp-encryption-function \
    --region=us-central1 \
    --limit=50
```

## Local Testing

To test the encryption logic locally (without deploying):

1. Create a test class:

```java
package com.example.pgp;

import java.nio.file.Files;
import java.nio.file.Paths;

public class LocalTest {
    public static void main(String[] args) throws Exception {
        PgpFileEncryptor encryptor = new PgpFileEncryptor();
        
        // Read test file
        byte[] data = Files.readAllBytes(Paths.get("test-file.txt"));
        
        // Read public key
        String publicKey = new String(Files.readAllBytes(Paths.get("public-key.asc")));
        
        // Encrypt
        byte[] encrypted = encryptor.encryptData(data, publicKey, "test-file.txt", true);
        
        // Save encrypted file
        Files.write(Paths.get("test-file.txt.pgp"), encrypted);
        
        System.out.println("Encryption successful!");
    }
}
```

2. Run with Maven:

```bash
mvn compile exec:java -Dexec.mainClass=com.example.pgp.LocalTest
```

## Dependencies

- **Bouncy Castle**: 1.78 - PGP encryption/decryption
- **Google Cloud Storage**: 2.30.1 - GCS operations
- **Google Cloud Secret Manager**: 2.33.0 - Secret retrieval
- **Google Cloud Functions Framework**: 1.1.0 - HTTP function support

## License

This project is provided as-is for use in your organization.

## Support

For issues or questions, please contact your infrastructure team or refer to:
- [Google Cloud Functions Documentation](https://cloud.google.com/functions/docs)
- [Bouncy Castle Documentation](https://www.bouncycastle.org/documentation.html)
- [Google Cloud Storage Documentation](https://cloud.google.com/storage/docs)
