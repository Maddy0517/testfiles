-- BigQuery Table Creation Script for Workday Employee Data
-- This script creates the employee table with partitioning and clustering for optimal performance

-- Create dataset if it doesn't exist
CREATE SCHEMA IF NOT EXISTS workday_data
OPTIONS(
  description="Workday employee data ingested from SOAP API",
  location="US"
);

-- Create employees table with partitioning and clustering
CREATE OR REPLACE TABLE `your-project-id.workday_data.employees`
(
  employee_id STRING NOT NULL OPTIONS(description="Unique employee identifier from Workday"),
  first_name STRING OPTIONS(description="Employee first name"),
  last_name STRING OPTIONS(description="Employee last name"),
  email STRING OPTIONS(description="Employee email address"),
  phone STRING OPTIONS(description="Employee phone number"),
  hire_date STRING OPTIONS(description="Employee hire date"),
  job_title STRING OPTIONS(description="Employee job title or business title"),
  department STRING OPTIONS(description="Employee department or organization"),
  manager_id STRING OPTIONS(description="Manager employee ID"),
  location STRING OPTIONS(description="Employee work location"),
  employment_status STRING OPTIONS(description="Employment status (Active, Terminated, etc.)"),
  effective_date STRING OPTIONS(description="Effective date for the data extract"),
  ingestion_timestamp TIMESTAMP NOT NULL OPTIONS(description="Timestamp when data was ingested into BigQuery")
)
PARTITION BY DATE(ingestion_timestamp)
CLUSTER BY employee_id, department
OPTIONS(
  description="Workday employee data with daily partitioning and clustering for query optimization",
  require_partition_filter=false,
  partition_expiration_days=null
);

-- Create view for latest employee records (most recent ingestion)
CREATE OR REPLACE VIEW `your-project-id.workday_data.employees_latest` AS
SELECT 
  e.*
FROM `your-project-id.workday_data.employees` e
INNER JOIN (
  SELECT 
    employee_id,
    MAX(ingestion_timestamp) as max_timestamp
  FROM `your-project-id.workday_data.employees`
  GROUP BY employee_id
) latest
ON e.employee_id = latest.employee_id 
AND e.ingestion_timestamp = latest.max_timestamp;

-- Create view for active employees only
CREATE OR REPLACE VIEW `your-project-id.workday_data.employees_active` AS
SELECT *
FROM `your-project-id.workday_data.employees_latest`
WHERE employment_status = 'Active';

-- Sample queries for data validation

-- Count total employees
SELECT COUNT(DISTINCT employee_id) as total_employees
FROM `your-project-id.workday_data.employees`;

-- Count by department
SELECT 
  department,
  COUNT(*) as employee_count
FROM `your-project-id.workday_data.employees_latest`
GROUP BY department
ORDER BY employee_count DESC;

-- Count by employment status
SELECT 
  employment_status,
  COUNT(*) as employee_count
FROM `your-project-id.workday_data.employees_latest`
GROUP BY employment_status;

-- Data quality check - find records with missing critical fields
SELECT 
  employee_id,
  first_name,
  last_name,
  email,
  CASE 
    WHEN first_name IS NULL THEN 'Missing first_name'
    WHEN last_name IS NULL THEN 'Missing last_name'
    WHEN email IS NULL THEN 'Missing email'
  END as issue
FROM `your-project-id.workday_data.employees_latest`
WHERE first_name IS NULL 
   OR last_name IS NULL 
   OR email IS NULL;

-- Ingestion history
SELECT 
  DATE(ingestion_timestamp) as ingestion_date,
  COUNT(DISTINCT employee_id) as employee_count,
  MIN(ingestion_timestamp) as first_ingestion,
  MAX(ingestion_timestamp) as last_ingestion
FROM `your-project-id.workday_data.employees`
GROUP BY DATE(ingestion_timestamp)
ORDER BY ingestion_date DESC;
