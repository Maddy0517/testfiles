# Running Workday to BigQuery Pipeline in Eclipse IDE

## Prerequisites

Before setting up in Eclipse, ensure you have:

- **Eclipse IDE** (2020-03 or later recommended)
- **Java 11** or higher
- **Maven** integration in Eclipse (usually included by default)
- **Google Cloud SDK** installed locally
- **Service Account Key** JSON file for Google Cloud authentication

## Step-by-Step Setup

### 1. Import the Maven Project

1. **Open Eclipse IDE**

2. **Import the Project**:
   - Go to `File` → `Import`
   - Select `Existing Maven Projects`
   - Click `Next`

3. **Browse to Project**:
   - Click `Browse` and navigate to your workspace directory
   - Select the root folder containing `pom.xml`
   - Eclipse will automatically detect it as a Maven project
   - Click `Finish`

4. **Wait for Maven Dependencies**:
   - Eclipse will automatically download all dependencies
   - This may take a few minutes for the first time
   - Check the bottom-right corner for progress

### 2. Configure Project Settings

1. **Set Java Version**:
   - Right-click on the project → `Properties`
   - Go to `Java Build Path` → `Libraries`
   - Expand `Modulepath` or `Classpath`
   - If JRE version is wrong, remove it and add JRE 11+
   - Go to `Project Facets` and ensure Java version is 11+

2. **Refresh and Clean**:
   - Right-click project → `Refresh`
   - Go to `Project` → `Clean` → Select your project → `Clean`

### 3. Set Up Configuration

1. **Create Local Properties File**:
   ```
   config/my-local-pipeline.properties
   ```

2. **Edit the Properties File**:
   ```properties
   # Workday Configuration
   workday.soap.url=https://your-tenant.workday.com/ccx/service/your-tenant/Human_Resources/v40.0
   workday.username=your-username@your-tenant
   workday.password=your-password
   workday.tenant=your-tenant
   workday.api.version=v40.0

   # BigQuery Configuration
   bigquery.project=your-gcp-project-id
   bigquery.dataset=workday_data_dev
   bigquery.table=workers_dev
   bigquery.write.disposition=WRITE_TRUNCATE
   bigquery.create.disposition=CREATE_IF_NEEDED

   # Local Development Settings
   api.batch.size=5
   api.max.retries=2
   api.request.timeout=30000
   api.enable.pagination=true
   api.page.size=5

   # Date Filters (optional)
   workday.effective.from.date=2024-01-01
   workday.effective.to.date=2024-01-31
   ```

### 4. Set Up Google Cloud Authentication

**Option 1: Service Account Key File**
1. Download your service account key JSON file
2. Place it in a secure location (e.g., `~/.gcp/service-account-key.json`)
3. Set environment variable in Eclipse:
   - Go to `Run` → `Run Configurations`
   - Select your run configuration
   - Go to `Environment` tab
   - Add: `GOOGLE_APPLICATION_CREDENTIALS` = `/path/to/your/service-account-key.json`

**Option 2: Application Default Credentials**
1. Open terminal/command prompt
2. Run: `gcloud auth application-default login`
3. Follow the browser authentication flow

### 5. Create Run Configuration

1. **Right-click on `WorkdayToBigQueryPipeline.java`**
   - Select `Run As` → `Java Application`

2. **Or Create Custom Run Configuration**:
   - Go to `Run` → `Run Configurations`
   - Right-click `Java Application` → `New Configuration`
   - Set the following:

   **Main Tab**:
   - **Name**: `Workday to BigQuery Pipeline`
   - **Project**: `workday-bigquery-pipeline`
   - **Main class**: `com.workday.dataflow.WorkdayToBigQueryPipeline`

   **Arguments Tab** - Program arguments:
   ```
   --propertiesFile=config/my-local-pipeline.properties
   ```

   **Arguments Tab** - VM arguments (optional):
   ```
   -Xmx2g
   -Dlog4j.configuration=file:src/main/resources/log4j.properties
   ```

   **Environment Tab**:
   - Add `GOOGLE_APPLICATION_CREDENTIALS` if using service account key

3. **Click `Apply` and `Run`**

### 6. Alternative: Run with Date Filters

You can also pass date filters as program arguments:

**Program arguments**:
```
--propertiesFile=config/my-local-pipeline.properties
--effectiveFromDate=2024-01-01
--effectiveToDate=2024-01-31
```

### 7. Debug Configuration

For debugging:

1. **Create Debug Configuration**:
   - Go to `Run` → `Debug Configurations`
   - Right-click `Java Application` → `New Configuration`
   - Use same settings as Run Configuration
   - Click `Debug`

2. **Set Breakpoints**:
   - Double-click on line numbers in Java files
   - Breakpoints will appear as blue dots
   - Program will pause at breakpoints during debug

### 8. View Logs and Output

1. **Console Output**:
   - Check the `Console` tab at the bottom of Eclipse
   - All pipeline logs will appear here

