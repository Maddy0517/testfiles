# GCS to BigQuery Cloud Function

Processes CSV files from GCS bucket and loads employee data to BigQuery.

## Files

```
cloud_function/
├── pom.xml
├── deploy.sh
├── config/
│   └── application-dev.properties    # Configuration (edit this)
└── src/main/java/com/example/gcf/
    ├── GcsFileToBigQueryFunction.java  # Main function
    └── CsvFileParser.java              # CSV parser
```

## Configuration

Edit `config/application-dev.properties`:

```properties
gcp.project.id=your-project-id
gcs.bucket.name=your-bucket-name
gcs.file.prefix=
bigquery.dataset.id=your_dataset
bigquery.table.id=file_uploads
```

## Deploy

```bash
chmod +x deploy.sh
./deploy.sh
```

## Trigger

```bash
curl YOUR_FUNCTION_URL
```

## CSV Format

```csv
employee_id,name,department
EMP001,John Smith,Engineering
EMP002,Jane Doe,Marketing
```

## Filename Pattern

Files should contain HUM code in name: `employees_HUM-100_report.csv` → extracts `HUM-100`

## BigQuery Output

| employee_id | upload_date | file_name | hum_code | bucket_name | processed_at |
|-------------|-------------|-----------|----------|-------------|--------------|
| EMP001 | 2024-01-15 10:30:00 | employees_HUM-100.csv | HUM-100 | my-bucket | 2024-01-15 11:00:00 |
