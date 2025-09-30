#!/bin/bash

# Workday Employee Data Pipeline Deployment Script for Google Cloud Dataflow
# This script builds and deploys the Apache Beam pipeline to Google Cloud Dataflow

set -e

# Configuration
PROJECT_ID=${PROJECT_ID:-"your-gcp-project-id"}
REGION=${REGION:-"us-central1"}
TEMP_LOCATION=${TEMP_LOCATION:-"gs://your-temp-bucket/temp"}
STAGING_LOCATION=${STAGING_LOCATION:-"gs://your-staging-bucket/staging"}
SERVICE_ACCOUNT=${SERVICE_ACCOUNT:-"dataflow-service-account@${PROJECT_ID}.iam.gserviceaccount.com"}

# Pipeline Configuration
WORKDAY_ENDPOINT=${WORKDAY_ENDPOINT:-"https://wd2-impl-services1.workday.com/ccx/service/your-tenant"}
WORKDAY_TENANT=${WORKDAY_TENANT:-"your-tenant"}
BIGQUERY_DATASET=${BIGQUERY_DATASET:-"workday_data"}
BIGQUERY_TABLE=${BIGQUERY_TABLE:-"employees"}

# Build configuration
JAR_NAME="workday-beam-pipeline-1.0.0.jar"
MAIN_CLASS="com.company.workday.WorkdayEmployeePipeline"

echo "============================================"
echo "Workday Employee Data Pipeline Deployment"
echo "============================================"
echo "Project ID: $PROJECT_ID"
echo "Region: $REGION"
echo "Target Table: $PROJECT_ID.$BIGQUERY_DATASET.$BIGQUERY_TABLE"
echo "============================================"

# Function to check if required environment variables are set
check_env_vars() {
    local required_vars=("PROJECT_ID" "WORKDAY_USERNAME" "WORKDAY_PASSWORD")
    for var in "${required_vars[@]}"; do
        if [[ -z "${!var}" ]]; then
            echo "Error: Environment variable $var is not set"
            echo "Please set all required environment variables:"
            echo "  export PROJECT_ID=your-gcp-project-id"
            echo "  export WORKDAY_USERNAME=your-workday-username"
            echo "  export WORKDAY_PASSWORD=your-workday-password"
            exit 1
        fi
    done
}

# Function to build the JAR file
build_jar() {
    echo "Building JAR file..."
    mvn clean package -DskipTests
    
    if [[ ! -f "target/$JAR_NAME" ]]; then
        echo "Error: JAR file not found at target/$JAR_NAME"
        exit 1
    fi
    
    echo "JAR file built successfully: target/$JAR_NAME"
}

# Function to create BigQuery dataset and table if they don't exist
setup_bigquery() {
    echo "Setting up BigQuery dataset and table..."
    
    # Create dataset if it doesn't exist
    if ! bq ls -d "$PROJECT_ID:$BIGQUERY_DATASET" &>/dev/null; then
        echo "Creating BigQuery dataset: $BIGQUERY_DATASET"
        bq mk --dataset --location=US "$PROJECT_ID:$BIGQUERY_DATASET"
    else
        echo "BigQuery dataset already exists: $BIGQUERY_DATASET"
    fi
    
    # Create table if it doesn't exist (will be created automatically by pipeline if needed)
    echo "BigQuery table will be created automatically by the pipeline if it doesn't exist"
}

# Function to deploy for historical (full) load
deploy_historical() {
    local job_name="workday-historical-$(date +%Y%m%d-%H%M%S)"
    
    echo "Deploying historical load pipeline..."
    echo "Job name: $job_name"
    
    gcloud dataflow jobs run "$job_name" \
        --gcs-location="$STAGING_LOCATION/$JAR_NAME" \
        --region="$REGION" \
        --service-account-email="$SERVICE_ACCOUNT" \
        --parameters="
            project=$PROJECT_ID,
            runner=DataflowRunner,
            region=$REGION,
            tempLocation=$TEMP_LOCATION,
            stagingLocation=$STAGING_LOCATION,
            workdayEndpoint=$WORKDAY_ENDPOINT,
            workdayUsername=$WORKDAY_USERNAME,
            workdayPassword=$WORKDAY_PASSWORD,
            workdayTenant=$WORKDAY_TENANT,
            bigQueryDataset=$BIGQUERY_DATASET,
            bigQueryTable=$BIGQUERY_TABLE,
            loadMode=HISTORICAL,
            maxParallelRequests=5"
    
    echo "Historical load pipeline deployed successfully!"
    echo "Job name: $job_name"
}

