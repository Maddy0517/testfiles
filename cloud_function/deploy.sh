#!/bin/bash

# ==============================================================================
# Google Cloud Function Deployment Script (Java)
# Deploys the GCS to BigQuery processing function
# ==============================================================================

set -e

# Configuration - UPDATE THESE VALUES
PROJECT_ID="${GCP_PROJECT_ID:-your-project-id}"
REGION="${GCP_REGION:-us-central1}"
BUCKET_NAME="${GCS_BUCKET_NAME:-your-trigger-bucket}"
FUNCTION_NAME="${FUNCTION_NAME:-process-file-uploads}"
DATASET_ID="${BQ_DATASET_ID:-your_dataset}"
TABLE_ID="${BQ_TABLE_ID:-file_uploads}"
RUNTIME="java17"
MEMORY="512MB"
TIMEOUT="120s"
ENTRY_POINT="com.example.gcf.GcsFileToBigQueryFunction"

echo "=============================================="
echo "Deploying Cloud Function: ${FUNCTION_NAME}"
echo "=============================================="
echo "Project:     ${PROJECT_ID}"
echo "Region:      ${REGION}"
echo "Bucket:      ${BUCKET_NAME}"
echo "Dataset:     ${DATASET_ID}"
echo "Table:       ${TABLE_ID}"
echo "Runtime:     ${RUNTIME}"
echo "Entry Point: ${ENTRY_POINT}"
echo "=============================================="

# Check if gcloud is installed
if ! command -v gcloud &> /dev/null; then
    echo "Error: gcloud CLI is not installed. Please install it first."
    exit 1
fi

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed. Please install it first."
    exit 1
fi

# Set project
gcloud config set project ${PROJECT_ID}

# Enable required APIs
echo "Enabling required APIs..."
gcloud services enable cloudfunctions.googleapis.com
gcloud services enable cloudbuild.googleapis.com
gcloud services enable storage.googleapis.com
gcloud services enable bigquery.googleapis.com
gcloud services enable eventarc.googleapis.com
gcloud services enable run.googleapis.com

# Build the project
echo "Building project with Maven..."
mvn clean package -DskipTests

# Create BigQuery dataset if it doesn't exist
echo "Creating BigQuery dataset (if not exists)..."
bq mk --dataset --location=US ${PROJECT_ID}:${DATASET_ID} 2>/dev/null || echo "Dataset already exists"

# Create BigQuery table if it doesn't exist
echo "Creating BigQuery table (if not exists)..."
bq mk --table \
  --schema 'employee_id:STRING,upload_date:TIMESTAMP,file_name:STRING,hum_code:STRING,bucket_name:STRING,processed_at:TIMESTAMP' \
  --time_partitioning_field upload_date \
  --clustering_fields hum_code,employee_id \
  ${PROJECT_ID}:${DATASET_ID}.${TABLE_ID} 2>/dev/null || echo "Table already exists"

# Create GCS bucket if it doesn't exist
echo "Creating GCS bucket (if not exists)..."
gsutil mb -p ${PROJECT_ID} -l ${REGION} gs://${BUCKET_NAME} 2>/dev/null || echo "Bucket already exists"

# Deploy Cloud Function (Gen 2)
echo "Deploying Cloud Function (Gen 2)..."
gcloud functions deploy ${FUNCTION_NAME} \
    --gen2 \
    --runtime=${RUNTIME} \
    --region=${REGION} \
    --source=. \
    --entry-point=${ENTRY_POINT} \
    --trigger-event-filters="type=google.cloud.storage.object.v1.finalized" \
    --trigger-event-filters="bucket=${BUCKET_NAME}" \
    --memory=${MEMORY} \
    --timeout=${TIMEOUT} \
    --set-env-vars="GCP_PROJECT_ID=${PROJECT_ID},BQ_DATASET_ID=${DATASET_ID},BQ_TABLE_ID=${TABLE_ID}"

echo "=============================================="
echo "Deployment Complete!"
echo "=============================================="
echo ""
echo "To test, upload a file to gs://${BUCKET_NAME}/"
echo "Example: gsutil cp sample_data/employees_HUM-100_report.csv gs://${BUCKET_NAME}/"
echo ""
echo "View logs: gcloud functions logs read ${FUNCTION_NAME} --region=${REGION} --gen2"
echo ""
