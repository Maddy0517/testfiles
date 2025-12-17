# Running Workday Pipeline from Eclipse IDE

## Prerequisites

1. **Eclipse IDE** (2023-03 or later recommended)
   - Download from: https://www.eclipse.org/downloads/

2. **Java 21 JDK**
   - Download from: https://www.oracle.com/java/technologies/downloads/#java21
   - Or OpenJDK: https://adoptium.net/

3. **Maven** (included in Eclipse or separate)

## Step 1: Import Project into Eclipse

### Method 1: Import Existing Maven Project

1. **Open Eclipse**

2. **File** → **Import** → **Maven** → **Existing Maven Projects**

3. **Browse** to your project folder (where `pom.xml` is located)

4. **Select** the project and click **Finish**

### Method 2: Clone from Git (if applicable)

1. **File** → **Import** → **Git** → **Projects from Git**

2. Choose your repository location

3. Import as **Maven project**

## Step 2: Configure Java 21

### Set Project Java Version

1. **Right-click** on project → **Properties**

2. **Java Build Path** → **Libraries** tab

3. Click **JRE System Library** → **Edit**

4. Select **Workspace default JRE** or **Alternate JRE**

5. Make sure it points to **Java 21**

### Configure Java Compiler

1. **Right-click** on project → **Properties**

2. **Java Compiler**

3. Check **Enable project specific settings**

4. Set **Compiler compliance level** to **21**

5. Click **Apply and Close**

### Configure Maven JDK

1. **Window** → **Preferences** → **Java** → **Installed JREs**

2. Click **Add** → **Standard VM**

3. Point to your **Java 21 installation directory**

4. Click **Finish** and make sure it's **checked**

## Step 3: Build the Project

### Update Maven Dependencies

1. **Right-click** on project → **Maven** → **Update Project**

2. Check **Force Update of Snapshots/Releases**

3. Click **OK**

### Clean and Build

1. **Right-click** on project → **Run As** → **Maven build...**

2. In **Goals** field, enter: `clean package`

3. Click **Run**

4. Check **Console** tab - should show `BUILD SUCCESS`

## Step 4: Configure Run Configuration

### ⭐ RECOMMENDED: Java Application Run Configuration

This is the easiest and fastest way to run in Eclipse.

### Create Local Run Configuration (DirectRunner)

1. **Right-click** on `WorkdayDataflowPipeline.java` → **Run As** → **Run Configurations...**

2. **Right-click** on **Java Application** → **New Configuration**

3. Set **Name**: `Workday Pipeline - Local`

4. **Main class**: `com.example.dataflow.WorkdayDataflowPipeline`

5. **Arguments** tab → **Program arguments**:
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

6. **JRE** tab → Ensure **Java 21** is selected

7. Click **Apply** then **Run**

### Create Dataflow Run Configuration (Cloud)

1. **Run** → **Run Configurations...**

2. Create new **Java Application** configuration

3. Set **Name**: `Workday Pipeline - Dataflow`

4. **Main class**: `com.example.dataflow.WorkdayDataflowPipeline`

5. **Arguments** tab → **Program arguments**:
   ```
   --runner=DataflowRunner
   --project=your-gcp-project
   --region=us-central1
   --tempLocation=gs://your-bucket/temp
   --stagingLocation=gs://your-bucket/staging
   --numWorkers=2
   --maxNumWorkers=5
   --workerMachineType=n1-standard-2
   --workdaySoapUrl=https://wd2-impl-services1.workday.com/ccx/service/YOUR-TENANT/Human_Resources/v38.0
   --workdayUsername=integration_user@tenant
   --workdayPassword=your-password
   --workdayTenantId=your-tenant
   --effectiveDate=2025-12-15
   --bigQueryTable=your-project:workday_data.employees
   --writeDisposition=WRITE_APPEND
   ```

6. Click **Apply** then **Run**

## Step 5: Running the Pipeline

### Quick Run (After Configuration)

1. Select `WorkdayDataflowPipeline.java` in **Package Explorer**

2. **Run** → **Run Configurations**

3. Select your saved configuration

4. Click **Run**

### Debug Mode

1. Set **breakpoints** by double-clicking on line numbers

2. **Right-click** on `WorkdayDataflowPipeline.java` → **Debug As** → **Java Application**

3. Or use your saved **Debug Configuration**

## Step 6: View Output

### Console Output

- **Console** tab shows logs and progress
- Look for: `Pipeline execution completed successfully!`

