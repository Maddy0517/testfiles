#!/bin/bash

# Deployment script for Google Cloud Function
# Usage: ./deploy.sh

set -e

# Configuration - Update these values
FUNCTION_NAME="process_file_upload"
REGION="us-central1"
RUNTIME="python311"
BUCKET_NAME=""  # Your GCS bucket name
PROJECT_ID=""   # Your GCP project ID
DATASET_ID=""   # Your BigQuery dataset ID
TABLE_ID="employee_data"
SERVICE_ACCOUNT=""  # Your service account email

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Google Cloud Function Deployment Script${NC}"
echo "=========================================="

# Check if required variables are set
if [ -z "$BUCKET_NAME" ] || [ -z "$PROJECT_ID" ] || [ -z "$DATASET_ID" ]; then
    echo -e "${YELLOW}Please update the configuration variables in this script:${NC}"
    echo "  - BUCKET_NAME: Your Cloud Storage bucket name"
    echo "  - PROJECT_ID: Your GCP project ID"
    echo "  - DATASET_ID: Your BigQuery dataset ID"
    echo ""
    echo "Or set them as environment variables:"
    echo "  export BUCKET_NAME=your-bucket"
    echo "  export PROJECT_ID=your-project-id"
    echo "  export DATASET_ID=your-dataset"
    exit 1
fi

# Use environment variables if set
BUCKET_NAME=${BUCKET_NAME:-$BUCKET_NAME_ENV}
PROJECT_ID=${PROJECT_ID:-$PROJECT_ID_ENV}
DATASET_ID=${DATASET_ID:-$DATASET_ID_ENV}

# Set default service account if not provided
if [ -z "$SERVICE_ACCOUNT" ]; then
    SERVICE_ACCOUNT="${PROJECT_ID}@appspot.gserviceaccount.com"
fi

echo "Configuration:"
echo "  Function Name: $FUNCTION_NAME"
echo "  Region: $REGION"
echo "  Runtime: $RUNTIME"
echo "  Bucket: $BUCKET_NAME"
echo "  Project: $PROJECT_ID"
echo "  Dataset: $DATASET_ID"
echo "  Table: $TABLE_ID"
echo "  Service Account: $SERVICE_ACCOUNT"
echo ""

read -p "Continue with deployment? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Deployment cancelled."
    exit 1
fi

# Deploy the function
echo -e "${GREEN}Deploying Cloud Function...${NC}"

gcloud functions deploy $FUNCTION_NAME \
    --gen2 \
    --runtime=$RUNTIME \
    --region=$REGION \
    --source=. \
    --entry-point=process_file \
    --trigger-bucket=$BUCKET_NAME \
    --service-account=$SERVICE_ACCOUNT \
    --set-env-vars PROJECT_ID=$PROJECT_ID,DATASET_ID=$DATASET_ID,TABLE_ID=$TABLE_ID \
    --memory=256MB \
    --timeout=540s \
    --max-instances=10 \
    --project=$PROJECT_ID

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Function deployed successfully!${NC}"
    echo ""
    echo "Next steps:"
    echo "1. Verify permissions are set correctly"
    echo "2. Upload a test file to gs://$BUCKET_NAME/"
    echo "3. Check function logs: gcloud functions logs read $FUNCTION_NAME --gen2"
    echo "4. Query BigQuery: SELECT * FROM \`$PROJECT_ID.$DATASET_ID.$TABLE_ID\`"
else
    echo -e "${RED}✗ Deployment failed${NC}"
    exit 1
fi
