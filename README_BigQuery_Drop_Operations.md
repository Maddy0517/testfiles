# BigQuery Drop Operations Guide

This repository contains comprehensive examples and utilities for dropping datasets and their contents in Google BigQuery using SQL, Python, and shell scripts.

## 📁 Files in this Repository

1. **`bigquery_drop_operations.sql`** - Complete SQL reference for dropping BigQuery objects
2. **`bigquery_drop_operations.py`** - Python utility class for programmatic drop operations
3. **`bigquery_drop_operations.sh`** - Bash script for command-line drop operations
4. **`README_BigQuery_Drop_Operations.md`** - This documentation file

## ⚠️ IMPORTANT WARNINGS

**These operations are DESTRUCTIVE and IRREVERSIBLE!**
- Always backup important data before dropping
- Dropped tables can only be restored within 7 days using time travel
- Ensure you have proper permissions before executing drop operations
- Test in a development environment first

## 🔑 Required Permissions

To perform drop operations, you need the following IAM permissions:
- `bigquery.datasets.delete` - Drop datasets
- `bigquery.tables.delete` - Drop tables and views
- `bigquery.models.delete` - Drop ML models
- `bigquery.routines.delete` - Drop functions and procedures

## 📝 Quick SQL Examples

### Drop a Single Table
```sql
DROP TABLE IF EXISTS `project-id.dataset_name.table_name`;
```

### Drop a View
```sql
DROP VIEW IF EXISTS `project-id.dataset_name.view_name`;
```

### Drop an Entire Dataset (Empty)
```sql
DROP SCHEMA IF EXISTS `project-id.dataset_name` RESTRICT;
```

### Drop an Entire Dataset with All Contents (CASCADE)
```sql
DROP SCHEMA IF EXISTS `project-id.dataset_name` CASCADE;
```

### Drop a Model
```sql
DROP MODEL IF EXISTS `project-id.dataset_name.model_name`;
```

### Drop a Function
```sql
DROP FUNCTION IF EXISTS `project-id.dataset_name.function_name`;
```

## 🐍 Python Usage

### Installation
```bash
pip install google-cloud-bigquery
```

### Basic Usage
```python
from bigquery_drop_operations import BigQueryDropManager

# Initialize
manager = BigQueryDropManager("your-project-id")

# Drop a table
manager.drop_table("dataset_id", "table_id")

# Drop all tables with a prefix
manager.drop_tables_by_prefix("dataset_id", "temp_")

# Drop entire dataset with contents
manager.drop_dataset("dataset_id", delete_contents=True)

# Backup before dropping
manager.backup_table("source_dataset", "source_table", "backup_dataset")
manager.drop_table("source_dataset", "source_table")
```

## 💻 Command-Line Usage (bq tool)

### Prerequisites
```bash
# Install Google Cloud SDK
curl https://sdk.cloud.google.com | bash

# Authenticate
gcloud auth login

# Set project
gcloud config set project YOUR_PROJECT_ID
```

### Basic Commands
```bash
# Drop a table
bq rm -f -t project-id:dataset.table

# Drop a view (same as table)
bq rm -f -t project-id:dataset.view

# Drop a model
bq rm -f -m project-id:dataset.model

# Drop a dataset (empty only)
bq rm -f -d project-id:dataset

# Drop a dataset with all contents
bq rm -r -f -d project-id:dataset

# Drop a routine
bq rm -f --routine project-id:dataset.function_name
```

### Using the Shell Script
```bash
# Make executable
chmod +x bigquery_drop_operations.sh

# Run interactive menu
./bigquery_drop_operations.sh

# Or source and use functions
source bigquery_drop_operations.sh
drop_table "my_dataset" "my_table"
```

## 🔄 Best Practices

### 1. Always Use IF EXISTS
Prevents errors when objects don't exist:
```sql
DROP TABLE IF EXISTS `project.dataset.table`;
```

### 2. Create Backups First
```sql
-- Create backup
CREATE TABLE `project.backup_dataset.table_backup_20241007`
AS SELECT * FROM `project.dataset.table`;

-- Verify backup
SELECT COUNT(*) FROM `project.backup_dataset.table_backup_20241007`;

-- Then drop original
DROP TABLE IF EXISTS `project.dataset.table`;
```

### 3. Use CASCADE vs RESTRICT Appropriately
- **CASCADE**: Drops dataset and all contents
- **RESTRICT**: Only drops if dataset is empty (safer)

### 4. Implement Safeguards
```python
# Always confirm destructive operations
if input("Are you sure? (yes/no): ").lower() != "yes":
    print("Operation cancelled")
    exit()
```

