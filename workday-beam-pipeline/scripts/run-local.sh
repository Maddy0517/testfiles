#!/bin/bash

# Script to run the pipeline locally using DirectRunner

# Set your configuration values here
WORKDAY_ENDPOINT="https://wd2-impl-services1.workday.com"
WORKDAY_TENANT="your_tenant_name"
WORKDAY_USERNAME="your_username"
WORKDAY_PASSWORD="your_password"
SERVICE_NAME="Human_Resources"
OPERATION_NAME="Get_Workers"
BIGQUERY_PROJECT="your-gcp-project-id"
BIGQUERY_DATASET="workday_data"
BIGQUERY_TABLE="workers"
TRANSFORMATION_TYPE="worker"

# Build the project
echo "Building the project..."
mvn clean compile

# Run the pipeline
echo "Running pipeline locally..."
mvn exec:java -Dexec.mainClass=com.example.workday.WorkdayToBigQueryPipeline \
  -Dexec.args="--runner=DirectRunner \
    --workdayEndpoint=${WORKDAY_ENDPOINT} \
    --workdayUsername=${WORKDAY_USERNAME} \
    --workdayPassword=${WORKDAY_PASSWORD} \
    --workdayTenant=${WORKDAY_TENANT} \
    --serviceName=${SERVICE_NAME} \
    --operationName=${OPERATION_NAME} \
    --bigQueryProject=${BIGQUERY_PROJECT} \
    --bigQueryDataset=${BIGQUERY_DATASET} \
    --bigQueryTable=${BIGQUERY_TABLE} \
    --transformationType=${TRANSFORMATION_TYPE} \
    --pageSize=50 \
    --maxRetries=3"