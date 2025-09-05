# Eclipse IDE Setup Instructions

## Prerequisites
- Eclipse IDE for Enterprise Java Developers
- Java 11 or higher
- Maven integration for Eclipse (m2e plugin)

## Setting up the Project in Eclipse

### 1. Import the Maven Project
1. Open Eclipse IDE
2. Go to `File` → `Import`
3. Select `Existing Maven Projects`
4. Browse to the `workday-beam-pipeline` directory
5. Click `Finish`

### 2. Configure Java Build Path
1. Right-click on the project → `Properties`
2. Go to `Java Build Path` → `Libraries`
3. Ensure Java 11+ is selected in `Modulepath` or `Classpath`
4. If needed, add JRE: `Add Library` → `JRE System Library` → Select Java 11+

### 3. Maven Configuration
1. Right-click on project → `Maven` → `Reload Projects`
2. If dependencies are not downloading, try:
   - Right-click project → `Maven` → `Update Project`
   - Check "Force Update of Snapshots/Releases"

### 4. Run Configurations

#### Local Development Run Configuration
1. Right-click project → `Run As` → `Run Configurations`
2. Create new `Java Application` configuration
3. Set:
   - **Name**: `Workday Pipeline Local`
   - **Project**: `workday-beam-pipeline`
   - **Main class**: `com.example.workday.WorkdayToBigQueryPipeline`
   - **Arguments**: Add program arguments (see example below)

#### Example Program Arguments for Local Run:
```
--runner=DirectRunner
--workdayEndpoint=https://wd2-impl-services1.workday.com
--workdayUsername=your_username
--workdayPassword=your_password
--workdayTenant=your_tenant
--serviceName=Human_Resources
--operationName=Get_Workers
--bigQueryProject=your-project-id
--bigQueryDataset=workday_data
--bigQueryTable=workers
--transformationType=worker
--pageSize=50
```

#### Dataflow Run Configuration
1. Create another `Java Application` configuration
2. Set:
   - **Name**: `Workday Pipeline Dataflow`
   - **Main class**: `com.example.workday.WorkdayToBigQueryPipeline`
   - **Arguments**: Add Dataflow-specific arguments

#### Example Program Arguments for Dataflow:
```
--runner=DataflowRunner
--project=your-gcp-project
--region=us-central1
--jobName=workday-to-bigquery-job
--stagingLocation=gs://your-bucket/staging
--tempLocation=gs://your-bucket/temp
--workdayEndpoint=https://wd2-impl-services1.workday.com
--workdayUsername=your_username
--workdayPassword=your_password
--workdayTenant=your_tenant
--serviceName=Human_Resources
--operationName=Get_Workers
--bigQueryProject=your-project-id
--bigQueryDataset=workday_data
--bigQueryTable=workers
--transformationType=worker
```

### 5. Environment Variables (Optional)
For security, you can set sensitive information as environment variables:

1. In Run Configuration → `Environment` tab
2. Add variables:
   - `WORKDAY_USERNAME`
   - `WORKDAY_PASSWORD`
   - `GOOGLE_APPLICATION_CREDENTIALS` (path to service account key)

Then modify arguments to use `${WORKDAY_USERNAME}` instead of hardcoded values.

### 6. Debug Configuration
1. Create a `Debug Configuration` similar to Run Configuration
2. Set breakpoints in your code
3. Use `Debug As` → `Java Application`

### 7. Testing
1. Right-click on `src/test/java` → `Run As` → `JUnit Test`
2. Or run specific test classes

## Troubleshooting

### Common Issues:
1. **Maven dependencies not resolving**: 
   - Check internet connection
   - Try `mvn clean install` from command line
   
2. **Java version mismatch**:
   - Ensure project is using Java 11+
   - Check `Project Properties` → `Java Build Path`
   
3. **Google Cloud authentication**:
   - Set `GOOGLE_APPLICATION_CREDENTIALS` environment variable
   - Or run `gcloud auth application-default login`

4. **Workday connection issues**:
   - Verify endpoint URL, credentials, and tenant name
   - Check network connectivity and firewall settings

### Useful Eclipse Plugins:
- **Google Cloud Tools for Eclipse**: For better GCP integration
- **Buildship Gradle Integration**: If you prefer Gradle
- **SonarLint**: For code quality analysis