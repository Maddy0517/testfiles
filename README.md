# Workday Employee Data Pipeline

A robust Apache Beam pipeline for extracting employee data from Workday SOAP API and loading it into Google BigQuery via Google Cloud Dataflow.

## Architecture Overview

This pipeline implements a scalable solution for both historical and incremental data loads from Workday's SOAP API with the following features:

- **Pagination Handling**: Efficiently processes Workday's 999 records per page limit with parallel processing
- **Historical vs Incremental Loads**: Supports both full historical data pulls and incremental updates based on effective dates
- **Error Handling**: Robust retry logic and error handling for API failures
- **BigQuery Integration**: Optimized loading with proper schema management and partitioning
- **Cloud Deployment**: Ready-to-deploy on Google Cloud Dataflow with automated scaling

## Key Features

### 1. Workday SOAP API Integration
- Handles Workday's SOAP API authentication and pagination
- Supports effective date filtering for incremental loads
- Parallel processing of multiple pages for improved performance
- Configurable request limits to respect API rate limits

### 2. Apache Beam Pipeline Architecture
- **GeneratePageNumbersFn**: Determines total pages and generates page numbers for parallel processing
- **ExtractWorkdayEmployeesFn**: Extracts employee data from specific pages with retry logic
- **TransformToBigQueryFn**: Transforms Workday employee objects to BigQuery TableRow format
- **BigQuery Loading**: Streams data directly to BigQuery with proper schema handling

### 3. Data Processing Modes
- **Historical Mode**: Full data load with `WRITE_TRUNCATE` disposition
- **Incremental Mode**: Delta load based on effective date with `WRITE_APPEND` disposition
- **Configurable Lookback**: Adjustable number of days for incremental processing

## Project Structure

```
src/main/java/com/company/workday/
├── WorkdayEmployeePipeline.java      # Main pipeline class
├── WorkdayConfig.java                # Configuration class
├── GeneratePageNumbersFn.java        # Page number generation DoFn
├── ExtractWorkdayEmployeesFn.java    # Data extraction DoFn
├── TransformToBigQueryFn.java        # BigQuery transformation DoFn
├── WorkdaySOAPClient.java            # SOAP API client
├── WorkdayEmployee.java              # Employee data model
├── WorkdayResponse.java              # API response wrapper
└── BigQuerySchemaUtil.java           # BigQuery schema utilities
```

## Setup and Configuration

### Prerequisites

1. **Google Cloud Platform Setup**:
   - GCP project with billing enabled
   - Dataflow API enabled
   - BigQuery API enabled
   - Cloud Storage buckets for temp and staging
   - Service account with appropriate permissions

2. **Workday API Access**:
   - Workday SOAP API endpoint URL
   - Valid Workday credentials (username/password)
   - Workday tenant information

3. **Development Environment**:
   - Java 11 or higher
   - Maven 3.6+
   - Google Cloud SDK

### Environment Variables

Set the following environment variables before deployment:

```bash
export PROJECT_ID="your-gcp-project-id"
export WORKDAY_USERNAME="your-workday-username"
export WORKDAY_PASSWORD="your-workday-password"
export WORKDAY_ENDPOINT="https://wd2-impl-services1.workday.com/ccx/service/your-tenant"
export WORKDAY_TENANT="your-tenant"
export BIGQUERY_DATASET="workday_data"
export BIGQUERY_TABLE="employees"
export TEMP_LOCATION="gs://your-temp-bucket/temp"
export STAGING_LOCATION="gs://your-staging-bucket/staging"
export SERVICE_ACCOUNT="dataflow-service-account@your-project.iam.gserviceaccount.com"
```

## Deployment Guide

### 1. Build the Project

```bash
./deploy.sh build
```

### 2. Run Local Test

```bash
./deploy.sh test
```

### 3. Deploy Historical Load (Full Data)

```bash
./deploy.sh historical
```

### 4. Deploy Incremental Load

```bash
./deploy.sh incremental
```

### 5. Set Up Scheduled Incremental Loads

