#!/bin/bash

# Workday to BigQuery Dataflow Pipeline Execution Script
# This script demonstrates how to run the pipeline locally and on Google Cloud Dataflow

set -e

# Load configuration
source config.properties 2>/dev/null || echo "Warning: config.properties not found"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Workday Employee Dataflow Pipeline${NC}"
echo -e "${GREEN}========================================${NC}"

# Check if required variables are set
if [ -z "$1" ]; then
    echo -e "${YELLOW}Usage: $0 [local|dataflow] [options]${NC}"
    echo ""
    echo "Examples:"
    echo "  $0 local"
    echo "  $0 dataflow"
    exit 1
fi

RUNNER_TYPE=$1

# Build the project
echo -e "${YELLOW}Building the project...${NC}"
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo -e "${RED}Build failed!${NC}"
    exit 1
fi

echo -e "${GREEN}Build successful!${NC}"

# Set common parameters
WORKDAY_SOAP_URL="${WORKDAY_SOAP_URL:-https://wd2-impl-services1.workday.com/ccx/service/your-tenant/Human_Resources/v38.0}"
WORKDAY_USERNAME="${WORKDAY_USERNAME:-your-username}"
WORKDAY_PASSWORD="${WORKDAY_PASSWORD:-your-password}"
WORKDAY_TENANT_ID="${WORKDAY_TENANT_ID:-your-tenant}"
EFFECTIVE_DATE="${EFFECTIVE_DATE:-2025-12-15}"
BIGQUERY_TABLE="${BIGQUERY_TABLE:-your-project:workday_data.employees}"
WRITE_DISPOSITION="${WRITE_DISPOSITION:-WRITE_APPEND}"

if [ "$RUNNER_TYPE" == "local" ]; then
    echo -e "${YELLOW}Running pipeline locally with DirectRunner...${NC}"
    
    java -cp target/workday-dataflow-pipeline-1.0.0.jar \
        com.example.dataflow.WorkdayEmployeeDataflowPipeline \
        --runner=DirectRunner \
        --workdaySoapUrl="$WORKDAY_SOAP_URL" \
        --workdayUsername="$WORKDAY_USERNAME" \
        --workdayPassword="$WORKDAY_PASSWORD" \
        --workdayTenantId="$WORKDAY_TENANT_ID" \
        --effectiveDate="$EFFECTIVE_DATE" \
        --bigQueryTable="$BIGQUERY_TABLE" \
        --writeDisposition="$WRITE_DISPOSITION" \
        --maxRetries=3

elif [ "$RUNNER_TYPE" == "dataflow" ]; then
    echo -e "${YELLOW}Running pipeline on Google Cloud Dataflow...${NC}"
    
    # Dataflow-specific parameters
    PROJECT_ID="${GCP_PROJECT_ID:-your-gcp-project}"
    REGION="${DATAFLOW_REGION:-us-central1}"
    TEMP_LOCATION="${TEMP_LOCATION:-gs://your-bucket/temp}"
    STAGING_LOCATION="${STAGING_LOCATION:-gs://your-bucket/staging}"
    NUM_WORKERS="${NUM_WORKERS:-10}"
    MAX_NUM_WORKERS="${MAX_NUM_WORKERS:-50}"
    WORKER_MACHINE_TYPE="${WORKER_MACHINE_TYPE:-n1-standard-4}"
    JOB_NAME="workday-employee-ingestion-$(date +%Y%m%d-%H%M%S)"
    
    echo "Project: $PROJECT_ID"
    echo "Region: $REGION"
    echo "Job Name: $JOB_NAME"
    
    java -cp target/workday-dataflow-pipeline-1.0.0.jar \
        com.example.dataflow.WorkdayEmployeeDataflowPipeline \
        --runner=DataflowRunner \
        --project="$PROJECT_ID" \
        --region="$REGION" \
        --jobName="$JOB_NAME" \
        --tempLocation="$TEMP_LOCATION" \
        --stagingLocation="$STAGING_LOCATION" \
        --numWorkers="$NUM_WORKERS" \
        --maxNumWorkers="$MAX_NUM_WORKERS" \
        --workerMachineType="$WORKER_MACHINE_TYPE" \
        --workdaySoapUrl="$WORKDAY_SOAP_URL" \
        --workdayUsername="$WORKDAY_USERNAME" \
        --workdayPassword="$WORKDAY_PASSWORD" \
        --workdayTenantId="$WORKDAY_TENANT_ID" \
        --effectiveDate="$EFFECTIVE_DATE" \
        --bigQueryTable="$BIGQUERY_TABLE" \
        --writeDisposition="$WRITE_DISPOSITION" \
        --maxRetries=3 \
        --estimatedTotalCount=0
    
    echo -e "${GREEN}Dataflow job submitted successfully!${NC}"
    echo "Monitor your job at: https://console.cloud.google.com/dataflow/jobs/$REGION/$JOB_NAME?project=$PROJECT_ID"
else
    echo -e "${RED}Invalid runner type. Use 'local' or 'dataflow'${NC}"
    exit 1
fi

echo -e "${GREEN}Pipeline execution completed!${NC}"
