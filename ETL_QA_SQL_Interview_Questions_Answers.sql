-- ===============================================================================
-- ETL QA SQL INTERVIEW QUESTIONS & ANSWERS
-- Comprehensive guide for ETL Quality Assurance roles
-- ===============================================================================

-- ===============================================================================
-- SECTION 1: DATA QUALITY & VALIDATION
-- ===============================================================================

-- Q1: How do you check for duplicate records in a table?
-- A1: Multiple approaches depending on requirements

-- Method 1: Find duplicate records based on specific columns
SELECT customer_id, email, COUNT(*) as duplicate_count
FROM customers
GROUP BY customer_id, email
HAVING COUNT(*) > 1;

-- Method 2: Find all rows that are complete duplicates
SELECT column1, column2, column3, COUNT(*)
FROM table_name
GROUP BY column1, column2, column3
HAVING COUNT(*) > 1;

-- Method 3: Using window functions to identify duplicates
SELECT *,
       ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY created_date DESC) as row_num
FROM customers
WHERE row_num > 1;

-- -------------------------------------------------------------------------------

-- Q2: How do you validate NULL values in critical columns?
-- A2: Check for NULLs and calculate NULL percentage

SELECT 
    COUNT(*) as total_records,
    COUNT(customer_id) as non_null_customer_id,
    COUNT(*) - COUNT(customer_id) as null_customer_id,
    ROUND(100.0 * (COUNT(*) - COUNT(customer_id)) / COUNT(*), 2) as null_percentage
FROM customers;

-- Check multiple columns for NULLs
SELECT 
    SUM(CASE WHEN customer_id IS NULL THEN 1 ELSE 0 END) as null_customer_id,
    SUM(CASE WHEN email IS NULL THEN 1 ELSE 0 END) as null_email,
    SUM(CASE WHEN phone IS NULL THEN 1 ELSE 0 END) as null_phone
FROM customers;

-- -------------------------------------------------------------------------------

-- Q3: How do you check data completeness between source and target tables?
-- A3: Record count validation

-- Basic count comparison
SELECT 'Source' as table_name, COUNT(*) as record_count FROM source_table
UNION ALL
SELECT 'Target' as table_name, COUNT(*) as record_count FROM target_table;

-- Advanced validation with mismatch flag
WITH source_count AS (SELECT COUNT(*) as cnt FROM source_table),
     target_count AS (SELECT COUNT(*) as cnt FROM target_table)
SELECT 
    s.cnt as source_count,
    t.cnt as target_count,
    s.cnt - t.cnt as difference,
    CASE 
        WHEN s.cnt = t.cnt THEN 'PASS'
        ELSE 'FAIL'
    END as validation_status
FROM source_count s, target_count t;

-- -------------------------------------------------------------------------------

-- Q4: How do you identify orphaned records (referential integrity check)?
-- A4: Find records without valid foreign key references

-- Find orders without valid customers
SELECT o.*
FROM orders o
LEFT JOIN customers c ON o.customer_id = c.customer_id
WHERE c.customer_id IS NULL;

-- Find all orphaned records with counts
SELECT 
    'Orders without Customers' as check_type,
    COUNT(*) as orphaned_count
FROM orders o
LEFT JOIN customers c ON o.customer_id = c.customer_id
WHERE c.customer_id IS NULL;

-- ===============================================================================
-- SECTION 2: DATA RECONCILIATION
-- ===============================================================================

-- Q5: How do you reconcile data between source and target after ETL?
-- A5: Compare records to find mismatches

-- Find records in source but not in target
SELECT s.*
FROM source_table s
LEFT JOIN target_table t ON s.id = t.id
WHERE t.id IS NULL;

-- Find records in target but not in source
SELECT t.*
FROM target_table t
LEFT JOIN source_table s ON t.id = s.id
WHERE s.id IS NULL;

-- Full reconciliation report
SELECT 
    COALESCE(s.id, t.id) as id,
    CASE 
        WHEN s.id IS NULL THEN 'Only in Target'
        WHEN t.id IS NULL THEN 'Only in Source'
        WHEN s.amount <> t.amount THEN 'Amount Mismatch'
        ELSE 'Match'
    END as status,
    s.amount as source_amount,
    t.amount as target_amount