### BigQuery Results

- Open **BigQuery Console**
- Query: `SELECT COUNT(*) FROM your-project.workday_data.employees`

## Maven exec:java in Eclipse (Alternative)

If you want to use Maven's exec:java goal:

### Setup Maven Run Configuration

1. **Right-click** on `pom.xml` → **Run As** → **Maven build...**

2. **Goals**: `clean compile exec:java`

3. **Parameter** tab → **Add**:
   - Name: `exec.mainClass`
   - Value: `com.example.dataflow.WorkdayDataflowPipeline`

4. **Add** another parameter:
   - Name: `exec.args`
   - Value: `--runner=DirectRunner --workdaySoapUrl=https://... --workdayUsername=user@tenant --workdayPassword=password --effectiveDate=2025-12-15 --bigQueryTable=project:dataset.table`

5. **Apply** and **Run**

**Note**: All arguments must be in one line for `exec.args`.

### ❌ Common Mistake
```
-Dexec.args=file://src/main/resources/config/application.properties  ❌ Wrong!
```

### ✅ Correct Format
```
-Dexec.args="--runner=DirectRunner --workdaySoapUrl=https://... --workdayUsername=user --workdayPassword=pass --effectiveDate=2025-12-15 --bigQueryTable=project:dataset.table"  ✅ Correct!
```

## Common Eclipse Issues

### Issue 1: "Cannot resolve AvroCoder"

**Solution**: Update Maven project
1. **Right-click** project → **Maven** → **Update Project**
2. Check **Force Update**
3. **OK**

### Issue 2: "Java version mismatch"

**Solution**: Verify Java 21
1. **Project** → **Properties** → **Java Build Path**
2. Ensure JRE is **Java 21**
3. **Java Compiler** → Set to **21**

### Issue 3: "Main class not found"

**Solution**: Rebuild project
1. **Project** → **Clean**
2. Select project
3. **Clean**
4. Wait for rebuild

### Issue 4: "Missing Maven dependencies"

**Solution**: 
1. Delete `.m2/repository` cache (if needed)
2. **Right-click** project → **Maven** → **Update Project**
3. Check **Force Update**

## Tips for Eclipse Users

### 1. Use Maven Profiles

Create profiles in `pom.xml` for different environments:
```xml
<profiles>
    <profile>
        <id>local</id>
        <!-- Local runner settings -->
    </profile>
    <profile>
        <id>dataflow</id>
        <!-- Dataflow runner settings -->
    </profile>
</profiles>
```

### 2. External Configuration

Store credentials in external file:
1. Create `config.properties` outside project
2. Load in code:
   ```java
   Properties props = new Properties();
   props.load(new FileInputStream("C:/path/to/config.properties"));
   ```

### 3. Run from Package Explorer

**Right-click** `WorkdayDataflowPipeline.java` → **Run As** → **Java Application**

Eclipse will use the last run configuration.

### 4. Quick Launch

Press **Ctrl+F11** to run with last configuration
Press **F11** to debug with last configuration

### 5. Console Shortcuts

- **Ctrl+F** to search console output
- **Ctrl+A** to select all
- **Right-click** → **Save As** to save logs

## Project Structure in Eclipse

```
workday-dataflow-pipeline
├── src/main/java
│   └── com.example.dataflow
│       ├── WorkdayDataflowPipeline.java ← Run this
│       ├── WorkdaySoapHandler.java
│       ├── WorkdayDataModel.java
│       └── BigQuerySchema.java
├── src/main/resources
│   └── (empty)
├── pom.xml
├── application.properties
└── Maven Dependencies (auto-managed)
```

## Keyboard Shortcuts

| Action | Shortcut |
|--------|----------|
| Run | **Ctrl+F11** |
| Debug | **F11** |
| Open Run Configurations | **Alt+R, N** |
| Build Project | **Ctrl+B** |
| Clean Project | **Alt+P, C** |
| Update Maven | **Alt+F5** |

## Next Steps

1. ✅ Import project
2. ✅ Configure Java 21
3. ✅ Create run configuration
4. ✅ Update `application.properties` with your credentials
5. ✅ Run locally first (DirectRunner)
6. ✅ Verify in BigQuery
7. ✅ Run on Dataflow (if needed)

## Support

- Eclipse Help: **Help** → **Help Contents**
- Maven Console: **Window** → **Show View** → **Console**
- Problems View: **Window** → **Show View** → **Problems**
