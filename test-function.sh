#!/bin/bash

# Test script for the PGP GCS Cloud Function
# Usage: ./test-function.sh [FUNCTION_URL]

# Default function URL (update with your actual function URL)
FUNCTION_URL=${1:-"https://us-central1-your-project-id.cloudfunctions.net/pgp-gcs-processor"}

# Test payload - This will decrypt an encrypted PGP file
TEST_PAYLOAD='{
    "Src_Bucket": "xyz",
    "Tgt_Bucket": "xyz", 
    "Src_File": "gcsfile path",
    "Gcs_ProjectID": "project_id",
    "passphrase": "pqaddddzxx",
    "Private_encrypt_Key": "PULSE_BYOD_FILE_ENCRYPTION_KEY"
}'

echo "Testing PGP GCS Cloud Function..."
echo "Function URL: $FUNCTION_URL"
echo "Payload: $TEST_PAYLOAD"
echo ""

# Make the request
curl -X POST \
  "$FUNCTION_URL" \
  -H "Content-Type: application/json" \
  -d "$TEST_PAYLOAD" \
  -w "\n\nHTTP Status: %{http_code}\nTotal Time: %{time_total}s\n"

echo ""
echo "Test completed."