```bash
./deploy.sh schedule
```

### 6. Complete Deployment (Historical + Scheduling)

```bash
./deploy.sh all
```

## Pipeline Parameters

| Parameter | Description | Required | Default |
|-----------|-------------|----------|---------|
| `workdayEndpoint` | Workday SOAP API endpoint URL | Yes | - |
| `workdayUsername` | Workday username | Yes | - |
| `workdayPassword` | Workday password | Yes | - |
| `workdayTenant` | Workday tenant name | Yes | - |
| `bigQueryDataset` | BigQuery dataset ID | Yes | - |
| `bigQueryTable` | BigQuery table ID | Yes | - |
| `effectiveAsOfDate` | Effective date (YYYY-MM-DD) | No | Current date |
| `loadMode` | HISTORICAL or INCREMENTAL | No | INCREMENTAL |
| `incrementalDays` | Days to look back for incremental | No | 1 |
| `maxParallelRequests` | Max parallel API requests | No | 5 |

## BigQuery Schema

The pipeline creates a BigQuery table with the following schema:

```sql
CREATE TABLE workday_data.employees (
  worker_id STRING,
  employee_id STRING,
  first_name STRING,
  last_name STRING,
  full_name STRING,
  email STRING,
  phone_number STRING,
  employment_status STRING,
  job_title STRING,
  department STRING,
  location STRING,
  manager STRING,
  manager_id STRING,
  cost_center STRING,
  business_unit STRING,
  annual_salary FLOAT64,
  currency STRING,
  pay_group STRING,
  hire_date DATE,
  termination_date DATE,
  effective_date DATE,
  last_modified_by STRING,
  last_modified_date STRING,
  processed_timestamp TIMESTAMP
)
PARTITION BY DATE(processed_timestamp)
CLUSTER BY department, location;
```

## Performance Optimization

### 1. Parallel Processing
- The pipeline automatically determines the total number of pages and processes them in parallel
- Configurable `maxParallelRequests` to respect Workday API rate limits
- Each worker processes individual pages independently

### 2. BigQuery Optimization
- Table partitioned by `processed_timestamp` for efficient querying
- Clustered by `department` and `location` for common query patterns
- Streaming inserts for real-time data availability

### 3. Error Handling
- Retry logic with exponential backoff for API failures
- Comprehensive logging for monitoring and debugging
- Dead letter queue pattern for failed records (can be extended)

## Monitoring and Alerting

### Dataflow Monitoring
- Monitor job status in Google Cloud Console
- Set up alerts for job failures or performance issues
- Use Stackdriver logging for detailed pipeline logs

### BigQuery Monitoring
- Monitor data freshness and quality
- Set up alerts for missing daily loads
- Track data volume trends

## Best Practices

### 1. Security
- Store credentials in Google Secret Manager instead of environment variables
- Use IAM service accounts with minimal required permissions
- Enable audit logging for all API calls

### 2. Data Quality
- Implement data validation checks before loading to BigQuery
- Set up data quality monitoring and alerting
- Maintain data lineage and processing metadata

### 3. Cost Optimization
- Use appropriate Dataflow machine types for your workload
- Implement autoscaling for variable data volumes
- Consider using Dataflow Flex Templates for better resource utilization

## Troubleshooting

### Common Issues

1. **SOAP Authentication Failures**
   - Verify credentials and endpoint URL
   - Check if Workday account has necessary permissions
   - Ensure tenant name is correct

2. **Pagination Issues**
   - Monitor logs for page processing failures
   - Adjust `maxParallelRequests` if hitting rate limits
   - Check for data consistency across pages

3. **BigQuery Loading Errors**
   - Verify schema compatibility
   - Check for data type conversion issues
   - Monitor streaming insert quotas

### Debugging

Enable debug logging by setting the log level:

```bash
--experiments=enable_debug_logging
```

Check Dataflow job logs in Google Cloud Console for detailed error information.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions:
1. Check the troubleshooting section
2. Review Dataflow and BigQuery documentation
3. Open an issue in the project repository