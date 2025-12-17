# Configuration Guide - How to Pass Parameters

## ❌ Common Error

```
java.lang.IllegalArgumentException: Argument 'file://src/main/resources/config/application.properties' does not begin with '--'
```

**Cause**: Trying to pass a properties file directly as an argument. **This doesn't work!**

## ✅ Correct Approach

The `application.properties` file is **just a reference/template**. You must pass parameters in one of these ways:

## Method 1: Environment Variables (Recommended)

### Windows CMD/PowerShell
```cmd
REM Set environment variables
set WORKDAY_SOAP_URL=https://wd2-impl.workday.com/ccx/service/tenant/Human_Resources/v38.0
set WORKDAY_USERNAME=user@tenant
set WORKDAY_PASSWORD=your-password
set WORKDAY_TENANT_ID=tenant
set EFFECTIVE_DATE=2025-12-15
set BIGQUERY_TABLE=project:dataset.employees

REM Then run
java -cp target/workday-dataflow-pipeline-1.0.0.jar ^
  com.example.dataflow.WorkdayDataflowPipeline ^
  --runner=DirectRunner ^
  --workdaySoapUrl=%WORKDAY_SOAP_URL% ^
  --workdayUsername=%WORKDAY_USERNAME% ^
  --workdayPassword=%WORKDAY_PASSWORD% ^
  --workdayTenantId=%WORKDAY_TENANT_ID% ^
  --effectiveDate=%EFFECTIVE_DATE% ^
  --bigQueryTable=%BIGQUERY_TABLE%
```

### Linux/Mac/Git Bash
```bash
export WORKDAY_SOAP_URL="https://..."
export WORKDAY_USERNAME="user@tenant"
export WORKDAY_PASSWORD="password"
export EFFECTIVE_DATE="2025-12-15"
export BIGQUERY_TABLE="project:dataset.employees"

# Then run
./run.sh local
```

## Method 2: Direct Command-Line Arguments

### Windows
```cmd
java -cp target/workday-dataflow-pipeline-1.0.0.jar ^
  com.example.dataflow.WorkdayDataflowPipeline ^
  --runner=DirectRunner ^
  --workdaySoapUrl=https://wd2-impl.workday.com/ccx/service/tenant/Human_Resources/v38.0 ^
  --workdayUsername=user@tenant ^
  --workdayPassword=your-password ^
  --workdayTenantId=tenant ^
  --effectiveDate=2025-12-15 ^
  --bigQueryTable=project:dataset.employees ^
  --writeDisposition=WRITE_APPEND
```

### Linux/Mac
```bash
java -cp target/workday-dataflow-pipeline-1.0.0.jar \
  com.example.dataflow.WorkdayDataflowPipeline \
  --runner=DirectRunner \
  --workdaySoapUrl=https://... \
  --workdayUsername=user@tenant \
  --workdayPassword=password \
  --effectiveDate=2025-12-15 \
  --bigQueryTable=project:dataset.employees
```

## Method 3: Eclipse Run Configuration

1. **Right-click** `WorkdayDataflowPipeline.java` → **Run As** → **Run Configurations**

2. **Arguments** tab → **Program arguments**:

```
--runner=DirectRunner
--workdaySoapUrl=https://wd2-impl.workday.com/ccx/service/YOUR-TENANT/Human_Resources/v38.0
--workdayUsername=integration_user@tenant
--workdayPassword=your-password
--workdayTenantId=your-tenant
--effectiveDate=2025-12-15
--bigQueryTable=your-project:workday_data.employees
--writeDisposition=WRITE_APPEND
--maxRetries=3
```

3. **Click Apply and Run**

## Method 4: Maven Exec Plugin (Alternative)

Add to your command:

```cmd
mvn exec:java -Dexec.mainClass="com.example.dataflow.WorkdayDataflowPipeline" ^
  -Dexec.args="--runner=DirectRunner ^
    --workdaySoapUrl=https://... ^
    --workdayUsername=user@tenant ^
    --workdayPassword=password ^
    --effectiveDate=2025-12-15 ^
    --bigQueryTable=project:dataset.employees"
```

## What is application.properties For?

