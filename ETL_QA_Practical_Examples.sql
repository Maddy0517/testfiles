-- ===============================================================================
-- ETL QA PRACTICAL EXAMPLES WITH SAMPLE DATA
-- Hands-on practice scenarios with solutions
-- ===============================================================================

-- ===============================================================================
-- SECTION 1: SAMPLE DATA SETUP
-- ===============================================================================

-- Create sample source database tables
CREATE TABLE source_customers (
    customer_id INT PRIMARY KEY,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20),
    registration_date DATE,
    last_modified TIMESTAMP
);

CREATE TABLE source_orders (
    order_id INT PRIMARY KEY,
    customer_id INT,
    order_date DATE,
    total_amount DECIMAL(10,2),
    status VARCHAR(20),
    last_modified TIMESTAMP
);

CREATE TABLE source_order_items (
    item_id INT PRIMARY KEY,
    order_id INT,
    product_id INT,
    quantity INT,
    unit_price DECIMAL(10,2)
);

-- Create target database tables (should mirror source in a perfect ETL)
CREATE TABLE target_customers (
    customer_id INT PRIMARY KEY,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20),
    registration_date DATE,
    load_timestamp TIMESTAMP
);

CREATE TABLE target_orders (
    order_id INT PRIMARY KEY,
    customer_id INT,
    order_date DATE,
    total_amount DECIMAL(10,2),
    status VARCHAR(20),
    load_timestamp TIMESTAMP
);

CREATE TABLE target_order_items (
    item_id INT PRIMARY KEY,
    order_id INT,
    product_id INT,
    quantity INT,
    unit_price DECIMAL(10,2),
    load_timestamp TIMESTAMP
);

-- ETL audit table
CREATE TABLE etl_job_log (
    job_id INT PRIMARY KEY AUTO_INCREMENT,
    job_name VARCHAR(100),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(20),
    records_processed INT,
    records_failed INT,
    error_message TEXT
);

-- ===============================================================================
-- SECTION 2: INSERT SAMPLE DATA
-- ===============================================================================

-- Insert source customer data
INSERT INTO source_customers VALUES
(1, 'John', 'Doe', 'john.doe@email.com', '555-0101', '2024-01-15', '2024-01-15 10:00:00'),
(2, 'Jane', 'Smith', 'jane.smith@email.com', '555-0102', '2024-01-16', '2024-01-16 11:00:00'),
(3, 'Bob', 'Johnson', 'bob.j@email.com', '555-0103', '2024-01-17', '2024-01-17 09:00:00'),
(4, 'Alice', 'Williams', 'alice.w@email.com', '555-0104', '2024-01-18', '2024-01-18 14:00:00'),
(5, 'Charlie', 'Brown', 'charlie.b@email.com', NULL, '2024-01-19', '2024-01-19 15:00:00'),
(6, 'Diana', 'Davis', NULL, '555-0106', '2024-01-20', '2024-01-20 10:30:00'),  -- NULL email
(7, 'Frank', 'Miller', 'frank.m@email.com', '555-0107', '2024-01-21', '2024-01-21 11:30:00'),
(8, 'Grace', 'Wilson', 'grace.w@email.com', '555-0108', '2024-01-22', '2024-01-22 12:00:00'),
(9, 'Henry', 'Moore', 'henry.m@email.com', '555-0109', '2024-01-23', '2024-01-23 13:00:00'),
(10, 'Ivy', 'Taylor', 'ivy.t@email.com', '555-0110', '2024-01-24', '2024-01-24 14:00:00');

-- Insert source orders data
INSERT INTO source_orders VALUES
(1001, 1, '2024-02-01', 150.00, 'Completed', '2024-02-01 10:00:00'),
(1002, 2, '2024-02-02', 200.00, 'Completed', '2024-02-02 11:00:00'),
(1003, 1, '2024-02-03', 75.50, 'Pending', '2024-02-03 09:00:00'),
(1004, 3, '2024-02-04', 300.00, 'Completed', '2024-02-04 14:00:00'),
(1005, 4, '2024-02-05', 125.75, 'Shipped', '2024-02-05 15:00:00'),
(1006, 5, '2024-02-06', 99.99, 'Completed', '2024-02-06 10:30:00'),
(1007, 2, '2024-02-07', 450.00, 'Cancelled', '2024-02-07 11:30:00'),
(1008, 6, '2024-02-08', 275.50, 'Completed', '2024-02-08 12:00:00'),
(1009, 7, '2024-02-09', 199.99, 'Pending', '2024-02-09 13:00:00'),
(1010, 8, '2024-02-10', 350.00, 'Completed', '2024-02-10 14:00:00'),
(1011, 99, '2024-02-11', 100.00, 'Completed', '2024-02-11 15:00:00');  -- Orphan record

