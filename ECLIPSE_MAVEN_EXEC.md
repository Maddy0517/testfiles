# Running with Maven exec:java in Eclipse

## ❌ What Doesn't Work

```
mvn clean compile exec:java 
  -Dexec.mainClass=com.example.dataflow.WorkdayDataflowPipeline 
  -Dexec.args=file://src/main/resources/config/application.properties
```

**Problem**: The application doesn't load properties files. It needs individual `--` arguments.

## ✅ Correct Approach - Maven exec:java

### Method 1: Full Command with All Arguments

```
mvn clean compile exec:java ^
  -Dexec.mainClass=com.example.dataflow.WorkdayDataflowPipeline ^
  -Dexec.args="--runner=DirectRunner --workdaySoapUrl=https://wd2-impl-services1.workday.com/ccx/service/YOUR-TENANT/Human_Resources/v38.0 --workdayUsername=integration_user@tenant --workdayPassword=your-password --workdayTenantId=your-tenant --effectiveDate=2025-12-15 --bigQueryTable=your-project:workday_data.employees --writeDisposition=WRITE_APPEND"
```

**Note**: All arguments must be in quotes after `-Dexec.args=`

### Method 2: Eclipse Maven Run Configuration (RECOMMENDED)

This is much easier in Eclipse!

#### Step 1: Create Maven Run Configuration

1. **Right-click** on `pom.xml` → **Run As** → **Maven build...**

2. **Name**: `Workday Pipeline - Local (Maven)`

3. **Base directory**: `${project_loc}`

4. **Goals**: `clean compile exec:java`

5. **Parameters** tab → Add parameters:

| Name | Value |
|------|-------|
| `exec.mainClass` | `com.example.dataflow.WorkdayDataflowPipeline` |
| `exec.args` | `--runner=DirectRunner --workdaySoapUrl=https://wd2-impl-services1.workday.com/ccx/service/YOUR-TENANT/Human_Resources/v38.0 --workdayUsername=integration_user@tenant --workdayPassword=your-password --workdayTenantId=your-tenant --effectiveDate=2025-12-15 --bigQueryTable=your-project:workday_data.employees` |

6. Click **Apply** then **Run**

#### Step 2: Reuse Configuration

- **Run** → **Run History** → Select your saved configuration
- Or press **Ctrl+F11** after selecting it once

## Better Alternative: Direct Java Application Run

Instead of using Maven exec, run directly as Java Application:

### Step 1: Create Java Application Run Configuration

1. **Right-click** `WorkdayDataflowPipeline.java` → **Run As** → **Run Configurations...**

2. **Right-click** "Java Application" → **New Configuration**

3. **Name**: `Workday Pipeline - Local`

4. **Project**: Select your project

5. **Main class**: `com.example.dataflow.WorkdayDataflowPipeline`

6. **Arguments** tab → **Program arguments**:
```
--runner=DirectRunner
--workdaySoapUrl=https://wd2-impl-services1.workday.com/ccx/service/YOUR-TENANT/Human_Resources/v38.0
--workdayUsername=integration_user@tenant
--workdayPassword=your-password
--workdayTenantId=your-tenant
--effectiveDate=2025-12-15
--bigQueryTable=your-project:workday_data.employees
--writeDisposition=WRITE_APPEND
--maxRetries=3
```

7. **JRE** tab → Ensure Java 21 is selected

8. Click **Apply** and **Run**

### Why This is Better:
- ✅ Faster (no Maven overhead)
- ✅ Easier to debug
- ✅ Can set breakpoints
- ✅ Cleaner console output

## Using Properties File (If You Really Want To)

If you want to load properties from a file, you need to add code to read them. Here's how:

### Option A: Modify Main Method (Not Recommended)

Add this to `WorkdayDataflowPipeline.java`:

```java
public static void main(String[] args) {
    // Load from properties file if first arg is a file path
    if (args.length > 0 && args[0].contains(".properties")) {
        try {
            Properties props = new Properties();
            props.load(new FileInputStream(args[0].replace("file://", "")));
            
            // Convert properties to arguments
            List<String> argList = new ArrayList<>();
            argList.add("--runner=DirectRunner");
            argList.add("--workdaySoapUrl=" + props.getProperty("workday.soap.url"));
            argList.add("--workdayUsername=" + props.getProperty("workday.username"));
            argList.add("--workdayPassword=" + props.getProperty("workday.password"));
            argList.add("--workdayTenantId=" + props.getProperty("workday.tenant.id"));
            argList.add("--effectiveDate=" + props.getProperty("effective.date"));
            argList.add("--bigQueryTable=" + props.getProperty("bigquery.table"));
            
            args = argList.toArray(new String[0]);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load properties", e);
        }
    }
    
    // Rest of existing main method
    WorkdayPipelineOptions options = PipelineOptionsFactory
            .fromArgs(args)
            .withValidation()
            .as(WorkdayPipelineOptions.class);
    
    runPipeline(options);
}
```

