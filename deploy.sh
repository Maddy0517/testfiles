#!/bin/bash

# Workday to BigQuery Dataflow Deployment Script
# This script builds and deploys the pipeline to Google Cloud Dataflow

set -e

# Configuration - Update these values for your environment
PROJECT_ID="${PROJECT_ID:-your-gcp-project-id}"
REGION="${REGION:-us-central1}"
BUCKET_NAME="${BUCKET_NAME:-${PROJECT_ID}-dataflow-staging}"
WORKDAY_ENDPOINT="${WORKDAY_ENDPOINT:-https://wd2-impl-services1.workday.com/ccx/service/tenant/Human_Resources/v35.0}"
WORKDAY_USERNAME="${WORKDAY_USERNAME}"
WORKDAY_PASSWORD="${WORKDAY_PASSWORD}"
WORKDAY_TENANT="${WORKDAY_TENANT:-tenant}"
BQ_DATASET="${BQ_DATASET:-workday_data}"
BQ_TABLE="${BQ_TABLE:-employees}"
BATCH_SIZE="${BATCH_SIZE:-100}"
MAX_WORKERS="${MAX_WORKERS:-10}"
MACHINE_TYPE="${MACHINE_TYPE:-n1-standard-2}"
DISK_SIZE_GB="${DISK_SIZE_GB:-100}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check required environment variables
check_env() {
    log_info "Checking environment variables..."
    
    if [ -z "$PROJECT_ID" ]; then
        log_error "PROJECT_ID is not set"
        exit 1
    fi
    
    if [ -z "$WORKDAY_USERNAME" ]; then
        log_error "WORKDAY_USERNAME is not set"
        exit 1
    fi
    
    if [ -z "$WORKDAY_PASSWORD" ]; then
        log_error "WORKDAY_PASSWORD is not set"
        exit 1
    fi
    
    log_info "Environment variables validated"
}

# Check if required tools are installed
check_tools() {
    log_info "Checking required tools..."
    
    if ! command -v gcloud &> /dev/null; then
        log_error "gcloud CLI is not installed"
        exit 1
    fi
    
    if ! command -v mvn &> /dev/null; then
        log_error "Maven is not installed"
        exit 1
    fi
    
    if ! command -v java &> /dev/null; then
        log_error "Java is not installed"
        exit 1
    fi
    
    log_info "All required tools are available"
}

# Set up GCP project
setup_gcp() {
    log_info "Setting up GCP project: $PROJECT_ID"
    
    # Set project
    gcloud config set project $PROJECT_ID
    
    # Enable required APIs
    log_info "Enabling required GCP APIs..."
    gcloud services enable dataflow.googleapis.com
    gcloud services enable bigquery.googleapis.com
    gcloud services enable storage.googleapis.com
    gcloud services enable compute.googleapis.com
    
    # Create staging bucket if it doesn't exist
    if ! gsutil ls gs://$BUCKET_NAME &> /dev/null; then
        log_info "Creating staging bucket: gs://$BUCKET_NAME"
        gsutil mb -p $PROJECT_ID -l $REGION gs://$BUCKET_NAME
    else
        log_info "Staging bucket already exists: gs://$BUCKET_NAME"
    fi
    
    # Create BigQuery dataset if it doesn't exist
    if ! bq ls -d $PROJECT_ID:$BQ_DATASET &> /dev/null; then
        log_info "Creating BigQuery dataset: $BQ_DATASET"
        bq mk --location=$REGION --dataset $PROJECT_ID:$BQ_DATASET
    else
        log_info "BigQuery dataset already exists: $BQ_DATASET"
    fi
}

# Build the application
build_app() {
    log_info "Building the application..."
    
    # Clean and compile
    mvn clean compile
    
    # Run tests
    log_info "Running tests..."
    mvn test
    
    # Package
    log_info "Creating JAR package..."
    mvn package -DskipTests
    
    log_info "Build completed successfully"
}

