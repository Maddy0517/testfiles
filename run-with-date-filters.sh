#!/bin/bash

# Script to run the Workday to BigQuery pipeline with date filters
# Usage: ./run-with-date-filters.sh [from-date] [to-date]
# Example: ./run-with-date-filters.sh 2024-01-01 2024-01-31

FROM_DATE=${1:-$(date -d '7 days ago' '+%Y-%m-%d')}  # Default: 7 days ago
TO_DATE=${2:-$(date '+%Y-%m-%d')}                    # Default: today

echo "Running Workday to BigQuery Pipeline with date filters..."
echo "From Date: $FROM_DATE"
echo "To Date: $TO_DATE"

mvn compile exec:java \
  -Dexec.mainClass=com.workday.dataflow.WorkdayToBigQueryPipeline \
  -Dexec.args="--propertiesFile=config/pipeline-local.properties \
               --effectiveFromDate=$FROM_DATE \
               --effectiveToDate=$TO_DATE"