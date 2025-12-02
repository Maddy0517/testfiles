# GCS to BigQuery Cloud Function (Java)

A Google Cloud Function written in **Java** that processes CSV files from Google Cloud Storage and loads metadata to BigQuery.

## Key Features

- **HTTP Trigger**: Invoked manually or via scheduled job (NOT auto-triggered on file upload)
- **Bucket Scanning**: Scans the configured GCS bucket for all CSV files when triggered
- **Filename Parsing**: Extracts codes like `HUM-100`, `HUM-200`, `HUM-300` from filenames
- **Employee ID Extraction**: Reads `employee_id` column from CSV file content
- **Environment Configuration**: Configuration via properties file for easy migration between environments
- **BigQuery Integration**: Loads processed data to BigQuery

## Architecture

```
┌──────────────────┐     ┌─────────────────┐     ┌──────────────────┐     ┌─────────────┐
│  Manual Trigger  │────>│  Cloud Function │────>│    GCS Bucket    │────>│  BigQuery   │
│  or Scheduler    │     │   (HTTP Java)   │     │  (Scan for CSV)  │     │   Table     │
└──────────────────┘     └─────────────────┘     └──────────────────┘     └─────────────┘
```

## Project Structure

```
cloud_function/
├── pom.xml                                      # Maven configuration
├── deploy.sh                                    # Deployment script
├── bigquery_schema.sql                          # BigQuery table DDL
├── README.md                                    # This file
├── config/                                      # Environment-specific configs
│   ├── application-dev.properties
│   ├── application-sit.properties
│   ├── application-uat.properties
│   └── application-prod.properties
├── sample_data/
│   ├── employees_HUM-100_report.csv
│   └── data_HUM-200_quarterly.json
└── src/
    ├── main/
    │   ├── java/com/example/gcf/
    │   │   ├── GcsFileToBigQueryFunction.java   # Main Cloud Function (HTTP)
    │   │   ├── CsvFileParser.java               # CSV parser
    │   │   ├── ConfigProperties.java            # Properties reader
    │   │   ├── ProcessingResult.java            # Result container
    │   │   └── FileProcessingResult.java        # Per-file result
    │   └── resources/
    │       └── application.properties           # Active configuration
    └── test/java/com/example/gcf/
        ├── GcsFileToBigQueryFunctionTest.java
        └── CsvFileParserTest.java
```

## Configuration

### Properties File (`src/main/resources/application.properties`)

```properties
# GCP Project Configuration
gcp.project.id=your-project-id

# Google Cloud Storage Configuration
gcs.bucket.name=your-bucket-name
gcs.file.prefix=                    # Optional: folder prefix to scan

# BigQuery Configuration
bigquery.dataset.id=your_dataset
bigquery.table.id=file_uploads
```

### Environment Migration

Simply copy the appropriate config file for your environment:

```bash
# For DEV
cp config/application-dev.properties src/main/resources/application.properties

# For SIT
cp config/application-sit.properties src/main/resources/application.properties

# For UAT
cp config/application-uat.properties src/main/resources/application.properties

# For PROD
cp config/application-prod.properties src/main/resources/application.properties
```

Then build and deploy - **no code changes required!**

## BigQuery Table Schema

| Column        | Type      | Description                           |
|---------------|-----------|---------------------------------------|
| employee_id   | STRING    | Employee ID extracted from CSV file   |
| upload_date   | TIMESTAMP | When the file was uploaded to GCS     |
| file_name     | STRING    | Full filename including path          |
| hum_code      | STRING    | Extracted code (HUM-100, HUM-200, etc)|
| bucket_name   | STRING    | GCS bucket name                       |
| processed_at  | TIMESTAMP | When the function processed the file  |

## CSV File Format

### Supported Format
```csv
employee_id,name,department,hire_date
EMP001,John Smith,Engineering,2023-01-15
EMP002,Jane Doe,Marketing,2023-02-20
EMP003,Bob Johnson,Sales,2023-03-10
```

### Supported Column Names (case-insensitive)
- `employee_id`, `employeeid`, `emp_id`, `empid`, `id`
- `employee-id`, `emp-id`, `staff_id`, `worker_id`

## Filename Patterns

The function extracts `HUM-XXX` codes from filenames using regex pattern `(HUM-\d+)`.

| Filename                          | Extracted Code |
|-----------------------------------|----------------|
| `employees_HUM-100_report.csv`    | HUM-100        |
| `data_HUM-200_quarterly.csv`      | HUM-200        |
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
   - Storage Object Viewer
   - BigQuery Data Editor

