#!/bin/bash

# Local Testing Script for Workday Pipeline
# This script runs the pipeline locally using DirectRunner for testing

set -e

# Configuration variables
DATASET="${BIGQUERY_DATASET:-workday_data_test}"
TABLE="${BIGQUERY_TABLE:-employees_test}"

# Workday configuration (use test/sandbox environment)
WORKDAY_ENDPOINT="${WORKDAY_ENDPOINT:-https://wd-services-impl.myworkday.com/ccx/service/tenant_test/Human_Resources/v41.0}"
WORKDAY_USERNAME="${WORKDAY_USERNAME}"
WORKDAY_PASSWORD="${WORKDAY_PASSWORD}"
WORKDAY_TENANT="${WORKDAY_TENANT}"

# Load type
LOAD_TYPE="${LOAD_TYPE:-INCREMENTAL}"
EFFECTIVE_DATE="${EFFECTIVE_DATE:-$(date +%Y-%m-%d)}"
LAST_MODIFIED_FROM="${LAST_MODIFIED_FROM:-$(date -d '1 day ago' +%Y-%m-%d'T'00:00:00)}"

echo "========================================="
echo "Running Workday Pipeline Locally"
echo "========================================="
echo "Dataset: $DATASET"
echo "Table: $TABLE"
echo "Load Type: $LOAD_TYPE"
echo "Effective Date: $EFFECTIVE_DATE"
echo "========================================="

# Validate required parameters
if [ -z "$WORKDAY_USERNAME" ] || [ -z "$WORKDAY_PASSWORD" ] || [ -z "$WORKDAY_TENANT" ]; then
    echo "Error: Workday credentials are required"
    echo "Please set WORKDAY_USERNAME, WORKDAY_PASSWORD, and WORKDAY_TENANT environment variables"
    exit 1
fi

# Build the project
echo "Building the project..."
mvn clean compile

# Build the arguments
PIPELINE_ARGS=(
    "--runner=DirectRunner"
    "--workdayEndpoint=$WORKDAY_ENDPOINT"
    "--workdayUsername=$WORKDAY_USERNAME"
    "--workdayPassword=$WORKDAY_PASSWORD"
    "--workdayTenant=$WORKDAY_TENANT"
    "--bigQueryDataset=$DATASET"
    "--bigQueryTable=$TABLE"
    "--loadType=$LOAD_TYPE"
    "--effectiveDate=$EFFECTIVE_DATE"
)

if [ "$LOAD_TYPE" == "INCREMENTAL" ] && [ ! -z "$LAST_MODIFIED_FROM" ]; then
    PIPELINE_ARGS+=("--lastModifiedFrom=$LAST_MODIFIED_FROM")
fi

# Run locally
echo "Running pipeline locally..."
mvn compile exec:java \
    -Dexec.mainClass="com.example.workday.WorkdayPipeline" \
    -Dexec.args="${PIPELINE_ARGS[*]}"

echo "========================================="
echo "Pipeline completed successfully!"
echo "========================================="