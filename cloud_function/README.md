# GCS to BigQuery Cloud Function (Java)

A Google Cloud Function written in **Java** that automatically processes files uploaded to Google Cloud Storage and loads metadata to BigQuery.

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
│  (File Upload)  │    │  (Java 17)       │    │   Table     │
└─────────────────┘    └──────────────────┘    └─────────────┘
```

## Project Structure

```
cloud_function/
├── pom.xml                              # Maven configuration
├── deploy.sh                            # Deployment script
├── bigquery_schema.sql                  # BigQuery table schema
├── README.md                            # This file
├── sample_data/
│   ├── employees_HUM-100_report.csv     # Sample CSV file
│   └── data_HUM-200_quarterly.json      # Sample JSON file
└── src/
    ├── main/java/com/example/gcf/
    │   ├── GcsFileToBigQueryFunction.java   # Main function class
    │   └── FileParser.java                  # CSV/JSON parser
    └── test/java/com/example/gcf/
        ├── GcsFileToBigQueryFunctionTest.java  # Function tests
        └── FileParserTest.java                 # Parser tests
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

Supported column names (case-insensitive):
- `employee_id`, `employeeid`, `emp_id`, `empid`, `id`
- `employee-id`, `emp-id`, `staff_id`, `worker_id`

### JSON Files

**Array format:**
```json
[
    {"employee_id": "EMP001", "name": "John Smith"},
    {"employee_id": "EMP002", "name": "Jane Doe"}
]
```

**Nested format:**
```json
{
    "employees": [
        {"employee_id": "EMP001", "name": "John Smith"},
        {"employee_id": "EMP002", "name": "Jane Doe"}
    ]
}
```

Supported container fields: `employees`, `data`, `records`, `items`, `results`

## Filename Patterns

The function extracts `HUM-XXX` codes from filenames using regex pattern `(HUM-\d+)`.

| Filename                          | Extracted Code |
|-----------------------------------|----------------|
| `employees_HUM-100_report.csv`    | HUM-100        |
| `data_HUM-200_quarterly.json`     | HUM-200        |
| `HUM-300_employees.csv`           | HUM-300        |
| `uploads/2024/HUM-456_data.csv`   | HUM-456        |
| `report_2024.csv`                 | UNKNOWN        |

## Prerequisites

1. **Java 17+** installed
2. **Maven 3.6+** installed
3. **Google Cloud SDK** installed and configured
4. **GCP Project** with billing enabled
5. Required **IAM permissions**:
   - Cloud Functions Admin
   - Storage Admin
   - BigQuery Data Editor
   - Eventarc Admin

## Setup & Deployment

### 1. Configure Environment Variables

```bash
export GCP_PROJECT_ID="your-project-id"
export GCP_REGION="us-central1"
export GCS_BUCKET_NAME="your-file-upload-bucket"
export BQ_DATASET_ID="your_dataset"
export BQ_TABLE_ID="file_uploads"
```

### 2. Build and Run Tests

```bash
cd cloud_function
mvn clean test
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
gcloud services enable eventarc.googleapis.com
gcloud services enable run.googleapis.com

# Build
mvn clean package -DskipTests

# Create BigQuery resources
bq mk --dataset your-project-id:your_dataset
bq mk --table \
  --schema 'employee_id:STRING,upload_date:TIMESTAMP,file_name:STRING,hum_code:STRING,bucket_name:STRING,processed_at:TIMESTAMP' \
  your-project-id:your_dataset.file_uploads

# Deploy function
gcloud functions deploy process-file-uploads \
    --gen2 \
    --runtime=java17 \
    --region=us-central1 \
    --source=. \
    --entry-point=com.example.gcf.GcsFileToBigQueryFunction \
    --trigger-event-filters="type=google.cloud.storage.object.v1.finalized" \
    --trigger-event-filters="bucket=your-file-upload-bucket" \
    --memory=512MB \
    --timeout=120s \
    --set-env-vars="GCP_PROJECT_ID=your-project-id,BQ_DATASET_ID=your_dataset,BQ_TABLE_ID=file_uploads"
```

## Testing

### Run Unit Tests

```bash
mvn test
```

### Run Local Function (for development)

```bash
mvn function:run
```

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

### Adding New Code Patterns

Modify the regex pattern in `GcsFileToBigQueryFunction.java`:

```java
// Current pattern: HUM-100, HUM-200, HUM-300, etc.
private static final Pattern HUM_CODE_PATTERN = Pattern.compile("(HUM-\\d+)", Pattern.CASE_INSENSITIVE);

// Example: Also match ABC-100, DEF-200 codes
private static final Pattern HUM_CODE_PATTERN = Pattern.compile("((?:HUM|ABC|DEF)-\\d+)", Pattern.CASE_INSENSITIVE);
```

### Supporting Additional Employee ID Column Names

Add to the column name list in `FileParser.java`:

```java
private static final List<String> EMPLOYEE_ID_COLUMNS = Arrays.asList(
        "employee_id", "employeeid", "emp_id", "empid", "id",
        "staff_id", "worker_id",
        "your_custom_column"  // Add your column name here
);
```

### Changing File Type Detection

Modify `extractEmployeeIds()` method in `FileParser.java` to add support for additional file formats (e.g., XML, Parquet).

## Troubleshooting

### Common Issues

1. **Permission Denied**
   - Ensure the Cloud Function service account has BigQuery Data Editor role
   - Check that Storage Object Viewer role is granted for the bucket

2. **Table Not Found**
   ```bash
   bq mk --table your-project-id:your_dataset.file_uploads
   ```

3. **Function Not Triggering**
   - Verify the bucket name in the trigger matches exactly
   - Check Eventarc permissions

4. **No Employee IDs Found**
   - Verify your file has a column named `employee_id` or similar
   - Check column name case sensitivity

5. **Build Failures**
   ```bash
   mvn clean install -U  # Force update dependencies
   ```

### View Detailed Logs

```bash
gcloud functions logs read process-file-uploads \
    --region=us-central1 \
    --gen2 \
    --limit=50
```

## Performance Tuning

- **Memory**: Default 512MB, increase for large files
- **Timeout**: Default 120s, increase for slow processing
- **Concurrency**: Configure max instances if needed

```bash
gcloud functions deploy process-file-uploads \
    --max-instances=10 \
    --memory=1024MB \
    --timeout=300s
```

## Cost Considerations

- **Cloud Functions**: Billed per invocation and compute time
- **Cloud Storage**: Standard storage rates apply
- **BigQuery**: Streaming insert costs apply for each row inserted

For high-volume workloads, consider:
- Batch processing with Cloud Storage triggers
- Using BigQuery load jobs instead of streaming inserts
- Setting up appropriate partitioning and clustering

## License

MIT License - Feel free to use and modify as needed.