**But this is NOT recommended** - better to use standard argument passing.

### Option B: Use Spring Boot Properties (Advanced)

This requires adding Spring Boot dependencies, which is overkill for this project.

## Complete Examples

### Eclipse Console - Maven exec:java

In Eclipse **Console** view, run:

```bash
# Windows (use ^ for line continuation)
mvn clean compile exec:java ^
  -Dexec.mainClass=com.example.dataflow.WorkdayDataflowPipeline ^
  -Dexec.args="--runner=DirectRunner --workdaySoapUrl=https://wd2-impl-services1.workday.com/ccx/service/acme/Human_Resources/v38.0 --workdayUsername=svc_user@acme --workdayPassword=SecurePass123 --workdayTenantId=acme --effectiveDate=2025-12-15 --bigQueryTable=my-project:workday_data.employees"

# Linux/Mac (use \ for line continuation)
mvn clean compile exec:java \
  -Dexec.mainClass=com.example.dataflow.WorkdayDataflowPipeline \
  -Dexec.args="--runner=DirectRunner --workdaySoapUrl=https://... --workdayUsername=user@tenant --workdayPassword=password --effectiveDate=2025-12-15 --bigQueryTable=project:dataset.table"
```

### Run Configuration XML (for sharing)

Save this as `.launch` file in your Eclipse workspace:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<launchConfiguration type="org.eclipse.jdt.launching.localJavaApplication">
    <stringAttribute key="org.eclipse.jdt.launching.MAIN_TYPE" value="com.example.dataflow.WorkdayDataflowPipeline"/>
    <stringAttribute key="org.eclipse.jdt.launching.PROGRAM_ARGUMENTS" value="--runner=DirectRunner&#10;--workdaySoapUrl=https://wd2-impl-services1.workday.com/ccx/service/YOUR-TENANT/Human_Resources/v38.0&#10;--workdayUsername=integration_user@tenant&#10;--workdayPassword=your-password&#10;--workdayTenantId=your-tenant&#10;--effectiveDate=2025-12-15&#10;--bigQueryTable=your-project:workday_data.employees"/>
    <stringAttribute key="org.eclipse.jdt.launching.PROJECT_ATTR" value="workday-dataflow-pipeline"/>
    <stringAttribute key="org.eclipse.jdt.launching.VM_ARGUMENTS" value=""/>
</launchConfiguration>
```

## Comparison: exec:java vs Java Application

| Aspect | Maven exec:java | Java Application |
|--------|----------------|------------------|
| **Speed** | Slower (Maven overhead) | Faster (direct) |
| **Debugging** | Harder | Easier |
| **Dependencies** | Auto-managed | Auto-managed |
| **Console Output** | Maven + app logs | App logs only |
| **Recommended for** | CI/CD pipelines | Development |

## My Recommendation for Eclipse

**Use Java Application Run Configuration** - It's:
- ✅ Faster
- ✅ Easier to configure
- ✅ Better for debugging
- ✅ Standard Eclipse workflow

Save Maven exec:java for:
- Command-line runs
- CI/CD pipelines
- Automated builds

## Quick Setup Steps

### For Eclipse (Recommended):

1. **Right-click** `WorkdayDataflowPipeline.java`
2. **Run As** → **Run Configurations...**
3. **Java Application** → **New**
4. **Arguments** tab → Add all `--parameter=value` arguments
5. **Apply** and **Run**

### For Maven exec:java (Alternative):

1. **Right-click** `pom.xml`
2. **Run As** → **Maven build...**
3. **Goals**: `clean compile exec:java`
4. **Parameter**: `exec.mainClass` = `com.example.dataflow.WorkdayDataflowPipeline`
5. **Parameter**: `exec.args` = `--runner=DirectRunner --workdaySoapUrl=... --workdayUsername=... ...`
6. **Apply** and **Run**

## Summary

❌ **Don't**:
```
-Dexec.args=file://src/main/resources/config/application.properties
```

✅ **Do**:
```
-Dexec.args="--runner=DirectRunner --workdaySoapUrl=https://... --workdayUsername=user@tenant --workdayPassword=pass --effectiveDate=2025-12-15 --bigQueryTable=project:dataset.table"
```

Or better yet, **use Java Application run configuration** instead of Maven exec:java!
