# Date Filtering Quick Reference Guide

## Overview

The Workday to BigQuery pipeline now supports effective date filtering to enable incremental data processing and reduce API load.

## Configuration Options

### Properties File Configuration

Add these properties to your `pipeline.properties` file:

```properties
# Date Filter Configuration
workday.effective.from.date=2024-01-01    # Start date (YYYY-MM-DD)
workday.effective.to.date=2024-12-31      # End date (YYYY-MM-DD)
workday.include.effective.from.date=true  # Include from date
workday.include.effective.to.date=true    # Include to date
```

### Command Line Arguments

Override properties with command line arguments:

```bash
--effectiveFromDate=2024-01-01
--effectiveToDate=2024-12-31
--includeEffectiveFromDate=true
--includeEffectiveToDate=true
```

## Common Use Cases

### 1. Initial Full Load
```properties
# Get all historical data
workday.effective.from.date=
workday.effective.to.date=
```

### 2. Daily Incremental Load
```properties
# Get yesterday's changes
workday.effective.from.date=2024-12-07  # Yesterday
workday.effective.to.date=2024-12-07    # Yesterday
```

### 3. Weekly Batch Load
```properties
# Get last week's changes
workday.effective.from.date=2024-12-01
workday.effective.to.date=2024-12-07
```

### 4. Catch-up Processing
```properties
# Process missed data from last month
workday.effective.from.date=2024-11-01
workday.effective.to.date=2024-11-30
```

### 5. Point-in-Time Query
```properties
# Get data as of specific date
workday.effective.from.date=2024-06-30
workday.effective.to.date=2024-06-30
```

## Running Examples

### Using Properties File
```bash
# Edit config/pipeline-with-dates.properties first
./run-local.sh
```

### Using Command Line
```bash
# Run with specific date range
./run-with-date-filters.sh 2024-01-01 2024-01-31
```

### Using Maven Directly
```bash
mvn compile exec:java \
  -Dexec.mainClass=com.workday.dataflow.WorkdayToBigQueryPipeline \
  -Dexec.args="--propertiesFile=config/pipeline.properties \
               --effectiveFromDate=2024-01-01 \
               --effectiveToDate=2024-12-31"
```

## SOAP API Implementation Details

The pipeline uses these Workday SOAP API features for date filtering:

1. **Transaction_Log_Criteria_Data**: Filters by record update/modification dates
2. **As_Of_Effective_Date**: For point-in-time data queries
3. **As_Of_Entry_DateTime**: For effective date filtering in response

## Date Format Requirements

- **Format**: YYYY-MM-DD (ISO 8601 date format)
- **Examples**: 
  - ✅ `2024-01-01`
  - ✅ `2024-12-31`
  - ❌ `01/01/2024`
  - ❌ `1-1-2024`
  - ❌ `2024/01/01`

## Error Handling

### Common Errors

1. **Invalid Date Format**
   ```
   Error: Invalid effective from date format: 01-01-2024
   Solution: Use YYYY-MM-DD format (2024-01-01)
   ```

2. **From Date After To Date**
   ```
   Error: Effective from date cannot be after effective to date
   Solution: Ensure from_date <= to_date
   ```

3. **No Data in Date Range**
   ```
   Warning: No workers found in specified date range
   Solution: Expand date range or check data availability
   ```

## Performance Tips

1. **Smaller Date Ranges**: Use smaller date ranges for faster processing
2. **Incremental Loading**: Process only changed data using date filters
3. **Batch Size**: Reduce batch size for large date ranges
4. **Parallel Processing**: Use Dataflow for processing multiple date ranges

## Monitoring

### BigQuery Queries for Date Range Analysis

```sql
-- Check data coverage by processing date
SELECT 
  DATE(processed_timestamp) as processing_date,
  COUNT(*) as records_processed,
  MIN(hire_date) as earliest_hire_date,
  MAX(hire_date) as latest_hire_date
FROM `your-project.workday_data.workers`
GROUP BY DATE(processed_timestamp)
ORDER BY processing_date DESC;

-- Find gaps in incremental processing
SELECT 
  DATE(processed_timestamp) as processing_date,
  LAG(DATE(processed_timestamp)) OVER (ORDER BY DATE(processed_timestamp)) as prev_date,
  DATE_DIFF(DATE(processed_timestamp), 
           LAG(DATE(processed_timestamp)) OVER (ORDER BY DATE(processed_timestamp)), 
           DAY) as gap_days
FROM `your-project.workday_data.workers`
GROUP BY DATE(processed_timestamp)
HAVING gap_days > 1
ORDER BY processing_date;
```

## Automation Examples

### Daily Cron Job
```bash
#!/bin/bash
# daily-workday-sync.sh
YESTERDAY=$(date -d '1 day ago' '+%Y-%m-%d')
./run-with-date-filters.sh $YESTERDAY $YESTERDAY
```

### Weekly Catch-up
```bash
#!/bin/bash
# weekly-catchup.sh
WEEK_START=$(date -d '7 days ago' '+%Y-%m-%d')
TODAY=$(date '+%Y-%m-%d')
./run-with-date-filters.sh $WEEK_START $TODAY
```

## Best Practices

1. **Start Small**: Begin with small date ranges for testing
2. **Log Date Ranges**: Always log the date ranges being processed
3. **Monitor Results**: Check BigQuery for expected data volumes
4. **Handle Failures**: Implement retry logic for failed date ranges
5. **Document Runs**: Keep track of successful processing dates
6. **Test Thoroughly**: Validate date filtering logic before production use

---

For more details, see the main [README.md](README.md) file.