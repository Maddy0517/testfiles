# Workday Employee Data Ingestion - Google Cloud Dataflow

High-performance Apache Beam pipeline for ingesting Workday employee data via SOAP API into BigQuery with parallel page processing. Built with Java 21+ modern language features.

## Project Structure

```
workday-dataflow-pipeline/
├── pom.xml                           # Maven configuration
├── application.properties            # Configuration file
├── run.sh                            # Execution script
├── README.md                         # This file
└── src/main/java/com/example/dataflow/
    ├── WorkdayDataflowPipeline.java  # Main pipeline class
    ├── WorkdaySoapHandler.java       # SOAP API handler
    ├── WorkdayDataModel.java         # Data models (Employee, PageRequest, Config)
    └── BigQuerySchema.java           # BigQuery schema definition
```

## Features

- **Parallel Processing**: Processes up to 999 records per page across distributed workers
- **Automatic Retries**: Configurable retry logic for API failures
- **Scalable**: Handles millions of employee records
- **BigQuery Integration**: Direct loading with schema management

## Prerequisites

- **Java 21 or higher** (required for modern language features)
- Maven 3.9+
- Google Cloud account with Dataflow and BigQuery APIs enabled
- Workday SOAP API credentials

## Quick Start

### 1. Configure

Edit `application.properties` with your credentials:

```properties
workday.soap.url=https://wd2-impl-services1.workday.com/ccx/service/YOUR-TENANT/Human_Resources/v38.0
workday.username=integration_user@tenant
workday.password=your-password
workday.tenant.id=your-tenant
effective.date=2025-12-15
bigquery.table=your-project:workday_data.employees
```

### 2. Build

```bash
mvn clean package
```

### 3. Run Locally (Testing)

```bash
export WORKDAY_SOAP_URL="https://wd2-impl.workday.com/ccx/service/tenant/Human_Resources/v38.0"
export WORKDAY_USERNAME="user@tenant"
export WORKDAY_PASSWORD="password"
export WORKDAY_TENANT_ID="tenant"
export EFFECTIVE_DATE="2025-12-15"
export BIGQUERY_TABLE="project:dataset.employees"

./run.sh local
```

### 4. Run on Dataflow (Production)

```bash
export GCP_PROJECT_ID="your-project"
export TEMP_LOCATION="gs://your-bucket/temp"
export STAGING_LOCATION="gs://your-bucket/staging"
export NUM_WORKERS=10
export MAX_NUM_WORKERS=50

./run.sh dataflow
```

## Pipeline Architecture

```
Workday API (SOAP) → Get Total Count → Generate Pages → Parallel Fetch → Transform → BigQuery
                                            ↓
                                    (999 records/page)
                                            ↓
                                    Distributed Workers
```

**How it works:**
1. Fetches total employee count from Workday
2. Calculates number of pages (totalCount / 999)
3. Distributes page fetching across multiple workers in parallel
4. Transforms employee data to BigQuery format
5. Batch loads into BigQuery table

## Configuration Parameters

### Required Parameters
- `--workdaySoapUrl`: Workday SOAP API endpoint
- `--workdayUsername`: Integration user (format: user@tenant)
- `--workdayPassword`: User password
- `--effectiveDate`: Data extraction date (YYYY-MM-DD)
- `--bigQueryTable`: Destination table (project:dataset.table)

### Optional Parameters
- `--workdayTenantId`: Tenant identifier
- `--writeDisposition`: WRITE_APPEND (default), WRITE_TRUNCATE, WRITE_EMPTY
- `--maxRetries`: Retry attempts (default: 3)
- `--estimatedTotalCount`: Pre-calculated count (0 = auto-fetch)

### Dataflow Parameters
- `--project`: GCP project ID
- `--region`: GCP region (default: us-central1)
- `--tempLocation`: GCS temp location (required)
- `--stagingLocation`: GCS staging location (required)
- `--numWorkers`: Initial worker count (default: 10)
- `--maxNumWorkers`: Max workers (default: 50)

## BigQuery Schema

The pipeline creates a table with these fields:

