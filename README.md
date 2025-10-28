# File Encryption Google Cloud Function

This Google Cloud Function encrypts files stored in Google Cloud Storage (GCS) buckets using AES encryption. It's designed to be called from Airflow/Google Cloud Composer DAG files with parameters passed via HTTP query parameters.

## Features

- **File Encryption**: Encrypts files using AES-256 encryption in ECB mode with PKCS7 padding
- **Secret Management**: Retrieves encryption keys from Google Secret Manager
- **GCS Integration**: Reads from source bucket, stores encrypted files in target bucket
- **Automatic Cleanup**: Deletes source files after successful encryption
- **Parameter Validation**: Validates all required parameters before processing
- **Comprehensive Logging**: Detailed logging for monitoring and debugging

## Architecture

The function follows the same structure as the provided Java example:

1. **Parameter Extraction**: Gets parameters from HTTP query string
2. **Secret Retrieval**: Accesses encryption key from Secret Manager
3. **File Processing**: Downloads, encrypts, and uploads files
4. **Cleanup**: Removes source files after successful encryption

## Deployment

### Prerequisites

- Google Cloud Project with the following APIs enabled:
  - Cloud Functions API
  - Cloud Storage API
  - Secret Manager API
- Service account with appropriate permissions:
  - Storage Object Admin (for source and target buckets)
  - Secret Manager Secret Accessor (for encryption keys)

### Deploy to Google Cloud Functions

```bash
# Deploy the function
gcloud functions deploy encrypt-file \
    --source . \
    --entry-point encrypt_file \
    --runtime python311 \
    --trigger-http \
    --allow-unauthenticated \
    --memory 512MB \
    --timeout 540s \
    --project YOUR_PROJECT_ID
```

## Usage

### HTTP Request Parameters

The function expects the following query parameters:

| Parameter | Required | Description |
|-----------|----------|-------------|
| `Src_Bucket` | Yes | Source GCS bucket name |
| `Tgt_Bucket` | Yes | Target GCS bucket name |
| `Src_File` | Yes | Source file path in the bucket |
| `Gcs_ProjectID` | Yes | GCP project ID |
| `Encrypt_Key` | Yes | Name of the encryption key in Secret Manager |

### Example Usage

#### From Airflow/Cloud Composer

```python
from airflow import DAG
from airflow.providers.http.operators.http import SimpleHttpOperator
from datetime import datetime

dag = DAG(
    'file_encryption_dag',
    start_date=datetime(2024, 1, 1),
    schedule_interval=None
)

encrypt_task = SimpleHttpOperator(
    task_id='encrypt_file',
    http_conn_id='cloud_function_conn',
    endpoint='/encrypt-file',
    data={
        'Src_Bucket': 'my-source-bucket',
        'Tgt_Bucket': 'my-target-bucket',
        'Src_File': 'data/survey_data.csv',
        'Gcs_ProjectID': 'my-project-id',
        'Encrypt_Key': 'survey-encryption-key'
    },
    method='GET',
    dag=dag
)
```

#### Direct HTTP Call

```bash
curl "https://YOUR_REGION-YOUR_PROJECT.cloudfunctions.net/encrypt-file?Src_Bucket=source-bucket&Tgt_Bucket=target-bucket&Src_File=data.csv&Gcs_ProjectID=your-project&Encrypt_Key=encryption-key-name"
```

## Local Development

### Setup

```bash
# Install dependencies
pip install -r requirements.txt

# Run locally
python main.py
```

### Testing

```bash
# Test the function locally
curl "http://localhost:8080/?Src_Bucket=test-source&Tgt_Bucket=test-target&Src_File=test.txt&Gcs_ProjectID=your-project&Encrypt_Key=test-key"
```

## Security Considerations

1. **Encryption Keys**: Store encryption keys in Google Secret Manager
2. **IAM Permissions**: Use least privilege principle for service accounts
3. **Network Security**: Consider using VPC connectors for additional security
4. **Audit Logging**: Enable Cloud Audit Logs for compliance

## Error Handling

The function includes comprehensive error handling for:

- Missing required parameters
- File not found in source bucket
- Secret Manager access failures
- Encryption failures
- GCS operation failures

## Monitoring

Monitor the function using:

- **Cloud Functions Logs**: View execution logs in Google Cloud Console
- **Cloud Monitoring**: Set up alerts for function failures
- **Error Reporting**: Automatic error detection and reporting

## Differences from Java Version

This Python implementation maintains the same functionality as the Java version with these adaptations:

1. **Framework**: Uses Flask instead of Java HTTP functions
2. **Libraries**: Uses `cryptography` library for AES encryption
3. **Error Handling**: Python-style exception handling
4. **Logging**: Uses Python logging module
5. **Type Hints**: Includes type annotations for better code documentation

## Dependencies

- `google-cloud-storage`: GCS operations
- `google-cloud-secret-manager`: Secret retrieval
- `cryptography`: AES encryption
- `flask`: HTTP request handling
- `functions-framework`: Local testing support