## Setup & Deployment

### 1. Configure Properties File

Edit `src/main/resources/application.properties`:

```properties
gcp.project.id=my-gcp-project
gcs.bucket.name=my-csv-bucket
bigquery.dataset.id=employee_data
bigquery.table.id=file_uploads
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
# Build
mvn clean package -DskipTests

# Deploy with HTTP trigger
gcloud functions deploy process-csv-files \
    --gen2 \
    --runtime=java17 \
    --region=us-central1 \
    --source=. \
    --entry-point=com.example.gcf.GcsFileToBigQueryFunction \
    --trigger-http \
    --allow-unauthenticated \
    --memory=512MB \
    --timeout=300s
```

## Triggering the Function

### Manual Trigger (curl)

```bash
# Get function URL
FUNCTION_URL=$(gcloud functions describe process-csv-files --region=us-central1 --gen2 --format='value(serviceConfig.uri)')

# Trigger
curl $FUNCTION_URL
```

### Scheduled Trigger (Cloud Scheduler)

```bash
# Create a scheduler job to run every 6 hours
gcloud scheduler jobs create http process-csv-job \
    --schedule='0 */6 * * *' \
    --uri='YOUR_FUNCTION_URL' \
    --http-method=GET \
    --location=us-central1
```

### Response Format

```json
{
  "status": "success",
  "totalFilesProcessed": 3,
  "totalRowsInserted": 15,
  "files": [
    {
      "fileName": "employees_HUM-100_report.csv",
      "humCode": "HUM-100",
      "rowsInserted": 5,
      "status": "Success"
    },
    {
      "fileName": "data_HUM-200_monthly.csv",
      "humCode": "HUM-200",
      "rowsInserted": 10,
      "status": "Success"
    }
  ]
}
```

## Testing

### Run Unit Tests

```bash
mvn test
```

### Run Local Function

```bash
mvn function:run
# Then in another terminal:
curl http://localhost:8080/
```

### Upload Test Files to GCS

```bash
gsutil cp sample_data/employees_HUM-100_report.csv gs://your-bucket/
```

### View Function Logs

```bash
gcloud functions logs read process-csv-files --region=us-central1 --gen2
```

### Query BigQuery Results

```sql
SELECT * 
FROM `your-project.your_dataset.file_uploads` 
ORDER BY processed_at DESC 
LIMIT 10;
```

## Customization

### Adding New Code Patterns

Modify the regex pattern in `GcsFileToBigQueryFunction.java`:

```java
// Current pattern
private static final Pattern HUM_CODE_PATTERN = 
    Pattern.compile("(HUM-\\d+)", Pattern.CASE_INSENSITIVE);

// Example: Also match ABC-100, DEF-200 codes
private static final Pattern HUM_CODE_PATTERN = 
    Pattern.compile("((?:HUM|ABC|DEF)-\\d+)", Pattern.CASE_INSENSITIVE);
```

### Adding More Employee ID Column Names

Edit `CsvFileParser.java`:

```java
private static final List<String> EMPLOYEE_ID_COLUMNS = Arrays.asList(
        "employee_id", "employeeid", "emp_id", "empid", "id",
        "staff_id", "worker_id",
        "your_custom_column"  // Add here
);
```

### Using a Specific Folder Prefix

In `application.properties`:

```properties
# Only process files in the 'pending/' folder
gcs.file.prefix=pending/
```

## Troubleshooting

### Common Issues

1. **Properties File Not Found**
   - Ensure `application.properties` is in `src/main/resources/`
   - Verify file is included in the build

2. **Permission Denied on GCS**
   - Grant Storage Object Viewer role to the function's service account

3. **BigQuery Insert Errors**
   - Verify dataset and table exist
   - Check BigQuery Data Editor role

4. **No Files Found**
   - Verify bucket name in properties file
   - Check if files have `.csv` extension

### View Detailed Logs

```bash
gcloud functions logs read process-csv-files \
    --region=us-central1 \
    --gen2 \
    --limit=100
```

## Cost Considerations

- **Cloud Functions**: Billed per invocation and compute time
- **Cloud Storage**: Read operation costs
- **BigQuery**: Streaming insert costs per row
- **Cloud Scheduler**: Billed per job execution (if used)

## License

MIT License - Feel free to use and modify as needed.
