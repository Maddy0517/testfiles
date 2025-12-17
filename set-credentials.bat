@echo off
REM ==========================================
REM Workday Pipeline - Credentials Setup
REM ==========================================
REM
REM IMPORTANT: Update the values below with your actual credentials
REM DO NOT commit this file to version control!
REM ==========================================

echo Setting environment variables...

REM Workday SOAP API Configuration
set WORKDAY_SOAP_URL=https://wd2-impl-services1.workday.com/ccx/service/YOUR-TENANT/Human_Resources/v38.0
set WORKDAY_USERNAME=integration_user@tenant
set WORKDAY_PASSWORD=your-password
set WORKDAY_TENANT_ID=your-tenant

REM Pipeline Configuration
set EFFECTIVE_DATE=2025-12-15
set WRITE_DISPOSITION=WRITE_APPEND

REM BigQuery Configuration
set BIGQUERY_TABLE=your-project:workday_data.employees

REM Dataflow Configuration (for cloud execution)
set GCP_PROJECT_ID=your-gcp-project-id
set GCP_REGION=us-central1
set TEMP_LOCATION=gs://your-bucket/temp
set STAGING_LOCATION=gs://your-bucket/staging
set NUM_WORKERS=2
set MAX_NUM_WORKERS=5
set WORKER_MACHINE_TYPE=n1-standard-2

echo.
echo Environment variables set successfully!
echo.
echo To use these variables:
echo   1. Run: call set-credentials.bat
echo   2. Then run: run-local.bat (or run-dataflow.bat)
echo.
