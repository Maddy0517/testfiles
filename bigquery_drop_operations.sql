-- BigQuery SQL Commands for Dropping Datasets and Content
-- =========================================================

-- IMPORTANT NOTES:
-- 1. These operations are IRREVERSIBLE. Always backup important data before dropping.
-- 2. You need appropriate permissions (bigquery.datasets.delete, bigquery.tables.delete)
-- 3. Dataset names are case-sensitive in BigQuery

-- ===========================================
-- DROPPING INDIVIDUAL TABLES
-- ===========================================

-- Drop a single table
DROP TABLE IF EXISTS `project_id.dataset_name.table_name`;

-- Example:
DROP TABLE IF EXISTS `my-project.analytics_dataset.user_events`;

-- Drop multiple tables (execute separately)
DROP TABLE IF EXISTS `my-project.analytics_dataset.table1`;
DROP TABLE IF EXISTS `my-project.analytics_dataset.table2`;
DROP TABLE IF EXISTS `my-project.analytics_dataset.table3`;

-- ===========================================
-- DROPPING VIEWS
-- ===========================================

-- Drop a view
DROP VIEW IF EXISTS `project_id.dataset_name.view_name`;

-- Example:
DROP VIEW IF EXISTS `my-project.analytics_dataset.monthly_summary_view`;

-- ===========================================
-- DROPPING MATERIALIZED VIEWS
-- ===========================================

-- Drop a materialized view
DROP MATERIALIZED VIEW IF EXISTS `project_id.dataset_name.materialized_view_name`;

-- Example:
DROP MATERIALIZED VIEW IF EXISTS `my-project.analytics_dataset.daily_aggregates`;

-- ===========================================
-- DROPPING EXTERNAL TABLES
-- ===========================================

-- Drop an external table
DROP EXTERNAL TABLE IF EXISTS `project_id.dataset_name.external_table_name`;

-- Example:
DROP EXTERNAL TABLE IF EXISTS `my-project.analytics_dataset.gcs_data`;

-- ===========================================
-- DROPPING MODELS (ML Models)
-- ===========================================

-- Drop a machine learning model
DROP MODEL IF EXISTS `project_id.dataset_name.model_name`;

-- Example:
DROP MODEL IF EXISTS `my-project.ml_dataset.customer_churn_model`;

-- ===========================================
-- DROPPING FUNCTIONS
-- ===========================================

-- Drop a user-defined function (UDF)
DROP FUNCTION IF EXISTS `project_id.dataset_name.function_name`;

-- Example:
DROP FUNCTION IF EXISTS `my-project.analytics_dataset.calculate_revenue`;

-- Drop a table function
DROP TABLE FUNCTION IF EXISTS `project_id.dataset_name.table_function_name`;

-- ===========================================
-- DROPPING PROCEDURES
-- ===========================================

-- Drop a stored procedure
DROP PROCEDURE IF EXISTS `project_id.dataset_name.procedure_name`;

-- Example:
DROP PROCEDURE IF EXISTS `my-project.analytics_dataset.daily_etl_process`;

-- ===========================================
-- DROPPING SEARCH INDEXES
-- ===========================================

-- Drop a search index
DROP SEARCH INDEX IF EXISTS `index_name` ON `project_id.dataset_name.table_name`;

-- Example:
DROP SEARCH INDEX IF EXISTS `text_search_idx` ON `my-project.analytics_dataset.documents`;

-- ===========================================
-- DROPPING ROW ACCESS POLICIES
-- ===========================================

-- Drop a row access policy
DROP ROW ACCESS POLICY IF EXISTS `policy_name` ON `project_id.dataset_name.table_name`;

-- Drop all row access policies on a table
DROP ALL ROW ACCESS POLICIES ON `project_id.dataset_name.table_name`;

-- ===========================================
-- DROPPING ENTIRE SCHEMAS (DATASETS)
-- ===========================================

-- Drop an entire dataset/schema with CASCADE (removes all content)
DROP SCHEMA IF EXISTS `project_id.dataset_name` CASCADE;

-- Example:
DROP SCHEMA IF EXISTS `my-project.temp_dataset` CASCADE;

-- Drop a dataset without CASCADE (fails if dataset contains objects)
DROP SCHEMA IF EXISTS `project_id.dataset_name` RESTRICT;

-- Note: SCHEMA and DATASET are synonymous in BigQuery
DROP DATASET IF EXISTS `project_id.dataset_name` CASCADE;

-- ===========================================
-- PROGRAMMATIC APPROACH TO DROP ALL TABLES IN A DATASET
-- ===========================================

-- You can use a script to drop all tables in a dataset
-- This example uses BigQuery's procedural language

DECLARE table_list ARRAY<STRING>;

-- Get list of all tables in the dataset
SET table_list = (
  SELECT ARRAY_AGG(table_name)
  FROM `project_id.dataset_name.INFORMATION_SCHEMA.TABLES`
  WHERE table_type = 'BASE TABLE'
);

