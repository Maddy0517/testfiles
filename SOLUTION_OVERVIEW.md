# Solution Overview: GCS to BigQuery Cloud Function

## Problem Statement

Process files uploaded to Google Cloud Storage by:
1. Reading the filename
2. Extracting a code pattern (e.g., HUM-100, HUM-200) from the filename
3. Reading file content and extracting employee_id
4. Loading data to BigQuery with:
   - `employee_id` (from file data)
   - `upload_date` (timestamp when processed)
   - `file_name` (from GCS)
   - `extracted_code` (from filename pattern)

## Solution Architecture

```
┌─────────────────┐
│  Cloud Storage  │
│   (File Upload) │
└────────┬────────┘
         │
         │ Triggers
         ▼
┌─────────────────┐
│ Cloud Function  │
│  (process_file) │
└────────┬────────┘
         │
         ├─► Extract code from filename (HUM-XXX)
         ├─► Read file from GCS
         ├─► Parse CSV and extract employee_id
         └─► Insert to BigQuery
         │
         ▼
┌─────────────────┐
│    BigQuery     │
│  employee_data  │
└─────────────────┘
```

## Key Components

### 1. Main Function (`main.py`)

**Entry Point**: `process_file(event, context)`
- Triggered by Cloud Storage file upload events
- Processes each uploaded file automatically

**Key Functions**:
- `extract_code_from_filename()`: Uses regex to find HUM-XXX pattern
- `read_file_from_gcs()`: Downloads file content from GCS
- `parse_file_content()`: Parses CSV and extracts employee_id column
- `ensure_bigquery_table_exists()`: Creates table if missing
- `load_to_bigquery()`: Inserts rows with all required fields

### 2. Configuration

Uses environment variables for flexibility:
- `PROJECT_ID`: GCP project ID
- `DATASET_ID`: BigQuery dataset name
- `TABLE_ID`: BigQuery table name
- `EMPLOYEE_ID_COLUMN`: Column name in CSV (default: "employee_id")

### 3. BigQuery Schema

Automatically creates table with:
```sql
CREATE TABLE employee_data (
  employee_id STRING REQUIRED,
  upload_date TIMESTAMP REQUIRED,
  file_name STRING REQUIRED,
  extracted_code STRING NULLABLE
)
```

## Data Flow Example

### Input File
**Filename**: `employee_report_HUM-100_2024.csv`

**Content**:
```csv
employee_id,name,department
EMP001,John Doe,Engineering
EMP002,Jane Smith,Sales
```

### Processing Steps

1. **Filename Analysis**:
   - Input: `employee_report_HUM-100_2024.csv`
   - Extracted Code: `HUM-100`

2. **File Parsing**:
   - Reads CSV content
   - Finds `employee_id` column
   - Extracts: `EMP001`, `EMP002`

3. **BigQuery Insert**:
   ```json
   [
     {
       "employee_id": "EMP001",
       "upload_date": "2024-01-15T10:30:00Z",
       "file_name": "employee_report_HUM-100_2024.csv",
       "extracted_code": "HUM-100"
     },
     {
       "employee_id": "EMP002",
       "upload_date": "2024-01-15T10:30:00Z",
       "file_name": "employee_report_HUM-100_2024.csv",
       "extracted_code": "HUM-100"
     }
   ]
   ```

## Pattern Matching

The function uses regex pattern: `(HUM-\d{3})`

**Matches**:
- ✅ `HUM-100`, `HUM-200`, `HUM-999`
- ✅ Case-insensitive: `hum-100` → `HUM-100`
- ✅ Anywhere in filename: `data_HUM-100.csv`, `HUM-200_report.csv`

**Doesn't Match**:
- ❌ `HUM-10` (needs 3 digits)
- ❌ `HUM-1000` (too many digits)
- ❌ `EMP-100` (different prefix)

**To Change Pattern**: Modify `CODE_PATTERN` in `main.py`:
```python
CODE_PATTERN = re.compile(r'(EMP-\d{3})', re.IGNORECASE)  # For EMP-XXX
```

## Error Handling

The function handles:
- ✅ Missing code pattern → Sets `extracted_code` to NULL, continues processing
- ✅ Missing employee_id column → Logs error, fails function (retryable)
- ✅ Empty files → Logs warning, returns early
- ✅ Invalid rows → Skips invalid rows, processes valid ones
- ✅ BigQuery errors → Logs error, fails function (retryable)
- ✅ Missing table → Creates table automatically

## Scalability

- **Concurrent Processing**: Multiple files processed in parallel
- **Auto-scaling**: Cloud Functions scales automatically
- **Batch Processing**: Uses streaming inserts (efficient for small-medium files)
- **Large Files**: Consider using BigQuery load jobs for files > 100MB

## Cost Considerations

- **Cloud Functions**: Pay per invocation and execution time
- **Cloud Storage**: Standard storage costs
- **BigQuery**: 
  - Streaming inserts: $0.01 per 200MB
  - Storage: $0.02 per GB/month
  - Queries: $5 per TB scanned

**Optimization Tips**:
- Batch multiple rows per insert
- Use partitioned tables for date-based queries
- Consider load jobs for very large files

## Security Best Practices

1. **Service Account**: Use least-privilege service account
2. **IAM Roles**: Only grant necessary permissions
3. **Environment Variables**: Store secrets in Secret Manager (if needed)
4. **VPC**: Use VPC connector if accessing private resources
5. **Audit Logs**: Enable Cloud Audit Logs for compliance

## Monitoring & Alerting

**Key Metrics**:
- Function invocations
- Function execution time
- Error rate
- BigQuery insert success rate

**Recommended Alerts**:
- Function error rate > 5%
- Function execution time > 30s
- BigQuery insert failures

## Testing Strategy

1. **Unit Tests**: Test pattern extraction, parsing logic
2. **Integration Tests**: Test with real GCS and BigQuery (test project)
3. **Load Tests**: Test with multiple concurrent uploads
4. **Error Scenarios**: Test with invalid files, missing columns, etc.

## Future Enhancements

Potential improvements:
- [ ] Support for multiple file formats (JSON, TSV, Parquet)
- [ ] Batch processing for large files
- [ ] Data validation and transformation
- [ ] Dead-letter queue for failed processing
- [ ] Notification on completion/failure
- [ ] Support for multiple code patterns
- [ ] Configurable CSV delimiters
- [ ] Data deduplication logic
