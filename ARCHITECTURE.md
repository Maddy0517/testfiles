# Architecture Overview

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     File Upload Process                      │
└─────────────────────────────────────────────────────────────┘

    User/System uploads file
            │
            ▼
    ┌───────────────────┐
    │  Google Cloud     │  File: employee_data_HUM-100.csv
    │  Storage Bucket   │  Pattern: HUM-100
    └───────────────────┘
            │
            │ (Trigger: google.storage.object.finalize)
            ▼
    ┌───────────────────┐
    │  Cloud Function   │
    │  (Python 3.11)    │
    └───────────────────┘
            │
            ├─────────────────────────┐
            │                         │
            ▼                         ▼
    ┌─────────────┐          ┌──────────────┐
    │  Extract    │          │  Read File   │
    │  Pattern    │          │  from GCS    │
    │  from Name  │          │             │
    └─────────────┘          └──────────────┘
            │                         │
            └────────┬────────────────┘
                     ▼
            ┌────────────────┐
            │  Add Metadata  │
            │  - upload_date │
            │  - file_name   │
            │  - pattern     │
            └────────────────┘
                     │
                     ▼
            ┌────────────────┐
            │   Transform    │
            │   Data using   │
            │   Pandas       │
            └────────────────┘
                     │
                     ▼
            ┌────────────────┐
            │   BigQuery     │
            │   Load Job     │
            └────────────────┘
                     │
                     ▼
            ┌────────────────┐
            │   BigQuery     │
            │   Table        │
            └────────────────┘
```

## Component Details

### 1. Google Cloud Storage (GCS)

**Role**: File storage and event source

**Key Features**:
- Triggers Cloud Function on file upload
- Supports various file formats (CSV, JSON, Excel)
- Scalable object storage

**Events Monitored**:
- `google.storage.object.finalize`: Triggered when a file upload completes

### 2. Cloud Function

**Role**: Event processor and ETL orchestrator

**Specifications**:
- Runtime: Python 3.11
- Memory: 512MB
- Timeout: 540s (9 minutes)
- Trigger: Cloud Storage bucket

**Key Functions**:

| Function | Purpose |
|----------|---------|
| `process_file_upload()` | Main entry point, orchestrates the entire process |
| `extract_pattern_from_filename()` | Extracts pattern (HUM-XXX) from filename |
| `read_file_from_gcs()` | Reads and parses file from GCS |
| `prepare_data_for_bigquery()` | Adds metadata columns to DataFrame |
| `load_to_bigquery()` | Loads data into BigQuery table |

### 3. BigQuery

**Role**: Data warehouse and analytics platform

**Table Schema**:

```sql
CREATE TABLE employee_data (
  -- Metadata columns (added by function)
  employee_id STRING,
  upload_date TIMESTAMP,
  file_name STRING,
  file_pattern STRING,
  
  -- Original columns from file (auto-detected)
  employee_name STRING,
  department STRING,
  salary INT64,
  hire_date DATE,
  ...
)
```

## Data Flow

### Step-by-Step Process

1. **File Upload**
   - User uploads `employee_data_HUM-100.csv` to GCS bucket
   - GCS emits `object.finalize` event

2. **Function Trigger**
   - Cloud Function receives event payload:
     ```json
     {
       "bucket": "my-bucket",
       "name": "employee_data_HUM-100.csv",
       "timeCreated": "2024-12-02T10:30:00Z"
     }
     ```

3. **Pattern Extraction**
   - Regex pattern: `r'(HUM-\d+)'`
   - Extracts: `HUM-100`
   - Case-insensitive matching

4. **File Reading**
   - Downloads file from GCS
   - Detects format based on extension
   - Parses into Pandas DataFrame

5. **Data Transformation**
   - Adds metadata columns:
     - `employee_id`: From file data
     - `upload_date`: From event timestamp
     - `file_name`: From event payload
     - `file_pattern`: Extracted pattern
   
6. **BigQuery Load**
   - Uses `load_table_from_dataframe()`
   - Auto-detects schema
   - Appends to existing table
   - Returns success/failure

## Scalability Considerations

### Performance

| Metric | Configuration |
|--------|---------------|
| Max Instances | 10 (configurable) |
| Memory | 512MB |
| Timeout | 540s |
| Concurrent Executions | Up to max instances |

### Handling Large Files

For files > 100MB:
- Increase memory allocation (up to 8GB)
- Increase timeout (up to 540s)
- Consider chunked processing
- Use BigQuery load from GCS directly for very large files

### Cost Optimization

**Cloud Function Costs**:
- Charged per invocation
- Charged per GB-second (memory × time)
- Free tier: 2M invocations/month

**BigQuery Costs**:
- Storage: $0.02/GB/month
- Query: $5/TB processed
- Loading data: Free

**GCS Costs**:
- Storage: $0.02/GB/month
- Operations: Minimal for this use case

## Security Architecture

### Authentication & Authorization

```
Cloud Function Service Account
        │
        ├─── roles/storage.objectViewer
        │    (Read files from GCS)
        │
        ├─── roles/bigquery.dataEditor
        │    (Write data to BigQuery)
        │
        └─── roles/bigquery.jobUser
             (Run BigQuery jobs)
