# Properties Configuration Guide

This guide explains how to use the properties file approach for configuring the Workday to BigQuery pipeline.

## 🎯 Overview

The pipeline now supports configuration through properties files, making it easier to:
- ✅ Manage different environments (dev, test, prod)
- ✅ Keep sensitive data separate from code
- ✅ Switch between different data extraction scenarios
- ✅ Maintain consistent configurations across team members

## 📁 Properties Files Structure

```
workday-beam-pipeline/
├── config/
│   ├── local-dev.properties          # Local development settings
│   ├── dataflow-prod.properties      # Production Dataflow settings
│   ├── organizations.properties      # Organization data extraction
│   └── generic-data.properties       # Template for any Workday service
```

## 🔧 Eclipse Run Configuration Setup

### Step 1: Create Run Configuration

1. **Right-click project** → `Run As` → `Run Configurations`
2. **Create new** `Java Application` configuration
3. **Configure Basic Settings**:
   - **Name**: `Workday Pipeline - Local Dev`
   - **Project**: `workday-beam-pipeline`
   - **Main class**: `com.example.workday.WorkdayToBigQueryPipeline`

### Step 2: Configure Arguments

#### **Arguments Tab - Program Arguments**:
```
--propertiesFile=C:/absolute/path/to/workday-beam-pipeline/config/local-dev.properties
```

#### **Arguments Tab - VM Arguments**:
```
-DGOOGLE_APPLICATION_CREDENTIALS=C:/path/to/your-service-account-key.json
-Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG
-Xmx2g
-Xms512m
```

### Step 3: Apply and Run

1. Click **Apply**
2. Click **Run**

## 📋 Properties File Configuration

### Required Properties

Every properties file must include these required properties:

```properties
# Workday Configuration
workdayEndpoint=https://your-tenant.workday.com
workdayUsername=your_username
workdayPassword=your_password
workdayTenant=your_tenant_name
serviceName=Human_Resources
operationName=Get_Workers

# BigQuery Configuration  
bigQueryProject=your-gcp-project-id
bigQueryDataset=workday_data
bigQueryTable=workers
```

### Complete Properties Reference

```properties
# =============================================================================
# BEAM RUNNER CONFIGURATION
# =============================================================================
runner=DirectRunner                    # DirectRunner or DataflowRunner

# =============================================================================
# GOOGLE CLOUD DATAFLOW CONFIGURATION (for DataflowRunner only)
# =============================================================================
project=your-gcp-project-id
region=us-central1
jobName=workday-to-bigquery-job
maxNumWorkers=10
autoscalingAlgorithm=THROUGHPUT_BASED
machineType=n1-standard-2
stagingLocation=gs://your-bucket/staging
tempLocation=gs://your-bucket/temp
gcpTempLocation=gs://your-bucket/temp

# =============================================================================
# WORKDAY CONFIGURATION
# =============================================================================
workdayEndpoint=https://your-tenant.workday.com
workdayUsername=your_username
workdayPassword=your_password
workdayTenant=your_tenant_name
workdayVersion=v41.2
serviceName=Human_Resources
operationName=Get_Workers
pageSize=100
maxRetries=3

# =============================================================================
# BIGQUERY CONFIGURATION
# =============================================================================
bigQueryProject=your-gcp-project-id
bigQueryDataset=workday_data
bigQueryTable=workers
writeDisposition=WRITE_APPEND
createDisposition=CREATE_IF_NEEDED

# =============================================================================
# TRANSFORMATION CONFIGURATION
# =============================================================================
transformationType=worker              # generic, worker, organization
```

## 🔄 Multiple Run Configurations

Create separate run configurations for different scenarios:

### 1. **Local Development**
- **Name**: `Workday Pipeline - Local Dev`
- **Properties**: `config/local-dev.properties`
- **VM Args**: Include debug logging

### 2. **Production Dataflow**
- **Name**: `Workday Pipeline - Dataflow Prod`
- **Properties**: `config/dataflow-prod.properties`
- **VM Args**: Production memory settings

### 3. **Organization Data**
- **Name**: `Workday Pipeline - Organizations`
- **Properties**: `config/organizations.properties`
- **VM Args**: Standard settings

### 4. **Custom Service**
- **Name**: `Workday Pipeline - Custom`
- **Properties**: `config/generic-data.properties`
- **VM Args**: Customize as needed

## 🔐 Security Best Practices

### 1. **Protect Sensitive Data**

**Option A: Environment Variables**
```properties
workdayUsername=${WORKDAY_USERNAME}
workdayPassword=${WORKDAY_PASSWORD}
```

