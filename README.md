# GCS to BigQuery Cloud Function

This Cloud Function automatically processes files uploaded to Google Cloud Storage and loads them into BigQuery with additional metadata.

## Features

- **Automatic Triggering**: Triggered automatically when a file is uploaded to a GCS bucket
- **Pattern Extraction**: Extracts patterns like HUM-100, HUM-200, HUM-300 from filenames
- **Metadata Enrichment**: Adds upload date, filename, and extracted pattern to each row
- **Multiple File Formats**: Supports CSV, JSON, and Excel files
- **Error Handling**: Comprehensive error handling and logging

## Data Flow

1. File uploaded to GCS bucket → `employee_data_HUM-100.csv`
2. Cloud Function triggered
3. Extract pattern from filename → `HUM-100`
4. Read file and extract employee data
5. Add metadata columns:
   - `employee_id` (from file)
   - `upload_date` (when file was uploaded)
   - `file_name` (name of the file)
   - `file_pattern` (extracted pattern like HUM-100)
6. Load to BigQuery table

## Prerequisites

- Google Cloud Project with billing enabled
- Google Cloud Storage bucket
- BigQuery dataset and table
- Required IAM permissions:
  - `roles/storage.objectViewer` (to read from GCS)
  - `roles/bigquery.dataEditor` (to write to BigQuery)
  - `roles/bigquery.jobUser` (to run BigQuery jobs)

## BigQuery Table Schema

Your BigQuery table should have at least these columns:

```sql
CREATE TABLE `your-project.your_dataset.employee_data` (
  employee_id STRING,
  upload_date TIMESTAMP,
  file_name STRING,
  file_pattern STRING,
  -- Add other columns from your file here
  -- The function uses autodetect, so it will add columns automatically
);
```

## File Format Requirements

Your uploaded files should:
1. Contain an `employee_id` column (or configure a different column name)
2. Be in CSV, JSON, or Excel format
3. Include the pattern (e.g., HUM-100) in the filename

### Example File Naming

- ✅ `employee_data_HUM-100.csv`
- ✅ `payroll_HUM-200_2024.xlsx`
- ✅ `HUM-300_employees.json`
- ❌ `employees.csv` (no pattern)

### Example File Content (CSV)

```csv
employee_id,employee_name,department,salary
E001,John Doe,Engineering,75000
E002,Jane Smith,Marketing,68000
E003,Bob Johnson,Sales,72000
```

## Deployment

### Step 1: Prepare Your Environment

```bash
# Set your project ID
export PROJECT_ID="your-project-id"
export REGION="us-central1"
export BUCKET_NAME="your-bucket-name"
export DATASET_ID="your_dataset"
export TABLE_ID="employee_data"

# Set the project
gcloud config set project $PROJECT_ID
```

### Step 2: Create GCS Bucket (if not exists)

```bash
gsutil mb -l $REGION gs://$BUCKET_NAME
```

### Step 3: Create BigQuery Dataset and Table (if not exists)

```bash
# Create dataset
bq mk --dataset --location=$REGION $PROJECT_ID:$DATASET_ID

# Create table (basic schema - will auto-detect additional columns)
bq mk --table $PROJECT_ID:$DATASET_ID.$TABLE_ID \
  employee_id:STRING,upload_date:TIMESTAMP,file_name:STRING,file_pattern:STRING
```

### Step 4: Deploy Cloud Function

```bash
gcloud functions deploy process-file-upload \
  --runtime python311 \
  --trigger-resource $BUCKET_NAME \
  --trigger-event google.storage.object.finalize \
  --entry-point process_file_upload \
  --region $REGION \
  --set-env-vars GCP_PROJECT_ID=$PROJECT_ID,BIGQUERY_DATASET_ID=$DATASET_ID,BIGQUERY_TABLE_ID=$TABLE_ID \
  --memory 512MB \
  --timeout 540s \
  --max-instances 10
```

### Step 5: Test the Function

Upload a test file:

```bash
gsutil cp test_file_HUM-100.csv gs://$BUCKET_NAME/
```

Check the logs:

```bash
gcloud functions logs read process-file-upload --region $REGION --limit 50
```

Verify data in BigQuery:

```bash
bq query --use_legacy_sql=false "SELECT * FROM \`$PROJECT_ID.$DATASET_ID.$TABLE_ID\` LIMIT 10"
```

## Configuration

### Environment Variables

Set these when deploying the Cloud Function:

- `GCP_PROJECT_ID`: Your GCP project ID (required)
- `BIGQUERY_DATASET_ID`: BigQuery dataset ID (required)
- `BIGQUERY_TABLE_ID`: BigQuery table ID (required)
- `EMPLOYEE_ID_COLUMN`: Column name for employee ID (default: "employee_id")

### Custom Pattern Matching

If you need to extract different patterns from filenames, modify the regex in `main.py`:

```python
def extract_pattern_from_filename(filename: str) -> Optional[str]:
    # Current pattern: HUM-100, HUM-200, etc.
    pattern = r'(HUM-\d+)'
    
    # Example alternatives:
    # pattern = r'(DEPT-[A-Z]+)'  # Match DEPT-SALES, DEPT-HR, etc.
    # pattern = r'(LOC-\w+)'       # Match LOC-NYC, LOC-LA, etc.
    
    match = re.search(pattern, filename, re.IGNORECASE)
    return match.group(1).upper() if match else None
```

## Local Testing

1. Install dependencies:

```bash
pip install -r requirements.txt
```

2. Set up authentication:

```bash
gcloud auth application-default login
```

3. Set environment variables:

```bash
export GCP_PROJECT_ID="your-project-id"
export BIGQUERY_DATASET_ID="your_dataset"
export BIGQUERY_TABLE_ID="employee_data"
```

4. Modify the `main()` function in `main.py` with your test data

5. Run locally:

```bash
python main.py
```

## Monitoring and Troubleshooting

### View Logs

```bash
# View recent logs
gcloud functions logs read process-file-upload --region $REGION --limit 100

# Stream logs
gcloud functions logs read process-file-upload --region $REGION --follow
```

### Common Issues

1. **Missing employee_id column**
   - Ensure your file contains the column specified in `EMPLOYEE_ID_COLUMN`
   - Check column names match exactly (case-sensitive)

2. **Pattern not found in filename**
   - Verify filename contains pattern like HUM-100
   - Check regex pattern in `extract_pattern_from_filename()`

3. **Permission errors**
   - Verify Cloud Function service account has necessary permissions
   - Check BigQuery dataset and GCS bucket permissions

4. **File format errors**
   - Ensure file is valid CSV, JSON, or Excel
   - Check file encoding (UTF-8 recommended)

### View Function Details

```bash
gcloud functions describe process-file-upload --region $REGION
```

## Cost Optimization

- Set appropriate memory limits (512MB is usually sufficient)
- Configure max instances to control concurrency
- Set reasonable timeout values (540s default)
- Use lifecycle policies to archive/delete processed files

## Security Best Practices

1. Use service accounts with minimal permissions
2. Enable VPC Service Controls if needed
3. Use customer-managed encryption keys (CMEK) for sensitive data
4. Enable audit logging
5. Implement data validation and sanitization

## Advanced Features

### Processing Only Specific Files

Add a filter in the function to process only certain files:

```python
def process_file_upload(event: Dict[str, Any], context: Any) -> None:
    file_name = event['name']
    
    # Skip files that don't match criteria
    if not file_name.startswith('employee_'):
        logger.info(f"Skipping file: {file_name}")
        return
    
    # Rest of the processing...
```

### Dead Letter Queue

Configure a Pub/Sub topic for failed events:

```bash
gcloud functions deploy process-file-upload \
  ... \
  --retry \
  --dead-letter-topic projects/$PROJECT_ID/topics/failed-file-processing
```

### Custom Schema Mapping

Modify `prepare_data_for_bigquery()` to map or transform columns as needed.

## Support

For issues or questions:
1. Check Cloud Function logs
2. Verify BigQuery table schema
3. Review file format and content
4. Check IAM permissions

## License

MIT License
