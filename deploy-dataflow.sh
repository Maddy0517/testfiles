#!/bin/bash

# Dataflow Deployment Script for Workday Pipeline
# This script builds and deploys the Apache Beam pipeline to Google Cloud Dataflow

set -e

# Configuration variables - Update these with your values
PROJECT_ID="${GCP_PROJECT_ID:-your-project-id}"
REGION="${GCP_REGION:-us-central1}"
TEMP_LOCATION="${GCS_TEMP_LOCATION:-gs://your-bucket/temp}"
STAGING_LOCATION="${GCS_STAGING_LOCATION:-gs://your-bucket/staging}"
DATASET="${BIGQUERY_DATASET:-workday_data}"
TABLE="${BIGQUERY_TABLE:-employees}"

# Workday configuration
WORKDAY_ENDPOINT="${WORKDAY_ENDPOINT:-https://wd-services.myworkday.com/ccx/service/tenant/Human_Resources/v41.0}"
WORKDAY_USERNAME="${WORKDAY_USERNAME}"
WORKDAY_PASSWORD="${WORKDAY_PASSWORD}"
WORKDAY_TENANT="${WORKDAY_TENANT}"

# Load type: HISTORICAL or INCREMENTAL
LOAD_TYPE="${LOAD_TYPE:-INCREMENTAL}"
EFFECTIVE_DATE="${EFFECTIVE_DATE:-$(date +%Y-%m-%d)}"
LAST_MODIFIED_FROM="${LAST_MODIFIED_FROM}"

# Dataflow job configuration
JOB_NAME="workday-pipeline-$(date +%Y%m%d-%H%M%S)"
MAX_WORKERS="${MAX_WORKERS:-10}"
NUM_WORKERS="${NUM_WORKERS:-2}"
WORKER_MACHINE_TYPE="${WORKER_MACHINE_TYPE:-n1-standard-2}"
AUTOSCALING_ALGORITHM="${AUTOSCALING_ALGORITHM:-THROUGHPUT_BASED}"

# Network configuration (optional)
NETWORK="${GCP_NETWORK}"
SUBNETWORK="${GCP_SUBNETWORK}"
SERVICE_ACCOUNT="${GCP_SERVICE_ACCOUNT}"

# Proxy configuration (optional)
USE_PROXY="${USE_PROXY:-false}"
PROXY_HOST="${PROXY_HOST}"
PROXY_PORT="${PROXY_PORT:-8080}"

echo "========================================="
echo "Workday to BigQuery Pipeline Deployment"
echo "========================================="
echo "Project: $PROJECT_ID"
echo "Region: $REGION"
echo "Dataset: $DATASET"
echo "Table: $TABLE"
echo "Load Type: $LOAD_TYPE"
echo "Job Name: $JOB_NAME"
echo "========================================="

# Validate required parameters
if [ -z "$WORKDAY_USERNAME" ] || [ -z "$WORKDAY_PASSWORD" ] || [ -z "$WORKDAY_TENANT" ]; then
    echo "Error: Workday credentials are required"
    echo "Please set WORKDAY_USERNAME, WORKDAY_PASSWORD, and WORKDAY_TENANT environment variables"
    exit 1
fi

if [ -z "$PROJECT_ID" ] || [ "$PROJECT_ID" == "your-project-id" ]; then
    echo "Error: GCP_PROJECT_ID is required"
    exit 1
fi

if [ -z "$TEMP_LOCATION" ] || [ "$TEMP_LOCATION" == "gs://your-bucket/temp" ]; then
    echo "Error: GCS_TEMP_LOCATION is required"
    exit 1
fi

# Build the project
echo "Building the project..."
mvn clean compile

# Build the arguments
PIPELINE_ARGS=(
    "--project=$PROJECT_ID"
    "--region=$REGION"
    "--runner=DataflowRunner"
    "--jobName=$JOB_NAME"
    "--tempLocation=$TEMP_LOCATION"
    "--stagingLocation=$STAGING_LOCATION"
    "--maxNumWorkers=$MAX_WORKERS"
    "--numWorkers=$NUM_WORKERS"
    "--workerMachineType=$WORKER_MACHINE_TYPE"
    "--autoscalingAlgorithm=$AUTOSCALING_ALGORITHM"
    "--workdayEndpoint=$WORKDAY_ENDPOINT"
    "--workdayUsername=$WORKDAY_USERNAME"
    "--workdayPassword=$WORKDAY_PASSWORD"
    "--workdayTenant=$WORKDAY_TENANT"
    "--bigQueryDataset=$DATASET"
    "--bigQueryTable=$TABLE"
    "--loadType=$LOAD_TYPE"
    "--effectiveDate=$EFFECTIVE_DATE"
    "--tempGcsBucket=$TEMP_LOCATION"
    "--enableStreamingEngine"
)

# Add optional parameters
if [ ! -z "$LAST_MODIFIED_FROM" ]; then
    PIPELINE_ARGS+=("--lastModifiedFrom=$LAST_MODIFIED_FROM")
fi

if [ ! -z "$NETWORK" ]; then
    PIPELINE_ARGS+=("--network=$NETWORK")
fi

if [ ! -z "$SUBNETWORK" ]; then
    PIPELINE_ARGS+=("--subnetwork=$SUBNETWORK")
fi

if [ ! -z "$SERVICE_ACCOUNT" ]; then
    PIPELINE_ARGS+=("--serviceAccount=$SERVICE_ACCOUNT")
fi

if [ "$USE_PROXY" == "true" ] && [ ! -z "$PROXY_HOST" ]; then
    PIPELINE_ARGS+=("--useProxy=true")
    PIPELINE_ARGS+=("--proxyHost=$PROXY_HOST")
    PIPELINE_ARGS+=("--proxyPort=$PROXY_PORT")
fi

# Add write disposition based on load type
if [ "$LOAD_TYPE" == "HISTORICAL" ]; then
    PIPELINE_ARGS+=("--writeDisposition=WRITE_TRUNCATE")
else
    PIPELINE_ARGS+=("--writeDisposition=WRITE_APPEND")
fi

# Deploy to Dataflow
echo "Deploying to Google Cloud Dataflow..."
echo "Arguments: ${PIPELINE_ARGS[@]}"

mvn compile exec:java \
    -Dexec.mainClass="com.example.workday.WorkdayPipeline" \
    -Dexec.args="${PIPELINE_ARGS[*]}"

echo "========================================="
echo "Pipeline deployed successfully!"
echo "Job Name: $JOB_NAME"
echo "Monitor at: https://console.cloud.google.com/dataflow/jobs/$REGION/$JOB_NAME?project=$PROJECT_ID"
echo "========================================="