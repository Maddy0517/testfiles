#!/bin/bash

# ==============================================================================
# Google Cloud Function Deployment Script (Java - HTTP Trigger)
# Deploys the GCS to BigQuery processing function
# ==============================================================================

set -e

# Configuration - Read from properties file or use defaults
PROPERTIES_FILE="src/main/resources/application.properties"

# Function to read property from file
read_property() {
    local key=$1
    local default=$2
    if [ -f "$PROPERTIES_FILE" ]; then
        local value=$(grep "^${key}=" "$PROPERTIES_FILE" | cut -d'=' -f2 | tr -d ' ')
        echo "${value:-$default}"
    else
        echo "$default"
    fi
}

# Read configuration from properties file
PROJECT_ID=$(read_property "gcp.project.id" "your-project-id")
BUCKET_NAME=$(read_property "gcs.bucket.name" "your-bucket-name")
DATASET_ID=$(read_property "bigquery.dataset.id" "your_dataset")
TABLE_ID=$(read_property "bigquery.table.id" "file_uploads")

# Deployment settings
REGION="${GCP_REGION:-us-central1}"
FUNCTION_NAME="${FUNCTION_NAME:-process-csv-files}"
RUNTIME="java17"
MEMORY="512MB"
TIMEOUT="300s"
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
echo "Trigger:     HTTP (Manual/Scheduled)"
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

# Deploy Cloud Function (Gen 2) with HTTP trigger
echo "Deploying Cloud Function (Gen 2) with HTTP trigger..."
gcloud functions deploy ${FUNCTION_NAME} \
    --gen2 \
    --runtime=${RUNTIME} \
    --region=${REGION} \
    --source=. \
    --entry-point=${ENTRY_POINT} \
    --trigger-http \
    --allow-unauthenticated \
    --memory=${MEMORY} \
    --timeout=${TIMEOUT}

# Get the function URL
FUNCTION_URL=$(gcloud functions describe ${FUNCTION_NAME} --region=${REGION} --gen2 --format='value(serviceConfig.uri)')

echo "=============================================="
echo "Deployment Complete!"
echo "=============================================="
echo ""
echo "Function URL: ${FUNCTION_URL}"
echo ""
echo "To trigger manually:"
echo "  curl ${FUNCTION_URL}"
echo ""
echo "To schedule with Cloud Scheduler:"
echo "  gcloud scheduler jobs create http process-csv-job \\"
echo "    --schedule='0 */6 * * *' \\"
echo "    --uri='${FUNCTION_URL}' \\"
echo "    --http-method=GET \\"
echo "    --location=${REGION}"
echo ""
echo "View logs: gcloud functions logs read ${FUNCTION_NAME} --region=${REGION} --gen2"
echo ""
