# Google Cloud Function: File Processing to BigQuery

This Cloud Function automatically processes files uploaded to Google Cloud Storage and loads employee data into BigQuery.

## Features

- **Automatic Trigger**: Triggers on file uploads to Cloud Storage bucket
- **Filename Pattern Extraction**: Extracts codes like `HUM-100`, `HUM-200`, `HUM-300` from filenames
- **Data Extraction**: Reads employee_id from CSV files
- **BigQuery Integration**: Automatically creates table if needed and loads data

## Architecture

```
Cloud Storage (File Upload)
    ↓
Cloud Function Trigger
    ↓
1. Extract code from filename (HUM-XXX)
2. Read file content from GCS
3. Parse CSV and extract employee_id
4. Load to BigQuery with:
   - employee_id (from file)
   - upload_date (current timestamp)
   - file_name (from GCS)
   - extracted_code (from filename)
```

## BigQuery Table Schema

The function creates/uses a table with the following schema:

| Field | Type | Mode | Description |
|-------|------|------|-------------|
| employee_id | STRING | REQUIRED | Employee ID extracted from file |
| upload_date | TIMESTAMP | REQUIRED | When the file was processed |
| file_name | STRING | REQUIRED | Name of the uploaded file |
| extracted_code | STRING | NULLABLE | Code extracted from filename (e.g., HUM-100) |

## Setup Instructions

### 1. Prerequisites

- Google Cloud Project with billing enabled
- Cloud Storage bucket
- BigQuery dataset
- Service account with appropriate permissions

### 2. Configure the Function

Edit `main.py` and update these constants:

```python
PROJECT_ID = "your-project-id"
DATASET_ID = "your_dataset"
TABLE_ID = "employee_data"
```

### 3. Adjust File Parsing (if needed)

If your file format differs from CSV, modify the `parse_file_content()` function:
- Change delimiter (currently comma)
- Adjust column name for employee_id
- Handle different file formats (JSON, TSV, etc.)

### 4. Adjust Filename Pattern (if needed)

Modify the regex pattern in `extract_code_from_filename()`:

```python
CODE_PATTERN = re.compile(r'(HUM-\d{3})', re.IGNORECASE)
```

For example, to match `EMP-100`:
```python
CODE_PATTERN = re.compile(r'(EMP-\d{3})', re.IGNORECASE)
```

### 5. Deploy the Function

#### Option A: Using gcloud CLI

```bash
gcloud functions deploy process_file_upload \
    --gen2 \
    --runtime=python311 \
    --region=us-central1 \
    --source=. \
    --entry-point=process_file \
    --trigger-bucket=YOUR_BUCKET_NAME \
    --service-account=YOUR_SERVICE_ACCOUNT@PROJECT_ID.iam.gserviceaccount.com \
    --set-env-vars PROJECT_ID=your-project-id,DATASET_ID=your_dataset,TABLE_ID=employee_data
```

#### Option B: Using Cloud Console

1. Go to Cloud Functions in GCP Console
2. Click "Create Function"
3. Select "2nd gen" environment
4. Configure:
   - **Name**: `process_file_upload`
   - **Region**: Choose your region
   - **Trigger**: Cloud Storage
   - **Event type**: Finalize/Create
   - **Bucket**: Select your bucket
5. Runtime: Python 3.11
6. Entry point: `process_file`
7. Upload the code files

### 6. Set Permissions

Ensure the Cloud Function's service account has:

- **Cloud Storage**: `storage.objects.get`, `storage.objects.list`
- **BigQuery**: `bigquery.tables.create`, `bigquery.tables.getData`, `bigquery.tables.updateData`, `bigquery.jobs.create`

You can grant these with:

```bash
PROJECT_ID="your-project-id"
SERVICE_ACCOUNT="your-service-account@${PROJECT_ID}.iam.gserviceaccount.com"

# Storage permissions
gcloud projects add-iam-policy-binding ${PROJECT_ID} \
    --member="serviceAccount:${SERVICE_ACCOUNT}" \
    --role="roles/storage.objectViewer"

# BigQuery permissions
gcloud projects add-iam-policy-binding ${PROJECT_ID} \
    --member="serviceAccount:${SERVICE_ACCOUNT}" \
    --role="roles/bigquery.dataEditor"

gcloud projects add-iam-policy-binding ${PROJECT_ID} \
    --member="serviceAccount:${SERVICE_ACCOUNT}" \
    --role="roles/bigquery.jobUser"
```

## File Format Requirements

### CSV Format Example

```csv
employee_id,name,department
EMP001,John Doe,Engineering
EMP002,Jane Smith,Sales
EMP003,Bob Johnson,Marketing
```

The function expects:
- First row as headers
- Column named `employee_id` (case-insensitive)
- Comma-separated values

## Testing

### Test File Upload

Upload a test file to your Cloud Storage bucket:

```bash
# Create a test CSV file
echo "employee_id,name,department
EMP001,John Doe,Engineering
EMP002,Jane Smith,Sales" > test_HUM-100.csv

# Upload to bucket
gsutil cp test_HUM-100.csv gs://YOUR_BUCKET_NAME/
```

### Check BigQuery

Query the BigQuery table to verify data:

```sql
SELECT *
FROM `your-project-id.your_dataset.employee_data`
ORDER BY upload_date DESC
LIMIT 10;
```

### View Logs

```bash
gcloud functions logs read process_file_upload --gen2 --limit=50
```

## Error Handling

The function includes comprehensive error handling:
- Missing employee_id column → Logs error and fails
- Invalid file format → Logs warning and skips invalid rows
- BigQuery errors → Logs error and fails (retryable)
- Missing code pattern → Processes file but sets extracted_code to NULL

## Monitoring

Monitor the function using:
- Cloud Functions logs in GCP Console
- BigQuery table to verify data loads
- Cloud Monitoring for function metrics

## Cost Optimization

- Function uses streaming inserts (cost-effective for small batches)
- Consider batch processing for large files
- Monitor BigQuery storage and query costs

## Troubleshooting

### Function not triggering
- Check bucket name matches trigger configuration
- Verify function is deployed and active
- Check service account permissions

### Data not appearing in BigQuery
- Check function logs for errors
- Verify table schema matches expectations
- Ensure employee_id column exists in source files

### Pattern not extracted
- Verify filename contains expected pattern (e.g., HUM-100)
- Check regex pattern matches your naming convention
- Review logs for pattern matching warnings

## Environment Variables (Alternative)

Instead of hardcoding, you can use environment variables:

```python
import os

PROJECT_ID = os.environ.get('PROJECT_ID', 'your-project-id')
DATASET_ID = os.environ.get('DATASET_ID', 'your_dataset')
TABLE_ID = os.environ.get('TABLE_ID', 'employee_data')
```

Set them during deployment:
```bash
--set-env-vars PROJECT_ID=xxx,DATASET_ID=yyy,TABLE_ID=zzz
```