The `application.properties` file is **ONLY a reference/template** showing you what values to configure.

**It is NOT automatically loaded by the application!**

```properties
# application.properties - THIS IS JUST A TEMPLATE
# Copy these values to your environment variables or command-line arguments

workday.soap.url=https://...  # Copy this value
workday.username=user@tenant  # Copy this value
# etc.
```

## Required Parameters

### Minimum Required (Local Run)
```
--runner=DirectRunner
--workdaySoapUrl=<your-workday-url>
--workdayUsername=<username@tenant>
--workdayPassword=<password>
--effectiveDate=<YYYY-MM-DD>
--bigQueryTable=<project:dataset.table>
```

### Additional for Dataflow (Cloud Run)
```
--runner=DataflowRunner
--project=<gcp-project-id>
--region=<gcp-region>
--tempLocation=<gs://bucket/temp>
--stagingLocation=<gs://bucket/staging>
```

## Full Example: Windows CMD

### Step 1: Create set-env.bat
```batch
@echo off
set WORKDAY_SOAP_URL=https://wd2-impl-services1.workday.com/ccx/service/acme_corp/Human_Resources/v38.0
set WORKDAY_USERNAME=integration_svc@acme_corp
set WORKDAY_PASSWORD=MySecurePassword123
set WORKDAY_TENANT_ID=acme_corp
set EFFECTIVE_DATE=2025-12-15
set BIGQUERY_TABLE=my-project:workday_data.employees
```

### Step 2: Run the pipeline
```batch
REM Load environment variables
call set-env.bat

REM Build
mvn clean package -DskipTests

REM Run
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

## Troubleshooting

### Error: "Argument ... does not begin with '--'"

**Cause**: You're trying to pass something that's not a proper argument.

**Fix**: Make sure every argument starts with `--`

❌ **Wrong**:
```cmd
java -cp target/jar.jar Main application.properties
java -cp target/jar.jar Main file://config.properties
```

✅ **Correct**:
```cmd
java -cp target/jar.jar Main --workdaySoapUrl=https://...
```

### Error: "Missing required option: workdaySoapUrl"

**Cause**: Required parameter not provided

**Fix**: Add all required parameters:
```cmd
--workdaySoapUrl=...
--workdayUsername=...
--workdayPassword=...
--effectiveDate=...
--bigQueryTable=...
```

### Error: "Invalid URL format"

**Cause**: URL contains spaces or special characters without proper escaping

**Fix**: Quote the URL:
```cmd
--workdaySoapUrl="https://wd2-impl-services1.workday.com/ccx/service/tenant/Human_Resources/v38.0"
```

## Quick Reference

### All Available Parameters

| Parameter | Required | Example |
|-----------|----------|---------|
| `--runner` | Yes | DirectRunner or DataflowRunner |
| `--workdaySoapUrl` | Yes | https://wd2-impl.workday.com/ccx/service/... |
| `--workdayUsername` | Yes | integration_user@tenant |
| `--workdayPassword` | Yes | your-password |
| `--workdayTenantId` | No | your-tenant |
| `--effectiveDate` | Yes | 2025-12-15 |
| `--bigQueryTable` | Yes | project:dataset.table |
| `--writeDisposition` | No | WRITE_APPEND (default) |
| `--maxRetries` | No | 3 (default) |
| `--project` | Yes* | your-gcp-project (*Dataflow only) |
| `--region` | No | us-central1 (Dataflow) |
| `--tempLocation` | Yes* | gs://bucket/temp (*Dataflow only) |
| `--stagingLocation` | Yes* | gs://bucket/staging (*Dataflow only) |
| `--numWorkers` | No | 2 (default) |
| `--maxNumWorkers` | No | 5 (default) |

## Summary

1. ✅ **Use environment variables** + command-line arguments
2. ✅ **Use `--` prefix** for all arguments
3. ✅ **Set variables in batch files** (Windows) or shell (Linux)
4. ❌ **Don't try to load** `application.properties` directly
5. ❌ **Don't pass file paths** as arguments

The `application.properties` file is just a **reference guide** - copy values from it to your environment variables or command-line arguments!