FROM source_table s
FULL OUTER JOIN target_table t ON s.id = t.id
WHERE s.id IS NULL OR t.id IS NULL OR s.amount <> t.amount;

-- -------------------------------------------------------------------------------

-- Q6: How do you validate sum/aggregate reconciliation?
-- A6: Compare aggregated values between source and target

SELECT 
    'Source' as table_name,
    SUM(amount) as total_amount,
    AVG(amount) as avg_amount,
    COUNT(*) as record_count
FROM source_table
UNION ALL
SELECT 
    'Target' as table_name,
    SUM(amount) as total_amount,
    AVG(amount) as avg_amount,
    COUNT(*) as record_count
FROM target_table;

-- Detailed reconciliation by category
SELECT 
    COALESCE(s.category, t.category) as category,
    COALESCE(s.total, 0) as source_total,
    COALESCE(t.total, 0) as target_total,
    COALESCE(s.total, 0) - COALESCE(t.total, 0) as difference
FROM (
    SELECT category, SUM(amount) as total
    FROM source_table
    GROUP BY category
) s
FULL OUTER JOIN (
    SELECT category, SUM(amount) as total
    FROM target_table
    GROUP BY category
) t ON s.category = t.category;

-- ===============================================================================
-- SECTION 3: DATA TRANSFORMATION VALIDATION
-- ===============================================================================

-- Q7: How do you validate date transformations in ETL?
-- A7: Check date format conversions and calculations

-- Validate date range
SELECT 
    MIN(order_date) as earliest_date,
    MAX(order_date) as latest_date,
    COUNT(CASE WHEN order_date > CURRENT_DATE THEN 1 END) as future_dates,
    COUNT(CASE WHEN order_date < '2000-01-01' THEN 1 END) as old_dates
FROM orders;

-- Validate date transformations
SELECT 
    source_date,
    target_date,
    CASE 
        WHEN DATE(source_date) = DATE(target_date) THEN 'PASS'
        ELSE 'FAIL'
    END as validation
FROM (
    SELECT s.id, s.date as source_date, t.date as target_date
    FROM source_table s
    JOIN target_table t ON s.id = t.id
) subquery
WHERE DATE(source_date) <> DATE(target_date);

-- -------------------------------------------------------------------------------

-- Q8: How do you validate string transformations (TRIM, UPPER, LOWER)?
-- A8: Compare transformed values

-- Check for leading/trailing spaces
SELECT 
    id,
    name,
    LENGTH(name) as length_with_spaces,
    LENGTH(TRIM(name)) as length_trimmed,
    CASE 
        WHEN LENGTH(name) = LENGTH(TRIM(name)) THEN 'Clean'
        ELSE 'Has Spaces'
    END as status
FROM customers
WHERE LENGTH(name) <> LENGTH(TRIM(name));

-- Validate case transformations
SELECT 
    s.id,
    s.name as source_name,
    t.name as target_name,
    UPPER(s.name) as expected_target
FROM source_table s
JOIN target_table t ON s.id = t.id
WHERE UPPER(s.name) <> t.name;

-- ===============================================================================
-- SECTION 4: INCREMENTAL LOAD VALIDATION
-- ===============================================================================

-- Q9: How do you identify new, updated, and unchanged records?
-- A9: Use timestamps and checksums

-- Identify record status using timestamps
SELECT 
    t.id,
    CASE 
        WHEN s.id IS NULL THEN 'Deleted from Source'
        WHEN t.id IS NULL THEN 'New Record'
        WHEN s.last_modified > t.last_modified THEN 'Updated'
        ELSE 'Unchanged'
    END as record_status
FROM source_table s
FULL OUTER JOIN target_table t ON s.id = t.id;

-- Count records by status
WITH record_status AS (
    SELECT 
        CASE 
            WHEN s.id IS NULL THEN 'Deleted'
            WHEN t.id IS NULL THEN 'New'
            WHEN s.last_modified > t.last_modified THEN 'Updated'
            ELSE 'Unchanged'
        END as status
    FROM source_table s
    FULL OUTER JOIN target_table t ON s.id = t.id
)
SELECT status, COUNT(*) as count
FROM record_status
GROUP BY status;

