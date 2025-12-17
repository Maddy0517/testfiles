@echo off
REM ==========================================
REM Workday Pipeline - Local Execution
REM ==========================================

echo Building project...
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo.
echo Running pipeline locally with DirectRunner...
echo.

java -cp target/workday-dataflow-pipeline-1.0.0.jar ^
  com.example.dataflow.WorkdayDataflowPipeline ^
  --runner=DirectRunner ^
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
    echo Pipeline completed successfully!
    echo ==========================================
) else (
    echo.
    echo ==========================================
    echo Pipeline failed! Check the logs above.
    echo ==========================================
)

pause
