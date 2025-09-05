#!/bin/bash

# Script to run the pipeline using properties file configuration

# =============================================================================
# Configuration
# =============================================================================

# Properties file path - UPDATE THIS PATH
PROPERTIES_FILE="config/local-dev.properties"

# Google Cloud Service Account Key - UPDATE THIS PATH  
SERVICE_ACCOUNT_KEY="/path/to/your-service-account-key.json"

# Optional: Override specific properties from command line
OVERRIDE_ARGS=""
# Examples:
# OVERRIDE_ARGS="--pageSize=25 --bigQueryTable=workers_test"

# =============================================================================
# Validation
# =============================================================================

# Check if properties file exists
if [ ! -f "$PROPERTIES_FILE" ]; then
    echo "❌ Properties file not found: $PROPERTIES_FILE"
    echo "Please update PROPERTIES_FILE variable in this script"
    exit 1
fi

# Check if service account key exists
if [ ! -f "$SERVICE_ACCOUNT_KEY" ]; then
    echo "❌ Service account key not found: $SERVICE_ACCOUNT_KEY"
    echo "Please update SERVICE_ACCOUNT_KEY variable in this script"
    echo "Or set GOOGLE_APPLICATION_CREDENTIALS environment variable"
    exit 1
fi

# =============================================================================
# Execution
# =============================================================================

echo "🚀 Starting Workday to BigQuery Pipeline"
echo "📄 Properties file: $PROPERTIES_FILE"
echo "🔑 Service account: $SERVICE_ACCOUNT_KEY"

# Set Google Cloud credentials
export GOOGLE_APPLICATION_CREDENTIALS="$SERVICE_ACCOUNT_KEY"

# Build the project
echo "🔨 Building project..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi

# Run the pipeline
echo "▶️ Running pipeline..."
mvn exec:java \
    -Dexec.mainClass=com.example.workday.WorkdayToBigQueryPipeline \
    -Dexec.args="--propertiesFile=$PROPERTIES_FILE $OVERRIDE_ARGS" \
    -Dorg.slf4j.simpleLogger.defaultLogLevel=INFO

# Check exit status
if [ $? -eq 0 ]; then
    echo "✅ Pipeline completed successfully"
else
    echo "❌ Pipeline failed"
    exit 1
fi