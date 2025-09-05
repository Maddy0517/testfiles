#!/bin/bash

# Script to run the Workday to BigQuery pipeline on Google Cloud Dataflow
# Usage: ./run-dataflow.sh

# Set your GCP project and region
PROJECT_ID="your-gcp-project-id"
REGION="us-central1"
BUCKET="your-gcs-bucket"

echo "Running Workday to BigQuery Pipeline on Dataflow..."

mvn compile exec:java \
  -Dexec.mainClass=com.workday.dataflow.WorkdayToBigQueryPipeline \
  -Dexec.args="--propertiesFile=config/pipeline.properties \
               --runner=DataflowRunner \
               --project=${PROJECT_ID} \
               --region=${REGION} \
               --tempLocation=gs://${BUCKET}/temp \
               --stagingLocation=gs://${BUCKET}/staging \
               --jobName=workday-to-bigquery-$(date +%Y%m%d-%H%M%S) \
               --maxNumWorkers=10 \
               --machineType=n1-standard-2 \
               --diskSizeGb=100"