-- Insert source order items data
INSERT INTO source_order_items VALUES
(1, 1001, 101, 2, 50.00),
(2, 1001, 102, 1, 50.00),
(3, 1002, 103, 4, 50.00),
(4, 1003, 101, 1, 50.00),
(5, 1003, 104, 1, 25.50),
(6, 1004, 105, 3, 100.00),
(7, 1005, 106, 5, 25.15),
(8, 1006, 107, 1, 99.99),
(9, 1007, 108, 9, 50.00),
(10, 1008, 109, 11, 25.05),
(11, 1009, 110, 2, 99.99),
(12, 1010, 111, 7, 50.00);

-- Insert target data (simulating ETL with some issues)
-- Most records match, but some have issues for testing

-- Target customers - mostly matching
INSERT INTO target_customers VALUES
(1, 'John', 'Doe', 'john.doe@email.com', '555-0101', '2024-01-15', '2024-02-15 10:00:00'),
(2, 'Jane', 'Smith', 'jane.smith@email.com', '555-0102', '2024-01-16', '2024-02-15 10:00:00'),
(3, 'Bob', 'Johnson', 'bob.j@email.com', '555-0103', '2024-01-17', '2024-02-15 10:00:00'),
(4, 'Alice', 'Williams', 'alice.w@email.com', '555-0104', '2024-01-18', '2024-02-15 10:00:00'),
(5, 'Charlie', 'Brown', 'charlie.b@email.com', NULL, '2024-01-19', '2024-02-15 10:00:00'),
(6, 'Diana', 'Davis', NULL, '555-0106', '2024-01-20', '2024-02-15 10:00:00'),
(7, 'Frank', 'Miller', 'frank.m@email.com', '555-0107', '2024-01-21', '2024-02-15 10:00:00'),
(8, 'Grace', 'Wilson', 'grace.w@email.com', '555-0108', '2024-01-22', '2024-02-15 10:00:00'),
-- Customer 9 missing in target (data quality issue)
(10, 'Ivy', 'Taylor', 'ivy.t@email.com', '555-0110', '2024-01-24', '2024-02-15 10:00:00'),
(11, 'Extra', 'Person', 'extra@email.com', '555-0111', '2024-01-25', '2024-02-15 10:00:00'); -- Extra record

-- Target orders - with some discrepancies
INSERT INTO target_orders VALUES
(1001, 1, '2024-02-01', 150.00, 'Completed', '2024-02-15 10:00:00'),
(1002, 2, '2024-02-02', 200.00, 'Completed', '2024-02-15 10:00:00'),
(1003, 1, '2024-02-03', 80.00, 'Pending', '2024-02-15 10:00:00'),  -- Amount mismatch
(1004, 3, '2024-02-04', 300.00, 'Completed', '2024-02-15 10:00:00'),
(1005, 4, '2024-02-05', 125.75, 'Shipped', '2024-02-15 10:00:00'),
(1006, 5, '2024-02-06', 99.99, 'Completed', '2024-02-15 10:00:00'),
(1007, 2, '2024-02-07', 450.00, 'Cancelled', '2024-02-15 10:00:00'),
(1008, 6, '2024-02-08', 275.50, 'Completed', '2024-02-15 10:00:00'),
-- Order 1009 missing in target
(1010, 8, '2024-02-10', 350.00, 'Completed', '2024-02-15 10:00:00');

-- Target order items
INSERT INTO target_order_items VALUES
(1, 1001, 101, 2, 50.00, '2024-02-15 10:00:00'),
(2, 1001, 102, 1, 50.00, '2024-02-15 10:00:00'),
(3, 1002, 103, 4, 50.00, '2024-02-15 10:00:00'),
(4, 1003, 101, 1, 50.00, '2024-02-15 10:00:00'),
(5, 1003, 104, 1, 30.00, '2024-02-15 10:00:00'),  -- Price mismatch
(6, 1004, 105, 3, 100.00, '2024-02-15 10:00:00'),
(7, 1005, 106, 5, 25.15, '2024-02-15 10:00:00'),
(8, 1006, 107, 1, 99.99, '2024-02-15 10:00:00'),
(9, 1007, 108, 9, 50.00, '2024-02-15 10:00:00'),
(10, 1008, 109, 11, 25.05, '2024-02-15 10:00:00'),
-- Item 11 missing
(12, 1010, 111, 7, 50.00, '2024-02-15 10:00:00');

