# Workday Employee Data Ingestion - Google Cloud Dataflow Pipeline

A high-performance, scalable Apache Beam pipeline for ingesting Workday employee data via SOAP API and loading it into Google BigQuery using parallel page processing.

## Overview

This pipeline extracts employee data from Workday's SOAP API with optimal performance by:
- **Parallel Page Processing**: Each page (999 records max) is processed independently across distributed workers
- **Configurable Effective Date**: Filter data based on effective date
- **Automatic Retry Logic**: Built-in retry mechanism with exponential backoff
- **Scalable Architecture**: Leverages Google Cloud Dataflow's distributed processing
- **BigQuery Integration**: Direct loading with schema management

## Architecture

```
┌─────────────────┐
│  Workday SOAP   │
│      API        │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────┐
│   Dataflow Pipeline                 │
│                                     │
│  1. Get Total Count                 │
│  2. Generate Page Requests          │
│  3. Fetch Pages in Parallel ████    │
│  4. Transform to TableRows          │
│  5. Write to BigQuery               │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────┐
│   BigQuery      │
│   Table         │
└─────────────────┘
```

## Features

### Performance Optimizations
- **Parallel Processing**: Distributes page fetching across multiple Dataflow workers
- **Batch Loading**: Uses BigQuery FILE_LOADS method for efficient bulk inserts
- **Worker Autoscaling**: Automatically scales workers based on workload
- **Connection Pooling**: Reuses SOAP connections within workers

### Reliability
- **Automatic Retries**: Configurable retry logic for transient failures
- **Error Handling**: Comprehensive error logging and handling
- **Checkpointing**: Dataflow's built-in checkpointing for fault tolerance

### Data Quality
- **Schema Enforcement**: Predefined BigQuery schema with validation
- **Timestamp Tracking**: Records ingestion timestamp for each record
- **Effective Date Filtering**: Ensures data freshness

## Prerequisites

1. **Java Development Kit (JDK) 11+**
   ```bash
   java -version
   ```

2. **Apache Maven 3.6+**
   ```bash
   mvn -version
   ```

3. **Google Cloud Platform Account**
   - Project with billing enabled
   - BigQuery API enabled
   - Dataflow API enabled
   - Cloud Storage API enabled

4. **Workday SOAP API Access**
   - SOAP API URL
   - Integration user credentials
   - Tenant ID

5. **Google Cloud SDK (for Dataflow execution)**
   ```bash
   gcloud auth application-default login
   ```

## Project Structure

```
workday-dataflow-pipeline/
├── pom.xml                                 # Maven configuration
├── config.properties                       # Configuration file
├── run-pipeline.sh                         # Execution script
├── README.md                               # This file
└── src/
    └── main/
        └── java/
            └── com/
                └── example/
                    └── dataflow/
                        ├── WorkdayEmployeeDataflowPipeline.java  # Main pipeline
                        ├── client/
                        │   └── WorkdaySoapClient.java            # SOAP client
                        ├── config/
                        │   └── WorkdayConfig.java                # Configuration
                        ├── model/
                        │   ├── Employee.java                     # Employee model
                        │   └── PageRequest.java                  # Page request model
                        ├── transform/
                        │   ├── GeneratePageRequestsFn.java       # Page generator
                        │   ├── FetchEmployeePageFn.java          # Page fetcher
                        │   └── EmployeeToTableRowFn.java         # BQ converter
                        └── utils/
                            └── BigQuerySchemaFactory.java        # Schema factory
```

## Configuration

### 1. Update `config.properties`

```properties
# Workday Configuration
workday.soap.url=https://wd2-impl-services1.workday.com/ccx/service/YOUR-TENANT/Human_Resources/v38.0
workday.username=integration_user@tenant
workday.password=your-secure-password
workday.tenant.id=your-tenant

# Effective Date
effective.date=2025-12-15

# BigQuery Configuration
bigquery.project.id=your-gcp-project-id
bigquery.dataset.id=workday_data
bigquery.table.id=employees

# Dataflow Configuration
dataflow.project.id=your-gcp-project-id
dataflow.region=us-central1
dataflow.temp.location=gs://your-bucket/temp
dataflow.staging.location=gs://your-bucket/staging
dataflow.num.workers=10
dataflow.max.num.workers=50
```

### 2. Set Environment Variables (Alternative)