Set in Eclipse → Run Configuration → Environment tab:
```
WORKDAY_USERNAME=your_actual_username
WORKDAY_PASSWORD=your_actual_password
```

**Option B: Separate Credentials File**
Create `config/credentials.properties`:
```properties
workdayUsername=your_username
workdayPassword=your_password
```

Add to `.gitignore`:
```
config/credentials.properties
config/*-local.properties
```

### 2. **Service Account Security**
- Store service account keys outside the project directory
- Use absolute paths in VM arguments
- Never commit service account keys to version control

## 🔧 Property Override

You can override individual properties from the command line:

**Program Arguments**:
```
--propertiesFile=C:/path/to/config/local-dev.properties
--pageSize=50
--bigQueryTable=workers_test
--transformationType=generic
```

**Precedence Order** (highest to lowest):
1. Command line arguments
2. Properties file values
3. Default values in code

## 🧪 Testing Different Configurations

### Create Test-Specific Properties

**`config/test-workers.properties`**:
```properties
runner=DirectRunner
workdayEndpoint=https://your-tenant.workday.com
serviceName=Human_Resources
operationName=Get_Workers
transformationType=worker
bigQueryTable=workers_test
pageSize=10
writeDisposition=WRITE_TRUNCATE
```

**`config/test-organizations.properties`**:
```properties
runner=DirectRunner
serviceName=Human_Resources
operationName=Get_Organizations
transformationType=organization
bigQueryTable=organizations_test
pageSize=20
```

## 🚀 Quick Start Examples

### Example 1: Worker Data Extraction

**Program Arguments**:
```
--propertiesFile=C:/workday-beam-pipeline/config/local-dev.properties
```

**Properties File** (`local-dev.properties`):
```properties
runner=DirectRunner
workdayEndpoint=https://your-tenant.workday.com
workdayUsername=your_username
workdayPassword=your_password
workdayTenant=your_tenant
serviceName=Human_Resources
operationName=Get_Workers
bigQueryProject=your-project
bigQueryDataset=workday_dev
bigQueryTable=workers
transformationType=worker
pageSize=50
```

### Example 2: Organization Data Extraction

**Program Arguments**:
```
--propertiesFile=C:/workday-beam-pipeline/config/organizations.properties
```

**Properties File** (`organizations.properties`):
```properties
runner=DirectRunner
serviceName=Human_Resources
operationName=Get_Organizations
transformationType=organization
bigQueryTable=organizations
pageSize=100
```

### Example 3: Custom Service Extraction

**Program Arguments**:
```
--propertiesFile=C:/workday-beam-pipeline/config/generic-data.properties
--serviceName=Financial_Management
--operationName=Get_Cost_Centers
--bigQueryTable=cost_centers
```

## 📊 Monitoring and Logging

### Enable Debug Logging

**VM Arguments**:
```
-Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG
-Dorg.slf4j.simpleLogger.showDateTime=true
-Dorg.slf4j.simpleLogger.dateTimeFormat=yyyy-MM-dd HH:mm:ss
```

### Log Configuration Properties

Add to properties file:
```properties
# Logging (reference only - set in VM arguments)
# -Dorg.slf4j.simpleLogger.log.com.example.workday=DEBUG
# -Dorg.slf4j.simpleLogger.log.org.apache.beam=WARN
```

## ❗ Troubleshooting

### Common Issues:

1. **Properties file not found**
   - Use absolute paths in `--propertiesFile`
   - Check file exists and is readable

2. **Missing required properties**
   - Review error message for specific missing property
   - Check property names match exactly (case-sensitive)

3. **Authentication errors**
   - Verify `GOOGLE_APPLICATION_CREDENTIALS` path
   - Check service account has BigQuery permissions

4. **Workday connection issues**
   - Verify endpoint URL format
   - Test credentials manually if possible

### Debug Steps:

1. **Enable debug logging** in VM arguments
2. **Check console output** for property loading messages
3. **Verify property values** are loaded correctly
4. **Test with minimal properties** first

## 📝 Template Creation

To create a new properties configuration:

1. **Copy existing template**:
   ```bash
   cp config/local-dev.properties config/my-custom.properties
   ```

2. **Edit values**:
   - Update Workday credentials
   - Change service/operation names
   - Modify BigQuery settings
   - Adjust performance parameters

3. **Create run configuration**:
   - Program args: `--propertiesFile=C:/path/to/config/my-custom.properties`
   - VM args: Include authentication and memory settings

4. **Test and refine**:
   - Start with small page sizes
   - Enable debug logging
   - Verify data extraction works

---

This properties-based approach provides a clean, maintainable way to manage pipeline configurations across different environments and use cases.