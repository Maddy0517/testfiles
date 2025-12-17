# Running Workday Pipeline from Windows CMD

## Prerequisites

### 1. Install Java 21

**Download**:
- Oracle JDK: https://www.oracle.com/java/technologies/downloads/#java21
- OpenJDK: https://adoptium.net/

**Install and Verify**:
```cmd
java -version
```
Should show: `java version "21"`

**Set JAVA_HOME** (if not set):
```cmd
setx JAVA_HOME "C:\Program Files\Java\jdk-21"
setx PATH "%PATH%;%JAVA_HOME%\bin"
```

### 2. Install Maven

**Download**: https://maven.apache.org/download.cgi

**Extract** to: `C:\Program Files\apache-maven-3.9.6`

**Set Environment Variables**:
```cmd
setx MAVEN_HOME "C:\Program Files\apache-maven-3.9.6"
setx PATH "%PATH%;%MAVEN_HOME%\bin"
```

**Verify**:
```cmd
mvn -version
```

### 3. Install Google Cloud SDK (for Dataflow)

**Download**: https://cloud.google.com/sdk/docs/install

**Install and Initialize**:
```cmd
gcloud init
gcloud auth application-default login
```

## Quick Start - Running from CMD

### Step 1: Navigate to Project Directory

```cmd
cd C:\path\to\workday-dataflow-pipeline
```

### Step 2: Build the Project

```cmd
mvn clean package
```

Wait for: `BUILD SUCCESS`

### Step 3: Set Environment Variables

#### Create a batch file for credentials (RECOMMENDED)

Create `set-credentials.bat`:
```batch
@echo off
set WORKDAY_SOAP_URL=https://wd2-impl-services1.workday.com/ccx/service/YOUR-TENANT/Human_Resources/v38.0
set WORKDAY_USERNAME=integration_user@tenant
set WORKDAY_PASSWORD=your-password
set WORKDAY_TENANT_ID=your-tenant
set EFFECTIVE_DATE=2025-12-15
set BIGQUERY_TABLE=your-project:workday_data.employees

echo Environment variables set successfully!
```

**Run it**:
```cmd
call set-credentials.bat
```

### Step 4: Run Locally (Testing)

```cmd
java -cp target/workday-dataflow-pipeline-1.0.0.jar ^
  com.example.dataflow.WorkdayDataflowPipeline ^
  --runner=DirectRunner ^
  --workdaySoapUrl=%WORKDAY_SOAP_URL% ^
  --workdayUsername=%WORKDAY_USERNAME% ^
  --workdayPassword=%WORKDAY_PASSWORD% ^
  --workdayTenantId=%WORKDAY_TENANT_ID% ^
  --effectiveDate=%EFFECTIVE_DATE% ^
  --bigQueryTable=%BIGQUERY_TABLE% ^
  --writeDisposition=WRITE_APPEND
```

**Note**: `^` is the line continuation character in Windows CMD

### Step 5: Run on Google Cloud Dataflow

First, set GCP variables:
```cmd
set GCP_PROJECT_ID=your-gcp-project
set GCP_REGION=us-central1
set TEMP_LOCATION=gs://your-bucket/temp
set STAGING_LOCATION=gs://your-bucket/staging
```

Then run:
```cmd
java -cp target/workday-dataflow-pipeline-1.0.0.jar ^
  com.example.dataflow.WorkdayDataflowPipeline ^
  --runner=DataflowRunner ^
  --project=%GCP_PROJECT_ID% ^
  --region=%GCP_REGION% ^
  --tempLocation=%TEMP_LOCATION% ^
  --stagingLocation=%STAGING_LOCATION% ^
  --numWorkers=2 ^
  --maxNumWorkers=5 ^
  --workerMachineType=n1-standard-2 ^
  --workdaySoapUrl=%WORKDAY_SOAP_URL% ^
  --workdayUsername=%WORKDAY_USERNAME% ^
  --workdayPassword=%WORKDAY_PASSWORD% ^
  --workdayTenantId=%WORKDAY_TENANT_ID% ^
  --effectiveDate=%EFFECTIVE_DATE% ^
  --bigQueryTable=%BIGQUERY_TABLE% ^
  --writeDisposition=WRITE_APPEND
```

## Create Batch Files for Easy Execution

### run-local.bat

