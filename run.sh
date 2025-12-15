#!/bin/bash

# Simple script to run the Workday Dataflow Pipeline

set -e

echo "Building project..."
mvn clean package -DskipTests

if [ "$1" == "local" ]; then
    echo "Running locally with DirectRunner..."
    java -cp target/workday-dataflow-pipeline-1.0.0.jar \
        com.example.dataflow.WorkdayDataflowPipeline \
        --runner=DirectRunner \
        --workdaySoapUrl="${WORKDAY_SOAP_URL}" \
        --workdayUsername="${WORKDAY_USERNAME}" \
        --workdayPassword="${WORKDAY_PASSWORD}" \
        --workdayTenantId="${WORKDAY_TENANT_ID}" \
        --effectiveDate="${EFFECTIVE_DATE}" \
        --bigQueryTable="${BIGQUERY_TABLE}" \
        --writeDisposition="${WRITE_DISPOSITION:-WRITE_APPEND}" \
        --maxRetries=3

elif [ "$1" == "dataflow" ]; then
    echo "Running on Google Cloud Dataflow..."
    java -cp target/workday-dataflow-pipeline-1.0.0.jar \
        com.example.dataflow.WorkdayDataflowPipeline \
        --runner=DataflowRunner \
        --project="${GCP_PROJECT_ID}" \
        --region="${GCP_REGION:-us-central1}" \
        --tempLocation="${TEMP_LOCATION}" \
        --stagingLocation="${STAGING_LOCATION}" \
        --numWorkers="${NUM_WORKERS:-10}" \
        --maxNumWorkers="${MAX_NUM_WORKERS:-50}" \
        --workerMachineType="${WORKER_MACHINE_TYPE:-n1-standard-4}" \
        --jobName="workday-employee-$(date +%Y%m%d-%H%M%S)" \
        --workdaySoapUrl="${WORKDAY_SOAP_URL}" \
        --workdayUsername="${WORKDAY_USERNAME}" \
        --workdayPassword="${WORKDAY_PASSWORD}" \
        --workdayTenantId="${WORKDAY_TENANT_ID}" \
        --effectiveDate="${EFFECTIVE_DATE}" \
        --bigQueryTable="${BIGQUERY_TABLE}" \
        --writeDisposition="${WRITE_DISPOSITION:-WRITE_APPEND}" \
        --maxRetries=3

else
    echo "Usage: $0 [local|dataflow]"
    echo ""
    echo "Set environment variables:"
    echo "  WORKDAY_SOAP_URL"
    echo "  WORKDAY_USERNAME"
    echo "  WORKDAY_PASSWORD"
    echo "  WORKDAY_TENANT_ID"
    echo "  EFFECTIVE_DATE"
    echo "  BIGQUERY_TABLE"
    echo ""
    echo "For dataflow, also set:"
    echo "  GCP_PROJECT_ID"
    echo "  TEMP_LOCATION"
    echo "  STAGING_LOCATION"
    exit 1
fi

echo "Pipeline execution completed!"