| Field               | Type      | Mode     | Description                    |
|---------------------|-----------|----------|--------------------------------|
| employee_id         | STRING    | REQUIRED | Unique employee identifier     |
| first_name          | STRING    | NULLABLE | Employee first name            |
| last_name           | STRING    | NULLABLE | Employee last name             |
| email               | STRING    | NULLABLE | Email address                  |
| phone               | STRING    | NULLABLE | Phone number                   |
| hire_date           | STRING    | NULLABLE | Hire date                      |
| job_title           | STRING    | NULLABLE | Job title                      |
| department          | STRING    | NULLABLE | Department                     |
| manager_id          | STRING    | NULLABLE | Manager employee ID            |
| location            | STRING    | NULLABLE | Work location                  |
| employment_status   | STRING    | NULLABLE | Employment status              |
| effective_date      | STRING    | NULLABLE | Effective date of extract      |
| ingestion_timestamp | TIMESTAMP | REQUIRED | Ingestion timestamp            |

## Performance Guidelines

### Dataset Sizing (Cost-Optimized Defaults)

| Employees | Workers | Machine Type  | Est. Time | Est. Cost |
|-----------|---------|---------------|-----------|-----------|
| 5,000     | 1-2     | n1-standard-2 | 5 min     | $0.20     |
| 25,000    | 2       | n1-standard-2 | 12 min    | $0.50     |
| 100,000   | 5       | n1-standard-4 | 20 min    | $5        |
| 500,000   | 10      | n1-standard-8 | 40 min    | $20       |

**💡 Tip**: Start with 2 workers (default) and scale up only if needed. Most companies don't need more than 5 workers.

### Optimization Tips

1. **Right-size workers**: `numWorkers = totalPages / 5`
2. **Use faster machines**: n1-standard-8 for large datasets
3. **Batch writes**: Pipeline uses FILE_LOADS for efficiency
4. **Partition tables**: Partition by ingestion_timestamp for query performance

## Monitoring

View job status in GCP Console:
```
https://console.cloud.google.com/dataflow/jobs
```

Check logs:
```bash
gcloud logging read "resource.type=dataflow_step" --limit=50
```

Query results:
```sql
SELECT COUNT(*) FROM `your-project.workday_data.employees`;
```

## Troubleshooting

### Authentication Error
**Issue**: 401 Unauthorized
**Fix**: Verify username format is `user@tenant` and password is correct

### Permission Denied on BigQuery
**Issue**: Permission denied
**Fix**: Grant BigQuery Data Editor role
```bash
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member=user:YOUR-EMAIL \
  --role=roles/bigquery.dataEditor
```

### Out of Memory
**Issue**: Java heap space error
**Fix**: Increase worker machine type
```bash
--workerMachineType=n1-highmem-4
```

### SOAP Timeout
**Issue**: Read timed out
**Fix**: Timeouts are set to 60s connection, 120s read. Increase in `WorkdayDataModel.WorkdayConfig` if needed.

## Code Overview

### WorkdayDataflowPipeline.java
Main pipeline class that orchestrates the data flow:
- Configures pipeline options
- Creates page requests based on total count
- Coordinates parallel processing
- Writes to BigQuery

### WorkdaySoapHandler.java
Handles all SOAP API interactions:
- Builds SOAP requests with authentication
- Calls Workday Get_Workers API
- Parses XML responses
- Extracts employee data
- Implements retry logic

### WorkdayDataModel.java
Contains all data model classes:
- `Employee`: Employee data structure with 13 fields
- `PageRequest`: Pagination request model
- `WorkdayConfig`: Configuration with timeouts and retry settings

### BigQuerySchema.java
Defines BigQuery table schema with all employee fields

## Security

- Never commit credentials to version control
- Use environment variables for sensitive data
- Consider using Google Secret Manager for production
- Enable encryption for data at rest and in transit

## Cost Optimization

- Use preemptible workers (70% cost savings)
- Right-size worker count and machine types
- Schedule during off-peak hours
- Partition BigQuery tables to reduce query costs

## License

This project is provided as-is for educational and commercial use.

---

**Need Help?** Check the troubleshooting section or review Dataflow logs in GCP Console.