# Function to deploy for incremental load
deploy_incremental() {
    local job_name="workday-incremental-$(date +%Y%m%d-%H%M%S)"
    local effective_date=${EFFECTIVE_DATE:-$(date -d "1 day ago" +%Y-%m-%d)}
    
    echo "Deploying incremental load pipeline..."
    echo "Job name: $job_name"
    echo "Effective date: $effective_date"
    
    gcloud dataflow jobs run "$job_name" \
        --gcs-location="$STAGING_LOCATION/$JAR_NAME" \
        --region="$REGION" \
        --service-account-email="$SERVICE_ACCOUNT" \
        --parameters="
            project=$PROJECT_ID,
            runner=DataflowRunner,
            region=$REGION,
            tempLocation=$TEMP_LOCATION,
            stagingLocation=$STAGING_LOCATION,
            workdayEndpoint=$WORKDAY_ENDPOINT,
            workdayUsername=$WORKDAY_USERNAME,
            workdayPassword=$WORKDAY_PASSWORD,
            workdayTenant=$WORKDAY_TENANT,
            bigQueryDataset=$BIGQUERY_DATASET,
            bigQueryTable=$BIGQUERY_TABLE,
            loadMode=INCREMENTAL,
            effectiveAsOfDate=$effective_date,
            incrementalDays=1,
            maxParallelRequests=5"
    
    echo "Incremental load pipeline deployed successfully!"
    echo "Job name: $job_name"
}

# Function to upload JAR to staging location
upload_jar() {
    echo "Uploading JAR to staging location..."
    gsutil cp "target/$JAR_NAME" "$STAGING_LOCATION/"
    echo "JAR uploaded to: $STAGING_LOCATION/$JAR_NAME"
}

# Function to run local test
run_local_test() {
    echo "Running local test..."
    
    java -cp "target/$JAR_NAME" "$MAIN_CLASS" \
        --runner=DirectRunner \
        --workdayEndpoint="$WORKDAY_ENDPOINT" \
        --workdayUsername="$WORKDAY_USERNAME" \
        --workdayPassword="$WORKDAY_PASSWORD" \
        --workdayTenant="$WORKDAY_TENANT" \
        --bigQueryDataset="$BIGQUERY_DATASET" \
        --bigQueryTable="${BIGQUERY_TABLE}_test" \
        --loadMode=INCREMENTAL \
        --project="$PROJECT_ID"
    
    echo "Local test completed successfully!"
}

# Function to create Cloud Scheduler job for incremental loads
create_scheduler_job() {
    local job_name="workday-daily-incremental"
    local schedule="0 2 * * *"  # Daily at 2 AM
    
    echo "Creating Cloud Scheduler job for daily incremental loads..."
    
    gcloud scheduler jobs create http "$job_name" \
        --location="$REGION" \
        --schedule="$schedule" \
        --time-zone="UTC" \
        --uri="https://dataflow.googleapis.com/v1b3/projects/$PROJECT_ID/locations/$REGION/templates:launch?gcsPath=$STAGING_LOCATION/$JAR_NAME" \
        --http-method=POST \
        --headers="Content-Type=application/json" \
        --oauth-service-account-email="$SERVICE_ACCOUNT" \
        --message-body="{
            \"jobName\": \"workday-incremental-scheduled-$(date +%Y%m%d-%H%M%S)\",
            \"parameters\": {
                \"workdayEndpoint\": \"$WORKDAY_ENDPOINT\",
                \"workdayUsername\": \"$WORKDAY_USERNAME\",
                \"workdayPassword\": \"$WORKDAY_PASSWORD\",
                \"workdayTenant\": \"$WORKDAY_TENANT\",
                \"bigQueryDataset\": \"$BIGQUERY_DATASET\",
                \"bigQueryTable\": \"$BIGQUERY_TABLE\",
                \"loadMode\": \"INCREMENTAL\",
                \"incrementalDays\": \"1\",
                \"maxParallelRequests\": \"5\"
            },
            \"environment\": {
                \"tempLocation\": \"$TEMP_LOCATION\",
                \"serviceAccountEmail\": \"$SERVICE_ACCOUNT\"
            }
        }"
    
    echo "Cloud Scheduler job created: $job_name"
    echo "Schedule: $schedule (UTC)"
}

# Main deployment function
main() {
    local mode=${1:-"incremental"}
    
    case "$mode" in
        "build")
            check_env_vars
            build_jar
            ;;
        "test")
            check_env_vars
            build_jar
            setup_bigquery
            run_local_test
            ;;
        "historical")
            check_env_vars
            build_jar
            setup_bigquery
            upload_jar
            deploy_historical
            ;;
        "incremental")
            check_env_vars
            build_jar
            setup_bigquery
            upload_jar
            deploy_incremental
            ;;
        "schedule")
            check_env_vars
            build_jar
            upload_jar
            create_scheduler_job
            ;;
        "all")
            check_env_vars
            build_jar
            setup_bigquery
            upload_jar
            deploy_historical
            echo "Waiting 5 minutes before setting up incremental schedule..."
            sleep 300
            create_scheduler_job
            ;;
        *)
            echo "Usage: $0 {build|test|historical|incremental|schedule|all}"
            echo ""
            echo "Commands:"
            echo "  build        - Build the JAR file only"
            echo "  test         - Run local test with DirectRunner"
            echo "  historical   - Deploy full historical data load"
            echo "  incremental  - Deploy incremental data load"
            echo "  schedule     - Create scheduled job for daily incremental loads"
            echo "  all          - Deploy historical load and set up scheduling"
            echo ""
            echo "Required environment variables:"
            echo "  PROJECT_ID, WORKDAY_USERNAME, WORKDAY_PASSWORD"
            exit 1
            ;;
    esac
}

# Run main function with command line arguments
main "$@"