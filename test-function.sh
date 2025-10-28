#!/bin/bash

# Test script for the PGP Cloud Function
# Update the variables below with your actual values

set -e

# Configuration - UPDATE THESE VALUES
FUNCTION_URL="https://us-central1-your-project.cloudfunctions.net/pgp-file-processor"
SOURCE_BUCKET="your-source-bucket"
TARGET_BUCKET="your-target-bucket"
SOURCE_FILE="path/to/encrypted/file.pgp"
PROJECT_ID="your-project-id"
PASSPHRASE="your-pgp-key-passphrase"
SECRET_NAME="PULSE_BYOD_FILE_ENCRYPTION_KEY"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Testing PGP Cloud Function${NC}"
echo "Function URL: $FUNCTION_URL"
echo ""

# Create test request
cat > temp_test_request.json << EOF
{
    "Src_Bucket": "$SOURCE_BUCKET",
    "Tgt_Bucket": "$TARGET_BUCKET",
    "Src_File": "$SOURCE_FILE",
    "Gcs_ProjectID": "$PROJECT_ID",
    "passphrase": "$PASSPHRASE",
    "Private_encrypt_Key": "$SECRET_NAME"
}
EOF

echo -e "${YELLOW}Sending request...${NC}"
echo "Request payload:"
cat temp_test_request.json
echo ""

# Send the request
RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" \
    -X POST "$FUNCTION_URL" \
    -H 'Content-Type: application/json' \
    -d @temp_test_request.json)

# Extract response body and status code
HTTP_BODY=$(echo "$RESPONSE" | sed '$d')
HTTP_STATUS=$(echo "$RESPONSE" | tail -n1 | sed 's/HTTP_STATUS://')

echo -e "${YELLOW}Response:${NC}"
echo "HTTP Status: $HTTP_STATUS"
echo "Response Body:"
echo "$HTTP_BODY" | jq . 2>/dev/null || echo "$HTTP_BODY"

# Check if request was successful
if [ "$HTTP_STATUS" -eq 200 ]; then
    echo -e "\n${GREEN}✓ Test completed successfully!${NC}"
else
    echo -e "\n${RED}✗ Test failed with HTTP status: $HTTP_STATUS${NC}"
fi

# Cleanup
rm -f temp_test_request.json

echo ""
echo -e "${YELLOW}Note:${NC} Make sure to update the configuration variables in this script with your actual values."