#!/bin/bash

# Google Cloud Scheduler Setup Script
# This script creates a Cloud Scheduler job to run the Dataflow pipeline on a schedule

set -e

# Configuration
PROJECT_ID="your-gcp-project-id"
REGION="us-central1"
SCHEDULER_JOB_NAME="workday-employee-daily-sync"
SCHEDULE="0 2 * * *"  # Daily at 2 AM UTC
TIME_ZONE="America/New_York"
TEMPLATE_GCS_PATH="gs://your-bucket/templates/workday-pipeline"

# Dataflow template parameters
WORKDAY_SOAP_URL="https://wd2-impl-services1.workday.com/ccx/service/YOUR-TENANT/Human_Resources/v38.0"
WORKDAY_USERNAME="integration_user@tenant"
WORKDAY_PASSWORD_SECRET="projects/$PROJECT_ID/secrets/workday-password/versions/latest"
WORKDAY_TENANT_ID="your-tenant"
BIGQUERY_TABLE="$PROJECT_ID:workday_data.employees"
TEMP_LOCATION="gs://your-bucket/temp"
STAGING_LOCATION="gs://your-bucket/staging"

echo "============================================"
echo "Setting up Cloud Scheduler for Workday Pipeline"
echo "============================================"

# Enable required APIs
echo "Enabling required APIs..."
gcloud services enable cloudscheduler.googleapis.com --project=$PROJECT_ID
gcloud services enable dataflow.googleapis.com --project=$PROJECT_ID

# Create service account for scheduler if it doesn't exist
SERVICE_ACCOUNT="workday-scheduler@$PROJECT_ID.iam.gserviceaccount.com"
echo "Creating service account: $SERVICE_ACCOUNT"

gcloud iam service-accounts create workday-scheduler \
    --display-name="Workday Dataflow Scheduler" \
    --project=$PROJECT_ID \
    2>/dev/null || echo "Service account already exists"

# Grant necessary permissions
echo "Granting permissions..."
gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:$SERVICE_ACCOUNT" \
    --role="roles/dataflow.admin" \
    --condition=None

gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:$SERVICE_ACCOUNT" \
    --role="roles/bigquery.dataEditor" \
    --condition=None

gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:$SERVICE_ACCOUNT" \
    --role="roles/storage.objectAdmin" \
    --condition=None

# Create Cloud Scheduler job using Dataflow template
echo "Creating Cloud Scheduler job..."

# First, we need to create a Dataflow Flex Template
# Build and upload template
echo "Building Dataflow Flex Template..."

# Create template metadata
cat > /tmp/template-metadata.json <<EOF
{
  "name": "Workday Employee Ingestion Pipeline",
  "description": "Ingests employee data from Workday SOAP API to BigQuery with parallel processing",
  "parameters": [
    {
      "name": "workdaySoapUrl",
      "label": "Workday SOAP API URL",
      "helpText": "The Workday SOAP API endpoint URL",
      "isOptional": false
    },
    {
      "name": "workdayUsername",
      "label": "Workday Username",
      "helpText": "Workday integration user username",
      "isOptional": false
    },
    {
      "name": "workdayPassword",
      "label": "Workday Password",
      "helpText": "Workday integration user password",
      "isOptional": false
    },
    {
      "name": "workdayTenantId",
      "label": "Workday Tenant ID",
      "helpText": "Workday tenant identifier",
      "isOptional": true
    },
    {
      "name": "effectiveDate",
      "label": "Effective Date",
      "helpText": "Effective date for data extraction (YYYY-MM-DD)",
      "isOptional": false
    },
    {
      "name": "bigQueryTable",
      "label": "BigQuery Table",
      "helpText": "BigQuery destination table (project:dataset.table)",
      "isOptional": false
    }
  ]
}
EOF

# Alternative: Use HTTP POST to Dataflow API
# Create Cloud Scheduler job that triggers Dataflow via HTTP
echo "Creating scheduler job to trigger Dataflow..."

# Get current date for effective date (yesterday)
EFFECTIVE_DATE=$(date -d "yesterday" +%Y-%m-%d)

# Create JSON payload for Dataflow launch
cat > /tmp/dataflow-request.json <<EOF
{
  "jobName": "workday-employee-sync-scheduled",
  "parameters": {
    "workdaySoapUrl": "$WORKDAY_SOAP_URL",
    "workdayUsername": "$WORKDAY_USERNAME",
    "workdayPassword": "\${WORKDAY_PASSWORD}",
    "workdayTenantId": "$WORKDAY_TENANT_ID",
    "effectiveDate": "$EFFECTIVE_DATE",
    "bigQueryTable": "$BIGQUERY_TABLE",
    "writeDisposition": "WRITE_APPEND",
    "maxRetries": "3"
  },
  "environment": {
    "numWorkers": 10,
    "maxWorkers": 50,
    "zone": "us-central1-a",
    "tempLocation": "$TEMP_LOCATION",
    "stagingLocation": "$STAGING_LOCATION",
    "machineType": "n1-standard-4"
  }
}
EOF

# Create Cloud Scheduler job using HTTP target
gcloud scheduler jobs create http $SCHEDULER_JOB_NAME \
    --location=$REGION \
    --schedule="$SCHEDULE" \
    --time-zone="$TIME_ZONE" \
    --uri="https://dataflow.googleapis.com/v1b3/projects/$PROJECT_ID/locations/$REGION/templates:launch?gcsPath=gs://dataflow-templates/latest/flex/Workday_to_BigQuery" \
    --http-method=POST \
    --message-body-from-file=/tmp/dataflow-request.json \
    --oauth-service-account-email=$SERVICE_ACCOUNT \
    --oauth-token-scope="https://www.googleapis.com/auth/cloud-platform" \
    --description="Daily sync of Workday employee data to BigQuery" \
    2>/dev/null || echo "Scheduler job already exists, updating..."

# If job exists, update it
gcloud scheduler jobs update http $SCHEDULER_JOB_NAME \
    --location=$REGION \
    --schedule="$SCHEDULE" \
    --time-zone="$TIME_ZONE" \
    2>/dev/null || true

echo ""
echo "============================================"
echo "Cloud Scheduler Setup Complete!"
echo "============================================"
echo "Job Name: $SCHEDULER_JOB_NAME"
echo "Schedule: $SCHEDULE ($TIME_ZONE)"
echo "Next Run: $(gcloud scheduler jobs describe $SCHEDULER_JOB_NAME --location=$REGION --format='value(schedule.nextRunTime)')"
echo ""
echo "To manually trigger the job:"
echo "  gcloud scheduler jobs run $SCHEDULER_JOB_NAME --location=$REGION"
echo ""
echo "To pause the job:"
echo "  gcloud scheduler jobs pause $SCHEDULER_JOB_NAME --location=$REGION"
echo ""
echo "To resume the job:"
echo "  gcloud scheduler jobs resume $SCHEDULER_JOB_NAME --location=$REGION"
echo ""
echo "To view job details:"
echo "  gcloud scheduler jobs describe $SCHEDULER_JOB_NAME --location=$REGION"
echo "============================================"