```bash
export WORKDAY_SOAP_URL="https://wd2-impl-services1.workday.com/ccx/service/YOUR-TENANT/Human_Resources/v38.0"
export WORKDAY_USERNAME="integration_user@tenant"
export WORKDAY_PASSWORD="your-password"
export WORKDAY_TENANT_ID="your-tenant"
export EFFECTIVE_DATE="2025-12-15"
export BIGQUERY_TABLE="your-project:workday_data.employees"
export GCP_PROJECT_ID="your-gcp-project-id"
export TEMP_LOCATION="gs://your-bucket/temp"
export STAGING_LOCATION="gs://your-bucket/staging"
```

## Building the Project

```bash
mvn clean package
```

This creates: `target/workday-dataflow-pipeline-1.0.0.jar`

## Running the Pipeline

### Option 1: Using the Shell Script

#### Local Testing (DirectRunner)
```bash
chmod +x run-pipeline.sh
./run-pipeline.sh local
```

#### Production (DataflowRunner)
```bash
./run-pipeline.sh dataflow
```

### Option 2: Direct Maven Execution

#### Local Testing
```bash
mvn compile exec:java \
  -Dexec.mainClass=com.example.dataflow.WorkdayEmployeeDataflowPipeline \
  -Dexec.args="--runner=DirectRunner \
    --workdaySoapUrl=https://wd2-impl.workday.com/ccx/service/tenant/Human_Resources/v38.0 \
    --workdayUsername=user@tenant \
    --workdayPassword=password \
    --workdayTenantId=tenant \
    --effectiveDate=2025-12-15 \
    --bigQueryTable=project:dataset.employees \
    --writeDisposition=WRITE_APPEND"
```

#### Google Cloud Dataflow
```bash
mvn compile exec:java \
  -Dexec.mainClass=com.example.dataflow.WorkdayEmployeeDataflowPipeline \
  -Dexec.args="--runner=DataflowRunner \
    --project=your-project-id \
    --region=us-central1 \
    --tempLocation=gs://your-bucket/temp \
    --stagingLocation=gs://your-bucket/staging \
    --numWorkers=10 \
    --maxNumWorkers=50 \
    --workerMachineType=n1-standard-4 \
    --workdaySoapUrl=https://wd2-impl.workday.com/ccx/service/tenant/Human_Resources/v38.0 \
    --workdayUsername=user@tenant \
    --workdayPassword=password \
    --workdayTenantId=tenant \
    --effectiveDate=2025-12-15 \
    --bigQueryTable=project:dataset.employees \
    --writeDisposition=WRITE_APPEND"
```

## Pipeline Parameters

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `--runner` | Yes | - | DirectRunner (local) or DataflowRunner (cloud) |
| `--workdaySoapUrl` | Yes | - | Workday SOAP API endpoint URL |
| `--workdayUsername` | Yes | - | Workday integration username |
| `--workdayPassword` | Yes | - | Workday password |
| `--workdayTenantId` | No | - | Workday tenant ID |
| `--effectiveDate` | Yes | - | Effective date (YYYY-MM-DD) |
| `--bigQueryTable` | Yes | - | BigQuery table (project:dataset.table) |
| `--writeDisposition` | No | WRITE_APPEND | WRITE_TRUNCATE, WRITE_APPEND, WRITE_EMPTY |
| `--createDisposition` | No | CREATE_IF_NEEDED | CREATE_IF_NEEDED, CREATE_NEVER |
| `--maxRetries` | No | 3 | Maximum API retry attempts |
| `--estimatedTotalCount` | No | 0 | Estimated count (0 = fetch dynamically) |
| `--project` | Yes* | - | GCP project ID (*Dataflow only) |
| `--region` | No | us-central1 | GCP region (Dataflow only) |
| `--tempLocation` | Yes* | - | GCS temp location (*Dataflow only) |
| `--stagingLocation` | Yes* | - | GCS staging location (*Dataflow only) |
| `--numWorkers` | No | 10 | Initial worker count (Dataflow only) |
| `--maxNumWorkers` | No | 50 | Maximum worker count (Dataflow only) |

## BigQuery Schema

The pipeline creates a BigQuery table with the following schema:

```sql
CREATE TABLE `project.dataset.employees` (
  employee_id STRING NOT NULL,
  first_name STRING,
  last_name STRING,
  email STRING,
  phone STRING,
  hire_date STRING,
  job_title STRING,
  department STRING,
  manager_id STRING,
  location STRING,
  employment_status STRING,
  effective_date STRING,
  ingestion_timestamp TIMESTAMP NOT NULL
);
```

## Performance Tuning

### For Large Datasets (>100K records)

```bash
--numWorkers=20 \
--maxNumWorkers=100 \
--workerMachineType=n1-standard-8 \
--maxRetries=5
```

### For Small Datasets (<10K records)

