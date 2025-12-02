#!/bin/bash

# Deployment script for GCS to BigQuery Cloud Function
# This script automates the deployment process

set -e  # Exit on error

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check prerequisites
print_info "Checking prerequisites..."

if ! command_exists gcloud; then
    print_error "gcloud CLI not found. Please install Google Cloud SDK."
    exit 1
fi

if ! command_exists bq; then
    print_error "bq command not found. Please install BigQuery CLI."
    exit 1
fi

if ! command_exists gsutil; then
    print_error "gsutil not found. Please install Google Cloud SDK."
    exit 1
fi

# Configuration
print_info "Please provide the following configuration:"

read -p "Enter your GCP Project ID: " PROJECT_ID
read -p "Enter GCS Bucket Name: " BUCKET_NAME
read -p "Enter BigQuery Dataset ID: " DATASET_ID
read -p "Enter BigQuery Table ID: " TABLE_ID
read -p "Enter Region (default: us-central1): " REGION
REGION=${REGION:-us-central1}

read -p "Enter Employee ID Column Name (default: employee_id): " EMPLOYEE_ID_COLUMN
EMPLOYEE_ID_COLUMN=${EMPLOYEE_ID_COLUMN:-employee_id}

read -p "Enter Function Name (default: process-file-upload): " FUNCTION_NAME
FUNCTION_NAME=${FUNCTION_NAME:-process-file-upload}

print_info "Configuration:"
echo "  Project ID: $PROJECT_ID"
echo "  Bucket: $BUCKET_NAME"
echo "  Dataset: $DATASET_ID"
echo "  Table: $TABLE_ID"
echo "  Region: $REGION"
echo "  Employee ID Column: $EMPLOYEE_ID_COLUMN"
echo "  Function Name: $FUNCTION_NAME"
echo ""

read -p "Is this configuration correct? (y/n): " CONFIRM
if [[ ! $CONFIRM =~ ^[Yy]$ ]]; then
    print_error "Deployment cancelled."
    exit 1
fi

# Set project
print_info "Setting GCP project..."
gcloud config set project "$PROJECT_ID"

# Check if bucket exists, create if not
print_info "Checking if GCS bucket exists..."
if gsutil ls "gs://$BUCKET_NAME" 2>/dev/null; then
    print_info "Bucket already exists: $BUCKET_NAME"
else
    print_info "Creating GCS bucket: $BUCKET_NAME"
    gsutil mb -l "$REGION" "gs://$BUCKET_NAME"
    print_info "Bucket created successfully"
fi

# Check if dataset exists, create if not
print_info "Checking if BigQuery dataset exists..."
if bq ls -d "$PROJECT_ID:$DATASET_ID" >/dev/null 2>&1; then
    print_info "Dataset already exists: $DATASET_ID"
else
    print_info "Creating BigQuery dataset: $DATASET_ID"
    bq mk --dataset --location="$REGION" "$PROJECT_ID:$DATASET_ID"
    print_info "Dataset created successfully"
fi

# Check if table exists
print_info "Checking if BigQuery table exists..."
if bq show "$PROJECT_ID:$DATASET_ID.$TABLE_ID" >/dev/null 2>&1; then
    print_warning "Table already exists: $TABLE_ID"
    read -p "Do you want to continue? (y/n): " CONTINUE
    if [[ ! $CONTINUE =~ ^[Yy]$ ]]; then
        print_error "Deployment cancelled."
        exit 1
    fi
else
    print_info "Creating BigQuery table: $TABLE_ID"
    bq mk --table \
        "$PROJECT_ID:$DATASET_ID.$TABLE_ID" \
        employee_id:STRING,upload_date:TIMESTAMP,file_name:STRING,file_pattern:STRING
    print_info "Table created successfully"
fi

# Deploy Cloud Function
print_info "Deploying Cloud Function: $FUNCTION_NAME"
gcloud functions deploy "$FUNCTION_NAME" \
    --gen2 \
    --runtime python311 \
    --trigger-bucket "$BUCKET_NAME" \
    --entry-point process_file_upload \
    --region "$REGION" \
    --set-env-vars "GCP_PROJECT_ID=$PROJECT_ID,BIGQUERY_DATASET_ID=$DATASET_ID,BIGQUERY_TABLE_ID=$TABLE_ID,EMPLOYEE_ID_COLUMN=$EMPLOYEE_ID_COLUMN" \
    --memory 512MB \
    --timeout 540s \
    --max-instances 10 \
    --source .

if [ $? -eq 0 ]; then
    print_info "Cloud Function deployed successfully!"
else
    print_error "Cloud Function deployment failed!"
    exit 1
fi

# Get function details
print_info "Function details:"
gcloud functions describe "$FUNCTION_NAME" --region "$REGION" --gen2

# Summary
echo ""
print_info "========================================="
print_info "Deployment Complete!"
print_info "========================================="
echo ""
echo "Next steps:"
echo "1. Upload a test file to gs://$BUCKET_NAME/"
echo "   Example: gsutil cp test_file_HUM-100.csv gs://$BUCKET_NAME/"
echo ""
echo "2. View function logs:"
echo "   gcloud functions logs read $FUNCTION_NAME --region $REGION --gen2 --limit 50"
echo ""
echo "3. Query BigQuery table:"
echo "   bq query --use_legacy_sql=false 'SELECT * FROM \`$PROJECT_ID.$DATASET_ID.$TABLE_ID\` LIMIT 10'"
echo ""
print_info "Happy processing!"
