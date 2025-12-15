#!/bin/bash

# Build and deploy Dataflow Flex Template
# This creates a containerized version of the pipeline that can be triggered via Cloud Scheduler

set -e

# Configuration
PROJECT_ID="${GCP_PROJECT_ID:-your-gcp-project-id}"
REGION="${DATAFLOW_REGION:-us-central1}"
TEMPLATE_IMAGE="gcr.io/$PROJECT_ID/dataflow/workday-employee-pipeline:latest"
TEMPLATE_PATH="gs://your-bucket/dataflow-templates/workday-employee-pipeline.json"
METADATA_FILE="template-metadata.json"

echo "============================================"
echo "Building Dataflow Flex Template"
echo "============================================"
echo "Project: $PROJECT_ID"
echo "Image: $TEMPLATE_IMAGE"
echo "Template Path: $TEMPLATE_PATH"
echo ""

# Step 1: Build the Maven project
echo "Step 1: Building Maven project..."
cd ..
mvn clean package -DskipTests
echo "✓ Maven build complete"
echo ""

# Step 2: Build Docker image
echo "Step 2: Building Docker image..."
cd docker
docker build -t $TEMPLATE_IMAGE -f Dockerfile ..
echo "✓ Docker image built"
echo ""

# Step 3: Push to Container Registry
echo "Step 3: Pushing image to Google Container Registry..."
docker push $TEMPLATE_IMAGE
echo "✓ Image pushed"
echo ""

# Step 4: Create template metadata
echo "Step 4: Creating template metadata..."
cat > $METADATA_FILE <<EOF
{
  "name": "Workday Employee Ingestion",
  "description": "Ingests employee data from Workday SOAP API to BigQuery with parallel page processing",
  "streaming": false,
  "supportsAtLeastOnce": false,
  "supportsExactlyOnce": false,
  "defaultEnvironment": {},
  "parameters": [
    {
      "name": "workdaySoapUrl",
      "label": "Workday SOAP API URL",
      "helpText": "The Workday SOAP API endpoint URL (e.g., https://wd2-impl-services1.workday.com/ccx/service/tenant/Human_Resources/v38.0)",
      "isOptional": false,
      "regexes": ["^https://.*"],
      "paramType": "TEXT"
    },
    {
      "name": "workdayUsername",
      "label": "Workday Username",
      "helpText": "Workday integration user username (format: user@tenant)",
      "isOptional": false,
      "paramType": "TEXT"
    },
    {
      "name": "workdayPassword",
      "label": "Workday Password",
      "helpText": "Workday integration user password",
      "isOptional": false,
      "paramType": "TEXT"
    },
    {
      "name": "workdayTenantId",
      "label": "Workday Tenant ID",
      "helpText": "Workday tenant identifier",
      "isOptional": true,
      "paramType": "TEXT"
    },
    {
      "name": "effectiveDate",
      "label": "Effective Date",
      "helpText": "Effective date for data extraction (YYYY-MM-DD format)",
      "isOptional": false,
      "regexes": ["^\\d{4}-\\d{2}-\\d{2}$"],
      "paramType": "TEXT"
    },
    {
      "name": "bigQueryTable",
      "label": "BigQuery Output Table",
      "helpText": "BigQuery destination table (format: project:dataset.table)",
      "isOptional": false,
      "regexes": ["^[^:]+:[^.]+\\.[^.]+$"],
      "paramType": "TEXT"
    },
    {
      "name": "writeDisposition",
      "label": "BigQuery Write Disposition",
      "helpText": "Write disposition for BigQuery (WRITE_APPEND, WRITE_TRUNCATE, WRITE_EMPTY)",
      "isOptional": true,
      "paramType": "TEXT"
    },
    {
      "name": "maxRetries",
      "label": "Maximum Retries",
      "helpText": "Maximum number of retry attempts for API calls",
      "isOptional": true,
      "paramType": "TEXT"
    },
    {
      "name": "estimatedTotalCount",
      "label": "Estimated Total Count",
      "helpText": "Estimated total employee count (0 to fetch dynamically)",
      "isOptional": true,
      "paramType": "TEXT"
    }
  ],
  "metadata": {
    "name": "Workday to BigQuery",
    "description": "Batch pipeline to ingest Workday employee data via SOAP API into BigQuery with parallel processing",
    "category": "Data Integration",
    "version": "1.0.0"
  }
}
EOF
echo "✓ Metadata created"
echo ""

# Step 5: Create Flex Template spec
echo "Step 5: Creating Flex Template..."
gcloud dataflow flex-template build $TEMPLATE_PATH \
    --image=$TEMPLATE_IMAGE \
    --sdk-language=JAVA \
    --metadata-file=$METADATA_FILE \
    --project=$PROJECT_ID

echo "✓ Flex Template created"
echo ""

echo "============================================"
echo "Deployment Complete!"
echo "============================================"
echo ""
echo "Template Location: $TEMPLATE_PATH"
echo ""
echo "To launch a job from this template:"
echo ""
echo "gcloud dataflow flex-template run \"workday-employee-\$(date +%Y%m%d-%H%M%S)\" \\"
echo "    --template-file-gcs-location=\"$TEMPLATE_PATH\" \\"
echo "    --region=\"$REGION\" \\"
echo "    --parameters workdaySoapUrl=\"https://...\",\\"
echo "workdayUsername=\"user@tenant\",\\"
echo "workdayPassword=\"password\",\\"
echo "effectiveDate=\"2025-12-15\",\\"
echo "bigQueryTable=\"$PROJECT_ID:workday_data.employees\""
echo ""
echo "Or via API/Cloud Scheduler:"
echo "https://dataflow.googleapis.com/v1b3/projects/$PROJECT_ID/locations/$REGION/flexTemplates:launch"
echo ""
