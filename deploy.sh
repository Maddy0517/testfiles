#!/bin/bash

# Google Cloud Function Deployment Script
# Make sure you have the Google Cloud SDK installed and authenticated

set -e

# Configuration
FUNCTION_NAME="pgp-file-processor"
REGION="us-central1"
RUNTIME="java11"
ENTRY_POINT="com.example.pgp.PgpCloudFunction"
MEMORY="512MB"
TIMEOUT="540s"

# Check if gcloud is installed
if ! command -v gcloud &> /dev/null; then
    echo "Error: gcloud CLI is not installed. Please install it first."
    echo "Visit: https://cloud.google.com/sdk/docs/install"
    exit 1
fi

# Check if user is authenticated
if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" | head -n1 > /dev/null; then
    echo "Error: Not authenticated with gcloud. Please run 'gcloud auth login'"
    exit 1
fi

# Get current project
PROJECT_ID=$(gcloud config get-value project)
if [ -z "$PROJECT_ID" ]; then
    echo "Error: No project set. Please run 'gcloud config set project YOUR_PROJECT_ID'"
    exit 1
fi

echo "Deploying to project: $PROJECT_ID"
echo "Function name: $FUNCTION_NAME"
echo "Region: $REGION"

# Build the project
echo "Building the project..."
mvn clean compile

# Deploy the function
echo "Deploying the Cloud Function..."
gcloud functions deploy $FUNCTION_NAME \
    --gen2 \
    --runtime=$RUNTIME \
    --region=$REGION \
    --source=. \
    --entry-point=$ENTRY_POINT \
    --memory=$MEMORY \
    --timeout=$TIMEOUT \
    --trigger=http \
    --allow-unauthenticated \
    --set-env-vars="GOOGLE_CLOUD_PROJECT=$PROJECT_ID"

echo "Deployment completed successfully!"
echo "Function URL: https://$REGION-$PROJECT_ID.cloudfunctions.net/$FUNCTION_NAME"

# Test the deployment
echo ""
echo "To test the function, you can use curl:"
echo "curl -X POST https://$REGION-$PROJECT_ID.cloudfunctions.net/$FUNCTION_NAME \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '{"
echo "    \"Src_Bucket\": \"your-source-bucket\","
echo "    \"Tgt_Bucket\": \"your-target-bucket\","
echo "    \"Src_File\": \"path/to/encrypted/file.pgp\","
echo "    \"Gcs_ProjectID\": \"$PROJECT_ID\","
echo "    \"passphrase\": \"your-passphrase\","
echo "    \"Private_encrypt_Key\": \"PULSE_BYOD_FILE_ENCRYPTION_KEY\""
echo "  }'"