#!/bin/bash
# Deploy GCS to BigQuery Cloud Function

set -e

# Read config from properties file
CONFIG_FILE="config/application-dev.properties"
PROJECT_ID=$(grep "gcp.project.id" $CONFIG_FILE | cut -d'=' -f2 | tr -d ' ')
BUCKET_NAME=$(grep "gcs.bucket.name" $CONFIG_FILE | cut -d'=' -f2 | tr -d ' ')
DATASET_ID=$(grep "bigquery.dataset.id" $CONFIG_FILE | cut -d'=' -f2 | tr -d ' ')
TABLE_ID=$(grep "bigquery.table.id" $CONFIG_FILE | cut -d'=' -f2 | tr -d ' ')

REGION="${GCP_REGION:-us-central1}"
FUNCTION_NAME="process-csv-files"

echo "Deploying to Project: $PROJECT_ID, Region: $REGION"

# Set project
gcloud config set project $PROJECT_ID

# Enable APIs
gcloud services enable cloudfunctions.googleapis.com storage.googleapis.com bigquery.googleapis.com

# Build
mvn clean package -DskipTests

# Create BigQuery table if not exists
bq mk --dataset ${PROJECT_ID}:${DATASET_ID} 2>/dev/null || true
bq mk --table --schema 'employee_id:STRING,upload_date:TIMESTAMP,file_name:STRING,hum_code:STRING,bucket_name:STRING,processed_at:TIMESTAMP' ${PROJECT_ID}:${DATASET_ID}.${TABLE_ID} 2>/dev/null || true

# Deploy
gcloud functions deploy $FUNCTION_NAME \
    --gen2 \
    --runtime=java17 \
    --region=$REGION \
    --source=. \
    --entry-point=com.example.gcf.GcsFileToBigQueryFunction \
    --trigger-http \
    --allow-unauthenticated \
    --memory=512MB \
    --timeout=300s

echo "Done! Function URL:"
gcloud functions describe $FUNCTION_NAME --region=$REGION --gen2 --format='value(serviceConfig.uri)'