```bash
--numWorkers=2 \
--maxNumWorkers=5 \
--workerMachineType=n1-standard-2
```

### Optimization Tips

1. **Worker Count**: Set `numWorkers` based on total pages (totalRecords / 999)
   - Example: 50,000 records = 51 pages → Use 10-20 workers

2. **Machine Type**: Use higher CPU for faster SOAP processing
   - `n1-standard-4`: Good for most workloads
   - `n1-standard-8`: Better for >100K records
   - `n1-highmem-4`: If memory issues occur

3. **BigQuery Write Method**:
   - `FILE_LOADS`: Best for large batches (>10K records)
   - `STREAMING_INSERTS`: Better for small, frequent loads

4. **GCS Bucket Location**: Use same region as Dataflow for lower latency

## Monitoring

### Dataflow Console
Monitor job progress at:
```
https://console.cloud.google.com/dataflow/jobs/[REGION]/[JOB_NAME]?project=[PROJECT_ID]
```

### Key Metrics to Monitor
- **Elements Added**: Total employees processed
- **Data Watermark**: Pipeline progress
- **System Lag**: Processing delay
- **Worker CPU/Memory**: Resource utilization
- **Errors**: Failed elements

### Logging

View logs with:
```bash
gcloud logging read "resource.type=dataflow_step AND resource.labels.job_name=[JOB_NAME]" --limit 100 --format json
```

## Error Handling

The pipeline includes comprehensive error handling:

1. **SOAP API Failures**: Automatic retry with exponential backoff
2. **Network Issues**: Connection timeout and retry logic
3. **BigQuery Errors**: Automatic retry for transient failures
4. **Data Parsing Errors**: Logged but don't stop pipeline

## Troubleshooting

### Common Issues

#### 1. Authentication Errors
```
Error: 401 Unauthorized
```
**Solution**: Verify Workday credentials and tenant ID

#### 2. BigQuery Permission Errors
```
Error: Permission denied on BigQuery
```
**Solution**: Ensure service account has BigQuery Data Editor role
```bash
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member=serviceAccount:SERVICE_ACCOUNT \
  --role=roles/bigquery.dataEditor
```

#### 3. Out of Memory
```
Error: Java heap space
```
**Solution**: Increase worker machine type or memory
```bash
--workerMachineType=n1-highmem-4
```

#### 4. SOAP Timeout
```
Error: Read timed out
```
**Solution**: Increase timeout in WorkdayConfig or reduce page size

## Security Best Practices

1. **Never commit credentials** to version control
2. **Use Secret Manager** for production credentials:
   ```bash
   gcloud secrets create workday-password --data-file=-
   ```
3. **Use service accounts** with minimal permissions
4. **Enable VPC Service Controls** for sensitive data
5. **Encrypt data** at rest and in transit

## Cost Optimization

### Estimated Costs (for 50K employees)

- **Dataflow**: $2-5 per run (10 workers × 30 min)
- **BigQuery Storage**: $0.02/GB/month
- **BigQuery Queries**: $5/TB scanned
- **Cloud Storage**: $0.02/GB/month

### Cost Reduction Tips

1. Use **preemptible workers** (70% cost savings):
   ```bash
   --usePublicIps=false --enableStreamingEngine
   ```

2. **Schedule during off-peak hours** for better pricing

3. **Partition BigQuery tables** by ingestion_timestamp:
   ```sql
   CREATE TABLE employees
   PARTITION BY DATE(ingestion_timestamp)
   ```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Deploy Workday Pipeline

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-java@v2
        with:
          java-version: '11'
      - name: Build
        run: mvn clean package
      - name: Deploy to Dataflow
        env:
          GOOGLE_CREDENTIALS: ${{ secrets.GCP_SA_KEY }}
        run: ./run-pipeline.sh dataflow
```

## Development

### Adding Custom Fields

1. Add field to `Employee.java` model
2. Update `BigQuerySchemaFactory.createEmployeeSchema()`
3. Update `WorkdaySoapClient.parseWorkerElement()`
4. Rebuild and test

### Testing

```bash
# Run unit tests
mvn test

# Run integration tests (requires credentials)
mvn verify -P integration-tests
```

## Support and Contributing

For issues or questions:
1. Check the [Troubleshooting](#troubleshooting) section
2. Review Dataflow logs in GCP Console
3. Contact your Workday administrator for API issues

## License

This project is provided as-is for educational and commercial use.

## Changelog

### Version 1.0.0
- Initial release
- Parallel page processing
- BigQuery integration
- Automatic retry logic
- Comprehensive error handling

---

**Built with Apache Beam and Google Cloud Dataflow**
