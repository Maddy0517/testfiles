# Quick Start Guide - Workday to BigQuery Pipeline

Get your Workday data into BigQuery in 5 minutes!

## Prerequisites Checklist

- [ ] Java 11+ installed
- [ ] Maven 3.6+ installed
- [ ] Google Cloud account with billing enabled
- [ ] Workday SOAP API credentials
- [ ] Google Cloud SDK installed and authenticated

## 5-Minute Setup

### Step 1: Clone and Configure (1 minute)

```bash
cd workday-dataflow-pipeline

# Copy and edit configuration
cp config.properties config.properties.local
nano config.properties.local
```

Update these required values:
```properties
workday.soap.url=https://wd2-impl-services1.workday.com/ccx/service/YOUR-TENANT/Human_Resources/v38.0
workday.username=integration_user@tenant
workday.password=your-password
workday.tenant.id=your-tenant
effective.date=2025-12-15
bigquery.full.table=your-project:workday_data.employees
```

### Step 2: Build (1 minute)

```bash
mvn clean package
```

### Step 3: Test Locally (2 minutes)

```bash
export WORKDAY_SOAP_URL="your-soap-url"
export WORKDAY_USERNAME="your-username"
export WORKDAY_PASSWORD="your-password"
export EFFECTIVE_DATE="2025-12-15"
export BIGQUERY_TABLE="project:dataset.employees"

./run-pipeline.sh local
```

### Step 4: Create BigQuery Table (30 seconds)

```bash
# Edit project ID in bigquery-schema.sql first
bq query --use_legacy_sql=false < bigquery-schema.sql
```

### Step 5: Deploy to Dataflow (30 seconds)

```bash
export GCP_PROJECT_ID="your-project"
export TEMP_LOCATION="gs://your-bucket/temp"
export STAGING_LOCATION="gs://your-bucket/staging"

./run-pipeline.sh dataflow
```

## What Happens Next?

1. **Pipeline Starts**: Dataflow job is submitted
2. **Workers Spin Up**: 10 workers start (configurable)
3. **Count Fetched**: Total employee count retrieved from Workday
4. **Parallel Processing**: Pages fetched simultaneously across workers
5. **Data Loaded**: Employees written to BigQuery in batches
6. **Completion**: Check Dataflow console for status

## Monitor Your Job

View in Cloud Console:
```
https://console.cloud.google.com/dataflow/jobs
```

Or via CLI:
```bash
gcloud dataflow jobs list --region=us-central1
gcloud dataflow jobs describe JOB_ID --region=us-central1
```

## Verify Data

```sql
-- Count total records
SELECT COUNT(*) FROM `your-project.workday_data.employees`;

-- View latest records
SELECT * FROM `your-project.workday_data.employees_latest` LIMIT 10;

-- Check by department
SELECT department, COUNT(*) as count
FROM `your-project.workday_data.employees_latest`
GROUP BY department
ORDER BY count DESC;
```

## Common First-Time Issues

### Issue: "Permission denied on BigQuery"
**Fix**: Grant permissions
```bash
gcloud projects add-iam-policy-binding YOUR-PROJECT \
  --member=user:YOUR-EMAIL \
  --role=roles/bigquery.dataEditor
```

### Issue: "Workday authentication failed"
**Fix**: Check username format should be `user@tenant`

### Issue: "GCS bucket not found"
**Fix**: Create buckets
```bash
gsutil mb -p YOUR-PROJECT gs://your-bucket
```

### Issue: "No workers starting"
**Fix**: Enable Dataflow API
```bash
gcloud services enable dataflow.googleapis.com
```

## Next Steps

1. **Schedule Daily Runs**: See `cloud-scheduler-setup.sh`
2. **Optimize Performance**: Read `PERFORMANCE_TUNING.md`
3. **Customize Fields**: Modify `Employee.java` and `BigQuerySchemaFactory.java`
4. **Add Monitoring**: Set up alerts in Cloud Monitoring

## Cost Estimate

For 50,000 employees (typical mid-size company):
- **First Run**: $3-5 (30 minutes, 10 workers)
- **Daily Incremental**: $1-2 (10 minutes, 5 workers)
- **Monthly Total**: ~$50

Reduce costs by:
- Using preemptible workers (70% savings)
- Running during off-peak hours
- Optimizing worker count

## Support

- **Full Documentation**: See `README.md`
- **Performance Guide**: See `PERFORMANCE_TUNING.md`
- **Logs**: Check Dataflow console or Cloud Logging

## Architecture Summary

```
Workday API → Dataflow Workers (Parallel) → BigQuery
     ↓              ↓                          ↓
  999/page    10-50 workers           Partitioned table
  SOAP API    Auto-scaling            Clustered by ID
  Paginated   Retry logic             Schema enforced
```

**Key Features**:
- ✅ Parallel page processing (10x faster)
- ✅ Automatic retries on failure
- ✅ Schema validation
- ✅ Scalable to millions of records
- ✅ Cost-optimized with autoscaling

## Quick Commands Reference

```bash
# Build
mvn clean package

# Run locally
./run-pipeline.sh local

# Run on Dataflow
./run-pipeline.sh dataflow

# List jobs
gcloud dataflow jobs list --region=us-central1

# View logs
gcloud logging read "resource.type=dataflow_step" --limit=50

# Query data
bq query "SELECT COUNT(*) FROM \`project.dataset.employees\`"

# Cancel job
gcloud dataflow jobs cancel JOB_ID --region=us-central1
```

---

**Ready to Go?** Start with Step 1 above! 🚀
