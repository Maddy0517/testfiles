# GCS to BigQuery File Processing Cloud Function

This Google Cloud Function automatically processes files uploaded to a Google Cloud Storage bucket and loads data into BigQuery.

## Features

- **Automatic Trigger**: Triggers when files are uploaded to a GCS bucket
- **Filename Pattern Extraction**: Extracts patterns like `HUM-100`, `HUM-200`, `HUM-300` from filenames
- **Data Extraction**: Reads `employee_id` column from uploaded files
- **BigQuery Integration**: Automatically inserts records with:
  - `employee_id` (from file data)
  - `upload_date` (when file was uploaded)
  - `file_name` (name of the uploaded file)
  - `extracted_pattern` (pattern extracted from filename, e.g., HUM-100)

## Prerequisites

1. Google Cloud Project with billing enabled
2. BigQuery API enabled
3. Cloud Storage API enabled
4. Cloud Functions API enabled
5. Service account with appropriate permissions:
   - BigQuery Data Editor
   - Storage Object Viewer
   - Cloud Functions Invoker

## Configuration

Update the following constants in `main.py`:

```python
PROJECT_ID = "your-project-id"  # Your GCP project ID
DATASET_ID = "your_dataset"  # Your BigQuery dataset ID
TABLE_ID = "employee_uploads"  # Your BigQuery table ID
EMPLOYEE_ID_COLUMN = "employee_id"  # Column name in file containing employee_id
```

## File Format Support

The function supports:
- **CSV files**: With header row containing `employee_id` column
- **Text files**: Space-separated or tab-separated values (assumes first column is employee_id)

### Example CSV Format:
```csv
employee_id,name,department
EMP001,John Doe,Engineering
EMP002,Jane Smith,Sales
```

### Example Text Format:
```
EMP001 John Doe Engineering
EMP002 Jane Smith Sales
```

## Filename Pattern Extraction

The function extracts patterns matching `HUM-XXX` format (case-insensitive) from filenames.

Examples:
- `data_HUM-100_20240101.csv` → extracts `HUM-100`
- `report_HUM-200.xlsx` → extracts `HUM-200`
- `file_HUM-300.txt` → extracts `HUM-300`

To modify the pattern, edit the `extract_pattern_from_filename()` function in `main.py`.

## BigQuery Table Schema

The function automatically creates the following table schema if it doesn't exist:

| Column Name | Type | Mode | Description |
|------------|------|------|-------------|
| employee_id | STRING | REQUIRED | Employee ID from file |
| upload_date | DATE | REQUIRED | Date when file was uploaded |
| file_name | STRING | REQUIRED | Name of the uploaded file |
| extracted_pattern | STRING | NULLABLE | Pattern extracted from filename (e.g., HUM-100) |

## Deployment

### Option 1: Deploy using gcloud CLI

```bash
# Set your project
gcloud config set project YOUR_PROJECT_ID

# Deploy the function
gcloud functions deploy process_file_uploads \
  --runtime python311 \
  --trigger-bucket YOUR_BUCKET_NAME \
  --entry-point process_file_upload \
  --source . \
  --timeout 540s \
  --memory 512MB \
  --service-account YOUR_SERVICE_ACCOUNT@YOUR_PROJECT.iam.gserviceaccount.com
```

### Option 2: Deploy using Cloud Console

1. Go to Cloud Functions in Google Cloud Console
2. Click "Create Function"
3. Configure:
   - **Name**: `process_file_uploads`
   - **Region**: Choose your preferred region
   - **Trigger**: Cloud Storage
   - **Event type**: Finalize/Create
   - **Bucket**: Select your bucket
4. **Runtime**: Python 3.11
5. **Entry point**: `process_file_upload`
6. **Source code**: Upload the `main.py` and `requirements.txt` files
7. **Service account**: Select service account with BigQuery and Storage permissions

## Testing Locally

You can test the function locally before deploying:

```bash
# Install dependencies
pip install -r requirements.txt

# Run the function
python main.py
```

Or use the Functions Framework:

```bash
pip install functions-framework

functions-framework --target=process_file_upload --debug
```

## Monitoring and Logging

- View logs in Cloud Functions console
- Check BigQuery table for inserted records
- Monitor function execution in Cloud Monitoring

## Error Handling

The function includes error handling for:
- Missing bucket or file name in event
- Empty files
- Missing employee_id column in CSV files
- BigQuery insertion errors

All errors are logged for debugging.

## Cost Optimization

- The function only processes files when uploaded (event-driven)
- Uses efficient BigQuery batch inserts
- Minimal memory footprint (512MB default)

## Security Considerations

1. Use least-privilege IAM roles for the service account
2. Enable VPC connector if accessing private resources
3. Consider using Secret Manager for sensitive configuration
4. Enable audit logs for compliance

## Troubleshooting

### Function not triggering
- Check bucket notification configuration
- Verify function has Storage Object Viewer permission
- Check function logs for errors

### BigQuery insertion fails
- Verify service account has BigQuery Data Editor role
- Check table schema matches expected format
- Verify dataset exists

### Pattern not extracted
- Check filename format matches expected pattern
- Modify regex in `extract_pattern_from_filename()` if needed

## Customization

### Change Pattern Extraction
Modify the regex in `extract_pattern_from_filename()`:
```python
pattern = r'([A-Z]{3}-\d+)'  # Matches any 3 letters followed by dash and numbers
```

### Add More Columns
Update the schema in `create_table_if_not_exists()` and modify `insert_to_bigquery()` to include additional fields.

### Change File Parsing Logic
Modify `parse_file_content()` to support different file formats (JSON, Excel, etc.).

## License

This code is provided as-is for demonstration purposes.
