# Quick Fix for Eclipse Properties File Error

## The Error You're Seeing:
```
java.io.FileNotFoundException: config\pipeline-with-dates.properties 
(The system cannot find the path specified)
```

## 3 Quick Solutions:

### Solution 1: Fix Working Directory (Recommended)
1. In Eclipse, go to `Run` → `Run Configurations`
2. Select your configuration
3. Go to `Arguments` tab
4. In `Working directory` section:
   - Select `Other`
   - Click `Workspace...`
   - Select your project folder (`workday-bigquery-pipeline`)
   - Click `Apply`

### Solution 2: Use Eclipse Variables (Easy)
Change your program arguments to:
```
--propertiesFile=${workspace_loc:workday-bigquery-pipeline}/config/pipeline-with-dates.properties
```

### Solution 3: Use Classpath Resource (Simplest)
1. **Program arguments**: 
   ```
   --propertiesFile=eclipse-local.properties
   ```
2. **Edit the file**: `src/main/resources/eclipse-local.properties`
3. **Update your credentials** in that file

## Complete Eclipse Run Configuration:

### Program Arguments:
```
--propertiesFile=eclipse-local.properties
--effectiveFromDate=2024-01-01
--effectiveToDate=2024-01-31
```

### Main Class:
```
com.workday.dataflow.WorkdayToBigQueryPipeline
```

### Environment Variables:
```
GOOGLE_APPLICATION_CREDENTIALS=/path/to/your/service-account-key.json
```

## Step-by-Step Eclipse Setup:

1. **Right-click** on `WorkdayToBigQueryPipeline.java`
2. **Select** `Run As` → `Java Application`
3. **If it fails**, go to `Run` → `Run Configurations`
4. **Find** your configuration in the list
5. **Set Program Arguments**:
   ```
   --propertiesFile=eclipse-local.properties
   ```
6. **Set Working Directory** (Arguments tab):
   - Select `Other`
   - Click `Workspace...`
   - Choose your project
7. **Set Environment** (Environment tab):
   - Add `GOOGLE_APPLICATION_CREDENTIALS`
   - Point to your service account JSON file
8. **Click Apply and Run**

## Edit Your Configuration File:

Open `src/main/resources/eclipse-local.properties` and update:

```properties
# Your Workday credentials
workday.soap.url=https://YOUR-TENANT.workday.com/ccx/service/YOUR-TENANT/Human_Resources/v40.0
workday.username=YOUR-USERNAME@YOUR-TENANT
workday.password=YOUR-PASSWORD
workday.tenant=YOUR-TENANT

# Your Google Cloud settings
bigquery.project=YOUR-GCP-PROJECT-ID
bigquery.dataset=workday_data_dev
bigquery.table=workers_dev

# Date filters for testing
workday.effective.from.date=2024-01-01
workday.effective.to.date=2024-01-31
```

## Expected Success Output:
```
INFO  - Loading properties from classpath: eclipse-local.properties
INFO  - Successfully loaded properties from: eclipse-local.properties
INFO  - Using effective from date filter: 2024-01-01
INFO  - Using effective to date filter: 2024-01-31
INFO  - Starting Workday to BigQuery pipeline
```

## If You Still Get Errors:

### Properties File Errors:
1. **Check Project Structure**: Make sure `src/main/resources/eclipse-local.properties` exists
2. **Refresh Project**: Right-click project → Refresh
3. **Clean Build**: Project → Clean → Select your project
4. **Check Maven**: Right-click project → Maven → Reload Projects

### Serialization Errors:
If you see `NotSerializableException` or `unable to serialize DoFnWithExecutionInformation`:
1. **Clean and Rebuild**: Project → Clean → Select project → Clean
2. **Refresh Project**: Right-click project → Refresh  
3. **Restart Eclipse**: Sometimes a restart helps with serialization issues

### Authentication Errors:
1. **Check Service Account**: Verify `GOOGLE_APPLICATION_CREDENTIALS` path
2. **Test gcloud**: Run `gcloud auth application-default login`
3. **Verify Permissions**: Ensure service account has BigQuery access

The easiest solution is **Solution 3** - just use the properties file in `src/main/resources/` and update your credentials there!