# Workday to BigQuery Data Pipeline

A robust Apache Beam pipeline for ingesting employee data from Workday SOAP API and loading it into Google BigQuery, designed for deployment on Google Cloud Dataflow.

## Features

- 🚀 **Scalable**: Parallel processing with configurable batch sizes
- 🔒 **Secure**: Encrypted authentication and secure credential handling
- 📊 **Monitoring**: Comprehensive metrics and logging
- 🛡️ **Resilient**: Automatic retries and error handling
- 🎯 **Optimized**: Memory and performance optimizations for large datasets
- 📈 **Observable**: Built-in monitoring and alerting capabilities

## Architecture

```
Workday SOAP API → Apache Beam Pipeline → Google BigQuery
                      ↓
                 Google Cloud Dataflow
```

### Components

1. **WorkdaySOAPClient**: Handles SOAP API authentication and data retrieval
2. **ReadFromWorkdayAPI**: Apache Beam transform for parallel data ingestion
3. **TransformEmployeeData**: Data transformation and validation
4. **BigQuery Integration**: Optimized writes with schema management
5. **Error Handling**: Comprehensive error handling and dead letter queues
6. **Monitoring**: Metrics collection and health checks

## Prerequisites

- Java 11 or higher
- Maven 3.6+
- Google Cloud SDK
- Active Workday tenant with SOAP API access
- Google Cloud Project with required APIs enabled:
  - Dataflow API
  - BigQuery API
  - Cloud Storage API
  - Compute Engine API

## Quick Start

### 1. Clone and Build

```bash
git clone <repository-url>
cd workday-bigquery-pipeline
mvn clean compile
```

### 2. Set Environment Variables

```bash
export PROJECT_ID="your-gcp-project-id"
export WORKDAY_USERNAME="username@tenant"
export WORKDAY_PASSWORD="your-password"
export WORKDAY_ENDPOINT="https://wd2-impl-services1.workday.com/ccx/service/tenant/Human_Resources/v35.0"
```

### 3. Deploy to Dataflow

```bash
./deploy.sh
```

## Configuration

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `PROJECT_ID` | Yes | - | GCP Project ID |
| `WORKDAY_USERNAME` | Yes | - | Workday username |
| `WORKDAY_PASSWORD` | Yes | - | Workday password |
| `WORKDAY_ENDPOINT` | No | Auto-generated | Workday SOAP endpoint |
| `WORKDAY_TENANT` | No | `tenant` | Workday tenant name |
| `BQ_DATASET` | No | `workday_data` | BigQuery dataset |
| `BQ_TABLE` | No | `employees` | BigQuery table |
| `REGION` | No | `us-central1` | GCP region |
| `BATCH_SIZE` | No | `100` | API batch size |
| `MAX_WORKERS` | No | `10` | Maximum Dataflow workers |

### Pipeline Options

```bash
mvn compile exec:java \
  -Dexec.mainClass=com.company.dataflow.WorkdayToBigQueryPipeline \
  -Dexec.args="
    --project=your-project
    --region=us-central1
    --runner=DataflowRunner
    --workdayEndpoint=https://wd2-impl-services1.workday.com/ccx/service/tenant/Human_Resources/v35.0
    --workdayUsername=username@tenant
    --workdayPassword=password
    --workdayTenant=tenant
    --bigQueryDataset=workday_data
    --bigQueryTable=employees
    --batchSize=100
    --maxNumWorkers=10
    --stagingLocation=gs://your-bucket/staging
    --tempLocation=gs://your-bucket/temp
  "
```

## BigQuery Schema

The pipeline creates a BigQuery table with the following schema:

```sql
CREATE TABLE `project.dataset.employees` (
  employee_id STRING NOT NULL,
  first_name STRING,
  last_name STRING,
  email STRING,
  department STRING,
  job_title STRING,
  manager_id STRING,
  status STRING,
  location STRING,
  salary NUMERIC,
  hire_date TIMESTAMP,
  last_modified TIMESTAMP,
  ingestion_timestamp TIMESTAMP NOT NULL,
  is_complete BOOLEAN NOT NULL,
  data_source STRING NOT NULL,
  error_message STRING
);
```

## Monitoring and Alerting

### Metrics

The pipeline exposes the following metrics:

- `records_read`: Number of records read from Workday
- `records_transformed`: Number of records successfully transformed
- `records_written`: Number of records written to BigQuery
- `records_failed`: Number of failed records
- `api_calls_success`: Successful API calls
- `api_calls_failed`: Failed API calls
- `api_call_latency_ms`: API call latency distribution

### Logging

Structured logging is configured for:
- Local development (console output)
- Cloud deployment (JSON format for Cloud Logging)
- File-based logging with rotation

### Health Checks

- Memory usage monitoring
- Error rate tracking
- Pipeline health status
- Automatic garbage collection triggers

## Error Handling

### Retry Logic

- Exponential backoff for API calls
- Configurable maximum retry attempts
- Circuit breaker pattern for persistent failures

### Dead Letter Queue

Failed records are written to a separate BigQuery table with error details:
- Original record data (where possible)
- Error message and timestamp
- Processing stage where failure occurred

### Data Quality Checks

- Required field validation
- Data type validation
- Completeness indicators
- Duplicate detection

## Performance Optimization

### Memory Management

- G1 garbage collector configuration
- Memory usage monitoring
- Automatic GC triggers for high memory usage

### Parallel Processing

- Configurable worker parallelism
- Batch size optimization
- Connection pooling for API calls

### BigQuery Optimization

- Batch loading for better performance
- Partitioned tables support
- Clustering for query optimization
- Streaming inserts for real-time scenarios

## Deployment Options

### 1. Direct Dataflow Deployment

```bash
./deploy.sh
```

### 2. Docker Container

```bash
docker build -t workday-pipeline -f docker/Dockerfile .
docker run -e PROJECT_ID=your-project workday-pipeline
```

### 3. Kubernetes Job

```bash
kubectl apply -f kubernetes/workday-pipeline-job.yaml
```

### 4. Scheduled Execution

The pipeline supports cron-based scheduling:

```yaml
# Daily execution at 2 AM UTC
schedule: "0 2 * * *"
```

## Security Best Practices

### Credentials Management

- Use Google Secret Manager for production
- Environment variable injection
- IAM-based authentication
- Encrypted storage and transmission

### Network Security

- VPC configuration support
- Private IP usage
- Firewall rules
- SSL/TLS encryption

### Data Privacy

- Field-level encryption options
- PII data handling
- Audit logging
- Access control

## Troubleshooting

### Common Issues

1. **Authentication Failures**
   ```bash
   # Check credentials
   echo $WORKDAY_USERNAME
   # Verify endpoint accessibility
   curl -I $WORKDAY_ENDPOINT
   ```

2. **Memory Issues**
   ```bash
   # Increase worker memory
   --machineType=n1-standard-4
   --diskSizeGb=200
   ```

3. **API Rate Limiting**
   ```bash
   # Reduce batch size
   --batchSize=50
   # Increase retry delay
   --maxRetries=5
   ```

### Debug Mode

Enable debug logging:

```bash
export LOGGING_LEVEL=DEBUG
```

### Performance Tuning

Monitor and adjust:
- Batch sizes based on API response times
- Worker count based on data volume
- Memory allocation based on record size

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
- Check the troubleshooting guide
- Review the monitoring dashboards

## Changelog

### v1.0.0
- Initial release
- Workday SOAP API integration
- BigQuery integration
- Error handling and monitoring
- Dataflow deployment support