-- Insert ETL job log data
INSERT INTO etl_job_log VALUES
(1, 'Daily_Customer_Load', '2024-02-15 08:00:00', '2024-02-15 08:15:00', 'SUCCESS', 10, 0, NULL),
(2, 'Daily_Order_Load', '2024-02-15 08:15:00', '2024-02-15 08:30:00', 'SUCCESS', 11, 1, 'Orphan customer_id'),
(3, 'Daily_Customer_Load', '2024-02-16 08:00:00', '2024-02-16 08:20:00', 'FAILED', 0, 0, 'Connection timeout'),
(4, 'Daily_Order_Load', '2024-02-16 08:20:00', '2024-02-16 08:35:00', 'SUCCESS', 5, 0, NULL);

-- ===============================================================================
-- SECTION 3: PRACTICE EXERCISES
-- ===============================================================================

-- Exercise 1: Basic Record Count Validation
-- Task: Compare record counts between source and target for all tables
-- Expected Result: Should show mismatches

-- YOUR SOLUTION HERE:


-- SOLUTION:
SELECT 'Customers' as table_name,
       (SELECT COUNT(*) FROM source_customers) as source_count,
       (SELECT COUNT(*) FROM target_customers) as target_count,
       (SELECT COUNT(*) FROM source_customers) - (SELECT COUNT(*) FROM target_customers) as difference;

-- -------------------------------------------------------------------------------

-- Exercise 2: Find Missing Records
-- Task: Find customers that exist in source but not in target
-- Expected Result: Should find customer_id 9

-- YOUR SOLUTION HERE:


-- SOLUTION:
SELECT s.*
FROM source_customers s
LEFT JOIN target_customers t ON s.customer_id = t.customer_id
WHERE t.customer_id IS NULL;

-- -------------------------------------------------------------------------------

-- Exercise 3: Find Extra Records
-- Task: Find customers that exist in target but not in source
-- Expected Result: Should find customer_id 11

-- YOUR SOLUTION HERE:


-- SOLUTION:
SELECT t.*
FROM target_customers t
LEFT JOIN source_customers s ON t.customer_id = s.customer_id
WHERE s.customer_id IS NULL;

-- -------------------------------------------------------------------------------

-- Exercise 4: Data Value Mismatch
-- Task: Find orders where the amount differs between source and target
-- Expected Result: Should find order_id 1003

-- YOUR SOLUTION HERE:


-- SOLUTION:
SELECT 
    s.order_id,
    s.total_amount as source_amount,
    t.total_amount as target_amount,
    s.total_amount - t.total_amount as difference
FROM source_orders s
JOIN target_orders t ON s.order_id = t.order_id
WHERE s.total_amount <> t.total_amount;

-- -------------------------------------------------------------------------------

-- Exercise 5: NULL Value Analysis
-- Task: Count NULL values for email and phone in source_customers
-- Expected Result: 1 NULL email, 1 NULL phone

-- YOUR SOLUTION HERE:


-- SOLUTION:
SELECT 
    COUNT(*) as total_customers,
    SUM(CASE WHEN email IS NULL THEN 1 ELSE 0 END) as null_email_count,
    SUM(CASE WHEN phone IS NULL THEN 1 ELSE 0 END) as null_phone_count,
    ROUND(100.0 * SUM(CASE WHEN email IS NULL THEN 1 ELSE 0 END) / COUNT(*), 2) as null_email_pct,
    ROUND(100.0 * SUM(CASE WHEN phone IS NULL THEN 1 ELSE 0 END) / COUNT(*), 2) as null_phone_pct
FROM source_customers;

-- -------------------------------------------------------------------------------

-- Exercise 6: Orphan Record Detection
-- Task: Find orders that reference non-existent customers
-- Expected Result: Should find order_id 1011 with customer_id 99

-- YOUR SOLUTION HERE:


-- SOLUTION:
SELECT o.order_id, o.customer_id, o.total_amount
FROM source_orders o
LEFT JOIN source_customers c ON o.customer_id = c.customer_id
WHERE c.customer_id IS NULL;

-- -------------------------------------------------------------------------------

-- Exercise 7: Aggregate Reconciliation
-- Task: Compare total order amounts by customer between source and target
-- Expected Result: Should show mismatches for customers with missing/different orders

-- YOUR SOLUTION HERE:


-- SOLUTION:
WITH source_totals AS (
    SELECT 
        customer_id,
        COUNT(*) as order_count,
        SUM(total_amount) as total_amount
    FROM source_orders
    GROUP BY customer_id
),
target_totals AS (
    SELECT 
        customer_id,
        COUNT(*) as order_count,
        SUM(total_amount) as total_amount
    FROM target_orders
    GROUP BY customer_id
)
SELECT 
    COALESCE(s.customer_id, t.customer_id) as customer_id,
    COALESCE(s.order_count, 0) as source_order_count,
    COALESCE(t.order_count, 0) as target_order_count,
    COALESCE(s.total_amount, 0) as source_total,
    COALESCE(t.total_amount, 0) as target_total,
    COALESCE(s.total_amount, 0) - COALESCE(t.total_amount, 0) as amount_difference
FROM source_totals s
FULL OUTER JOIN target_totals t ON s.customer_id = t.customer_id
WHERE COALESCE(s.order_count, 0) <> COALESCE(t.order_count, 0)
   OR ABS(COALESCE(s.total_amount, 0) - COALESCE(t.total_amount, 0)) > 0.01;

-- -------------------------------------------------------------------------------

-- Exercise 8: Business Rule Validation
-- Task: Verify that order total_amount equals sum of order_items
-- Expected Result: May find mismatches due to data quality issues

-- YOUR SOLUTION HERE:


-- SOLUTION:
SELECT 
    o.order_id,
    o.total_amount as order_total,
    COALESCE(SUM(oi.quantity * oi.unit_price), 0) as calculated_total,
    ABS(o.total_amount - COALESCE(SUM(oi.quantity * oi.unit_price), 0)) as difference
FROM source_orders o
LEFT JOIN source_order_items oi ON o.order_id = oi.order_id
GROUP BY o.order_id, o.total_amount
HAVING ABS(o.total_amount - COALESCE(SUM(oi.quantity * oi.unit_price), 0)) > 0.01
ORDER BY difference DESC;

-- -------------------------------------------------------------------------------

-- Exercise 9: Duplicate Detection
-- Task: Check for duplicate customer emails in source_customers
-- Expected Result: Should be none if data is clean

-- YOUR SOLUTION HERE:


-- SOLUTION:
SELECT 
    email,
    COUNT(*) as duplicate_count,
    GROUP_CONCAT(customer_id) as customer_ids
FROM source_customers
WHERE email IS NOT NULL
GROUP BY email
HAVING COUNT(*) > 1;

-- -------------------------------------------------------------------------------

-- Exercise 10: ETL Job Monitoring
-- Task: Calculate success rate and average duration for each ETL job
-- Expected Result: Statistics for each job type

-- YOUR SOLUTION HERE:


-- SOLUTION:
SELECT 
    job_name,
    COUNT(*) as total_runs,
    SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) as successful_runs,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed_runs,
    ROUND(100.0 * SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) / COUNT(*), 2) as success_rate,
    AVG(TIMESTAMPDIFF(MINUTE, start_time, end_time)) as avg_duration_minutes
FROM etl_job_log
GROUP BY job_name;

-- ===============================================================================
-- SECTION 4: COMPREHENSIVE VALIDATION REPORT
-- ===============================================================================

-- Task: Create a comprehensive validation report combining multiple checks
-- This is what you would typically present to stakeholders

WITH 
-- Check 1: Record count validation
record_counts AS (
    SELECT 
        'Customers' as entity,
        (SELECT COUNT(*) FROM source_customers) as source_count,
        (SELECT COUNT(*) FROM target_customers) as target_count
    UNION ALL
    SELECT 
        'Orders' as entity,
        (SELECT COUNT(*) FROM source_orders) as source_count,
        (SELECT COUNT(*) FROM target_orders) as target_count
    UNION ALL
    SELECT 
        'Order Items' as entity,
        (SELECT COUNT(*) FROM source_order_items) as source_count,
        (SELECT COUNT(*) FROM target_order_items) as target_count
),
-- Check 2: Amount reconciliation
amount_recon AS (
    SELECT 
        'Order Amounts' as check_type,
        (SELECT SUM(total_amount) FROM source_orders) as source_value,
        (SELECT SUM(total_amount) FROM target_orders) as target_value
),
-- Check 3: Orphan records
orphan_check AS (
    SELECT 
        'Orphan Orders' as check_type,
        COUNT(*) as orphan_count
    FROM source_orders o
    LEFT JOIN source_customers c ON o.customer_id = c.customer_id
    WHERE c.customer_id IS NULL
),
-- Check 4: NULL values
null_check AS (
    SELECT 
        'Customer Email NULLs' as check_type,
        SUM(CASE WHEN email IS NULL THEN 1 ELSE 0 END) as null_count
    FROM source_customers
    UNION ALL
    SELECT 
        'Customer Phone NULLs' as check_type,
        SUM(CASE WHEN phone IS NULL THEN 1 ELSE 0 END) as null_count
    FROM source_customers
)
-- Final Report
SELECT 
    'ETL Validation Report' as report_section,
    entity as detail,
    source_count,
    target_count,
    CASE 
        WHEN source_count = target_count THEN 'PASS'
        ELSE 'FAIL'
    END as status