-- -------------------------------------------------------------------------------

-- Q10: How do you validate incremental loads using watermark/delta?
-- A10: Check records based on last load timestamp

-- Find records to be loaded (delta)
SELECT *
FROM source_table
WHERE last_modified > (SELECT MAX(last_load_timestamp) FROM etl_metadata);

-- Validate incremental load
SELECT 
    COUNT(*) as records_loaded,
    MIN(last_modified) as earliest_modified,
    MAX(last_modified) as latest_modified
FROM target_table
WHERE load_date = CURRENT_DATE;

-- ===============================================================================
-- SECTION 5: SLOWLY CHANGING DIMENSIONS (SCD)
-- ===============================================================================

-- Q11: How do you implement and validate SCD Type 2?
-- A11: Track historical changes with effective dates

-- Create SCD Type 2 dimension table
CREATE TABLE dim_customer (
    surrogate_key INT PRIMARY KEY,
    customer_id INT,
    name VARCHAR(100),
    email VARCHAR(100),
    effective_start_date DATE,
    effective_end_date DATE,
    is_current BOOLEAN
);

-- Query to find current records
SELECT *
FROM dim_customer
WHERE is_current = TRUE;

-- Query to find historical changes for a customer
SELECT 
    customer_id,
    name,
    email,
    effective_start_date,
    effective_end_date,
    CASE WHEN is_current THEN 'Current' ELSE 'Historical' END as status
FROM dim_customer
WHERE customer_id = 12345
ORDER BY effective_start_date;

-- Validate SCD Type 2 integrity
SELECT 
    customer_id,
    COUNT(*) as version_count,
    SUM(CASE WHEN is_current THEN 1 ELSE 0 END) as current_count
FROM dim_customer
GROUP BY customer_id
HAVING SUM(CASE WHEN is_current THEN 1 ELSE 0 END) <> 1;  -- Should be exactly 1

-- ===============================================================================
-- SECTION 6: PERFORMANCE & OPTIMIZATION
-- ===============================================================================

-- Q12: How do you identify large tables for optimization?
-- A12: Check table sizes and row counts

-- Table size analysis (PostgreSQL)
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size,
    pg_total_relation_size(schemaname||'.'||tablename) as size_bytes
FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
ORDER BY size_bytes DESC;

-- -------------------------------------------------------------------------------

-- Q13: How do you identify missing indexes?
-- A13: Analyze query performance and table scans

-- Find tables without indexes (excluding primary keys)
SELECT 
    t.table_schema,
    t.table_name,
    t.table_rows
FROM information_schema.tables t
LEFT JOIN information_schema.statistics s 
    ON t.table_schema = s.table_schema 
    AND t.table_name = s.table_name
WHERE t.table_schema NOT IN ('mysql', 'information_schema', 'performance_schema')
    AND s.index_name IS NULL
    AND t.table_rows > 1000;

-- ===============================================================================
-- SECTION 7: DATA PROFILING
-- ===============================================================================

-- Q14: How do you perform data profiling on a new table?
-- A14: Analyze data distribution, patterns, and quality

-- Comprehensive data profile
SELECT 
    COUNT(*) as total_records,
    COUNT(DISTINCT customer_id) as unique_customers,
    COUNT(*) - COUNT(customer_id) as null_customer_ids,
    MIN(order_date) as earliest_order,
    MAX(order_date) as latest_order,
    AVG(order_amount) as avg_amount,
    MIN(order_amount) as min_amount,
    MAX(order_amount) as max_amount,
    STDDEV(order_amount) as stddev_amount
FROM orders;

-- Value distribution analysis
SELECT 
    status,
    COUNT(*) as count,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2) as percentage
FROM orders
GROUP BY status
ORDER BY count DESC;

-- -------------------------------------------------------------------------------

-- Q15: How do you identify data anomalies and outliers?
-- A15: Use statistical methods

