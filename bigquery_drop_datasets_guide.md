# How to Drop BigQuery Datasets and Content Using SQL

## Overview
In Google BigQuery, you can drop (delete) datasets and their contents using SQL DDL (Data Definition Language) statements. This guide covers various methods and best practices.

## Basic Syntax

### Drop Dataset (Must be Empty)
```sql
DROP SCHEMA `project_id.dataset_name`;
-- OR
DROP SCHEMA IF EXISTS `project_id.dataset_name`;
```

### Drop Dataset with All Contents (CASCADE)
```sql
DROP SCHEMA `project_id.dataset_name` CASCADE;
-- OR  
DROP SCHEMA IF EXISTS `project_id.dataset_name` CASCADE;
```

## Detailed Examples

### 1. Drop an Empty Dataset
```sql
-- This will only work if the dataset contains no tables or views
DROP SCHEMA `my-project.my_dataset`;
```

### 2. Drop Dataset with Contents (Recommended)
```sql
-- This will delete the dataset and all tables/views inside it
DROP SCHEMA `my-project.my_dataset` CASCADE;
```

### 3. Safe Drop with IF EXISTS
```sql
-- This won't throw an error if the dataset doesn't exist
DROP SCHEMA IF EXISTS `my-project.my_dataset` CASCADE;
```

### 4. Drop Multiple Datasets
```sql
-- You need to run separate statements for each dataset
DROP SCHEMA IF EXISTS `my-project.dataset_1` CASCADE;
DROP SCHEMA IF EXISTS `my-project.dataset_2` CASCADE;
DROP SCHEMA IF EXISTS `my-project.dataset_3` CASCADE;
```

## Alternative: Drop Individual Tables First

If you want more control, you can drop tables individually before dropping the dataset:

### List All Tables in a Dataset
```sql
SELECT 
  table_name,
  table_type
FROM `my-project.my_dataset.INFORMATION_SCHEMA.TABLES`;
```

### Drop Individual Tables
```sql
DROP TABLE IF EXISTS `my-project.my_dataset.table1`;
DROP TABLE IF EXISTS `my-project.my_dataset.table2`;
DROP VIEW IF EXISTS `my-project.my_dataset.view1`;

-- Then drop the empty dataset
DROP SCHEMA `my-project.my_dataset`;
```

## Important Considerations

### 1. Permissions Required
- You need `bigquery.datasets.delete` permission
- For datasets with tables, you also need `bigquery.tables.delete` permission

### 2. Billing and Storage
- Dropping datasets immediately stops billing for storage
- Data cannot be recovered after deletion
- Consider exporting important data before deletion

### 3. Cross-Dataset Dependencies
- Check for views or procedures in other datasets that reference tables in the dataset you're dropping
- External tables and materialized views may have dependencies

### 4. Backup Considerations
```sql
-- Create a backup dataset before dropping
CREATE SCHEMA `my-project.backup_dataset`;

-- Copy tables to backup (example)
CREATE OR REPLACE TABLE `my-project.backup_dataset.important_table` 
AS SELECT * FROM `my-project.my_dataset.important_table`;
```

## Best Practices

1. **Always use IF EXISTS** to avoid errors in scripts
2. **Use CASCADE** when you want to delete all contents
3. **List contents first** to verify what will be deleted
4. **Check dependencies** before dropping
5. **Create backups** of important data
6. **Test in development** environment first

## Error Handling

### Common Errors and Solutions

1. **"Dataset not empty"**
   ```sql
   -- Solution: Use CASCADE or drop tables individually first
   DROP SCHEMA `project.dataset` CASCADE;
   ```

2. **"Access Denied"**
   - Check IAM permissions
   - Ensure you have the required roles (BigQuery Admin or Data Editor)

3. **"Dataset not found"**
   ```sql
   -- Solution: Use IF EXISTS
   DROP SCHEMA IF EXISTS `project.dataset` CASCADE;
   ```

## Verification

### Check if Dataset Still Exists
```sql
SELECT 
  schema_name 
FROM `my-project.INFORMATION_SCHEMA.SCHEMATA` 
WHERE schema_name = 'my_dataset';
```

### List All Datasets in Project
```sql
SELECT 
  schema_name,
  creation_time,
  last_modified_time
FROM `my-project.INFORMATION_SCHEMA.SCHEMATA`
ORDER BY schema_name;
```