FROM record_counts

UNION ALL

SELECT 
    'Amount Reconciliation' as report_section,
    check_type as detail,
    source_value,
    target_value,
    CASE 
        WHEN ABS(source_value - target_value) < 0.01 THEN 'PASS'
        ELSE 'FAIL'
    END as status
FROM amount_recon

UNION ALL

SELECT 
    'Data Quality Checks' as report_section,
    check_type as detail,
    orphan_count as value,
    NULL as target_value,
    CASE 
        WHEN orphan_count = 0 THEN 'PASS'
        ELSE 'FAIL'
    END as status
FROM orphan_check

UNION ALL

SELECT 
    'NULL Value Analysis' as report_section,
    check_type as detail,
    null_count as value,
    NULL as target_value,
    CASE 
        WHEN null_count = 0 THEN 'PASS'
        ELSE 'WARNING'
    END as status
FROM null_check;

-- ===============================================================================
-- SECTION 5: ADVANCED SCENARIOS
-- ===============================================================================

-- Scenario 1: Incremental Load Simulation
-- Task: Identify records modified after a certain watermark

-- Get records modified after last successful load
SELECT 
    customer_id,
    first_name,
    last_name,
    email,
    last_modified
FROM source_customers
WHERE last_modified > (
    SELECT end_time 
    FROM etl_job_log 
    WHERE job_name = 'Daily_Customer_Load' 
      AND status = 'SUCCESS'
    ORDER BY end_time DESC 
    LIMIT 1
);

-- -------------------------------------------------------------------------------

-- Scenario 2: Data Profiling Report
-- Task: Create a comprehensive data profile of source_orders

SELECT 
    'source_orders' as table_name,
    COUNT(*) as total_records,
    COUNT(DISTINCT customer_id) as unique_customers,
    COUNT(DISTINCT order_date) as unique_dates,
    MIN(order_date) as earliest_date,
    MAX(order_date) as latest_date,
    MIN(total_amount) as min_amount,
    MAX(total_amount) as max_amount,
    AVG(total_amount) as avg_amount,
    ROUND(STDDEV(total_amount), 2) as stddev_amount,
    SUM(CASE WHEN status = 'Completed' THEN 1 ELSE 0 END) as completed_orders,
    SUM(CASE WHEN status = 'Pending' THEN 1 ELSE 0 END) as pending_orders,
    SUM(CASE WHEN status = 'Cancelled' THEN 1 ELSE 0 END) as cancelled_orders;

-- -------------------------------------------------------------------------------

-- Scenario 3: Trend Analysis
-- Task: Compare order volumes by date

SELECT 
    order_date,
    COUNT(*) as order_count,
    SUM(total_amount) as daily_total,
    AVG(total_amount) as avg_order_value
FROM source_orders
GROUP BY order_date
ORDER BY order_date;

-- ===============================================================================
-- SECTION 6: CLEANUP (OPTIONAL)
-- ===============================================================================

-- Uncomment these lines to drop all tables and clean up

-- DROP TABLE IF EXISTS target_order_items;
-- DROP TABLE IF EXISTS target_orders;
-- DROP TABLE IF EXISTS target_customers;
-- DROP TABLE IF EXISTS source_order_items;
-- DROP TABLE IF EXISTS source_orders;
-- DROP TABLE IF EXISTS source_customers;
-- DROP TABLE IF EXISTS etl_job_log;

-- ===============================================================================
-- END OF PRACTICAL EXAMPLES
-- ===============================================================================

/*
PRACTICE TIPS:
1. Try to write solutions before looking at the provided answers
2. Modify the data to create different scenarios
3. Time yourself to simulate interview pressure
4. Explain your queries out loud
5. Think about edge cases not covered in examples
6. Practice on different database platforms (MySQL, PostgreSQL, SQL Server)
7. Try to optimize your queries for better performance
8. Create your own validation scenarios based on real projects
*/