-- Find outliers using IQR method
WITH stats AS (
    SELECT 
        PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY amount) as Q1,
        PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY amount) as Q3
    FROM transactions
),
outlier_bounds AS (
    SELECT 
        Q1,
        Q3,
        Q3 - Q1 as IQR,
        Q1 - 1.5 * (Q3 - Q1) as lower_bound,
        Q3 + 1.5 * (Q3 - Q1) as upper_bound
    FROM stats
)
SELECT t.*
FROM transactions t, outlier_bounds o
WHERE t.amount < o.lower_bound OR t.amount > o.upper_bound;

-- Find anomalies using Z-score
WITH stats AS (
    SELECT 
        AVG(amount) as mean,
        STDDEV(amount) as std_dev
    FROM transactions
)
SELECT 
    t.*,
    (t.amount - s.mean) / s.std_dev as z_score
FROM transactions t, stats s
WHERE ABS((t.amount - s.mean) / s.std_dev) > 3;  -- 3 standard deviations

-- ===============================================================================
-- SECTION 8: ETL FAILURE & ERROR HANDLING
-- ===============================================================================

-- Q16: How do you track and query ETL job execution history?
-- A16: Maintain ETL metadata/audit tables

-- ETL audit table structure
CREATE TABLE etl_job_audit (
    job_id INT PRIMARY KEY,
    job_name VARCHAR(100),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(20),
    records_processed INT,
    records_failed INT,
    error_message TEXT
);

-- Query recent job failures
SELECT 
    job_name,
    start_time,
    end_time,
    TIMESTAMPDIFF(MINUTE, start_time, end_time) as duration_minutes,
    records_processed,
    records_failed,
    error_message
FROM etl_job_audit
WHERE status = 'FAILED'
    AND start_time >= DATE_SUB(CURRENT_DATE, INTERVAL 7 DAY)
ORDER BY start_time DESC;

-- Job success rate analysis
SELECT 
    job_name,
    COUNT(*) as total_runs,
    SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) as successful_runs,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed_runs,
    ROUND(100.0 * SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) / COUNT(*), 2) as success_rate
FROM etl_job_audit
WHERE start_time >= DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY)
GROUP BY job_name;

-- ===============================================================================
-- SECTION 9: COMPLEX DATA VALIDATION SCENARIOS
-- ===============================================================================

-- Q17: How do you validate cross-table relationships and business rules?
-- A17: Write complex validation queries

-- Validate: Every order must have at least one order item
SELECT o.order_id, o.customer_id
FROM orders o
LEFT JOIN order_items oi ON o.order_id = oi.order_id
WHERE oi.order_id IS NULL;

-- Validate: Order total must equal sum of line items
SELECT 
    o.order_id,
    o.total_amount as order_total,
    COALESCE(SUM(oi.quantity * oi.unit_price), 0) as calculated_total,
    ABS(o.total_amount - COALESCE(SUM(oi.quantity * oi.unit_price), 0)) as difference
FROM orders o
LEFT JOIN order_items oi ON o.order_id = oi.order_id
GROUP BY o.order_id, o.total_amount
HAVING ABS(o.total_amount - COALESCE(SUM(oi.quantity * oi.unit_price), 0)) > 0.01;

-- -------------------------------------------------------------------------------

-- Q18: How do you validate data consistency across multiple tables?
-- A18: Cross-reference validation

-- Validate customer counts across tables
SELECT 
    'Customers' as table_name,
    COUNT(DISTINCT customer_id) as unique_customers
FROM customers
UNION ALL
SELECT 
    'Orders' as table_name,
    COUNT(DISTINCT customer_id) as unique_customers
FROM orders
UNION ALL
SELECT 
    'Payments' as table_name,
    COUNT(DISTINCT customer_id) as unique_customers
FROM payments;

-- Find customers with orders but no payment
SELECT DISTINCT o.customer_id, c.customer_name
FROM orders o
JOIN customers c ON o.customer_id = c.customer_id
LEFT JOIN payments p ON o.order_id = p.order_id
WHERE p.payment_id IS NULL;

-- ===============================================================================
-- SECTION 10: ADVANCED SQL FOR ETL QA
-- ===============================================================================

