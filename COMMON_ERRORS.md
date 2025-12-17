# Common Errors and Solutions

## Error 1: "Argument ... does not begin with '--'"

### Full Error
```
java.lang.IllegalArgumentException: Argument 'file://src/main/resources/config/application.properties' does not begin with '--'
```

### Cause
You're trying to pass a properties file as a command-line argument. **This doesn't work!**

### Solution
Don't pass the properties file. Instead:

**Windows**:
```cmd
REM 1. Edit set-credentials.bat
set WORKDAY_SOAP_URL=https://...
set WORKDAY_USERNAME=user@tenant

REM 2. Run the batch file
call set-credentials.bat
run-local.bat
```

**Linux/Mac**:
```bash
# 1. Export variables
export WORKDAY_SOAP_URL="https://..."
export WORKDAY_USERNAME="user@tenant"

# 2. Run
./run.sh local
```

**Eclipse**:
1. Run Configurations → Arguments tab
2. Add parameters with `--` prefix:
```
--workdaySoapUrl=https://...
--workdayUsername=user@tenant
```

---

## Error 2: "Cannot resolve AvroCoder"

### Full Error
```
The import org.apache.beam.sdk.coders.AvroCoder cannot be resolved
```

### Cause
Missing Avro extension dependency.

### Solution
Already fixed in `pom.xml`. Just rebuild:
```cmd
mvn clean package
```

---

## Error 3: "Invalid escape sequence"

### Full Error
```
Invalid escape sequence (valid ones are \b \t \n \f \r \" \' \\)
```

### Cause
String Templates were used (preview feature).

### Solution
Already fixed - code now uses SLF4J logging instead. Rebuild:
```cmd
mvn clean package
```

---

## Error 4: "Unexpected type int"

### Full Error
```
Unexpected type int, expected class or array type
```

### Cause
Pattern matching with primitive types in switch.

### Solution
Already fixed - code now uses simple if-else. Rebuild:
```cmd
mvn clean package
```

---

## Error 5: "Missing required option"

### Full Error
```
Missing required option: workdaySoapUrl
```

### Cause
Required parameter not provided.

### Solution
Ensure all required parameters are set:

```cmd
REM Windows
set WORKDAY_SOAP_URL=https://...
set WORKDAY_USERNAME=user@tenant
set WORKDAY_PASSWORD=password
set EFFECTIVE_DATE=2025-12-15
set BIGQUERY_TABLE=project:dataset.employees
```

Required parameters:
- `--workdaySoapUrl`
- `--workdayUsername`
- `--workdayPassword`
- `--effectiveDate`
- `--bigQueryTable`

---

## Error 6: "BUILD FAILURE - Java version mismatch"

### Full Error
```
Source option 21 is no longer supported. Use 21 or later.
```

### Cause
Wrong Java version installed.

### Solution
Install Java 21:
- Download: https://adoptium.net/
- Verify: `java -version` should show "21"
- Set JAVA_HOME:
  ```cmd
  setx JAVA_HOME "C:\Program Files\Java\jdk-21"
  ```

---

## Error 7: "Permission denied on BigQuery"

### Full Error
```
Permission denied on resource project:dataset.table
```

### Cause
Missing BigQuery permissions.

### Solution
```cmd
gcloud projects add-iam-policy-binding YOUR-PROJECT ^
  --member=user:YOUR-EMAIL ^
  --role=roles/bigquery.dataEditor
```

---

## Error 8: "GCS bucket not found"

### Full Error
```
The specified bucket does not exist
```

### Cause
Missing or incorrect GCS bucket for Dataflow temp files.

### Solution
Create bucket:
```cmd
gsutil mb -p YOUR-PROJECT gs://your-bucket-name
```

Or use existing bucket in your project.

---

## Error 9: "SOAP Fault: Unauthorized"

### Full Error
```
SOAP Fault: Unauthorized - Authentication failed
```

### Cause
Incorrect Workday credentials or tenant ID.

### Solution
1. Verify username format: `user@tenant`
2. Check password (no typos)
3. Verify tenant ID matches your Workday instance
4. Ensure integration user has permissions

---

## Error 10: "Out of Memory"

### Full Error
```
java.lang.OutOfMemoryError: Java heap space
```

### Cause
Large dataset, insufficient memory.

### Solution

**For local runs**:
```cmd
java -Xmx4g -cp target/jar.jar Main --runner=DirectRunner ...
```

**For Dataflow**:
```cmd
--workerMachineType=n1-highmem-4
```

---

## Error 11: "Cannot find main class"

### Full Error
```
Error: Could not find or load main class com.example.dataflow.WorkdayDataflowPipeline
```

### Cause
JAR file not built or wrong classpath.

### Solution
Rebuild:
```cmd
mvn clean package
```

Verify JAR exists:
```cmd
dir target\workday-dataflow-pipeline-1.0.0.jar
```

---

## Error 12: "Connection timeout"

### Full Error
```
java.net.SocketTimeoutException: Read timed out
```

### Cause
Workday API slow or network issues.

### Solution
Timeouts are already configured in code (60s connection, 120s read).
If still timing out:
1. Check network connectivity
2. Contact Workday support
3. Try during off-peak hours

---

## Quick Troubleshooting Checklist

When encountering errors:

1. ✅ **Verify Java version**: `java -version` (must be 21+)
2. ✅ **Verify Maven**: `mvn -version`
3. ✅ **Clean build**: `mvn clean package`
4. ✅ **Check credentials**: Review set-credentials.bat
5. ✅ **Verify all `--` arguments**: No file paths, all start with `--`
6. ✅ **Check logs**: Look for specific error messages
7. ✅ **Test with small data**: Use estimatedTotalCount=100 for testing
8. ✅ **Run locally first**: DirectRunner before Dataflow

---

## Still Having Issues?

### Check These Files
- `CONFIGURATION_GUIDE.md` - How to pass parameters correctly
- `COMPILATION_FIXES.md` - Build errors
- `ECLIPSE_SETUP.md` - Eclipse-specific issues
- `WINDOWS_SETUP.md` - Windows-specific issues

### Common Solutions
1. **Always rebuild after errors**: `mvn clean package`
2. **Use batch files**: Don't manually type long commands
3. **Check all variables are set**: Use `echo %VAR%` to verify
4. **Start with local testing**: DirectRunner is easier to debug

### Get Help
1. Check error message carefully
2. Search error in this file
3. Review relevant documentation
4. Check Dataflow console logs (for cloud runs)
