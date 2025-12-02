-- BigQuery Table Schema for File Upload Processing
-- Run this in BigQuery Console or using bq command-line tool

-- Create dataset (if not exists)
-- bq mk --dataset your-project-id:your_dataset

-- Create table with schema
CREATE TABLE IF NOT EXISTS `your-project-id.your_dataset.file_uploads` (
    employee_id STRING NOT NULL,
    upload_date TIMESTAMP NOT NULL,
    file_name STRING NOT NULL,
    hum_code STRING NOT NULL,
    bucket_name STRING,
    processed_at TIMESTAMP,
    
    -- Optional: Add partition and clustering for better query performance
)
PARTITION BY DATE(upload_date)
CLUSTER BY hum_code, employee_id;

-- Alternative: Create table using bq command
-- bq mk --table \
--   --schema 'employee_id:STRING,upload_date:TIMESTAMP,file_name:STRING,hum_code:STRING,bucket_name:STRING,processed_at:TIMESTAMP' \
--   --time_partitioning_field upload_date \
--   --clustering_fields hum_code,employee_id \
--   your-project-id:your_dataset.file_uploads

-- Example queries:

-- Get all uploads for a specific HUM code
-- SELECT * FROM `your-project-id.your_dataset.file_uploads` 
-- WHERE hum_code = 'HUM-100' 
-- ORDER BY upload_date DESC;

-- Get upload counts by HUM code
-- SELECT hum_code, COUNT(*) as upload_count 
-- FROM `your-project-id.your_dataset.file_uploads` 
-- GROUP BY hum_code;

-- Get recent uploads
-- SELECT * FROM `your-project-id.your_dataset.file_uploads` 
-- WHERE upload_date >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 7 DAY)
-- ORDER BY upload_date DESC;