-- Loop through and drop each table
IF ARRAY_LENGTH(table_list) > 0 THEN
  FOR i IN 0 .. ARRAY_LENGTH(table_list) - 1 DO
    EXECUTE IMMEDIATE FORMAT(
      'DROP TABLE IF EXISTS `project_id.dataset_name.%s`',
      table_list[OFFSET(i)]
    );
  END FOR;
END IF;

-- ===========================================
-- USING INFORMATION SCHEMA TO IDENTIFY OBJECTS
-- ===========================================

-- List all tables in a dataset before dropping
SELECT 
  table_catalog,
  table_schema,
  table_name,
  table_type,
  creation_time
FROM `project_id.dataset_name.INFORMATION_SCHEMA.TABLES`
ORDER BY table_name;

-- List all views in a dataset
SELECT 
  table_name
FROM `project_id.dataset_name.INFORMATION_SCHEMA.VIEWS`
ORDER BY table_name;

-- List all routines (functions and procedures)
SELECT 
  routine_name,
  routine_type
FROM `project_id.dataset_name.INFORMATION_SCHEMA.ROUTINES`
ORDER BY routine_name;

-- ===========================================
-- SAFE DROPPING PATTERN WITH BACKUP
-- ===========================================

-- 1. First, create a backup of important tables
CREATE TABLE `project_id.backup_dataset.table_backup_20241007`
AS SELECT * FROM `project_id.dataset_name.important_table`;

-- 2. Verify backup
SELECT COUNT(*) as row_count 
FROM `project_id.backup_dataset.table_backup_20241007`;

-- 3. Then drop the original
DROP TABLE IF EXISTS `project_id.dataset_name.important_table`;

-- ===========================================
-- CONDITIONAL DROPPING BASED ON AGE
-- ===========================================

-- Drop tables older than 30 days (requires procedural SQL)
DECLARE tables_to_drop ARRAY<STRING>;

SET tables_to_drop = (
  SELECT ARRAY_AGG(table_name)
  FROM `project_id.dataset_name.INFORMATION_SCHEMA.TABLES`
  WHERE table_type = 'BASE TABLE'
    AND creation_time < TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 30 DAY)
);

IF ARRAY_LENGTH(tables_to_drop) > 0 THEN
  FOR i IN 0 .. ARRAY_LENGTH(tables_to_drop) - 1 DO
    EXECUTE IMMEDIATE FORMAT(
      'DROP TABLE IF EXISTS `project_id.dataset_name.%s`',
      tables_to_drop[OFFSET(i)]
    );
  END FOR;
END IF;

-- ===========================================
-- IMPORTANT CONSIDERATIONS
-- ===========================================

/*
1. Permissions Required:
   - bigquery.datasets.delete - to drop datasets
   - bigquery.tables.delete - to drop tables
   - bigquery.models.delete - to drop models
   - bigquery.routines.delete - to drop functions/procedures

2. CASCADE vs RESTRICT:
   - CASCADE: Drops the dataset and all its contents
   - RESTRICT: Only drops the dataset if it's empty (default behavior)

3. IF EXISTS clause:
   - Prevents errors if the object doesn't exist
   - Recommended for production scripts

4. Recovery:
   - Dropped tables can be restored within 7 days using time travel
   - Example: CREATE TABLE recovered_table AS 
             SELECT * FROM `project.dataset.table` 
             FOR SYSTEM_TIME AS OF TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 HOUR)

5. Best Practices:
   - Always use IF EXISTS to avoid errors
   - Create backups before dropping important data
   - Use meaningful naming for backup tables
   - Test drop commands in a development environment first
   - Document the reason for dropping in your change management system
   
6. Alternative to Dropping:
   - Consider using table expiration for temporary tables
   - Use partitioning with partition expiration for time-based data
   - Rename tables instead of dropping if you might need them later
*/

-- ===========================================
-- DROPPING PARTITIONED TABLES
-- ===========================================

-- Drop specific partitions (for partitioned tables)
DELETE FROM `project_id.dataset_name.partitioned_table`
WHERE DATE(_PARTITIONTIME) = '2024-10-01';

-- Drop entire partitioned table
DROP TABLE IF EXISTS `project_id.dataset_name.partitioned_table`;

-- ===========================================
-- DROPPING WITH DEPENDENCIES CHECK
-- ===========================================

-- Check for dependent views before dropping a table
SELECT 
  table_name AS dependent_view
FROM `project_id.dataset_name.INFORMATION_SCHEMA.VIEWS`
WHERE view_definition LIKE '%table_to_drop%';

-- ===========================================
-- BATCH OPERATIONS USING EXECUTE IMMEDIATE
-- ===========================================

-- Example: Drop all tables with a specific prefix
DECLARE sql_statement STRING;
FOR record IN (
  SELECT table_name
  FROM `project_id.dataset_name.INFORMATION_SCHEMA.TABLES`
  WHERE table_name LIKE 'temp_%'
    AND table_type = 'BASE TABLE'
)
DO
  SET sql_statement = FORMAT(
    'DROP TABLE IF EXISTS `project_id.dataset_name.%s`',
    record.table_name
  );
  EXECUTE IMMEDIATE sql_statement;
END FOR;