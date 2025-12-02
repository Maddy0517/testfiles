# Quick Start Guide

## Prerequisites Checklist

- [ ] Google Cloud Project created
- [ ] Billing enabled on project
- [ ] Cloud Storage bucket created
- [ ] BigQuery dataset created
- [ ] Service account with permissions
- [ ] gcloud CLI installed and authenticated

## Step-by-Step Deployment

### 1. Update Configuration

Edit `main.py` or set environment variables:

```bash
export PROJECT_ID="your-project-id"
export DATASET_ID="your_dataset"
export TABLE_ID="employee_data"
```

### 2. Install Dependencies Locally (for testing)

```bash
pip install -r requirements.txt
```

### 3. Test Pattern Extraction

```bash
python test_local.py --test-pattern
```

### 4. Deploy Function

**Option A: Using deploy script**
```bash
# Edit deploy.sh and set variables, then:
./deploy.sh
```

**Option B: Using gcloud directly**
```bash
gcloud functions deploy process_file_upload \
    --gen2 \
    --runtime=python311 \
    --region=us-central1 \
    --source=. \
    --entry-point=process_file \
    --trigger-bucket=YOUR_BUCKET_NAME \
    --set-env-vars PROJECT_ID=your-project-id,DATASET_ID=your_dataset,TABLE_ID=employee_data
```

### 5. Set Permissions

```bash
PROJECT_ID="your-project-id"
SERVICE_ACCOUNT="${PROJECT_ID}@appspot.gserviceaccount.com"

# Storage
gcloud projects add-iam-policy-binding ${PROJECT_ID} \
    --member="serviceAccount:${SERVICE_ACCOUNT}" \
    --role="roles/storage.objectViewer"

# BigQuery
gcloud projects add-iam-policy-binding ${PROJECT_ID} \
    --member="serviceAccount:${SERVICE_ACCOUNT}" \
    --role="roles/bigquery.dataEditor"

gcloud projects add-iam-policy-binding ${PROJECT_ID} \
    --member="serviceAccount:${SERVICE_ACCOUNT}" \
    --role="roles/bigquery.jobUser"
```

### 6. Test Upload

```bash
# Create test file with pattern in name
echo "employee_id,name,department
EMP001,John Doe,Engineering
EMP002,Jane Smith,Sales" > test_HUM-100.csv

# Upload to bucket
gsutil cp test_HUM-100.csv gs://YOUR_BUCKET_NAME/
```

### 7. Verify Results

```bash
# Check logs
gcloud functions logs read process_file_upload --gen2 --limit=10

# Query BigQuery
bq query --use_legacy_sql=false \
  "SELECT * FROM \`your-project-id.your_dataset.employee_data\` ORDER BY upload_date DESC LIMIT 10"
```

## File Naming Convention

The function extracts codes from filenames using pattern: `HUM-XXX` (where XXX is 3 digits)

**Examples:**
- ✅ `employee_data_HUM-100.csv` → Extracts `HUM-100`
- ✅ `report_HUM-200_2024.csv` → Extracts `HUM-200`
- ✅ `HUM-300_data.csv` → Extracts `HUM-300`
- ❌ `data.csv` → No code extracted (NULL in BigQuery)

## CSV File Format

Your CSV files must have:
- Header row with `employee_id` column (case-insensitive)
- Comma-separated values
- At least one data row

**Example:**
```csv
employee_id,name,department
EMP001,John Doe,Engineering
EMP002,Jane Smith,Sales
```

## Troubleshooting

### Function not triggering
```bash
# Check function status
gcloud functions describe process_file_upload --gen2 --region=us-central1

# Check trigger configuration
gcloud functions describe process_file_upload --gen2 --region=us-central1 --format="value(eventTrigger)"
```

### Permission errors
```bash
# Verify service account permissions
gcloud projects get-iam-policy PROJECT_ID \
    --flatten="bindings[].members" \
    --filter="bindings.members:serviceAccount:*@PROJECT_ID.iam.gserviceaccount.com"
```

### BigQuery errors
```bash
# Check if table exists
bq show your-project-id:your_dataset.employee_data

# Check table schema
bq show --schema --format=prettyjson your-project-id:your_dataset.employee_data
```

## Monitoring

- **Cloud Functions Logs**: GCP Console → Cloud Functions → Logs
- **BigQuery**: Query the table to verify data
- **Cloud Monitoring**: Set up alerts for function errors
