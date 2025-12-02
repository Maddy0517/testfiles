# Quick Start Guide

Get your GCS to BigQuery function running in minutes!

## Prerequisites

- Google Cloud Project with billing enabled
- `gcloud` CLI installed and configured
- Required permissions on your GCP account

## 5-Minute Setup

### 1. Clone/Download the Code

```bash
# Navigate to the directory containing the function code
cd /path/to/this/directory
```

### 2. Run the Automated Deployment Script

```bash
./deploy.sh
```

The script will prompt you for:
- Project ID
- Bucket name
- BigQuery dataset and table names
- Region (default: us-central1)

### 3. Test the Function

Upload a test file:

```bash
# Replace with your bucket name
gsutil cp test_file_HUM-100.csv gs://your-bucket-name/
```

### 4. Verify the Results

Check if data was loaded to BigQuery:

```bash
# Replace with your project, dataset, and table
bq query --use_legacy_sql=false \
  'SELECT * FROM `your-project.your_dataset.employee_data` LIMIT 10'
```

## What Happens?

```
File Upload (GCS) 
    ↓
Cloud Function Triggered
    ↓
Extract Pattern (HUM-100)
    ↓
Read File Content
    ↓
Add Metadata (date, filename, pattern)
    ↓
Load to BigQuery
```

## Example Data Flow

**Input File**: `employee_data_HUM-100.csv`

```csv
employee_id,employee_name,department,salary
E001,John Doe,Engineering,75000
E002,Jane Smith,Marketing,68000
```

**Output in BigQuery**:

| employee_id | upload_date | file_name | file_pattern | employee_name | department | salary |
|------------|-------------|-----------|--------------|---------------|------------|---------|
| E001 | 2024-12-02T10:30:00 | employee_data_HUM-100.csv | HUM-100 | John Doe | Engineering | 75000 |
| E002 | 2024-12-02T10:30:00 | employee_data_HUM-100.csv | HUM-100 | Jane Smith | Marketing | 68000 |

## Monitoring

View logs in real-time:

```bash
gcloud functions logs read process-file-upload \
  --region us-central1 \
  --gen2 \
  --follow
```

## Troubleshooting

### Function not triggering?

1. Check if the function deployed successfully:
   ```bash
   gcloud functions describe process-file-upload --region us-central1 --gen2
   ```

2. Verify the trigger is configured:
   ```bash
   gcloud functions describe process-file-upload --region us-central1 --gen2 | grep trigger
   ```

### Data not appearing in BigQuery?

1. Check function logs for errors
2. Verify your file has an `employee_id` column
3. Ensure the filename contains a pattern like HUM-100

### Permission errors?

The Cloud Function service account needs:
- `Storage Object Viewer` role
- `BigQuery Data Editor` role
- `BigQuery Job User` role

Grant permissions:

```bash
# Get the service account email
SERVICE_ACCOUNT=$(gcloud functions describe process-file-upload \
  --region us-central1 --gen2 --format="value(serviceConfig.serviceAccountEmail)")

# Grant BigQuery permissions
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:$SERVICE_ACCOUNT" \
  --role="roles/bigquery.dataEditor"

gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:$SERVICE_ACCOUNT" \
  --role="roles/bigquery.jobUser"
```

## Next Steps

- Customize pattern extraction in `main.py`
- Add data validation logic
- Set up alerting for failures
- Configure lifecycle policies for processed files

For detailed documentation, see [README.md](README.md)
