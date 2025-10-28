#!/bin/bash

# Deploy script for PGP Encryption Cloud Function
# This script deploys the Cloud Function to Google Cloud Platform

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored messages
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Check if required environment variables are set
if [ -z "$GCP_PROJECT_ID" ]; then
    print_error "GCP_PROJECT_ID environment variable is not set"
    echo "Usage: export GCP_PROJECT_ID=your-project-id"
    exit 1
fi

# Configuration
FUNCTION_NAME="${FUNCTION_NAME:-pgp-encryption-function}"
REGION="${REGION:-us-central1}"
RUNTIME="${RUNTIME:-java11}"
MEMORY="${MEMORY:-512MB}"
TIMEOUT="${TIMEOUT:-540s}"
ENTRY_POINT="com.example.pgp.PgpEncryptionFunction"

print_info "Starting deployment of Cloud Function..."
print_info "Project ID: $GCP_PROJECT_ID"
print_info "Function Name: $FUNCTION_NAME"
print_info "Region: $REGION"
print_info "Runtime: $RUNTIME"

# Check if gcloud is installed
if ! command -v gcloud &> /dev/null; then
    print_error "gcloud CLI is not installed. Please install it first."
    exit 1
fi

# Set the project
print_info "Setting GCP project..."
gcloud config set project "$GCP_PROJECT_ID"

# Check if service account is provided
if [ -n "$SERVICE_ACCOUNT" ]; then
    SERVICE_ACCOUNT_FLAG="--service-account=$SERVICE_ACCOUNT"
    print_info "Using service account: $SERVICE_ACCOUNT"
else
    SERVICE_ACCOUNT_FLAG=""
    print_warning "No service account specified. Using default service account."
fi

# Deploy the function
print_info "Deploying Cloud Function..."

gcloud functions deploy "$FUNCTION_NAME" \
    --gen2 \
    --runtime="$RUNTIME" \
    --region="$REGION" \
    --source=. \
    --entry-point="$ENTRY_POINT" \
    --trigger-http \
    --allow-unauthenticated \
    --memory="$MEMORY" \
    --timeout="$TIMEOUT" \
    $SERVICE_ACCOUNT_FLAG

if [ $? -eq 0 ]; then
    print_info "Deployment successful!"
    
    # Get the function URL
    FUNCTION_URL=$(gcloud functions describe "$FUNCTION_NAME" \
        --region="$REGION" \
        --gen2 \
        --format="value(serviceConfig.uri)")
    
    print_info "Function URL: $FUNCTION_URL"
    
    echo ""
    print_info "Example usage:"
    echo "curl -X POST \"${FUNCTION_URL}?Src_Bucket=source-bucket&Tgt_Bucket=target-bucket&Src_File=file.csv&Gcs_ProjectID=${GCP_PROJECT_ID}&passphrase=your-passphrase&Private_encrypt_Key=PULSE_BYOD_FILE_ENCRYPTION_KEY\" \\"
    echo "  -H \"Authorization: Bearer \$(gcloud auth print-identity-token)\""
else
    print_error "Deployment failed!"
    exit 1
fi

print_info "Done!"