-- Q19: How do you compare schema between source and target?
-- A19: Query information schema

-- Compare column definitions
SELECT 
    COALESCE(s.column_name, t.column_name) as column_name,
    s.data_type as source_data_type,
    t.data_type as target_data_type,
    s.character_maximum_length as source_length,
    t.character_maximum_length as target_length,
    CASE 
        WHEN s.column_name IS NULL THEN 'Only in Target'
        WHEN t.column_name IS NULL THEN 'Only in Source'
        WHEN s.data_type <> t.data_type THEN 'Data Type Mismatch'
        WHEN s.character_maximum_length <> t.character_maximum_length THEN 'Length Mismatch'
        ELSE 'Match'
    END as comparison_status
FROM information_schema.columns s
FULL OUTER JOIN information_schema.columns t 
    ON s.column_name = t.column_name
WHERE s.table_name = 'source_table'
    AND t.table_name = 'target_table';

-- -------------------------------------------------------------------------------

-- Q20: How do you create a comprehensive ETL validation report?
-- A20: Combine multiple validation checks

WITH validation_checks AS (
    -- Check 1: Record count
    SELECT 
        'Record Count' as check_name,
        CASE WHEN (SELECT COUNT(*) FROM source_table) = (SELECT COUNT(*) FROM target_table)
             THEN 'PASS' ELSE 'FAIL' END as status,
        CONCAT('Source: ', (SELECT COUNT(*) FROM source_table), 
               ', Target: ', (SELECT COUNT(*) FROM target_table)) as details
    
    UNION ALL
    
    -- Check 2: Sum validation
    SELECT 
        'Sum Validation' as check_name,
        CASE WHEN ABS((SELECT SUM(amount) FROM source_table) - 
                      (SELECT SUM(amount) FROM target_table)) < 0.01
             THEN 'PASS' ELSE 'FAIL' END as status,
        CONCAT('Difference: ', 
               (SELECT SUM(amount) FROM source_table) - 
               (SELECT SUM(amount) FROM target_table)) as details
    
    UNION ALL
    
    -- Check 3: Null check
    SELECT 
        'Null Check' as check_name,
        CASE WHEN (SELECT COUNT(*) FROM target_table WHERE key_column IS NULL) = 0
             THEN 'PASS' ELSE 'FAIL' END as status,
        CONCAT('Null records: ', 
               (SELECT COUNT(*) FROM target_table WHERE key_column IS NULL)) as details
    
    UNION ALL
    
    -- Check 4: Duplicate check
    SELECT 
        'Duplicate Check' as check_name,
        CASE WHEN (SELECT COUNT(*) FROM (
                      SELECT key_column, COUNT(*) 
                      FROM target_table 
                      GROUP BY key_column 
                      HAVING COUNT(*) > 1
                   ) x) = 0
             THEN 'PASS' ELSE 'FAIL' END as status,
        CONCAT('Duplicate keys: ', 
               (SELECT COUNT(*) FROM (
                   SELECT key_column 
                   FROM target_table 
                   GROUP BY key_column 
                   HAVING COUNT(*) > 1
               ) x)) as details
)
SELECT 
    check_name,
    status,
    details,
    CURRENT_TIMESTAMP as validation_timestamp
FROM validation_checks;

-- ===============================================================================
-- BONUS: COMMON ETL QA INTERVIEW QUESTIONS (Conceptual)
-- ===============================================================================

