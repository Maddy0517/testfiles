# Workday to BigQuery Data Ingestion Pipeline

An Apache Beam pipeline that extracts worker data from Workday SOAP API and loads it into Google Cloud BigQuery. The pipeline supports both local development (Eclipse IDE) and production deployment on Google Cloud Dataflow.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
- [Local Development Setup](#local-development-setup)
- [Running the Pipeline](#running-the-pipeline)
- [Google Cloud Dataflow Deployment](#google-cloud-dataflow-deployment)
- [Monitoring and Troubleshooting](#monitoring-and-troubleshooting)
- [Data Schema](#data-schema)
- [Error Handling](#error-handling)
- [Performance Tuning](#performance-tuning)

## Overview

This pipeline performs the following operations:

1. **Authentication**: Connects to Workday SOAP API using provided credentials
2. **Data Extraction**: Fetches worker data with pagination support
3. **Data Transformation**: Cleans, validates, and enriches worker data
4. **Data Loading**: Writes transformed data to BigQuery with proper schema

### Key Features

- ✅ SOAP API authentication and pagination
- ✅ **Effective date filtering** for incremental data extraction
- ✅ Configurable batch processing
- ✅ Retry logic for failed API calls
- ✅ Data validation and cleansing
- ✅ BigQuery schema management
- ✅ Error handling and logging
- ✅ Support for both local and cloud execution
- ✅ Properties-based configuration

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐    ┌──────────────┐
│   Workday API   │───▶│  Apache Beam     │───▶│  Data Transform │───▶│   BigQuery   │
│   (SOAP)        │    │  Pipeline        │    │  & Validation   │    │   Table      │
└─────────────────┘    └──────────────────┘    └─────────────────┘    └──────────────┘
         │                        │                        │                    │
         │                        │                        │                    │
    ┌────▼────┐              ┌────▼────┐              ┌────▼────┐          ┌────▼────┐
    │ Auth &  │              │ Batch   │              │ Schema  │          │ Write   │
    │ Paging  │              │ Process │              │ Enforce │          │ Modes   │
    └─────────┘              └─────────┘              └─────────┘          └─────────┘
```

## Prerequisites

### Software Requirements

- **Java 11** or higher
- **Apache Maven 3.6+**
- **Google Cloud SDK** (for Dataflow deployment)
- **Eclipse IDE** (optional, for local development)

### Google Cloud Setup

1. Create a Google Cloud Project
2. Enable the following APIs:
   - BigQuery API
   - Dataflow API
   - Cloud Storage API (for Dataflow staging)
3. Create a service account with the following roles:
   - BigQuery Data Editor
   - BigQuery Job User
   - Dataflow Developer
   - Storage Admin (for staging bucket)
4. Download the service account key JSON file

### Workday Access

- Workday tenant URL
- Integration user credentials (username/password)
- Appropriate permissions for Human Resources web service

## Project Structure

```
workday-bigquery-pipeline/
├── pom.xml                                 # Maven configuration
├── README.md                              # This documentation
├── run-local.sh                           # Local execution script
├── run-dataflow.sh                        # Dataflow execution script
├── config/
│   ├── pipeline.properties               # Production configuration
│   ├── pipeline-local.properties         # Local development configuration
│   └── bigquery_schema.json              # BigQuery table schema
├── src/
│   ├── main/
│   │   ├── java/com/workday/dataflow/
│   │   │   ├── WorkdayToBigQueryPipeline.java    # Main pipeline class
│   │   │   ├── WorkdayToBigQueryOptions.java     # Pipeline options
│   │   │   ├── ConfigurationManager.java        # Configuration loader
│   │   │   ├── WorkdayApiClient.java            # SOAP API client
│   │   │   ├── WorkerData.java                  # Data model
│   │   │   ├── WorkdaySourceTransform.java      # Source transform
│   │   │   ├── WorkerDataTransform.java         # Data transformation
│   │   │   └── BigQuerySinkTransform.java       # BigQuery sink
│   │   └── resources/
│   └── test/
│       └── java/com/workday/dataflow/
└── target/                                # Maven build output
```

## Configuration

### Properties File Configuration

The pipeline uses properties files for configuration. Two sample files are provided:

#### Production Configuration (`config/pipeline.properties`)

```properties
# Workday SOAP API Configuration
workday.soap.url=https://your-tenant.workday.com/ccx/service/your-tenant/Human_Resources/v40.0
workday.username=your-username@your-tenant
workday.password=your-password
workday.tenant=your-tenant
workday.api.version=v40.0

# BigQuery Configuration
bigquery.project=your-gcp-project-id
bigquery.dataset=workday_data
bigquery.table=workers
bigquery.write.disposition=WRITE_APPEND
bigquery.create.disposition=CREATE_IF_NEEDED

# API Configuration
api.batch.size=100
api.max.retries=3
api.request.timeout=30000
api.enable.pagination=true
api.page.size=100

# Date Filter Configuration
workday.effective.from.date=2024-01-01
workday.effective.to.date=2024-12-31
workday.include.effective.from.date=true
workday.include.effective.to.date=true
```

#### Local Development Configuration (`config/pipeline-local.properties`)

Similar to production but with smaller batch sizes and different dataset names for testing.

### Environment Variables (Alternative)

You can also set configuration via environment variables:

```bash
export WORKDAY_SOAP_URL="https://your-tenant.workday.com/..."
export WORKDAY_USERNAME="your-username@your-tenant"
export WORKDAY_PASSWORD="your-password"
export BIGQUERY_PROJECT="your-gcp-project-id"
export BIGQUERY_DATASET="workday_data"
export BIGQUERY_TABLE="workers"
```

## Date Filtering

The pipeline supports filtering worker data by effective dates, which is crucial for incremental data loading and processing only changed records.

### Date Filter Configuration

Configure date filters in your properties file:

```properties
# Date Filter Configuration
workday.effective.from.date=2024-01-01    # Start date (YYYY-MM-DD format)
workday.effective.to.date=2024-12-31      # End date (YYYY-MM-DD format)
workday.include.effective.from.date=true  # Include records from start date
workday.include.effective.to.date=true    # Include records up to end date
```

### Date Filter Options

1. **Date Range Filtering**:
   ```properties
   workday.effective.from.date=2024-01-01
   workday.effective.to.date=2024-12-31
   ```
   Gets workers with effective dates between January 1, 2024, and December 31, 2024.

2. **From Date Only**:
   ```properties
   workday.effective.from.date=2024-01-01
   workday.effective.to.date=
   ```
   Gets workers with effective dates from January 1, 2024, onwards.

3. **To Date Only**:
   ```properties
   workday.effective.from.date=
   workday.effective.to.date=2024-12-31
   ```
   Gets workers with effective dates up to December 31, 2024.

4. **No Date Filtering** (default):
   ```properties
   workday.effective.from.date=
   workday.effective.to.date=
   ```
   Gets all workers regardless of effective date.

### Use Cases

- **Initial Load**: Leave date filters empty to get all historical data
- **Incremental Load**: Set `effective.from.date` to last successful run date
- **Historical Analysis**: Set specific date ranges for historical data analysis
- **Daily Updates**: Set date range to yesterday or last few days

### SOAP API Implementation

The pipeline implements date filtering using Workday's SOAP API features:

- **Transaction_Log_Criteria_Data**: For filtering by update/modification dates
- **As_Of_Effective_Date**: For point-in-time data queries
- **As_Of_Entry_DateTime**: For effective date filtering in response filters

## Local Development Setup

### Step 1: Clone and Build

```bash
# Clone the repository
git clone <repository-url>
cd workday-bigquery-pipeline

# Build the project
mvn clean compile
```

### Step 2: Configure Properties

1. Copy the sample configuration file:
   ```bash
   cp config/pipeline-local.properties config/my-pipeline.properties
   ```

2. Edit `config/my-pipeline.properties` with your actual credentials:
   ```properties
   workday.soap.url=https://your-actual-tenant.workday.com/ccx/service/your-tenant/Human_Resources/v40.0
   workday.username=your-actual-username@your-tenant
   workday.password=your-actual-password
   bigquery.project=your-actual-gcp-project
   bigquery.dataset=workday_data_dev
   bigquery.table=workers_dev
   ```

### Step 3: Set up Google Cloud Authentication

```bash
# Set the path to your service account key
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/your/service-account-key.json"

# Or authenticate with gcloud
gcloud auth application-default login
```

### Step 4: Create BigQuery Dataset

```bash
bq mk --dataset your-gcp-project:workday_data_dev
```

## Running the Pipeline

### Method 1: Using Maven (Recommended)

#### Local Execution (DirectRunner)

```bash
mvn compile exec:java \
  -Dexec.mainClass=com.workday.dataflow.WorkdayToBigQueryPipeline \
  -Dexec.args="--propertiesFile=config/my-pipeline.properties"
```

#### Running with Date Filters

You can also pass date filters directly as command line arguments:

```bash
mvn compile exec:java \
  -Dexec.mainClass=com.workday.dataflow.WorkdayToBigQueryPipeline \
  -Dexec.args="--propertiesFile=config/my-pipeline.properties \
               --effectiveFromDate=2024-01-01 \
               --effectiveToDate=2024-12-31"
```

#### Using the Convenience Script

```bash
# Make sure to edit the script with your configuration file
./run-local.sh
```

### Method 2: Eclipse IDE Setup

1. **Import Project**:
   - File → Import → Existing Maven Projects
   - Browse to the project directory
   - Click Finish

2. **Configure Run Configuration**:
   - Right-click on `WorkdayToBigQueryPipeline.java`
   - Run As → Java Application
   - Edit the run configuration to add program arguments:
     ```
     --propertiesFile=config/my-pipeline.properties
     ```

3. **Run the Pipeline**:
   - Click Run to execute the pipeline locally

### Method 3: Command Line with JAR

```bash
# Build the JAR
mvn clean package

# Run the JAR
java -cp target/workday-bigquery-pipeline-1.0.0.jar \
  com.workday.dataflow.WorkdayToBigQueryPipeline \
  --propertiesFile=config/my-pipeline.properties
```

## Google Cloud Dataflow Deployment

### Step 1: Prepare GCS Bucket

```bash
# Create a bucket for Dataflow staging
gsutil mb gs://your-dataflow-staging-bucket
```

### Step 2: Update Configuration

Edit `config/pipeline.properties` for production settings:

```properties
# Add Dataflow-specific settings
bigquery.write.disposition=WRITE_APPEND
api.batch.size=500
api.page.size=500
```

### Step 3: Deploy to Dataflow

#### Using Maven

```bash
mvn compile exec:java \
  -Dexec.mainClass=com.workday.dataflow.WorkdayToBigQueryPipeline \
  -Dexec.args="--propertiesFile=config/pipeline.properties \
               --runner=DataflowRunner \
               --project=your-gcp-project-id \
               --region=us-central1 \
               --tempLocation=gs://your-bucket/temp \
               --stagingLocation=gs://your-bucket/staging \
               --jobName=workday-to-bigquery-$(date +%Y%m%d-%H%M%S) \
               --maxNumWorkers=10"
```

#### Using the Convenience Script

```bash
# Edit run-dataflow.sh with your settings first
./run-dataflow.sh
```

### Step 4: Monitor the Job

```bash
# List running jobs
gcloud dataflow jobs list --region=us-central1

# View job details
gcloud dataflow jobs describe JOB_ID --region=us-central1

# View logs
gcloud dataflow jobs logs JOB_ID --region=us-central1
```

## Monitoring and Troubleshooting

### Logging

The pipeline uses SLF4J for logging. Key log points include:

- API connection status
- Batch processing progress
- Data validation errors
- BigQuery write status

### Common Issues and Solutions

#### 1. Authentication Errors

**Error**: `401 Unauthorized` from Workday API

**Solution**:
- Verify username/password in properties file
- Check if integration user has proper permissions
- Ensure tenant name is correct in the URL

#### 2. BigQuery Permission Errors

**Error**: `Access Denied` when writing to BigQuery

**Solution**:
- Verify service account has BigQuery Data Editor role
- Check if dataset exists and is accessible
- Ensure project ID is correct

#### 3. Memory Issues

**Error**: `OutOfMemoryError` during processing

**Solution**:
- Reduce `api.batch.size` and `api.page.size`
- Increase JVM heap size: `-Xmx4g`
- Use Dataflow for larger datasets

#### 4. Network Timeouts

**Error**: `SocketTimeoutException`

**Solution**:
- Increase `api.request.timeout` in properties
- Check network connectivity to Workday
- Implement exponential backoff (already included)

#### 5. Date Filter Errors

**Error**: `Invalid effective from date format`

**Solution**:
- Ensure date format is YYYY-MM-DD (e.g., 2024-01-01)
- Verify that from date is not after to date
- Check for typos in property names

**Error**: `No data returned with date filters`

**Solution**:
- Verify the date range contains actual data changes
- Check if the effective dates align with Workday's data model
- Try expanding the date range for testing

### Monitoring Queries

Use these BigQuery queries to monitor data quality:

```sql
-- Check data freshness
SELECT 
  MAX(processed_timestamp) as last_processed,
  COUNT(*) as total_records
FROM `your-project.workday_data.workers`;

-- Data quality checks
SELECT 
  status,
  COUNT(*) as count,
  AVG(salary) as avg_salary
FROM `your-project.workday_data.workers`
GROUP BY status;

-- Identify duplicates
SELECT 
  employee_id,
  COUNT(*) as count
FROM `your-project.workday_data.workers`
GROUP BY employee_id
HAVING COUNT(*) > 1;

-- Monitor date range coverage
SELECT 
  MIN(hire_date) as earliest_hire_date,
  MAX(hire_date) as latest_hire_date,
  MIN(processed_timestamp) as first_processed,
  MAX(processed_timestamp) as last_processed,
  COUNT(DISTINCT DATE(processed_timestamp)) as processing_days
FROM `your-project.workday_data.workers`;

-- Check for data in specific date ranges
SELECT 
  DATE(processed_timestamp) as processing_date,
  COUNT(*) as records_processed,
  COUNT(DISTINCT employee_id) as unique_employees
FROM `your-project.workday_data.workers`
WHERE DATE(processed_timestamp) >= '2024-01-01'
GROUP BY DATE(processed_timestamp)
ORDER BY processing_date DESC;
```

## Data Schema

The BigQuery table uses the following schema:

| Field | Type | Mode | Description |
|-------|------|------|-------------|
| worker_id | STRING | REQUIRED | Unique identifier from Workday |
| employee_id | STRING | REQUIRED | Employee ID number |
| first_name | STRING | NULLABLE | Employee first name |
| last_name | STRING | NULLABLE | Employee last name |
| full_name | STRING | NULLABLE | Computed full name |
| email | STRING | NULLABLE | Email address |
| job_title | STRING | NULLABLE | Job title |
| department | STRING | NULLABLE | Department |
| hire_date | DATE | NULLABLE | Hire date |
| status | STRING | NULLABLE | Employment status |
| salary | FLOAT | NULLABLE | Salary amount |
| location | STRING | NULLABLE | Work location |
| manager_id | STRING | NULLABLE | Manager's worker ID |
| last_modified | TIMESTAMP | NULLABLE | Last modified in Workday |
| processed_timestamp | TIMESTAMP | REQUIRED | Pipeline processing time |
| years_of_service | INTEGER | NULLABLE | Computed years of service |
| salary_band | STRING | NULLABLE | Computed salary category |

## Error Handling

The pipeline implements comprehensive error handling:

### API Errors
- Automatic retry with exponential backoff
- Configurable retry attempts
- Detailed error logging

### Data Validation Errors
- Invalid records are logged but don't stop the pipeline
- Null/empty required fields are handled gracefully
- Data type validation and conversion

### BigQuery Errors
- Failed inserts are logged to console
- Transient errors are automatically retried
- Schema mismatches are reported clearly

## Performance Tuning

### Configuration Parameters

Adjust these parameters based on your needs:

```properties
# API Performance
api.batch.size=100          # Records per API call
api.page.size=100           # Pagination size
api.request.timeout=30000   # Request timeout (ms)
api.max.retries=3           # Retry attempts

# Dataflow Performance (for cloud execution)
maxNumWorkers=10            # Maximum worker instances
machineType=n1-standard-2   # Worker machine type
diskSizeGb=100              # Worker disk size
```

### Recommendations

- **Local Development**: Use small batch sizes (10-50)
- **Production**: Use larger batch sizes (500-1000)
- **Large Datasets**: Use Dataflow with multiple workers
- **Frequent Updates**: Use `WRITE_APPEND` mode with date filters
- **Full Refresh**: Use `WRITE_TRUNCATE` mode without date filters
- **Incremental Loading**: Use date filters to process only changed data
- **Daily Runs**: Set effective date range to previous day
- **Catch-up Processing**: Use wider date ranges with smaller batch sizes

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions:

1. Check the troubleshooting section above
2. Review the logs for error messages
3. Open an issue with detailed error information
4. Include configuration (with sensitive data redacted)

---

**Last Updated**: December 2024
**Version**: 1.0.0