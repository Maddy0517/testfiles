#!/bin/bash

# Test script for the PGP GCS Cloud Function
# Usage: ./test-function.sh [FUNCTION_URL]

# Default function URL (update with your actual function URL)
FUNCTION_URL=${1:-"https://us-central1-your-project-id.cloudfunctions.net/pgp-gcs-processor"}

# Test parameters - This will decrypt an encrypted PGP file
SRC_BUCKET="xyz"
TGT_BUCKET="xyz"
SRC_FILE="gcsfile path"
GCS_PROJECT_ID="project_id"
PASSPHRASE="pqaddddzxx"
PRIVATE_ENCRYPT_KEY="PULSE_BYOD_FILE_ENCRYPTION_KEY"

# Build query string
QUERY_STRING="Src_Bucket=${SRC_BUCKET}&Tgt_Bucket=${TGT_BUCKET}&Src_File=${SRC_FILE}&Gcs_ProjectID=${GCS_PROJECT_ID}&passphrase=${PASSPHRASE}&Private_encrypt_Key=${PRIVATE_ENCRYPT_KEY}"

echo "Testing PGP GCS Cloud Function..."
echo "Function URL: $FUNCTION_URL"
echo "Parameters: $QUERY_STRING"
echo ""

# Make the request with query parameters
curl -X POST \
  "${FUNCTION_URL}?${QUERY_STRING}" \
  -H "Content-Type: application/json" \
  -w "\n\nHTTP Status: %{http_code}\nTotal Time: %{time_total}s\n"

echo ""
echo "Test completed."