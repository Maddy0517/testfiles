# Workday to BigQuery Data Pipeline

A robust Apache Beam Java pipeline for extracting employee data from Workday SOAP API and loading it into Google BigQuery. The pipeline runs on Google Cloud Dataflow and supports both historical full loads and incremental updates.

## Features

- **SOAP API Integration**: Connects to Workday Human Resources SOAP API v41.0
- **Pagination Support**: Handles Workday's 999 records per page limitation automatically
- **Dual Load Modes**:
  - **Historical Load**: Full data extraction with configurable effective date
  - **Incremental Load**: Only fetches records modified after a specified timestamp
- **Scalable Processing**: Leverages Apache Beam for distributed processing on Google Cloud Dataflow
- **BigQuery Integration**: 
  - Automatic schema creation
  - Table partitioning by effective date
  - Clustering for query optimization
  - Support for append and truncate write modes
- **Data Quality**: Built-in data validation, cleansing, and enrichment transforms
- **Production Ready**: Includes CI/CD configuration, monitoring, and scheduling

## Architecture

```
Workday SOAP API → Apache Beam Pipeline → Google Cloud Dataflow → BigQuery
                          ↓
                   Data Transformation
                   (Validation, Cleansing, Enrichment)
```

### Key Components

1. **WorkdaySoapClient**: Handles SOAP communication with Workday API
   - WS-Security authentication
   - Response parsing
   - Error handling

2. **WorkdayIO**: Custom Apache Beam I/O connector
   - Implements BoundedSource for batch processing
   - Automatic pagination handling
   - Parallel page fetching for performance

3. **EmployeeTransform**: Data transformation logic
   - Data validation
   - Field standardization
   - Data enrichment

4. **WorkdayPipeline**: Main pipeline orchestration
   - Configurable for historical vs incremental loads
   - BigQuery sink with schema management

## Prerequisites

- Java 11 or higher
- Maven 3.6+
- Google Cloud Platform account with:
  - Dataflow API enabled
  - BigQuery API enabled
  - Cloud Storage bucket for staging
  - Service account with appropriate permissions
- Workday API credentials:
  - Username
  - Password
  - Tenant ID
  - API endpoint URL

## Installation

1. Clone the repository:
```bash
git clone <repository-url>
cd workday-dataflow-pipeline
```

2. Build the project:
```bash
mvn clean compile package
```

## Configuration

### Environment Variables

Set the following environment variables:

```bash
# GCP Configuration
export GCP_PROJECT_ID="your-project-id"
export GCP_REGION="us-central1"
export GCS_TEMP_LOCATION="gs://your-bucket/temp"
export GCS_STAGING_LOCATION="gs://your-bucket/staging"

# Workday Configuration
export WORKDAY_ENDPOINT="https://wd-services.myworkday.com/ccx/service/tenant/Human_Resources/v41.0"
export WORKDAY_USERNAME="your-username"
export WORKDAY_PASSWORD="your-password"
export WORKDAY_TENANT="your-tenant"

# BigQuery Configuration
export BIGQUERY_DATASET="workday_data"
export BIGQUERY_TABLE="employees"

# Load Configuration
export LOAD_TYPE="INCREMENTAL"  # or "HISTORICAL"
```

### Pipeline Options

| Option | Description | Default |
|--------|-------------|---------|
| `--workdayEndpoint` | Workday SOAP API endpoint | Required |
| `--workdayUsername` | Workday username | Required |
| `--workdayPassword` | Workday password | Required |
| `--workdayTenant` | Workday tenant ID | Required |
| `--bigQueryDataset` | Target BigQuery dataset | Required |
| `--bigQueryTable` | Target BigQuery table | `workday_employees` |
| `--loadType` | HISTORICAL or INCREMENTAL | `INCREMENTAL` |
| `--effectiveDate` | Effective as-of date (yyyy-MM-dd) | Current date |
| `--lastModifiedFrom` | For incremental loads (yyyy-MM-dd'T'HH:mm:ss) | Last 24 hours |
| `--writeDisposition` | WRITE_TRUNCATE, WRITE_APPEND, or WRITE_EMPTY | `WRITE_APPEND` |
| `--enablePartitioning` | Enable BigQuery table partitioning | `true` |
| `--partitionField` | Field for partitioning | `effective_date` |
| `--clusteringFields` | Comma-separated clustering fields | `employee_id,department,location` |

## Usage

### Local Testing

Run the pipeline locally using DirectRunner:

```bash
chmod +x run-local.sh
./run-local.sh
```

### Deploy to Google Cloud Dataflow

Deploy the pipeline to Dataflow:

```bash
chmod +x deploy-dataflow.sh
./deploy-dataflow.sh
```

### Manual Execution

Run with custom parameters:

```bash
mvn compile exec:java \
  -Dexec.mainClass="com.example.workday.WorkdayPipeline" \
  -Dexec.args="--runner=DataflowRunner \
    --project=your-project-id \
    --region=us-central1 \
    --workdayEndpoint=https://... \
    --workdayUsername=username \
    --workdayPassword=password \
    --workdayTenant=tenant \
    --bigQueryDataset=workday_data \
    --bigQueryTable=employees \
    --loadType=INCREMENTAL \
    --tempLocation=gs://your-bucket/temp"
```

