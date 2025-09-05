# Workday to BigQuery Data Pipeline

This Apache Beam pipeline reads data from Workday SOAP API with pagination support and loads it into Google Cloud BigQuery. It can run both locally in Eclipse IDE and on Google Cloud Dataflow.

## Features

- ✅ **Workday SOAP API Integration**: Connects to Workday using SOAP web services
- ✅ **Pagination Support**: Handles large datasets with automatic pagination
- ✅ **Flexible Data Transformation**: Supports generic, worker, and organization data transformations
- ✅ **BigQuery Integration**: Writes data to BigQuery with configurable schemas
- ✅ **Retry Logic**: Built-in retry mechanism for failed API calls
- ✅ **Local & Cloud Execution**: Runs in Eclipse IDE (DirectRunner) and Google Cloud Dataflow
- ✅ **Configurable**: Extensive configuration options via command-line parameters

## Prerequisites

### Software Requirements
- Java 11 or higher
- Maven 3.6+
- Eclipse IDE (for local development)
- Google Cloud SDK (for Dataflow deployment)

### Access Requirements
- Workday tenant with API access
- Workday user account with appropriate permissions
- Google Cloud Project with BigQuery and Dataflow APIs enabled
- Service account with BigQuery and Dataflow permissions

## Project Structure

```
workday-beam-pipeline/
├── src/
│   ├── main/
│   │   ├── java/com/example/workday/
│   │   │   ├── WorkdayToBigQueryPipeline.java    # Main pipeline class
│   │   │   ├── WorkdayConfiguration.java         # Configuration class
│   │   │   ├── WorkdaySOAPClient.java           # SOAP client with pagination
│   │   │   ├── WorkdayRecord.java               # Data model
│   │   │   ├── WorkdayIO.java                   # Beam IO transform
│   │   │   ├── WorkdaySource.java               # Beam source implementation
│   │   │   ├── WorkdayToBigQueryTransform.java  # Data transformation
│   │   │   ├── BigQuerySchemaGenerator.java     # Schema definitions
│   │   │   └── WorkdayPipelineOptions.java      # Pipeline options
│   │   └── resources/
│   │       ├── application.properties           # Default configuration
│   │       └── logback.xml                      # Logging configuration
│   └── test/java/                               # Test classes
├── scripts/
│   ├── run-local.sh                             # Local execution script
│   └── run-dataflow.sh                          # Dataflow execution script
├── pom.xml                                      # Maven configuration
├── eclipse-setup.md                             # Eclipse setup instructions
└── README.md                                    # This file
```

## Quick Start

### 1. Clone and Build

```bash
git clone <repository-url>
cd workday-beam-pipeline
mvn clean compile
```

### 2. Configure Credentials

Update `src/main/resources/application.properties` with your settings:

```properties
# Workday Configuration
workday.endpoint=https://wd2-impl-services1.workday.com
workday.tenant=your_tenant_name
workday.username=your_username
workday.password=your_password

# BigQuery Configuration
bigquery.project=your-gcp-project-id
bigquery.dataset=workday_data
bigquery.table=workday_records
```

### 3. Set up Google Cloud Authentication

```bash
# Option 1: Service Account Key
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account-key.json

# Option 2: Application Default Credentials
gcloud auth application-default login
```

### 4. Run Locally

```bash
# Using the provided script
./scripts/run-local.sh

# Or using Maven directly
mvn exec:java -Dexec.mainClass=com.example.workday.WorkdayToBigQueryPipeline \
  -Dexec.args="--runner=DirectRunner \
    --workdayEndpoint=https://wd2-impl-services1.workday.com \
    --workdayUsername=your_username \
    --workdayPassword=your_password \
    --workdayTenant=your_tenant \
    --serviceName=Human_Resources \
    --operationName=Get_Workers \
    --bigQueryProject=your-project-id \
    --bigQueryDataset=workday_data \
    --bigQueryTable=workers \
    --transformationType=worker"
```

## Configuration Options

### Pipeline Parameters

| Parameter | Description | Required | Default |
|-----------|-------------|----------|---------|
| `--workdayEndpoint` | Workday service endpoint URL | Yes | - |
| `--workdayUsername` | Workday username | Yes | - |
| `--workdayPassword` | Workday password | Yes | - |
| `--workdayTenant` | Workday tenant name | Yes | - |
| `--serviceName` | Workday service name (e.g., Human_Resources) | Yes | - |
| `--operationName` | Workday operation name (e.g., Get_Workers) | Yes | - |
| `--bigQueryProject` | Google Cloud project ID | Yes | - |
| `--bigQueryDataset` | BigQuery dataset ID | Yes | - |
| `--bigQueryTable` | BigQuery table ID | Yes | - |
| `--transformationType` | Transformation type (generic, worker, organization) | No | generic |
| `--pageSize` | Records per API call | No | 100 |
| `--maxRetries` | Maximum retry attempts | No | 3 |
| `--workdayVersion` | Workday API version | No | v41.2 |
| `--writeDisposition` | BigQuery write mode | No | WRITE_APPEND |
| `--createDisposition` | BigQuery table creation | No | CREATE_IF_NEEDED |

