#!/bin/bash

# Script to run the Workday to BigQuery pipeline locally
# Usage: ./run-local.sh

echo "Running Workday to BigQuery Pipeline locally..."

mvn compile exec:java \
  -Dexec.mainClass=com.workday.dataflow.WorkdayToBigQueryPipeline \
  -Dexec.args="--propertiesFile=config/pipeline-local.properties"