/*
Q21: What is the difference between TRUNCATE and DELETE?
A21: 
- DELETE: DML command, can use WHERE clause, slower, logged, can be rolled back
- TRUNCATE: DDL command, removes all rows, faster, minimal logging, cannot be rolled back

Q22: What are the different types of joins and when do you use them in ETL?
A22:
- INNER JOIN: Returns matching records from both tables (most common for data integration)
- LEFT JOIN: Returns all from left table + matching from right (used for finding orphaned records)
- RIGHT JOIN: Returns all from right table + matching from left
- FULL OUTER JOIN: Returns all records from both tables (used for reconciliation)
- CROSS JOIN: Cartesian product (rarely used, mostly for generating test data)

Q23: What is the difference between WHERE and HAVING?
A23:
- WHERE: Filters rows before grouping, cannot use aggregate functions
- HAVING: Filters groups after aggregation, can use aggregate functions

Q24: Explain the order of SQL query execution
A24:
1. FROM (including JOINs)
2. WHERE
3. GROUP BY
4. HAVING
5. SELECT
6. DISTINCT
7. ORDER BY
8. LIMIT/OFFSET

Q25: How do you handle data quality issues in ETL?
A25:
- Implement data validation rules at source
- Use staging tables for data quality checks
- Log rejected records in error tables
- Create data quality dashboards
- Implement automated alerts for threshold breaches
- Use data profiling before ETL design

Q26: What is data lineage and why is it important?
A26:
Data lineage tracks the flow of data from source to target through all transformations.
Important for:
- Debugging data issues
- Impact analysis
- Compliance and auditing
- Understanding data dependencies

Q27: What are window functions and how are they useful in ETL?
A27:
Window functions perform calculations across rows related to the current row.
Useful for:
- Ranking (ROW_NUMBER, RANK, DENSE_RANK)
- Running totals (SUM OVER)
- Identifying duplicates
- Calculating deltas between rows (LAG, LEAD)

Q28: How do you optimize slow-running SQL queries?
A28:
- Add appropriate indexes
- Avoid SELECT *
- Use WHERE instead of HAVING when possible
- Avoid functions in WHERE clause on indexed columns
- Use EXISTS instead of IN for large datasets
- Partition large tables
- Update table statistics
- Use EXPLAIN PLAN to analyze execution

Q29: What is the difference between UNION and UNION ALL?
A29:
- UNION: Removes duplicate rows, slower (requires sorting)
- UNION ALL: Keeps all rows including duplicates, faster
Use UNION ALL when you know there are no duplicates or duplicates don't matter.

Q30: How do you handle timezone conversions in ETL?
A30:
- Store all timestamps in UTC in the database
- Convert to local timezone only for display
- Use CONVERT_TZ or AT TIME ZONE functions
- Document timezone assumptions
- Be careful with DST transitions
*/

-- ===============================================================================
-- PRACTICAL ETL QA TEST SCENARIOS
-- ===============================================================================

-- Scenario 1: Data Migration Validation Script
-- After migrating data from legacy system to new system

-- Step 1: Count validation
SELECT 'Legacy System' as system, COUNT(*) as count FROM legacy_orders
UNION ALL
SELECT 'New System' as system, COUNT(*) as count FROM new_orders;

-- Step 2: Data integrity validation
SELECT 
    l.order_id,
    'Amount Mismatch' as issue_type
FROM legacy_orders l
JOIN new_orders n ON l.order_id = n.order_id
WHERE l.amount <> n.amount
UNION ALL
SELECT 
    l.order_id,
    'Date Mismatch' as issue_type
FROM legacy_orders l
JOIN new_orders n ON l.order_id = n.order_id
WHERE DATE(l.order_date) <> DATE(n.order_date);

-- Step 3: Missing records check
SELECT order_id, 'Missing in New System' as issue
FROM legacy_orders
WHERE order_id NOT IN (SELECT order_id FROM new_orders)
UNION ALL
SELECT order_id, 'Extra in New System' as issue
FROM new_orders
WHERE order_id NOT IN (SELECT order_id FROM legacy_orders);

-- ===============================================================================
-- END OF ETL QA SQL INTERVIEW QUESTIONS & ANSWERS
-- ===============================================================================

/*
TIPS FOR ETL QA INTERVIEWS:
1. Always explain your approach before writing the query
2. Consider edge cases (NULLs, duplicates, missing data)
3. Think about performance for large datasets
4. Mention data quality best practices
5. Discuss error handling and logging
6. Be ready to explain your validation strategy
7. Know the differences between databases (Oracle, SQL Server, PostgreSQL, MySQL)
8. Understand ETL tools (Informatica, Talend, SSIS, Apache Airflow)
9. Be familiar with data warehousing concepts (Facts, Dimensions, SCD)
10. Practice writing queries without IDE assistance
*/