2. **Common Log Messages to Look For**:
   ```
   INFO  - Loading configuration from: config/my-local-pipeline.properties
   INFO  - Using effective from date filter: 2024-01-01
   INFO  - Using effective to date filter: 2024-01-31
   INFO  - Starting to fetch workers from Workday API
   INFO  - Successfully fetched 10 workers
   INFO  - Writing to BigQuery table: your-project:workday_data_dev.workers_dev
   INFO  - Pipeline completed with state: DONE
   ```

## Common Eclipse Issues and Solutions

### Issue 1: Maven Dependencies Not Downloaded

**Solution**:
1. Right-click project → `Maven` → `Reload Projects`
2. Or go to `Project` → `Clean` → Clean project
3. Check internet connection and Maven settings

### Issue 2: Java Version Mismatch

**Solution**:
1. Right-click project → `Properties`
2. Go to `Project Facets`
3. Change Java version to 11 or higher
4. Go to `Java Build Path` → `Libraries`
5. Remove old JRE and add correct JRE version

### Issue 3: Class Not Found Error

**Solution**:
1. Ensure all Maven dependencies are downloaded
2. Check `Java Build Path` → `Libraries` → `Maven Dependencies`
3. Refresh project: Right-click → `Refresh`

### Issue 4: Authentication Errors

**Solution**:
1. Verify `GOOGLE_APPLICATION_CREDENTIALS` environment variable
2. Check if service account key file exists and is readable
3. Ensure service account has proper BigQuery permissions

### Issue 5: Workday Connection Issues

**Solution**:
1. Verify Workday credentials in properties file
2. Check network connectivity to Workday tenant
3. Ensure SOAP API URL is correct

## Eclipse Project Structure

After import, your Eclipse project should look like:

```
workday-bigquery-pipeline/
├── src/main/java/
│   └── com.workday.dataflow/
│       ├── WorkdayToBigQueryPipeline.java    ← Main class to run
│       ├── WorkdayToBigQueryOptions.java
│       ├── ConfigurationManager.java
│       ├── WorkdayApiClient.java
│       ├── WorkdaySourceTransform.java
│       ├── WorkerDataTransform.java
│       ├── BigQuerySinkTransform.java
│       └── WorkerData.java
├── src/main/resources/
├── src/test/java/
├── config/
│   ├── pipeline.properties
│   ├── pipeline-local.properties
│   └── my-local-pipeline.properties         ← Your local config
├── Maven Dependencies/                       ← Auto-generated
├── pom.xml
└── target/                                  ← Build output
```

## Running Tests in Eclipse

1. **Run All Tests**:
   - Right-click on `src/test/java` → `Run As` → `JUnit Test`

2. **Run Single Test**:
   - Right-click on test class → `Run As` → `JUnit Test`

3. **Debug Tests**:
   - Right-click on test class → `Debug As` → `JUnit Test`

## Tips for Eclipse Development

1. **Auto-Import**: Use `Ctrl+Shift+O` to organize imports
2. **Code Formatting**: Use `Ctrl+Shift+F` to format code
3. **Quick Fix**: Use `Ctrl+1` for quick fixes and suggestions
4. **Search**: Use `Ctrl+H` to search across project files
5. **Navigate**: Use `Ctrl+Click` on class names to navigate
6. **Maven Console**: Check `Window` → `Show View` → `Other` → `Maven` → `Maven Console`

## Example Run Output

When successful, you should see output like:

```
INFO  c.w.d.ConfigurationManager - Loading configuration from: config/my-local-pipeline.properties
INFO  c.w.d.ConfigurationManager - Applied configuration properties to pipeline options
INFO  c.w.d.WorkdayApiClient - Using effective from date filter: 2024-01-01
INFO  c.w.d.WorkdayApiClient - Using effective to date filter: 2024-01-31
INFO  c.w.d.WorkdayToBigQueryPipeline - Starting Workday to BigQuery pipeline
INFO  c.w.d.WorkdayToBigQueryPipeline - Running with DirectRunner - waiting for completion
INFO  c.w.d.WorkdaySourceTransform - Starting to fetch workers from Workday API
INFO  c.w.d.WorkdayApiClient - Fetching worker data page: 1
INFO  c.w.d.WorkdayApiClient - Successfully fetched 5 workers
INFO  c.w.d.WorkdayToBigQueryPipeline - Pipeline completed with state: DONE
```

## Next Steps

After successful setup:

1. **Test with Small Data**: Start with small date ranges
2. **Verify BigQuery**: Check that data appears in your BigQuery table
3. **Expand Configuration**: Gradually increase batch sizes and date ranges
4. **Deploy to Dataflow**: Once tested locally, deploy to Google Cloud Dataflow

For more details, refer to the main [README.md](README.md) and [DATE_FILTERING_GUIDE.md](DATE_FILTERING_GUIDE.md).