#!/bin/bash

# Script to run the pipeline on Google Cloud Dataflow

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

# Dataflow specific settings
GCP_PROJECT="your-gcp-project-id"
GCS_BUCKET="your-gcs-bucket"
REGION="us-central1"
JOB_NAME="workday-to-bigquery-$(date +%Y%m%d-%H%M%S)"

# Build the project and create fat JAR
echo "Building the project..."
mvn clean package -DskipTests

# Run the pipeline on Dataflow
echo "Submitting job to Dataflow..."
mvn exec:java -Dexec.mainClass=com.example.workday.WorkdayToBigQueryPipeline \
  -Dexec.args="--runner=DataflowRunner \
    --project=${GCP_PROJECT} \
    --region=${REGION} \
    --jobName=${JOB_NAME} \
    --stagingLocation=gs://${GCS_BUCKET}/staging \
    --tempLocation=gs://${GCS_BUCKET}/temp \
    --gcpTempLocation=gs://${GCS_BUCKET}/temp \
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
    --pageSize=100 \
    --maxRetries=3 \
    --maxNumWorkers=10 \
    --autoscalingAlgorithm=THROUGHPUT_BASED"

echo "Job submitted. Check the Dataflow console for progress."