### 5. Use Time Travel for Recovery
If you accidentally drop a table, you can restore it within 7 days:
```sql
CREATE TABLE `project.dataset.recovered_table` AS 
SELECT * FROM `project.dataset.dropped_table` 
FOR SYSTEM_TIME AS OF TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 HOUR);
```

## 📊 Dropping Specific Object Types

### Partitioned Tables
```sql
-- Drop specific partitions
DELETE FROM `project.dataset.partitioned_table`
WHERE DATE(_PARTITIONTIME) = '2024-10-01';

-- Drop entire partitioned table
DROP TABLE IF EXISTS `project.dataset.partitioned_table`;
```

### External Tables
```sql
DROP EXTERNAL TABLE IF EXISTS `project.dataset.external_table`;
```

### Materialized Views
```sql
DROP MATERIALIZED VIEW IF EXISTS `project.dataset.materialized_view`;
```

### Search Indexes
```sql
DROP SEARCH INDEX IF EXISTS `index_name` ON `project.dataset.table`;
```

### Row Access Policies
```sql
DROP ALL ROW ACCESS POLICIES ON `project.dataset.table`;
```

## 🔍 Checking Before Dropping

### List All Tables in Dataset
```sql
SELECT 
  table_catalog,
  table_schema,
  table_name,
  table_type,
  creation_time
FROM `project.dataset.INFORMATION_SCHEMA.TABLES`
ORDER BY table_name;
```

### Check for Dependent Views
```sql
SELECT 
  table_name AS dependent_view
FROM `project.dataset.INFORMATION_SCHEMA.VIEWS`
WHERE view_definition LIKE '%table_to_drop%';
```

## 🚀 Advanced Operations

### Drop All Tables with Specific Pattern
```sql
DECLARE tables_to_drop ARRAY<STRING>;

SET tables_to_drop = (
  SELECT ARRAY_AGG(table_name)
  FROM `project.dataset.INFORMATION_SCHEMA.TABLES`
  WHERE table_name LIKE 'temp_%'
);

IF ARRAY_LENGTH(tables_to_drop) > 0 THEN
  FOR i IN 0 .. ARRAY_LENGTH(tables_to_drop) - 1 DO
    EXECUTE IMMEDIATE FORMAT(
      'DROP TABLE IF EXISTS `project.dataset.%s`',
      tables_to_drop[OFFSET(i)]
    );
  END FOR;
END IF;
```

### Drop Tables Older Than N Days
```sql
DECLARE old_tables ARRAY<STRING>;

SET old_tables = (
  SELECT ARRAY_AGG(table_name)
  FROM `project.dataset.INFORMATION_SCHEMA.TABLES`
  WHERE creation_time < TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 30 DAY)
);

-- Drop each old table
IF ARRAY_LENGTH(old_tables) > 0 THEN
  FOR i IN 0 .. ARRAY_LENGTH(old_tables) - 1 DO
    EXECUTE IMMEDIATE FORMAT(
      'DROP TABLE IF EXISTS `project.dataset.%s`',
      old_tables[OFFSET(i)]
    );
  END FOR;
END IF;
```

## 🛡️ Safety Checklist

Before dropping any BigQuery object:

- [ ] Verified the correct project and dataset
- [ ] Created backups of important data
- [ ] Checked for dependent objects (views, procedures)
- [ ] Confirmed with team members if shared resources
- [ ] Documented the reason for dropping
- [ ] Tested the operation in development first
- [ ] Have recovery plan if needed

## 📚 Additional Resources

- [BigQuery DROP Statement Documentation](https://cloud.google.com/bigquery/docs/reference/standard-sql/data-definition-language#drop_table_statement)
- [BigQuery Python Client Library](https://cloud.google.com/python/docs/reference/bigquery/latest)
- [BigQuery Command-Line Tool Reference](https://cloud.google.com/bigquery/docs/bq-command-line-tool)
- [BigQuery IAM Permissions](https://cloud.google.com/bigquery/docs/access-control)
- [BigQuery Time Travel](https://cloud.google.com/bigquery/docs/time-travel)

## 💡 Tips

1. **Use Expiration Instead of Dropping**: For temporary tables, set expiration times instead of manually dropping
2. **Partition Expiration**: Use partition expiration for time-series data
3. **Soft Delete**: Consider renaming tables with a `_deleted` suffix instead of dropping
4. **Audit Logs**: Enable audit logging to track who dropped what and when
5. **Cost Optimization**: Dropping unused tables reduces storage costs immediately

## ⚡ Performance Considerations

- Dropping large tables is fast (metadata operation)
- Dropping datasets with many objects can take time
- Use parallel operations when dropping multiple objects
- Consider using batch operations for better performance

Remember: **Think twice, drop once!** Always ensure you're dropping the right object in the right environment.