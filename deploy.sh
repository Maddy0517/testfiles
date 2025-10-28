#!/bin/bash

# Google Cloud Function deployment script
# Make sure you have gcloud CLI installed and authenticated

# Configuration variables
FUNCTION_NAME="pgp-gcs-processor"
REGION="us-central1"
RUNTIME="java11"
ENTRY_POINT="com.example.pgp.PgpGcsFunction"
MEMORY="512MB"
TIMEOUT="540s"
MAX_INSTANCES="10"

# Check if required environment variables are set
if [ -z "$GCP_PROJECT_ID" ]; then
    echo "Error: GCP_PROJECT_ID environment variable is not set"
    echo "Please set it using: export GCP_PROJECT_ID=your-project-id"
    exit 1
fi

echo "Deploying Google Cloud Function..."
echo "Project ID: $GCP_PROJECT_ID"
echo "Function Name: $FUNCTION_NAME"
echo "Region: $REGION"

# Build the project
echo "Building Maven project..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "Error: Maven build failed"
    exit 1
fi

# Deploy the function
echo "Deploying function to Google Cloud..."
gcloud functions deploy $FUNCTION_NAME \
    --gen2 \
    --runtime=$RUNTIME \
    --region=$REGION \
    --source=. \
    --entry-point=$ENTRY_POINT \
    --memory=$MEMORY \
    --timeout=$TIMEOUT \
    --max-instances=$MAX_INSTANCES \
    --trigger-http \
    --allow-unauthenticated \
    --project=$GCP_PROJECT_ID

if [ $? -eq 0 ]; then
    echo "Function deployed successfully!"
    echo "Function URL: https://$REGION-$GCP_PROJECT_ID.cloudfunctions.net/$FUNCTION_NAME"
else
    echo "Error: Function deployment failed"
    exit 1
fi