```batch
@echo off
echo ==========================================
echo Workday Pipeline - Local Execution
echo ==========================================

REM Load credentials
call set-credentials.bat

REM Build project
echo Building project...
call mvn clean package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    exit /b 1
)

echo Running pipeline locally...
java -cp target/workday-dataflow-pipeline-1.0.0.jar ^
  com.example.dataflow.WorkdayDataflowPipeline ^
  --runner=DirectRunner ^
  --workdaySoapUrl=%WORKDAY_SOAP_URL% ^
  --workdayUsername=%WORKDAY_USERNAME% ^
  --workdayPassword=%WORKDAY_PASSWORD% ^
  --workdayTenantId=%WORKDAY_TENANT_ID% ^
  --effectiveDate=%EFFECTIVE_DATE% ^
  --bigQueryTable=%BIGQUERY_TABLE% ^
  --writeDisposition=WRITE_APPEND

if %ERRORLEVEL% EQU 0 (
    echo Pipeline completed successfully!
) else (
    echo Pipeline failed!
    exit /b 1
)
```

### run-dataflow.bat

```batch
@echo off
echo ==========================================
echo Workday Pipeline - Google Cloud Dataflow
echo ==========================================

REM Load credentials
call set-credentials.bat

REM Set GCP variables
set GCP_PROJECT_ID=your-gcp-project
set GCP_REGION=us-central1
set TEMP_LOCATION=gs://your-bucket/temp
set STAGING_LOCATION=gs://your-bucket/staging
set NUM_WORKERS=2
set MAX_NUM_WORKERS=5

REM Build project
echo Building project...
call mvn clean package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    exit /b 1
)

echo Submitting job to Dataflow...
java -cp target/workday-dataflow-pipeline-1.0.0.jar ^
  com.example.dataflow.WorkdayDataflowPipeline ^
  --runner=DataflowRunner ^
  --project=%GCP_PROJECT_ID% ^
  --region=%GCP_REGION% ^
  --tempLocation=%TEMP_LOCATION% ^
  --stagingLocation=%STAGING_LOCATION% ^
  --numWorkers=%NUM_WORKERS% ^
  --maxNumWorkers=%MAX_NUM_WORKERS% ^
  --workerMachineType=n1-standard-2 ^
  --jobName=workday-employee-%date:~-4,4%%date:~-10,2%%date:~-7,2%-%time:~0,2%%time:~3,2%%time:~6,2% ^
  --workdaySoapUrl=%WORKDAY_SOAP_URL% ^
  --workdayUsername=%WORKDAY_USERNAME% ^
  --workdayPassword=%WORKDAY_PASSWORD% ^
  --workdayTenantId=%WORKDAY_TENANT_ID% ^
  --effectiveDate=%EFFECTIVE_DATE% ^
  --bigQueryTable=%BIGQUERY_TABLE% ^
  --writeDisposition=WRITE_APPEND

if %ERRORLEVEL% EQU 0 (
    echo Job submitted successfully!
    echo Monitor at: https://console.cloud.google.com/dataflow/jobs
) else (
    echo Job submission failed!
    exit /b 1
)
```

### Usage

```cmd
REM Run locally
run-local.bat

REM Run on Dataflow
run-dataflow.bat
```

## Using PowerShell (Alternative)

### run-local.ps1

```powershell
# Workday Pipeline - Local Execution

Write-Host "==========================================" -ForegroundColor Green
Write-Host "Workday Pipeline - Local Execution" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green

# Set environment variables
$env:WORKDAY_SOAP_URL = "https://wd2-impl-services1.workday.com/ccx/service/YOUR-TENANT/Human_Resources/v38.0"
$env:WORKDAY_USERNAME = "integration_user@tenant"
$env:WORKDAY_PASSWORD = "your-password"
$env:WORKDAY_TENANT_ID = "your-tenant"
$env:EFFECTIVE_DATE = "2025-12-15"
$env:BIGQUERY_TABLE = "your-project:workday_data.employees"

# Build
Write-Host "Building project..." -ForegroundColor Yellow
mvn clean package -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}

# Run
Write-Host "Running pipeline..." -ForegroundColor Yellow
java -cp target/workday-dataflow-pipeline-1.0.0.jar `
  com.example.dataflow.WorkdayDataflowPipeline `
  --runner=DirectRunner `
  --workdaySoapUrl=$env:WORKDAY_SOAP_URL `
  --workdayUsername=$env:WORKDAY_USERNAME `
  --workdayPassword=$env:WORKDAY_PASSWORD `
  --workdayTenantId=$env:WORKDAY_TENANT_ID `
  --effectiveDate=$env:EFFECTIVE_DATE `
  --bigQueryTable=$env:BIGQUERY_TABLE `
  --writeDisposition=WRITE_APPEND

