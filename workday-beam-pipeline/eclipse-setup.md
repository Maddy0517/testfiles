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

## 🔧 Run Configurations with Properties Files

### 4. Properties File Approach (RECOMMENDED)

All configuration is now managed through properties files. This approach is cleaner and easier to maintain.

#### 4.1. Local Development Run Configuration
1. Right-click project → `Run As` → `Run Configurations`
2. Create new `Java Application` configuration
3. Set:
   - **Name**: `Workday Pipeline - Local Dev`
   - **Project**: `workday-beam-pipeline`
   - **Main class**: `com.example.workday.WorkdayToBigQueryPipeline`

4. **Arguments Tab**:
   - **Program arguments**: 
     ```
     --propertiesFile=C:/path/to/workday-beam-pipeline/config/local-dev.properties
     ```
   - **VM arguments**:
     ```
     -DGOOGLE_APPLICATION_CREDENTIALS=C:/path/to/service-account-key.json
     -Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG
     -Xmx2g
     -Xms512m
     ```

#### 4.2. Dataflow Production Run Configuration
1. Create another `Java Application` configuration
2. Set:
   - **Name**: `Workday Pipeline - Dataflow Prod`
   - **Project**: `workday-beam-pipeline`
   - **Main class**: `com.example.workday.WorkdayToBigQueryPipeline`

3. **Arguments Tab**:
   - **Program arguments**: 
     ```
     --propertiesFile=C:/path/to/workday-beam-pipeline/config/dataflow-prod.properties
     ```
   - **VM arguments**:
     ```
     -DGOOGLE_APPLICATION_CREDENTIALS=C:/path/to/service-account-key.json
     -Xmx4g
     -Xms1g
     ```

#### 4.3. Organizations Data Run Configuration
1. Create another `Java Application` configuration
2. Set:
   - **Name**: `Workday Pipeline - Organizations`
   - **Project**: `workday-beam-pipeline`
   - **Main class**: `com.example.workday.WorkdayToBigQueryPipeline`

3. **Arguments Tab**:
   - **Program arguments**: 
     ```
     --propertiesFile=C:/path/to/workday-beam-pipeline/config/organizations.properties
     ```
   - **VM arguments**:
     ```
     -DGOOGLE_APPLICATION_CREDENTIALS=C:/path/to/service-account-key.json
     -Dorg.slf4j.simpleLogger.defaultLogLevel=INFO
     -Xmx2g
     -Xms512m
     ```

## 📁 Available Properties Files

The project includes several pre-configured properties files:

### 1. **`config/local-dev.properties`**
- DirectRunner (local execution)
- Small page sizes for development
- Debug logging enabled
- Truncate mode for clean testing

### 2. **`config/dataflow-prod.properties`**
- DataflowRunner (Google Cloud)
- Production-optimized settings
- Large page sizes and worker counts
- Append mode for production data

### 3. **`config/organizations.properties`**
- Configured for organization data extraction
- Uses organization transformation
- Separate BigQuery table

### 4. **`config/generic-data.properties`**
- Template for any Workday service
- Generic transformation preserves all data
- Easy to customize for different services

## 🔧 Customizing Properties Files

### 1. **Edit Properties Files**
Before running, customize the properties files with your actual values:

```properties
# Update these values in your chosen properties file
workdayEndpoint=https://your-tenant.workday.com
workdayUsername=your_actual_username
workdayPassword=your_actual_password
workdayTenant=your_tenant_name
bigQueryProject=your-gcp-project-id
```

### 2. **Override Individual Properties**
You can override specific properties from command line:

**Program arguments**:
```
--propertiesFile=C:/path/to/config/local-dev.properties
--pageSize=25
--bigQueryTable=workers_test
```

Command line arguments take precedence over properties file values.

## 🔧 VM Arguments Reference

### Common VM Arguments:
```
# Google Cloud Authentication
-DGOOGLE_APPLICATION_CREDENTIALS=C:/path/to/service-account-key.json

# Logging Level
-Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG

# Memory Settings
-Xmx4g
-Xms1g

# System Properties
-Dfile.encoding=UTF-8
-Duser.timezone=UTC
```

## 🔧 Environment Variables (Alternative)

For enhanced security, you can use environment variables:

### 1. **Set Environment Variables**
In Eclipse Run Configuration → `Environment` tab:
```
WORKDAY_USERNAME=your_username
WORKDAY_PASSWORD=your_password
GOOGLE_APPLICATION_CREDENTIALS=C:/path/to/service-account-key.json
```

### 2. **Update Properties Files**
Use environment variable placeholders:
```properties
workdayUsername=${WORKDAY_USERNAME}
workdayPassword=${WORKDAY_PASSWORD}
```

## 🔧 Debug Configuration

### 1. **Create Debug Configuration**
1. Right-click project → `Debug As` → `Debug Configurations`
2. Create new `Java Application` configuration
3. Use same settings as Run Configuration
4. Set breakpoints in your code

### 2. **Debug with Properties**
**Program arguments**:
```
--propertiesFile=C:/path/to/config/local-dev.properties
```

**VM arguments**:
```
-DGOOGLE_APPLICATION_CREDENTIALS=C:/path/to/service-account-key.json
-Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG
-Xmx2g
```

## 🧪 Testing Configuration

### 1. **Run Unit Tests**
1. Right-click on `src/test/java` → `Run As` → `JUnit Test`
2. Or run specific test classes

### 2. **Test with Different Properties**
Create test-specific properties files:
```
config/test.properties
config/integration-test.properties
```

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