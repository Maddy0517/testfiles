# GCS to BigQuery Cloud Function

A Google Cloud Function that automatically processes files uploaded to Google Cloud Storage and loads metadata to BigQuery.

## Features

- **Automatic Trigger**: Fires when files are uploaded to a GCS bucket
- **Filename Parsing**: Extracts codes like `HUM-100`, `HUM-200`, `HUM-300` from filenames
- **Employee ID Extraction**: Reads `employee_id` column from CSV or JSON file content
- **Metadata Capture**: Records upload date, filename, and bucket information
- **BigQuery Integration**: Automatically loads processed data to BigQuery

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────┐
│   GCS Bucket    │───>│  Cloud Function  │───>│  BigQuery   │
│  (File Upload)  │    │  (Processing)    │    │   Table     │
└─────────────────┘    └──────────────────┘    └─────────────┘
```

## BigQuery Table Schema

| Column        | Type      | Description                           |
|---------------|-----------|---------------------------------------|
| employee_id   | STRING    | Employee ID extracted from file data  |
| upload_date   | TIMESTAMP | When the file was uploaded            |
| file_name     | STRING    | Full filename including path          |
| hum_code      | STRING    | Extracted code (HUM-100, HUM-200, etc)|
| bucket_name   | STRING    | GCS bucket name                       |
| processed_at  | TIMESTAMP | When the function processed the file  |

## Supported File Formats

### CSV Files
```csv
employee_id,name,department
EMP001,John Smith,Engineering
EMP002,Jane Doe,Marketing
```

### JSON Files
```json
{
    "employees": [
        {"employee_id": "EMP001", "name": "John Smith"},
        {"employee_id": "EMP002", "name": "Jane Doe"}
    ]
}
```

Or as a list:
```json
[
    {"employee_id": "EMP001", "name": "John Smith"},
    {"employee_id": "EMP002", "name": "Jane Doe"}
]
```

## Filename Patterns

The function extracts `HUM-XXX` codes from filenames. Examples:

| Filename                          | Extracted Code |
|-----------------------------------|----------------|
| `employees_HUM-100_report.csv`    | HUM-100        |
| `data_HUM-200_quarterly.json`     | HUM-200        |
| `HUM-300_employees.csv`           | HUM-300        |
| `report_2024.csv`                 | UNKNOWN        |

## Prerequisites

1. **Google Cloud SDK** installed and configured
2. **Python 3.11+** (for local testing)
3. **GCP Project** with billing enabled
4. Required **IAM permissions**:
   - Cloud Functions Admin
   - Storage Admin
   - BigQuery Data Editor

## Setup & Deployment

### 1. Configure Environment Variables

```bash
export GCP_PROJECT_ID="your-project-id"
export GCP_REGION="us-central1"
export GCS_BUCKET_NAME="your-file-upload-bucket"
export BQ_DATASET_ID="your_dataset"
export BQ_TABLE_ID="file_uploads"
```

### 2. Run Local Tests

```bash
cd cloud_function
pip install -r requirements.txt
python test_local.py
```

### 3. Deploy

```bash
chmod +x deploy.sh
./deploy.sh
```

### Manual Deployment (Alternative)

```bash
# Enable APIs
gcloud services enable cloudfunctions.googleapis.com
gcloud services enable storage.googleapis.com
gcloud services enable bigquery.googleapis.com

# Create BigQuery dataset and table
bq mk --dataset your-project-id:your_dataset
bq mk --table \
  --schema 'employee_id:STRING,upload_date:TIMESTAMP,file_name:STRING,hum_code:STRING,bucket_name:STRING,processed_at:TIMESTAMP' \
  your-project-id:your_dataset.file_uploads

# Deploy function
gcloud functions deploy process-file-uploads \
    --gen2 \
    --runtime=python311 \
    --region=us-central1 \
    --source=. \
    --entry-point=main \
    --trigger-event-filters="type=google.cloud.storage.object.v1.finalized" \
    --trigger-event-filters="bucket=your-file-upload-bucket" \
    --memory=256MB \
    --timeout=120s \
    --set-env-vars="GCP_PROJECT_ID=your-project-id,BQ_DATASET_ID=your_dataset,BQ_TABLE_ID=file_uploads"
```

## Testing

### Upload a Test File

```bash
gsutil cp sample_data/employees_HUM-100_report.csv gs://your-file-upload-bucket/
```

### View Function Logs

```bash
gcloud functions logs read process-file-uploads --region=us-central1 --gen2
```

### Query BigQuery Results

```sql
SELECT * 
FROM `your-project-id.your_dataset.file_uploads` 
ORDER BY upload_date DESC 
LIMIT 10;
```

## Customization

### Adding New HUM Code Patterns

Modify the regex pattern in `main.py`:

```python
# Current pattern: HUM-100, HUM-200, HUM-300, etc.
HUM_CODE_PATTERN = r'(HUM-\d+)'

# Example: Also match ABC-100 codes
HUM_CODE_PATTERN = r'((?:HUM|ABC)-\d+)'
```

### Supporting Additional Employee ID Column Names

Add to the column name list in `extract_employee_ids_from_csv()`:

```python
if key.lower().replace(' ', '_') in [
    'employee_id', 'employeeid', 'emp_id', 'empid', 'id',
    'staff_id', 'worker_id'  # Add your custom column names
]:
```

### Processing Different File Types

Add new extractors in `main.py`:

```python
def extract_employee_ids_from_xml(content: str) -> list:
    # Your XML parsing logic
    pass
```

## Troubleshooting

### Common Issues

1. **Permission Denied**: Ensure the Cloud Function service account has BigQuery Data Editor role

2. **Table Not Found**: Create the BigQuery table before deploying:
   ```bash
   bq mk --table your-project-id:your_dataset.file_uploads
   ```

3. **Function Not Triggering**: Verify the bucket name in the trigger matches exactly

4. **No Employee IDs Found**: Check that your file has a column named `employee_id` or similar

### View Detailed Logs

```bash
gcloud functions logs read process-file-uploads \
    --region=us-central1 \
    --gen2 \
    --limit=50
```

## Cost Considerations

- **Cloud Functions**: Billed per invocation and compute time
- **Cloud Storage**: Standard storage rates apply
- **BigQuery**: Streaming insert costs apply for each row inserted

For high-volume workloads, consider:
- Batch processing with Cloud Storage triggers
- Using BigQuery load jobs instead of streaming inserts
- Setting up appropriate partitioning and clustering

## File Structure

```
cloud_function/
├── main.py              # Main Cloud Function code
├── requirements.txt     # Python dependencies
├── deploy.sh           # Deployment script
├── bigquery_schema.sql  # BigQuery table schema
├── test_local.py       # Local testing script
├── README.md           # This file
└── sample_data/
    ├── employees_HUM-100_report.csv
    └── data_HUM-200_quarterly.json
```

## License

MIT License - Feel free to use and modify as needed.