# Deploy to Dataflow
deploy_dataflow() {
    log_info "Deploying to Google Cloud Dataflow..."
    
    # Generate job name with timestamp
    JOB_NAME="workday-bigquery-$(date +%Y%m%d-%H%M%S)"
    
    # Build the command
    JAVA_CMD="mvn compile exec:java \
        -Dexec.mainClass=com.company.dataflow.WorkdayToBigQueryPipeline \
        -Dexec.args=\"\
            --project=$PROJECT_ID \
            --region=$REGION \
            --runner=DataflowRunner \
            --jobName=$JOB_NAME \
            --stagingLocation=gs://$BUCKET_NAME/staging \
            --tempLocation=gs://$BUCKET_NAME/temp \
            --workdayEndpoint=$WORKDAY_ENDPOINT \
            --workdayUsername=$WORKDAY_USERNAME \
            --workdayPassword=$WORKDAY_PASSWORD \
            --workdayTenant=$WORKDAY_TENANT \
            --bigQueryDataset=$BQ_DATASET \
            --bigQueryTable=$BQ_TABLE \
            --batchSize=$BATCH_SIZE \
            --maxNumWorkers=$MAX_WORKERS \
            --machineType=$MACHINE_TYPE \
            --diskSizeGb=$DISK_SIZE_GB \
            --usePublicIps=true \
            --enableStreamingEngine=false \
        \""
    
    log_info "Executing deployment command..."
    log_info "Job Name: $JOB_NAME"
    
    eval $JAVA_CMD
    
    if [ $? -eq 0 ]; then
        log_info "Pipeline deployed successfully!"
        log_info "Job Name: $JOB_NAME"
        log_info "Monitor the job at: https://console.cloud.google.com/dataflow/jobs/$REGION/$JOB_NAME?project=$PROJECT_ID"
    else
        log_error "Pipeline deployment failed"
        exit 1
    fi
}

# Main execution
main() {
    log_info "Starting Workday to BigQuery Pipeline Deployment"
    log_info "================================================"
    
    check_env
    check_tools
    setup_gcp
    build_app
    deploy_dataflow
    
    log_info "Deployment completed successfully!"
    log_info "Check the Dataflow console for job status: https://console.cloud.google.com/dataflow"
}

# Help function
show_help() {
    cat << EOF
Workday to BigQuery Dataflow Deployment Script

Usage: $0 [OPTIONS]

Environment Variables (required):
  PROJECT_ID          GCP Project ID
  WORKDAY_USERNAME    Workday username
  WORKDAY_PASSWORD    Workday password

Environment Variables (optional):
  REGION              GCP region (default: us-central1)
  BUCKET_NAME         Staging bucket name (default: PROJECT_ID-dataflow-staging)
  WORKDAY_ENDPOINT    Workday SOAP endpoint
  WORKDAY_TENANT      Workday tenant name (default: tenant)
  BQ_DATASET          BigQuery dataset name (default: workday_data)
  BQ_TABLE            BigQuery table name (default: employees)
  BATCH_SIZE          API batch size (default: 100)
  MAX_WORKERS         Maximum Dataflow workers (default: 10)
  MACHINE_TYPE        Dataflow machine type (default: n1-standard-2)
  DISK_SIZE_GB        Worker disk size in GB (default: 100)

Options:
  -h, --help          Show this help message

Examples:
  # Basic deployment
  export PROJECT_ID=my-project
  export WORKDAY_USERNAME=user@tenant
  export WORKDAY_PASSWORD=password
  ./deploy.sh

  # Custom configuration
  export PROJECT_ID=my-project
  export WORKDAY_USERNAME=user@tenant
  export WORKDAY_PASSWORD=password
  export REGION=us-west1
  export BQ_DATASET=hr_data
  ./deploy.sh
EOF
}

# Parse command line arguments
case "${1:-}" in
    -h|--help)
        show_help
        exit 0
        ;;
    "")
        main
        ;;
    *)
        log_error "Unknown option: $1"
        show_help
        exit 1
        ;;
esac