### Supported Workday Services

#### Human Resources Service
- **Service Name**: `Human_Resources`
- **Operations**: `Get_Workers`, `Get_Organizations`, `Get_Job_Profiles`
- **Transformation Type**: `worker`, `organization`, `generic`

#### Example API Calls
```bash
# Get Workers
--serviceName=Human_Resources --operationName=Get_Workers --transformationType=worker

# Get Organizations  
--serviceName=Human_Resources --operationName=Get_Organizations --transformationType=organization
```

## Eclipse IDE Setup

See [eclipse-setup.md](eclipse-setup.md) for detailed Eclipse configuration instructions.

### Quick Eclipse Setup:
1. Import as Maven project
2. Set Java 11+ as project JRE
3. Create run configuration with main class: `com.example.workday.WorkdayToBigQueryPipeline`
4. Add program arguments (see examples above)

## Google Cloud Dataflow Deployment

### 1. Prerequisites
```bash
# Enable APIs
gcloud services enable dataflow.googleapis.com
gcloud services enable bigquery.googleapis.com

# Create GCS bucket for staging
gsutil mb gs://your-bucket-name
```

### 2. Deploy to Dataflow
```bash
# Build fat JAR
mvn clean package -DskipTests

# Submit to Dataflow
./scripts/run-dataflow.sh
```

### 3. Monitor Job
```bash
# List running jobs
gcloud dataflow jobs list --region=us-central1

# View job details
gcloud dataflow jobs describe JOB_ID --region=us-central1
```

## Data Schemas

### Generic Schema
```sql
CREATE TABLE workday_data.workday_records (
  id STRING NOT NULL,
  data JSON,
  timestamp TIMESTAMP NOT NULL,
  source_system STRING NOT NULL,
  load_date DATE NOT NULL,
  load_timestamp TIMESTAMP NOT NULL
);
```

### Worker Schema
```sql
CREATE TABLE workday_data.workers (
  worker_id STRING NOT NULL,
  employee_id STRING,
  first_name STRING,
  last_name STRING,
  email STRING,
  hire_date DATE,
  job_title STRING,
  department STRING,
  manager_id STRING,
  status STRING,
  raw_data JSON,
  load_timestamp TIMESTAMP NOT NULL
);
```

### Organization Schema
```sql
CREATE TABLE workday_data.organizations (
  organization_id STRING NOT NULL,
  organization_name STRING,
  organization_type STRING,
  parent_organization_id STRING,
  organization_code STRING,
  effective_date DATE,
  raw_data JSON,
  load_timestamp TIMESTAMP NOT NULL
);
```

## Pagination Handling

The pipeline automatically handles pagination:

1. **Page Size**: Configurable via `--pageSize` parameter (default: 100)
2. **Automatic Pagination**: Continues fetching until no more data
3. **Memory Efficient**: Processes one page at a time
4. **Error Handling**: Retries failed pages with exponential backoff

## Error Handling and Monitoring

### Retry Logic
- Configurable retry attempts (`--maxRetries`)
- Exponential backoff for transient failures
- Logs all retry attempts

### Monitoring
- Comprehensive logging with SLF4J
- Pipeline metrics in Dataflow console
- BigQuery job monitoring

### Common Issues

1. **Authentication Errors**
   ```
   Solution: Verify GOOGLE_APPLICATION_CREDENTIALS or run gcloud auth
   ```

2. **Workday Connection Issues**
   ```
   Solution: Check endpoint URL, credentials, and network connectivity
   ```

3. **BigQuery Permission Errors**
   ```
   Solution: Ensure service account has BigQuery Data Editor role
   ```

## Performance Tuning

### Local Development
- Use smaller page sizes (50-100 records)
- Limit data volume for testing

### Production (Dataflow)
- Increase page size (100-500 records)
- Use appropriate machine types
- Enable autoscaling

```bash
# Performance-optimized Dataflow run
mvn exec:java -Dexec.mainClass=com.example.workday.WorkdayToBigQueryPipeline \
  -Dexec.args="--runner=DataflowRunner \
    --maxNumWorkers=10 \
    --autoscalingAlgorithm=THROUGHPUT_BASED \
    --machineType=n1-standard-4 \
    --pageSize=200"
```

## Testing

```bash
# Run unit tests
mvn test

# Run integration tests (requires credentials)
mvn verify -Pintegration-tests
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.

## Support

For issues and questions:
1. Check the troubleshooting section
2. Review Eclipse setup guide
3. Check Google Cloud documentation
4. Open an issue in the repository

## Version History

- **1.0.0**: Initial release with basic Workday to BigQuery functionality
- Pagination support
- Multiple transformation types
- Eclipse and Dataflow compatibility