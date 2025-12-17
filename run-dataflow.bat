@echo off
REM ==========================================
REM Workday Pipeline - Google Cloud Dataflow
REM ==========================================

echo Building project...
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo.
echo Submitting job to Google Cloud Dataflow...
echo.

java -cp target/workday-dataflow-pipeline-1.0.0.jar ^
  com.example.dataflow.WorkdayDataflowPipeline ^
  --runner=DataflowRunner ^
  --project=%GCP_PROJECT_ID% ^
  --region=%GCP_REGION% ^
  --tempLocation=%TEMP_LOCATION% ^
  --stagingLocation=%STAGING_LOCATION% ^
  --numWorkers=%NUM_WORKERS% ^
  --maxNumWorkers=%MAX_NUM_WORKERS% ^
  --workerMachineType=%WORKER_MACHINE_TYPE% ^
  --workdaySoapUrl=%WORKDAY_SOAP_URL% ^
  --workdayUsername=%WORKDAY_USERNAME% ^
  --workdayPassword=%WORKDAY_PASSWORD% ^
  --workdayTenantId=%WORKDAY_TENANT_ID% ^
  --effectiveDate=%EFFECTIVE_DATE% ^
  --bigQueryTable=%BIGQUERY_TABLE% ^
  --writeDisposition=WRITE_APPEND ^
  --maxRetries=3

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ==========================================
    echo Job submitted successfully!
    echo Monitor at: https://console.cloud.google.com/dataflow/jobs
    echo ==========================================
) else (
    echo.
    echo ==========================================
    echo Job submission failed! Check the logs above.
    echo ==========================================
)

pause