if ($LASTEXITCODE -eq 0) {
    Write-Host "Pipeline completed successfully!" -ForegroundColor Green
} else {
    Write-Host "Pipeline failed!" -ForegroundColor Red
    exit 1
}
```

**Run PowerShell script**:
```powershell
powershell -ExecutionPolicy Bypass -File run-local.ps1
```

## Maven Commands Reference

```cmd
REM Clean build artifacts
mvn clean

REM Compile code
mvn compile

REM Run tests
mvn test

REM Package JAR
mvn package

REM Skip tests during package
mvn package -DskipTests

REM Clean and package
mvn clean package

REM Show dependency tree
mvn dependency:tree

REM Update dependencies
mvn dependency:resolve
```

## Troubleshooting Windows Issues

### Issue 1: "Java not recognized"

**Solution**: Add Java to PATH
```cmd
setx PATH "%PATH%;C:\Program Files\Java\jdk-21\bin"
```
Restart CMD and verify:
```cmd
java -version
```

### Issue 2: "Maven not recognized"

**Solution**: Add Maven to PATH
```cmd
setx PATH "%PATH%;C:\Program Files\apache-maven-3.9.6\bin"
```
Restart CMD

### Issue 3: Long path names error

**Solution**: Enable long paths
```cmd
reg add HKLM\SYSTEM\CurrentControlSet\Control\FileSystem /v LongPathsEnabled /t REG_DWORD /d 1 /f
```
Or run from shorter path like `C:\workday`

### Issue 4: "Permission denied"

**Solution**: Run CMD as Administrator
1. Search "cmd"
2. Right-click → "Run as administrator"

### Issue 5: Credentials in plain text

**Solution**: Use Windows Credential Manager
```cmd
cmdkey /add:WorkdayAPI /user:your-username /pass:your-password
```

Then retrieve in code or use environment variables

## Using Git Bash (If Available)

If you have **Git for Windows** installed, you can use the Unix-style script:

```bash
# Open Git Bash
cd /c/path/to/workday-dataflow-pipeline

# Make script executable
chmod +x run.sh

# Run locally
./run.sh local

# Run on Dataflow
./run.sh dataflow
```

## Scheduling with Windows Task Scheduler

### Create Scheduled Task

1. **Open Task Scheduler**
2. **Action** → **Create Basic Task**
3. **Name**: Workday Daily Sync
4. **Trigger**: Daily at 2:00 AM
5. **Action**: Start a program
6. **Program**: `C:\path\to\workday-dataflow-pipeline\run-dataflow.bat`
7. **Finish**

### Or use Command Line

```cmd
schtasks /create /tn "Workday Daily Sync" /tr "C:\path\to\run-dataflow.bat" /sc daily /st 02:00
```

## Directory Structure on Windows

```
C:\workday-dataflow-pipeline\
├── src\main\java\com\example\dataflow\
│   ├── WorkdayDataflowPipeline.java
│   ├── WorkdaySoapHandler.java
│   ├── WorkdayDataModel.java
│   └── BigQuerySchema.java
├── target\
│   └── workday-dataflow-pipeline-1.0.0.jar
├── pom.xml
├── application.properties
├── set-credentials.bat (you create this)
├── run-local.bat (you create this)
└── run-dataflow.bat (you create this)
```

## Quick Reference

### One-Line Local Run
```cmd
mvn clean package && java -cp target/workday-dataflow-pipeline-1.0.0.jar com.example.dataflow.WorkdayDataflowPipeline --runner=DirectRunner --workdaySoapUrl=YOUR_URL --workdayUsername=USER --workdayPassword=PASS --effectiveDate=2025-12-15 --bigQueryTable=PROJECT:DATASET.TABLE
```

### Check Build Output
```cmd
dir target\*.jar
```

### View Logs
Logs are printed to console. To save:
```cmd
run-local.bat > output.log 2>&1
```

## Next Steps

1. ✅ Install Java 21 and Maven
2. ✅ Create `set-credentials.bat`
3. ✅ Create `run-local.bat`
4. ✅ Build: `mvn clean package`
5. ✅ Test locally: `run-local.bat`
6. ✅ Create `run-dataflow.bat` for production
7. ✅ Schedule with Task Scheduler (optional)

## Tips

- Use **short paths** (e.g., `C:\workday\` instead of long nested folders)
- Store credentials in **environment variables**, not in batch files
- Test locally first before running on Dataflow
- Use **PowerShell** for better scripting capabilities
- Keep CMD window open to see progress