```

### Data Protection

1. **In Transit**:
   - HTTPS for all API calls
   - TLS 1.2+ encryption

2. **At Rest**:
   - GCS: Google-managed encryption (default)
   - BigQuery: Google-managed encryption (default)
   - Optional: Customer-managed encryption keys (CMEK)

3. **Access Control**:
   - IAM policies
   - VPC Service Controls (optional)
   - Audit logging

## Error Handling

### Retry Strategy

```python
try:
    # Process file
    process_file_upload(event, context)
except Exception as e:
    # Log error
    logger.error(f"Error: {str(e)}")
    
    # Cloud Functions automatically retries
    # on exception (if retry is enabled)
    raise
```

### Error Scenarios

| Error Type | Handling |
|------------|----------|
| File not found | Log and skip |
| Invalid format | Log error, don't retry |
| Missing column | Raise exception, log details |
| BigQuery timeout | Retry automatically |
| Permission denied | Raise exception, alert |

## Monitoring & Observability

### Logging

All logs flow to Cloud Logging:
- Function invocations
- Processing steps
- Errors and exceptions
- Performance metrics

### Metrics

Available in Cloud Monitoring:
- Invocation count
- Execution time
- Error rate
- Memory usage
- Active instances

### Alerting

Recommended alerts:
- Error rate > 5%
- Execution time > 300s
- Memory usage > 90%
- Failed BigQuery loads

## Deployment Options

### Option 1: Automated Script (Recommended)

```bash
./deploy.sh
```

### Option 2: Manual Deployment

```bash
gcloud functions deploy process-file-upload \
  --gen2 \
  --runtime python311 \
  --trigger-bucket my-bucket \
  --entry-point process_file_upload \
  --region us-central1 \
  --set-env-vars GCP_PROJECT_ID=my-project,...
```

### Option 3: Terraform/IaC

See infrastructure-as-code examples in the repository.

## Best Practices

### File Organization

```
gs://my-bucket/
├── raw/                    # Incoming files
│   ├── HUM-100/
│   ├── HUM-200/
│   └── HUM-300/
├── processed/              # Successfully processed
└── failed/                 # Failed processing
```

### Naming Conventions

✅ Good:
- `employee_data_HUM-100_20241202.csv`
- `payroll_HUM-200.xlsx`
- `HUM-300_quarterly_report.json`

❌ Bad:
- `data.csv` (no pattern)
- `file123.xlsx` (unclear naming)

### Data Validation

Add validation before BigQuery load:

```python
def validate_data(df: pd.DataFrame) -> bool:
    # Check required columns
    required = ['employee_id']
    if not all(col in df.columns for col in required):
        return False
    
    # Check for nulls
    if df['employee_id'].isnull().any():
        return False
    
    # Check data types
    # Add your validations
    
    return True
```

## Future Enhancements

1. **Dead Letter Queue**: Store failed events for reprocessing
2. **Data Validation**: Schema validation before load
3. **Deduplication**: Check for duplicate files
4. **Partitioning**: Partition BigQuery table by upload_date
5. **Clustering**: Cluster by file_pattern for better query performance
6. **Notification**: Send alerts on success/failure
7. **Archival**: Move processed files to archive bucket

## References

- [Cloud Functions Documentation](https://cloud.google.com/functions/docs)
- [BigQuery Documentation](https://cloud.google.com/bigquery/docs)
- [Cloud Storage Documentation](https://cloud.google.com/storage/docs)
