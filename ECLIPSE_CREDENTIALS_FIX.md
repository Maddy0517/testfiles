# Fix for Username/Password Null Issue

## The Problem
Your logs show:
```
username--------------->
invalid username or password
```

This means the credentials are not being loaded from your properties file.

## ✅ **Quick Fix:**

### Step 1: Update Your Properties File
You need to replace the placeholder values in `src/main/resources/eclipse-local.properties` with your **actual Workday credentials**.

**Current file has:**
```properties
workday.soap.url=https://your-tenant.workday.com/ccx/service/your-tenant/Human_Resources/v40.0
workday.username=your-username@your-tenant
workday.password=your-password
workday.tenant=your-tenant
```

**You need to change it to your real values:**
```properties
workday.soap.url=https://ACTUAL-TENANT.workday.com/ccx/service/ACTUAL-TENANT/Human_Resources/v40.0
workday.username=ACTUAL-USERNAME@ACTUAL-TENANT
workday.password=ACTUAL-PASSWORD
workday.tenant=ACTUAL-TENANT
```

### Step 2: Example Configuration
If your Workday tenant is called "acme" and your integration user is "api_user":

```properties
workday.soap.url=https://acme.workday.com/ccx/service/acme/Human_Resources/v40.0
workday.username=api_user@acme
workday.password=MySecurePassword123
workday.tenant=acme
workday.api.version=v40.0

# Also update your Google Cloud project
bigquery.project=my-gcp-project-id
bigquery.dataset=workday_data_dev
bigquery.table=workers_dev
```

### Step 3: How to Find Your Workday Details

**Workday Tenant Name:**
- This is usually your organization's name in the Workday URL
- Example: If you access Workday at `https://acme.workday.com`, then tenant = `acme`

**SOAP API URL:**
- Format: `https://[TENANT].workday.com/ccx/service/[TENANT]/Human_Resources/v40.0`
- Replace `[TENANT]` with your actual tenant name

**Integration User Credentials:**
- You need a Workday integration user account (not your regular login)
- Format: `username@tenant` (e.g., `api_user@acme`)
- Ask your Workday admin if you don't have these credentials

### Step 4: Update the File in Eclipse

1. **Open the file**: `src/main/resources/eclipse-local.properties`
2. **Replace all placeholder values** with your actual credentials
3. **Save the file**
4. **Run the pipeline again**

### Step 5: Verify the Fix

After updating, you should see logs like:
```
INFO  - Loaded SOAP URL: https://your-tenant.workday.com/ccx/service/your-tenant/Human_Resources/v40.0
INFO  - Loaded username: your-username@your-tenant  
INFO  - Loaded password: ***PROVIDED***
INFO  - Creating WorkdayApiClient with credentials - URL: https://..., Username: your-username@your-tenant, Password: ***PROVIDED***
```

## Security Note

**Never commit your actual credentials to version control!**

The `eclipse-local.properties` file should be added to `.gitignore` and kept only on your local machine.

## Alternative: Use Environment Variables

Instead of hardcoding credentials, you can use environment variables:

1. **Set environment variables in Eclipse:**
   - Go to Run → Run Configurations
   - Go to Environment tab  
   - Add variables:
     ```
     WORKDAY_USERNAME=your-username@your-tenant
     WORKDAY_PASSWORD=your-password
     WORKDAY_SOAP_URL=https://your-tenant.workday.com/ccx/service/your-tenant/Human_Resources/v40.0
     ```

2. **Update properties file to use variables:**
   ```properties
   workday.soap.url=${WORKDAY_SOAP_URL}
   workday.username=${WORKDAY_USERNAME}  
   workday.password=${WORKDAY_PASSWORD}
   ```

## Test Your Credentials

Before running the full pipeline, you can test your Workday credentials by:
1. Trying to log into Workday web interface with the same credentials
2. Using a SOAP client tool like Postman to test the API endpoint
3. Checking with your Workday administrator that the integration user has proper permissions

The key issue is that you're using placeholder values instead of real credentials!