## Data Schema

The pipeline creates a BigQuery table with the following schema:

| Field | Type | Description |
|-------|------|-------------|
| `employee_id` | STRING | Unique employee identifier |
| `worker_reference_id` | STRING | Workday worker reference |
| `first_name` | STRING | Employee first name |
| `last_name` | STRING | Employee last name |
| `preferred_name` | STRING | Preferred name |
| `email` | STRING | Work email address |
| `phone_number` | STRING | Work phone |
| `job_title` | STRING | Current job title |
| `department` | STRING | Department name |
| `location` | STRING | Work location |
| `manager_id` | STRING | Manager's employee ID |
| `manager_name` | STRING | Manager's name |
| `employment_status` | STRING | Active/Terminated/On Leave |
| `hire_date` | DATE | Original hire date |
| `termination_date` | DATE | Termination date (if applicable) |
| `employee_type` | STRING | Employee classification |
| `cost_center` | STRING | Cost center code |
| `business_unit` | STRING | Business unit |
| `salary` | FLOAT64 | Base salary |
| `currency` | STRING | Salary currency |
| `pay_frequency` | STRING | Payment frequency |
| `effective_date` | DATE | Data effective date |
| `last_modified_date` | DATETIME | Last modification timestamp |
| `data_source` | STRING | Source system (Workday) |
| `extracted_at` | DATETIME | Extraction timestamp |

## Scheduling

### Cloud Scheduler Setup

Create scheduled jobs for automated runs:

1. **Daily Incremental Load**:
```bash
gcloud scheduler jobs create http workday-incremental-daily \
  --schedule="0 2 * * *" \
  --uri="https://dataflow.googleapis.com/v1b3/projects/${PROJECT_ID}/locations/us-central1/templates:launch" \
  --http-method=POST \
  --message-body-from-file=schedule-incremental.json
```

2. **Weekly Historical Load**:
```bash
gcloud scheduler jobs create http workday-historical-weekly \
  --schedule="0 3 * * 0" \
  --uri="https://dataflow.googleapis.com/v1b3/projects/${PROJECT_ID}/locations/us-central1/templates:launch" \
  --http-method=POST \
  --message-body-from-file=schedule-historical.json
```

## CI/CD

The project includes Cloud Build configuration for automated deployment:

1. **Setup Cloud Build Trigger**:
```bash
gcloud builds triggers create github \
  --repo-name=workday-pipeline \
  --repo-owner=your-org \
  --branch-pattern="^main$" \
  --build-config=cloudbuild.yaml
```

2. **Store Secrets**:
```bash
echo -n "your-username" | gcloud secrets create workday-username --data-file=-
echo -n "your-password" | gcloud secrets create workday-password --data-file=-
```

## Monitoring

Monitor pipeline execution:

1. **Dataflow Console**: 
   ```
   https://console.cloud.google.com/dataflow
   ```

2. **BigQuery Console**:
   ```
   https://console.cloud.google.com/bigquery
   ```

3. **Cloud Logging**:
   ```bash
   gcloud logging read "resource.type=dataflow_step" --limit=50
   ```

## Performance Optimization

### Pagination and Parallelism
- The pipeline automatically splits large datasets into multiple bundles
- Each bundle processes a range of pages in parallel
- Default configuration creates up to 10 parallel readers

### Dataflow Autoscaling
- Configure `maxNumWorkers` for automatic scaling
- Use `THROUGHPUT_BASED` autoscaling algorithm
- Adjust `workerMachineType` based on data volume

### BigQuery Optimization
- Tables are partitioned by `effective_date` for efficient querying
- Clustering on frequently filtered columns improves performance
- Use `WRITE_TRUNCATE` for historical loads to avoid duplicates

## Troubleshooting

### Common Issues

1. **Authentication Failures**:
   - Verify Workday credentials
   - Check tenant ID format
   - Ensure API endpoint URL is correct

2. **Pagination Errors**:
   - Check Workday API response for total pages
   - Verify page size limits (max 999)

3. **BigQuery Schema Mismatch**:
   - Review field mappings in Employee model
   - Check date/time format conversions

4. **Memory Issues**:
   - Increase worker machine type
   - Reduce page size in splits
   - Enable Dataflow shuffle service

### Debug Mode

Enable detailed logging:
```bash
export MAVEN_OPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug"
```

## Security Considerations

1. **Credentials Management**:
   - Use Google Secret Manager for sensitive data
   - Never commit credentials to version control
   - Rotate passwords regularly

2. **Network Security**:
   - Use VPC Service Controls for Dataflow
   - Configure Private Google Access
   - Implement firewall rules for Workday access

3. **Data Privacy**:
   - Encrypt data at rest in BigQuery
   - Use column-level security for sensitive fields
   - Implement data retention policies

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions:
- Create an issue in the repository
- Contact the development team
- Check the troubleshooting guide

## Acknowledgments

- Apache Beam community
- Google Cloud Dataflow team
- Workday API documentation