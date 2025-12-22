-- ===============================================================================
-- ETL QA SQL INTERVIEW QUESTIONS & ANSWERS
-- 50 MOST FREQUENTLY ASKED QUESTIONS WITH SAMPLE DATA
-- Complete, Self-Contained, and Ready to Execute
-- ===============================================================================

-- ===============================================================================
-- SECTION 0: SAMPLE DATABASE SETUP
-- Create tables and insert sample data for practicing all queries
-- ===============================================================================

-- Drop existing tables if they exist (for clean setup)
DROP TABLE IF EXISTS target_order_items;
DROP TABLE IF EXISTS target_orders;
DROP TABLE IF EXISTS target_customers;
DROP TABLE IF EXISTS source_order_items;
DROP TABLE IF EXISTS source_orders;
DROP TABLE IF EXISTS source_customers;
DROP TABLE IF EXISTS etl_job_log;
DROP TABLE IF EXISTS dim_customer;
DROP TABLE IF EXISTS fact_sales;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS employee_salary;
DROP TABLE IF EXISTS transactions;

-- -------------------------------------------------------------------------------
-- Source Tables
-- -------------------------------------------------------------------------------

CREATE TABLE source_customers (
    customer_id INT PRIMARY KEY,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20),
    city VARCHAR(50),
    state VARCHAR(2),
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

-- -------------------------------------------------------------------------------
-- Target Tables (for ETL validation)
-- -------------------------------------------------------------------------------

CREATE TABLE target_customers (
    customer_id INT PRIMARY KEY,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20),
    city VARCHAR(50),
    state VARCHAR(2),
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

-- -------------------------------------------------------------------------------
-- Supporting Tables
-- -------------------------------------------------------------------------------

CREATE TABLE products (
    product_id INT PRIMARY KEY,
    product_name VARCHAR(100),
    category VARCHAR(50),
    price DECIMAL(10,2)
);

CREATE TABLE payments (
    payment_id INT PRIMARY KEY,
    order_id INT,
    payment_date DATE,
    payment_amount DECIMAL(10,2),
    payment_method VARCHAR(20)
);

CREATE TABLE etl_job_log (
    job_id INT PRIMARY KEY,
    job_name VARCHAR(100),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(20),
    records_processed INT,
    records_failed INT,
    error_message TEXT
);

CREATE TABLE dim_customer (
    surrogate_key INT PRIMARY KEY,
    customer_id INT,
    name VARCHAR(100),
    email VARCHAR(100),
    city VARCHAR(50),
    effective_start_date DATE,
    effective_end_date DATE,
    is_current BOOLEAN
);

CREATE TABLE fact_sales (
    sale_id INT PRIMARY KEY,
    customer_key INT,
    product_id INT,
    sale_date DATE,
    quantity INT,
    amount DECIMAL(10,2)
);

CREATE TABLE employee_salary (
    employee_id INT PRIMARY KEY,
    employee_name VARCHAR(100),
    department VARCHAR(50),
    salary DECIMAL(10,2),
    hire_date DATE
);

CREATE TABLE transactions (
    transaction_id INT PRIMARY KEY,
    transaction_date DATE,
    amount DECIMAL(10,2),
    transaction_type VARCHAR(20)
);

-- ===============================================================================
-- INSERT SAMPLE DATA
-- Data includes intentional issues for testing data quality checks
-- ===============================================================================

-- Source Customers (15 records with various data quality issues)
INSERT INTO source_customers VALUES
(1, 'John', 'Doe', 'john.doe@email.com', '555-0101', 'New York', 'NY', '2024-01-15', '2024-01-15 10:00:00'),
(2, 'Jane', 'Smith', 'jane.smith@email.com', '555-0102', 'Los Angeles', 'CA', '2024-01-16', '2024-01-16 11:00:00'),
(3, 'Bob', 'Johnson', 'bob.j@email.com', '555-0103', 'Chicago', 'IL', '2024-01-17', '2024-01-17 09:00:00'),
(4, 'Alice', 'Williams', 'alice.w@email.com', '555-0104', 'Houston', 'TX', '2024-01-18', '2024-01-18 14:00:00'),
(5, 'Charlie', 'Brown', 'charlie.b@email.com', NULL, 'Phoenix', 'AZ', '2024-01-19', '2024-01-19 15:00:00'),
(6, 'Diana', 'Davis', NULL, '555-0106', 'Philadelphia', 'PA', '2024-01-20', '2024-01-20 10:30:00'),  -- NULL email
(7, '  Frank  ', 'Miller', 'frank.m@email.com', '555-0107', 'San Antonio', 'TX', '2024-01-21', '2024-01-21 11:30:00'),  -- Spaces in name
(8, 'Grace', 'Wilson', 'grace.w@email.com', '555-0108', 'San Diego', 'CA', '2024-01-22', '2024-01-22 12:00:00'),
(9, 'Henry', 'Moore', 'henry.m@email.com', '555-0109', 'Dallas', 'TX', '2024-01-23', '2024-01-23 13:00:00'),
(10, 'Ivy', 'Taylor', 'ivy.t@email.com', '555-0110', 'San Jose', 'CA', '2024-01-24', '2024-01-24 14:00:00'),
(11, 'Jack', 'Anderson', 'jack.a@email.com', '555-0111', 'Austin', 'TX', '2024-01-25', '2024-01-25 15:00:00'),
(12, 'Kate', 'Thomas', 'kate.t@email.com', '555-0112', 'Jacksonville', 'FL', '2024-01-26', '2024-01-26 16:00:00'),
(13, 'Leo', 'Jackson', 'leo.j@email.com', '555-0113', 'Fort Worth', 'TX', '2024-01-27', '2024-01-27 17:00:00'),
(14, 'Mary', 'White', 'jane.smith@email.com', '555-0114', 'Columbus', 'OH', '2024-01-28', '2024-01-28 18:00:00'),  -- Duplicate email
(15, 'Nancy', 'Harris', 'nancy.h@email.com', '555-0115', 'Charlotte', 'NC', '2024-01-29', '2024-01-29 19:00:00');

-- Source Orders (20 records)
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
(1011, 99, '2024-02-11', 100.00, 'Completed', '2024-02-11 15:00:00'),  -- Orphan record (customer_id 99 doesn't exist)
(1012, 9, '2024-02-12', 225.00, 'Completed', '2024-02-12 16:00:00'),
(1013, 10, '2024-02-13', 175.50, 'Shipped', '2024-02-13 17:00:00'),
(1014, 11, '2024-02-14', 425.00, 'Completed', '2024-02-14 18:00:00'),
(1015, 12, '2024-02-15', 315.75, 'Pending', '2024-02-15 19:00:00'),
(1016, 13, '2024-02-16', 189.99, 'Completed', '2024-02-16 20:00:00'),
(1017, 14, '2024-02-17', 275.00, 'Shipped', '2024-02-17 21:00:00'),
(1018, 15, '2024-02-18', 399.99, 'Completed', '2024-02-18 22:00:00'),
(1019, 1, '2024-02-19', 150.00, 'Completed', '2024-02-19 23:00:00'),  -- Duplicate amount for customer 1
(1020, 2, '2024-02-20', 525.00, 'Pending', '2024-02-20 10:00:00');

-- Source Order Items (25 records)
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
(12, 1010, 111, 7, 50.00),
(13, 1012, 101, 3, 75.00),
(14, 1013, 102, 2, 87.75),
(15, 1014, 103, 5, 85.00),
(16, 1015, 104, 4, 78.94),
(17, 1016, 105, 2, 94.99),
(18, 1017, 106, 3, 91.67),
(19, 1018, 107, 4, 99.99),
(20, 1019, 108, 3, 50.00),
(21, 1020, 109, 5, 105.00),
(22, 1001, 110, 1, 150.00),  -- Extra item causing total mismatch
(23, 1002, 111, 2, 100.00),
(24, 1003, 101, 1, 75.50),
(25, 1004, 102, 2, 150.00);

-- Target Customers (13 records - missing 2 from source, has 1 extra)
INSERT INTO target_customers VALUES
(1, 'John', 'Doe', 'john.doe@email.com', '555-0101', 'New York', 'NY', '2024-01-15', '2024-02-25 10:00:00'),
(2, 'Jane', 'Smith', 'jane.smith@email.com', '555-0102', 'Los Angeles', 'CA', '2024-01-16', '2024-02-25 10:00:00'),
(3, 'Bob', 'Johnson', 'bob.j@email.com', '555-0103', 'Chicago', 'IL', '2024-01-17', '2024-02-25 10:00:00'),
(4, 'Alice', 'Williams', 'alice.w@email.com', '555-0104', 'Houston', 'TX', '2024-01-18', '2024-02-25 10:00:00'),
(5, 'Charlie', 'Brown', 'charlie.b@email.com', NULL, 'Phoenix', 'AZ', '2024-01-19', '2024-02-25 10:00:00'),
(6, 'Diana', 'Davis', NULL, '555-0106', 'Philadelphia', 'PA', '2024-01-20', '2024-02-25 10:00:00'),
(7, 'Frank', 'Miller', 'frank.m@email.com', '555-0107', 'San Antonio', 'TX', '2024-01-21', '2024-02-25 10:00:00'),  -- Name trimmed
(8, 'Grace', 'Wilson', 'grace.w@email.com', '555-0108', 'San Diego', 'CA', '2024-01-22', '2024-02-25 10:00:00'),
-- Customer 9 missing in target (data quality issue)
(10, 'Ivy', 'Taylor', 'ivy.t@email.com', '555-0110', 'San Jose', 'CA', '2024-01-24', '2024-02-25 10:00:00'),
(11, 'Jack', 'Anderson', 'jack.a@email.com', '555-0111', 'Austin', 'TX', '2024-01-25', '2024-02-25 10:00:00'),
(12, 'Kate', 'Thomas', 'kate.t@email.com', '555-0112', 'Jacksonville', 'FL', '2024-01-26', '2024-02-25 10:00:00'),
-- Customer 13 missing in target
(14, 'Mary', 'White', 'jane.smith@email.com', '555-0114', 'Columbus', 'OH', '2024-01-28', '2024-02-25 10:00:00'),
(99, 'Test', 'User', 'test@email.com', '555-9999', 'Test City', 'TC', '2024-01-30', '2024-02-25 10:00:00');  -- Extra record

-- Target Orders (17 records - some missing, one with amount mismatch)
INSERT INTO target_orders VALUES
(1001, 1, '2024-02-01', 150.00, 'Completed', '2024-02-25 10:00:00'),
(1002, 2, '2024-02-02', 200.00, 'Completed', '2024-02-25 10:00:00'),
(1003, 1, '2024-02-03', 80.00, 'Pending', '2024-02-25 10:00:00'),  -- Amount mismatch (should be 75.50)
(1004, 3, '2024-02-04', 300.00, 'Completed', '2024-02-25 10:00:00'),
(1005, 4, '2024-02-05', 125.75, 'Shipped', '2024-02-25 10:00:00'),
(1006, 5, '2024-02-06', 99.99, 'Completed', '2024-02-25 10:00:00'),
(1007, 2, '2024-02-07', 450.00, 'Cancelled', '2024-02-25 10:00:00'),
(1008, 6, '2024-02-08', 275.50, 'Completed', '2024-02-25 10:00:00'),
-- Order 1009 missing in target
(1010, 8, '2024-02-10', 350.00, 'Completed', '2024-02-25 10:00:00'),
(1012, 9, '2024-02-12', 225.00, 'Completed', '2024-02-25 10:00:00'),
(1013, 10, '2024-02-13', 175.50, 'Shipped', '2024-02-25 10:00:00'),
(1014, 11, '2024-02-14', 425.00, 'Completed', '2024-02-25 10:00:00'),
-- Order 1015 missing
(1016, 13, '2024-02-16', 189.99, 'Completed', '2024-02-25 10:00:00'),
(1017, 14, '2024-02-17', 275.00, 'Shipped', '2024-02-25 10:00:00'),
(1018, 15, '2024-02-18', 399.99, 'Completed', '2024-02-25 10:00:00'),
(1019, 1, '2024-02-19', 150.00, 'Completed', '2024-02-25 10:00:00'),
(1020, 2, '2024-02-20', 525.00, 'Pending', '2024-02-25 10:00:00');

-- Target Order Items (20 records)
INSERT INTO target_order_items VALUES
(1, 1001, 101, 2, 50.00, '2024-02-25 10:00:00'),
(2, 1001, 102, 1, 50.00, '2024-02-25 10:00:00'),
(3, 1002, 103, 4, 50.00, '2024-02-25 10:00:00'),
(4, 1003, 101, 1, 50.00, '2024-02-25 10:00:00'),
(5, 1003, 104, 1, 30.00, '2024-02-25 10:00:00'),  -- Price mismatch (should be 25.50)
(6, 1004, 105, 3, 100.00, '2024-02-25 10:00:00'),
(7, 1005, 106, 5, 25.15, '2024-02-25 10:00:00'),
(8, 1006, 107, 1, 99.99, '2024-02-25 10:00:00'),
(9, 1007, 108, 9, 50.00, '2024-02-25 10:00:00'),
(10, 1008, 109, 11, 25.05, '2024-02-25 10:00:00'),
-- Item 11 missing
(12, 1010, 111, 7, 50.00, '2024-02-25 10:00:00'),
(13, 1012, 101, 3, 75.00, '2024-02-25 10:00:00'),
(14, 1013, 102, 2, 87.75, '2024-02-25 10:00:00'),
(15, 1014, 103, 5, 85.00, '2024-02-25 10:00:00'),
(16, 1016, 105, 2, 94.99, '2024-02-25 10:00:00'),
(17, 1017, 106, 3, 91.67, '2024-02-25 10:00:00'),
(18, 1018, 107, 4, 99.99, '2024-02-25 10:00:00'),
(19, 1019, 108, 3, 50.00, '2024-02-25 10:00:00'),
(20, 1020, 109, 5, 105.00, '2024-02-25 10:00:00');

-- Products
INSERT INTO products VALUES
(101, 'Laptop', 'Electronics', 999.99),
(102, 'Mouse', 'Electronics', 29.99),
(103, 'Keyboard', 'Electronics', 79.99),
(104, 'Monitor', 'Electronics', 299.99),
(105, 'Headphones', 'Electronics', 149.99),
(106, 'Webcam', 'Electronics', 89.99),
(107, 'Speaker', 'Electronics', 199.99),
(108, 'USB Cable', 'Accessories', 9.99),
(109, 'HDMI Cable', 'Accessories', 19.99),
(110, 'Desk Lamp', 'Furniture', 49.99),
(111, 'Office Chair', 'Furniture', 299.99);

-- Payments
INSERT INTO payments VALUES
(1, 1001, '2024-02-01', 150.00, 'Credit Card'),
(2, 1002, '2024-02-02', 200.00, 'PayPal'),
(3, 1004, '2024-02-04', 300.00, 'Credit Card'),
(4, 1005, '2024-02-05', 125.75, 'Debit Card'),
(5, 1006, '2024-02-06', 99.99, 'Credit Card'),
(6, 1008, '2024-02-08', 275.50, 'PayPal'),
(7, 1010, '2024-02-10', 350.00, 'Credit Card'),
(8, 1012, '2024-02-12', 225.00, 'Credit Card'),
(9, 1014, '2024-02-14', 425.00, 'PayPal'),
(10, 1016, '2024-02-16', 189.99, 'Credit Card');
-- Note: Some orders don't have payments (1003, 1007, 1009, etc.)

-- ETL Job Log
INSERT INTO etl_job_log VALUES
(1, 'Daily_Customer_Load', '2024-02-25 08:00:00', '2024-02-25 08:15:00', 'SUCCESS', 15, 0, NULL),
(2, 'Daily_Order_Load', '2024-02-25 08:15:00', '2024-02-25 08:30:00', 'SUCCESS', 20, 1, 'Orphan customer_id 99'),
(3, 'Daily_Customer_Load', '2024-02-26 08:00:00', '2024-02-26 08:20:00', 'FAILED', 0, 0, 'Connection timeout to source database'),
(4, 'Daily_Order_Load', '2024-02-26 08:20:00', NULL, 'RUNNING', 0, 0, NULL),
(5, 'Weekly_Full_Load', '2024-02-24 02:00:00', '2024-02-24 04:30:00', 'SUCCESS', 5000, 5, 'Minor data quality issues'),
(6, 'Daily_Customer_Load', '2024-02-24 08:00:00', '2024-02-24 08:12:00', 'SUCCESS', 15, 0, NULL),
(7, 'Daily_Order_Load', '2024-02-23 08:15:00', '2024-02-23 08:45:00', 'FAILED', 150, 50, 'Invalid foreign key constraint'),
(8, 'Hourly_Incremental', '2024-02-25 10:00:00', '2024-02-25 10:05:00', 'SUCCESS', 100, 0, NULL);

-- SCD Dimension Table (Slowly Changing Dimension Type 2)
INSERT INTO dim_customer VALUES
(1, 1001, 'John Smith', 'john.smith@email.com', 'New York', '2023-01-01', '2023-06-30', FALSE),
(2, 1001, 'John Smith', 'john.new@email.com', 'New York', '2023-07-01', '2023-12-31', FALSE),
(3, 1001, 'John Smith', 'john.new@email.com', 'Boston', '2024-01-01', '9999-12-31', TRUE),
(4, 1002, 'Jane Doe', 'jane.doe@email.com', 'Chicago', '2023-01-01', '9999-12-31', TRUE),
(5, 1003, 'Bob Wilson', 'bob.w@email.com', 'Miami', '2023-01-01', '2023-08-15', FALSE),
(6, 1003, 'Bob Wilson', 'bob.w@email.com', 'Atlanta', '2023-08-16', '9999-12-31', TRUE);

-- Fact Sales Table
INSERT INTO fact_sales VALUES
(1, 1, 101, '2024-02-01', 2, 1999.98),
(2, 1, 102, '2024-02-01', 1, 29.99),
(3, 2, 103, '2024-02-02', 4, 319.96),
(4, 3, 104, '2024-02-03', 1, 299.99),
(5, 4, 105, '2024-02-04', 3, 449.97),
(6, 1, 106, '2024-02-05', 2, 179.98),
(7, 2, 107, '2024-02-06', 1, 199.99),
(8, 3, 108, '2024-02-07', 5, 49.95),
(9, 4, 109, '2024-02-08', 3, 59.97),
(10, 1, 110, '2024-02-09', 1, 49.99);

-- Employee Salary (for ranking and analytics queries)
INSERT INTO employee_salary VALUES
(1, 'Alice Johnson', 'Sales', 75000, '2020-01-15'),
(2, 'Bob Smith', 'Sales', 68000, '2020-03-20'),
(3, 'Charlie Brown', 'IT', 85000, '2019-05-10'),
(4, 'Diana Prince', 'IT', 92000, '2018-07-22'),
(5, 'Eve Davis', 'HR', 62000, '2021-02-14'),
(6, 'Frank Miller', 'HR', 58000, '2021-06-30'),
(7, 'Grace Lee', 'Sales', 71000, '2020-08-11'),
(8, 'Henry Wilson', 'IT', 88000, '2019-11-05'),
(9, 'Irene Taylor', 'Finance', 79000, '2020-04-18'),
(10, 'Jack Anderson', 'Finance', 83000, '2019-09-25');

-- Transactions (for outlier detection)
INSERT INTO transactions VALUES
(1, '2024-02-01', 150.00, 'Purchase'),
(2, '2024-02-02', 200.00, 'Purchase'),
(3, '2024-02-03', 175.50, 'Purchase'),
(4, '2024-02-04', 190.00, 'Purchase'),
(5, '2024-02-05', 5000.00, 'Purchase'),  -- Outlier
(6, '2024-02-06', 165.00, 'Purchase'),
(7, '2024-02-07', 180.00, 'Purchase'),
(8, '2024-02-08', 155.00, 'Purchase'),
(9, '2024-02-09', 10.00, 'Refund'),  -- Outlier
(10, '2024-02-10', 195.00, 'Purchase'),
(11, '2024-02-11', 170.00, 'Purchase'),
(12, '2024-02-12', 8500.00, 'Purchase'),  -- Outlier
(13, '2024-02-13', 185.00, 'Purchase'),
(14, '2024-02-14', 160.00, 'Purchase'),
(15, '2024-02-15', 175.00, 'Purchase');

-- ===============================================================================
-- SECTION 1: DATA QUALITY & VALIDATION (10 Questions)
-- ===============================================================================

-- -------------------------------------------------------------------------------
-- Q1: How do you check for duplicate records based on specific columns?
-- -------------------------------------------------------------------------------
-- A1: Use GROUP BY with HAVING to find duplicates

-- Find duplicate emails
SELECT email, COUNT(*) as duplicate_count, GROUP_CONCAT(customer_id) as customer_ids
FROM source_customers
WHERE email IS NOT NULL
GROUP BY email
HAVING COUNT(*) > 1;

-- Expected Result: Should find 'jane.smith@email.com' appears twice (customers 2 and 14)

-- -------------------------------------------------------------------------------
-- Q2: How do you validate NULL values and calculate NULL percentage?
-- -------------------------------------------------------------------------------
-- A2: Use COUNT with CASE statements

SELECT 
    COUNT(*) as total_records,
    SUM(CASE WHEN email IS NULL THEN 1 ELSE 0 END) as null_email,
    SUM(CASE WHEN phone IS NULL THEN 1 ELSE 0 END) as null_phone,
    ROUND(100.0 * SUM(CASE WHEN email IS NULL THEN 1 ELSE 0 END) / COUNT(*), 2) as email_null_pct,
    ROUND(100.0 * SUM(CASE WHEN phone IS NULL THEN 1 ELSE 0 END) / COUNT(*), 2) as phone_null_pct
FROM source_customers;

-- Expected Result: 1 NULL email (6.67%), 1 NULL phone (6.67%)

-- -------------------------------------------------------------------------------
-- Q3: How do you compare record counts between source and target?
-- -------------------------------------------------------------------------------
-- A3: Use UNION ALL with counts

SELECT 
    'Source Customers' as table_name, 
    COUNT(*) as record_count 
FROM source_customers
UNION ALL
SELECT 
    'Target Customers' as table_name, 
    COUNT(*) as record_count 
FROM target_customers;

-- Expected Result: Source=15, Target=13 (shows data loss)

-- -------------------------------------------------------------------------------
-- Q4: How do you identify orphaned records (referential integrity)?
-- -------------------------------------------------------------------------------
-- A4: Use LEFT JOIN to find records without parent

-- Find orders without valid customers
SELECT 
    o.order_id,
    o.customer_id,
    o.total_amount,
    'Orphan Order - Customer Not Found' as issue
FROM source_orders o
LEFT JOIN source_customers c ON o.customer_id = c.customer_id
WHERE c.customer_id IS NULL;

-- Expected Result: Order 1011 with customer_id 99

-- -------------------------------------------------------------------------------
-- Q5: How do you detect records with leading/trailing spaces?
-- -------------------------------------------------------------------------------
-- A5: Compare LENGTH before and after TRIM

SELECT 
    customer_id,
    first_name,
    LENGTH(first_name) as length_original,
    LENGTH(TRIM(first_name)) as length_trimmed,
    LENGTH(first_name) - LENGTH(TRIM(first_name)) as extra_spaces
FROM source_customers
WHERE LENGTH(first_name) <> LENGTH(TRIM(first_name));

-- Expected Result: Customer 7 has spaces in first name

-- -------------------------------------------------------------------------------
-- Q6: How do you find records missing in target compared to source?
-- -------------------------------------------------------------------------------
-- A6: Use LEFT JOIN with NULL check

SELECT 
    s.customer_id,
    s.first_name,
    s.last_name,
    s.email,
    'Missing in Target' as status
FROM source_customers s
LEFT JOIN target_customers t ON s.customer_id = t.customer_id
WHERE t.customer_id IS NULL;

-- Expected Result: Customers 9, 13, and 15 missing in target

-- -------------------------------------------------------------------------------
-- Q7: How do you find extra records in target not in source?
-- -------------------------------------------------------------------------------
-- A7: Reverse LEFT JOIN

SELECT 
    t.customer_id,
    t.first_name,
    t.last_name,
    t.email,
    'Extra in Target' as status
FROM target_customers t
LEFT JOIN source_customers s ON t.customer_id = s.customer_id
WHERE s.customer_id IS NULL;

-- Expected Result: Customer 99 (Test User) extra in target

-- -------------------------------------------------------------------------------
-- Q8: How do you validate data type and format (email validation)?
-- -------------------------------------------------------------------------------
-- A8: Use pattern matching

SELECT 
    customer_id,
    email,
    CASE 
        WHEN email IS NULL THEN 'NULL Email'
        WHEN email NOT LIKE '%@%.%' THEN 'Invalid Format'
        WHEN email LIKE '%@%@%' THEN 'Multiple @ signs'
        WHEN email LIKE '@%' OR email LIKE '%@' THEN 'Missing Username/Domain'
        ELSE 'Valid'
    END as email_validation
FROM source_customers
WHERE email IS NULL 
   OR email NOT LIKE '%@%.%'
   OR email LIKE '%@%@%';

-- Expected Result: Customer 6 has NULL email

-- -------------------------------------------------------------------------------
-- Q9: How do you check for future dates or invalid date ranges?
-- -------------------------------------------------------------------------------
-- A9: Compare dates with current date and business rules

SELECT 
    order_id,
    order_date,
    CASE 
        WHEN order_date > CURRENT_DATE THEN 'Future Date'
        WHEN order_date < '2000-01-01' THEN 'Too Old'
        WHEN order_date > CURRENT_DATE + INTERVAL 1 YEAR THEN 'Beyond Valid Range'
        ELSE 'Valid'
    END as date_validation
FROM source_orders
WHERE order_date > CURRENT_DATE 
   OR order_date < '2000-01-01';

-- Expected Result: No invalid dates in sample data

-- -------------------------------------------------------------------------------
-- Q10: How do you identify duplicates using window functions?
-- -------------------------------------------------------------------------------
-- A10: Use ROW_NUMBER() OVER PARTITION BY

SELECT 
    customer_id,
    email,
    first_name,
    last_name,
    ROW_NUMBER() OVER (PARTITION BY email ORDER BY customer_id) as row_num
FROM source_customers
WHERE email IS NOT NULL
HAVING row_num > 1;

-- Alternative using subquery:
SELECT * FROM (
    SELECT 
        customer_id,
        email,
        first_name,
        last_name,
        ROW_NUMBER() OVER (PARTITION BY email ORDER BY customer_id) as row_num
    FROM source_customers
    WHERE email IS NOT NULL
) ranked
WHERE row_num > 1;

-- Expected Result: Customer 14 (Mary White) has duplicate email

-- ===============================================================================
-- SECTION 2: DATA RECONCILIATION (5 Questions)
-- ===============================================================================

-- -------------------------------------------------------------------------------
-- Q11: How do you perform full data reconciliation between source and target?
-- -------------------------------------------------------------------------------
-- A11: Use FULL OUTER JOIN to identify all discrepancies

SELECT 
    COALESCE(s.order_id, t.order_id) as order_id,
    CASE 
        WHEN s.order_id IS NULL THEN 'Only in Target'
        WHEN t.order_id IS NULL THEN 'Only in Source'
        WHEN s.total_amount <> t.total_amount THEN 'Amount Mismatch'
        WHEN s.status <> t.status THEN 'Status Mismatch'
        ELSE 'Match'
    END as reconciliation_status,
    s.total_amount as source_amount,
    t.total_amount as target_amount,
    s.status as source_status,
    t.status as target_status
FROM source_orders s
FULL OUTER JOIN target_orders t ON s.order_id = t.order_id
WHERE s.order_id IS NULL 
   OR t.order_id IS NULL 
   OR s.total_amount <> t.total_amount 
   OR s.status <> t.status
ORDER BY order_id;

-- Expected Result: Shows missing orders and amount mismatches

-- -------------------------------------------------------------------------------
-- Q12: How do you validate aggregate sum reconciliation?
-- -------------------------------------------------------------------------------
-- A12: Compare SUM across tables

SELECT 
    'Source Orders' as table_name,
    COUNT(*) as record_count,
    SUM(total_amount) as total_amount,
    AVG(total_amount) as avg_amount,
    MIN(total_amount) as min_amount,
    MAX(total_amount) as max_amount
FROM source_orders
WHERE customer_id IN (SELECT customer_id FROM source_customers)  -- Exclude orphans

UNION ALL

SELECT 
    'Target Orders' as table_name,
    COUNT(*) as record_count,
    SUM(total_amount) as total_amount,
    AVG(total_amount) as avg_amount,
    MIN(total_amount) as min_amount,
    MAX(total_amount) as max_amount
FROM target_orders;

-- Expected Result: Shows differences in totals

-- -------------------------------------------------------------------------------
-- Q13: How do you reconcile data by category/group?
-- -------------------------------------------------------------------------------
-- A13: Group by category and compare

SELECT 
    COALESCE(s.status, t.status) as status,
    COALESCE(s.order_count, 0) as source_count,
    COALESCE(t.order_count, 0) as target_count,
    COALESCE(s.order_count, 0) - COALESCE(t.order_count, 0) as count_diff,
    COALESCE(s.total_amount, 0) as source_total,
    COALESCE(t.total_amount, 0) as target_total,
    COALESCE(s.total_amount, 0) - COALESCE(t.total_amount, 0) as amount_diff
FROM (
    SELECT 
        status,
        COUNT(*) as order_count,
        SUM(total_amount) as total_amount
    FROM source_orders
    GROUP BY status
) s
FULL OUTER JOIN (
    SELECT 
        status,
        COUNT(*) as order_count,
        SUM(total_amount) as total_amount
    FROM target_orders
    GROUP BY status
) t ON s.status = t.status
ORDER BY status;

-- Expected Result: Shows reconciliation by order status

-- -------------------------------------------------------------------------------
-- Q14: How do you use EXCEPT to find mismatched rows?
-- -------------------------------------------------------------------------------
-- A14: Use EXCEPT (or MINUS in Oracle) to find differences

-- Find rows in source but not in target (excluding timestamps)
SELECT customer_id, first_name, last_name, email, phone, city, state
FROM source_customers
WHERE customer_id IN (SELECT customer_id FROM target_customers)

EXCEPT

SELECT customer_id, first_name, last_name, email, phone, city, state
FROM target_customers
WHERE customer_id IN (SELECT customer_id FROM source_customers);

-- Expected Result: Shows transformed data (e.g., Frank's name trimmed)

-- -------------------------------------------------------------------------------
-- Q15: How do you create a reconciliation summary report?
-- -------------------------------------------------------------------------------
-- A15: Combine multiple validation checks

WITH recon_summary AS (
    SELECT 
        'Customers' as entity,
        (SELECT COUNT(*) FROM source_customers) as source_count,
        (SELECT COUNT(*) FROM target_customers) as target_count,
        (SELECT COUNT(*) FROM source_customers s 
         LEFT JOIN target_customers t ON s.customer_id = t.customer_id 
         WHERE t.customer_id IS NULL) as missing_in_target,
        (SELECT COUNT(*) FROM target_customers t 
         LEFT JOIN source_customers s ON t.customer_id = s.customer_id 
         WHERE s.customer_id IS NULL) as extra_in_target
    
    UNION ALL
    
    SELECT 
        'Orders' as entity,
        (SELECT COUNT(*) FROM source_orders) as source_count,
        (SELECT COUNT(*) FROM target_orders) as target_count,
        (SELECT COUNT(*) FROM source_orders s 
         LEFT JOIN target_orders t ON s.order_id = t.order_id 
         WHERE t.order_id IS NULL) as missing_in_target,
        (SELECT COUNT(*) FROM target_orders t 
         LEFT JOIN source_orders s ON t.order_id = s.order_id 
         WHERE s.order_id IS NULL) as extra_in_target
)
SELECT 
    entity,
    source_count,
    target_count,
    source_count - target_count as net_difference,
    missing_in_target,
    extra_in_target,
    CASE 
        WHEN source_count = target_count AND missing_in_target = 0 AND extra_in_target = 0 
        THEN 'PASS' 
        ELSE 'FAIL' 
    END as status
FROM recon_summary;

-- Expected Result: Comprehensive reconciliation report

-- ===============================================================================
-- SECTION 3: DATA TRANSFORMATION VALIDATION (5 Questions)
-- ===============================================================================

-- -------------------------------------------------------------------------------
-- Q16: How do you validate string transformations (TRIM, UPPER, LOWER)?
-- -------------------------------------------------------------------------------
-- A16: Compare source and target after transformation

SELECT 
    s.customer_id,
    s.first_name as source_name,
    t.first_name as target_name,
    TRIM(s.first_name) as expected_name,
    CASE 
        WHEN TRIM(s.first_name) = t.first_name THEN 'Correct'
        ELSE 'Transformation Issue'
    END as validation
FROM source_customers s
JOIN target_customers t ON s.customer_id = t.customer_id
WHERE TRIM(s.first_name) <> s.first_name;  -- Check only where trimming needed

-- Expected Result: Customer 7 shows successful TRIM transformation

-- -------------------------------------------------------------------------------
-- Q17: How do you validate date transformations?
-- -------------------------------------------------------------------------------
-- A17: Compare date components and formats

SELECT 
    order_id,
    order_date,
    DATE_FORMAT(order_date, '%Y-%m-%d') as formatted_date,
    YEAR(order_date) as year,
    MONTH(order_date) as month,
    DAY(order_date) as day,
    DAYNAME(order_date) as day_name,
    QUARTER(order_date) as quarter,
    CASE 
        WHEN YEAR(order_date) < 2020 THEN 'Old Data'
        WHEN YEAR(order_date) > YEAR(CURRENT_DATE) THEN 'Future Date'
        ELSE 'Valid'
    END as date_validation
FROM source_orders
WHERE YEAR(order_date) = 2024
ORDER BY order_date
LIMIT 5;

-- Expected Result: Shows date components for validation

-- -------------------------------------------------------------------------------
-- Q18: How do you validate numeric transformations and rounding?
-- -------------------------------------------------------------------------------
-- A18: Check precision and rounding

SELECT 
    order_id,
    total_amount as original_amount,
    ROUND(total_amount, 2) as rounded_2_decimals,
    ROUND(total_amount, 0) as rounded_no_decimals,
    FLOOR(total_amount) as floor_value,
    CEIL(total_amount) as ceiling_value,
    TRUNCATE(total_amount, 1) as truncated_1_decimal
FROM source_orders
WHERE total_amount - FLOOR(total_amount) > 0  -- Has decimals
LIMIT 5;

-- Expected Result: Shows various rounding transformations

-- -------------------------------------------------------------------------------
-- Q19: How do you validate data type conversions?
-- -------------------------------------------------------------------------------
-- A19: Cast and compare data types

SELECT 
    customer_id,
    phone,
    CAST(REPLACE(REPLACE(phone, '-', ''), ' ', '') AS CHAR) as phone_numeric_only,
    LENGTH(phone) as original_length,
    LENGTH(REPLACE(REPLACE(phone, '-', ''), ' ', '')) as numeric_length,
    CASE 
        WHEN phone REGEXP '^[0-9]{3}-[0-9]{4}$' THEN 'Valid Format'
        WHEN phone IS NULL THEN 'NULL'
        ELSE 'Invalid Format'
    END as format_validation
FROM source_customers
LIMIT 5;

-- Expected Result: Shows phone number transformations

-- -------------------------------------------------------------------------------
-- Q20: How do you validate concatenation and splitting?
-- -------------------------------------------------------------------------------
-- A20: Use CONCAT and SUBSTRING functions

SELECT 
    customer_id,
    first_name,
    last_name,
    CONCAT(first_name, ' ', last_name) as full_name,
    CONCAT(last_name, ', ', first_name) as reversed_name,
    SUBSTRING(email, 1, LOCATE('@', email) - 1) as email_username,
    SUBSTRING(email, LOCATE('@', email) + 1) as email_domain
FROM source_customers
WHERE email IS NOT NULL
LIMIT 5;

-- Expected Result: Shows concatenation and string splitting

-- ===============================================================================
-- SECTION 4: INCREMENTAL LOAD VALIDATION (5 Questions)
-- ===============================================================================

-- -------------------------------------------------------------------------------
-- Q21: How do you identify new, updated, and unchanged records?
-- -------------------------------------------------------------------------------
-- A21: Compare using timestamps and FULL OUTER JOIN

SELECT 
    COALESCE(s.customer_id, t.customer_id) as customer_id,
    CASE 
        WHEN t.customer_id IS NULL THEN 'NEW'
        WHEN s.customer_id IS NULL THEN 'DELETED'
        WHEN s.last_modified > t.load_timestamp THEN 'UPDATED'
        ELSE 'UNCHANGED'
    END as record_status,
    s.last_modified as source_modified,
    t.load_timestamp as target_loaded
FROM source_customers s
FULL OUTER JOIN target_customers t ON s.customer_id = t.customer_id
ORDER BY customer_id;

-- Expected Result: Shows status of each record

-- -------------------------------------------------------------------------------
-- Q22: How do you count records by change type?
-- -------------------------------------------------------------------------------
-- A22: Group by status

WITH change_detection AS (
    SELECT 
        CASE 
            WHEN t.customer_id IS NULL THEN 'NEW'
            WHEN s.customer_id IS NULL THEN 'DELETED'
            WHEN s.last_modified > t.load_timestamp THEN 'UPDATED'
            ELSE 'UNCHANGED'
        END as change_type
    FROM source_customers s
    FULL OUTER JOIN target_customers t ON s.customer_id = t.customer_id
)
SELECT 
    change_type,
    COUNT(*) as record_count,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2) as percentage
FROM change_detection
GROUP BY change_type
ORDER BY record_count DESC;

-- Expected Result: Distribution of change types

-- -------------------------------------------------------------------------------
-- Q23: How do you implement watermark-based incremental load?
-- -------------------------------------------------------------------------------
-- A23: Filter based on last successful load timestamp

-- Get last successful load time
SELECT MAX(end_time) as last_load_time
FROM etl_job_log
WHERE job_name = 'Daily_Customer_Load'
  AND status = 'SUCCESS';

-- Get incremental records
SELECT 
    customer_id,
    first_name,
    last_name,
    email,
    last_modified
FROM source_customers
WHERE last_modified > (
    SELECT MAX(end_time)
    FROM etl_job_log
    WHERE job_name = 'Daily_Customer_Load'
      AND status = 'SUCCESS'
);

-- Expected Result: Records modified after last load

-- -------------------------------------------------------------------------------
-- Q24: How do you validate incremental load completeness?
-- -------------------------------------------------------------------------------
-- A24: Check all records in time window are loaded

SELECT 
    DATE(last_modified) as modification_date,
    COUNT(*) as source_records,
    COUNT(CASE WHEN customer_id IN (
        SELECT customer_id FROM target_customers
    ) THEN 1 END) as loaded_records,
    COUNT(*) - COUNT(CASE WHEN customer_id IN (
        SELECT customer_id FROM target_customers
    ) THEN 1 END) as missing_records
FROM source_customers
WHERE last_modified >= '2024-01-15'
GROUP BY DATE(last_modified)
ORDER BY modification_date;

-- Expected Result: Shows load completeness by date

-- -------------------------------------------------------------------------------
-- Q25: How do you detect late-arriving data?
-- -------------------------------------------------------------------------------
-- A25: Compare record dates with load dates

SELECT 
    o.order_id,
    o.order_date,
    o.last_modified,
    t.load_timestamp,
    DATEDIFF(t.load_timestamp, o.order_date) as days_to_load,
    CASE 
        WHEN DATEDIFF(t.load_timestamp, o.order_date) > 7 THEN 'Late Arrival'
        WHEN DATEDIFF(t.load_timestamp, o.order_date) > 3 THEN 'Delayed'
        ELSE 'On Time'
    END as timeliness_status
FROM source_orders o
JOIN target_orders t ON o.order_id = t.order_id
WHERE t.load_timestamp IS NOT NULL
ORDER BY days_to_load DESC
LIMIT 10;

-- Expected Result: Shows data arrival patterns

-- ===============================================================================
-- SECTION 5: SLOWLY CHANGING DIMENSIONS (SCD) (5 Questions)
-- ===============================================================================

-- -------------------------------------------------------------------------------
-- Q26: How do you query current records in SCD Type 2?
-- -------------------------------------------------------------------------------
-- A26: Filter by is_current flag

SELECT 
    customer_id,
    name,
    email,
    city,
    effective_start_date,
    effective_end_date
FROM dim_customer
WHERE is_current = TRUE
ORDER BY customer_id;

-- Expected Result: Only current versions of customers

-- -------------------------------------------------------------------------------
-- Q27: How do you track historical changes for a specific customer?
-- -------------------------------------------------------------------------------
-- A27: Query all versions ordered by date

SELECT 
    surrogate_key,
    customer_id,
    name,
    email,
    city,
    effective_start_date,
    effective_end_date,
    DATEDIFF(effective_end_date, effective_start_date) as days_active,
    CASE WHEN is_current THEN 'CURRENT' ELSE 'HISTORICAL' END as status
FROM dim_customer
WHERE customer_id = 1001
ORDER BY effective_start_date;

-- Expected Result: Shows customer 1001's history (email and city changes)

-- -------------------------------------------------------------------------------
-- Q28: How do you validate SCD Type 2 integrity?
-- -------------------------------------------------------------------------------
-- A28: Check for multiple current records and date overlaps

-- Check 1: Ensure each customer has exactly one current record
SELECT 
    customer_id,
    COUNT(*) as total_versions,
    SUM(CASE WHEN is_current THEN 1 ELSE 0 END) as current_count,
    CASE 
        WHEN SUM(CASE WHEN is_current THEN 1 ELSE 0 END) = 1 THEN 'VALID'
        WHEN SUM(CASE WHEN is_current THEN 1 ELSE 0 END) = 0 THEN 'NO CURRENT RECORD'
        ELSE 'MULTIPLE CURRENT RECORDS'
    END as validation_status
FROM dim_customer
GROUP BY customer_id
HAVING SUM(CASE WHEN is_current THEN 1 ELSE 0 END) <> 1;

-- Check 2: Ensure no date gaps or overlaps
SELECT 
    d1.customer_id,
    d1.surrogate_key,
    d1.effective_end_date as period1_end,
    d2.effective_start_date as period2_start,
    DATEDIFF(d2.effective_start_date, d1.effective_end_date) as gap_days
FROM dim_customer d1
JOIN dim_customer d2 ON d1.customer_id = d2.customer_id
WHERE d1.effective_end_date < d2.effective_start_date
  AND d1.effective_end_date <> '9999-12-31'
  AND DATEDIFF(d2.effective_start_date, d1.effective_end_date) <> 1;

-- Expected Result: Should find no integrity issues

-- -------------------------------------------------------------------------------
-- Q29: How do you implement SCD Type 1 (overwrite) validation?
-- -------------------------------------------------------------------------------
-- A29: Compare before and after snapshots

-- Simulate SCD Type 1 - Check what changed
SELECT 
    s.customer_id,
    s.email as source_email,
    t.email as target_email,
    CASE 
        WHEN s.email <> t.email THEN 'Email Changed (Type 1)'
        ELSE 'No Change'
    END as change_type
FROM source_customers s
JOIN target_customers t ON s.customer_id = t.customer_id
WHERE s.email <> t.email OR s.city <> t.city;

-- Expected Result: Shows Type 1 changes (overwritten values)

-- -------------------------------------------------------------------------------
-- Q30: How do you find customers with most frequent changes?
-- -------------------------------------------------------------------------------
-- A30: Count versions per customer

SELECT 
    customer_id,
    COUNT(*) as version_count,
    MIN(effective_start_date) as first_seen,
    MAX(CASE WHEN is_current THEN effective_start_date END) as last_changed,
    COUNT(*) - 1 as number_of_changes
FROM dim_customer
GROUP BY customer_id
ORDER BY version_count DESC;

-- Expected Result: Customer 1001 has most changes (3 versions)

-- ===============================================================================
-- SECTION 6: PERFORMANCE & OPTIMIZATION (5 Questions)
-- ===============================================================================

-- -------------------------------------------------------------------------------
-- Q31: How do you analyze query performance with row counts?
-- -------------------------------------------------------------------------------
-- A31: Get table statistics

SELECT 
    'source_customers' as table_name,
    COUNT(*) as row_count,
    COUNT(DISTINCT customer_id) as unique_keys,
    COUNT(*) - COUNT(email) as null_emails
FROM source_customers

UNION ALL

SELECT 
    'source_orders' as table_name,
    COUNT(*) as row_count,
    COUNT(DISTINCT order_id) as unique_keys,
    COUNT(*) - COUNT(customer_id) as null_customers
FROM source_orders

UNION ALL

SELECT 
    'source_order_items' as table_name,
    COUNT(*) as row_count,
    COUNT(DISTINCT item_id) as unique_keys,
    COUNT(*) - COUNT(order_id) as null_orders
FROM source_order_items;

-- Expected Result: Table statistics for analysis

-- -------------------------------------------------------------------------------
-- Q32: How do you identify most expensive queries using aggregations?
-- -------------------------------------------------------------------------------
-- A32: Analyze join cardinality

-- Check join explosion (many-to-many)
SELECT 
    COUNT(DISTINCT o.order_id) as distinct_orders,
    COUNT(DISTINCT oi.item_id) as distinct_items,
    COUNT(*) as joined_rows,
    ROUND(COUNT(*) / COUNT(DISTINCT o.order_id), 2) as avg_items_per_order
FROM source_orders o
JOIN source_order_items oi ON o.order_id = oi.order_id;

-- Expected Result: Shows join cardinality

-- -------------------------------------------------------------------------------
-- Q33: How do you optimize queries with EXISTS vs IN?
-- -------------------------------------------------------------------------------
-- A33: Use EXISTS for better performance on large datasets

-- Using IN (can be slow for large datasets)
SELECT customer_id, first_name, last_name
FROM source_customers
WHERE customer_id IN (
    SELECT DISTINCT customer_id 
    FROM source_orders
);

-- Using EXISTS (typically faster)
SELECT c.customer_id, c.first_name, c.last_name
FROM source_customers c
WHERE EXISTS (
    SELECT 1 
    FROM source_orders o 
    WHERE o.customer_id = c.customer_id
);

-- Expected Result: Same results, but EXISTS is more efficient

-- -------------------------------------------------------------------------------
-- Q34: How do you identify tables that need indexing?
-- -------------------------------------------------------------------------------
-- A34: Find frequently queried columns without indexes

-- Simulate index check - find foreign keys
SELECT 
    'source_orders' as table_name,
    'customer_id' as column_name,
    COUNT(DISTINCT customer_id) as distinct_values,
    COUNT(*) as total_rows,
    ROUND(COUNT(DISTINCT customer_id) / COUNT(*) * 100, 2) as cardinality_pct,
    CASE 
        WHEN COUNT(DISTINCT customer_id) / COUNT(*) > 0.95 THEN 'High Cardinality - Good for Index'
        WHEN COUNT(DISTINCT customer_id) / COUNT(*) > 0.05 THEN 'Medium Cardinality'
        ELSE 'Low Cardinality - Not Ideal for Index'
    END as index_recommendation
FROM source_orders;

-- Expected Result: Index recommendations

-- -------------------------------------------------------------------------------
-- Q35: How do you rewrite subqueries as JOIN for better performance?
-- -------------------------------------------------------------------------------
-- A35: Convert correlated subqueries to JOINs

-- Slow: Correlated subquery
SELECT 
    c.customer_id,
    c.first_name,
    c.last_name,
    (SELECT COUNT(*) FROM source_orders o WHERE o.customer_id = c.customer_id) as order_count
FROM source_customers c;

-- Fast: Using JOIN
SELECT 
    c.customer_id,
    c.first_name,
    c.last_name,
    COALESCE(o.order_count, 0) as order_count
FROM source_customers c
LEFT JOIN (
    SELECT customer_id, COUNT(*) as order_count
    FROM source_orders
    GROUP BY customer_id
) o ON c.customer_id = o.customer_id;

-- Expected Result: Same results, JOIN is faster

-- ===============================================================================
-- SECTION 7: DATA PROFILING (5 Questions)
-- ===============================================================================

-- -------------------------------------------------------------------------------
-- Q36: How do you create a comprehensive data profile?
-- -------------------------------------------------------------------------------
-- A36: Combine multiple statistics

SELECT 
    'source_orders' as table_name,
    COUNT(*) as total_records,
    COUNT(DISTINCT customer_id) as unique_customers,
    COUNT(DISTINCT order_date) as unique_dates,
    MIN(order_date) as earliest_date,
    MAX(order_date) as latest_date,
    ROUND(AVG(total_amount), 2) as avg_amount,
    ROUND(MIN(total_amount), 2) as min_amount,
    ROUND(MAX(total_amount), 2) as max_amount,
    ROUND(STDDEV(total_amount), 2) as stddev_amount,
    ROUND(VARIANCE(total_amount), 2) as variance_amount
FROM source_orders;

-- Expected Result: Complete statistical profile

-- -------------------------------------------------------------------------------
-- Q37: How do you analyze value distribution?
-- -------------------------------------------------------------------------------
-- A37: Create frequency distribution

SELECT 
    status,
    COUNT(*) as frequency,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2) as percentage,
    SUM(total_amount) as total_value,
    ROUND(AVG(total_amount), 2) as avg_value
FROM source_orders
GROUP BY status
ORDER BY frequency DESC;

-- Expected Result: Distribution by status

-- -------------------------------------------------------------------------------
-- Q38: How do you identify outliers using IQR method?
-- -------------------------------------------------------------------------------
-- A38: Calculate quartiles and identify outliers

WITH quartiles AS (
    SELECT 
        PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY amount) as Q1,
        PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY amount) as Q2_median,
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
    FROM quartiles
)
SELECT 
    t.transaction_id,
    t.amount,
    o.lower_bound,
    o.upper_bound,
    CASE 
        WHEN t.amount < o.lower_bound THEN 'Lower Outlier'
        WHEN t.amount > o.upper_bound THEN 'Upper Outlier'
    END as outlier_type
FROM transactions t, outlier_bounds o
WHERE t.amount < o.lower_bound OR t.amount > o.upper_bound
ORDER BY t.amount;

-- Expected Result: Transactions 5, 9, and 12 are outliers

-- -------------------------------------------------------------------------------
-- Q39: How do you create data quality score card?
-- -------------------------------------------------------------------------------
-- A39: Calculate multiple quality metrics

SELECT 
    'source_customers' as table_name,
    COUNT(*) as total_records,
    ROUND(100.0 * COUNT(email) / COUNT(*), 2) as email_completeness,
    ROUND(100.0 * COUNT(phone) / COUNT(*), 2) as phone_completeness,
    ROUND(100.0 * (COUNT(*) - COUNT(DISTINCT email)) / COUNT(*), 2) as email_duplicate_rate,
    ROUND(100.0 * SUM(CASE WHEN email LIKE '%@%.%' THEN 1 ELSE 0 END) / NULLIF(COUNT(email), 0), 2) as email_validity_rate,
    CASE 
        WHEN COUNT(email) / COUNT(*) >= 0.95 AND 
             (COUNT(*) - COUNT(DISTINCT email)) / COUNT(*) <= 0.05 
        THEN 'EXCELLENT'
        WHEN COUNT(email) / COUNT(*) >= 0.90 THEN 'GOOD'
        WHEN COUNT(email) / COUNT(*) >= 0.80 THEN 'FAIR'
        ELSE 'POOR'
    END as overall_quality_grade
FROM source_customers;

-- Expected Result: Overall data quality assessment

-- -------------------------------------------------------------------------------
-- Q40: How do you analyze column correlations?
-- -------------------------------------------------------------------------------
-- A40: Check relationships between columns

SELECT 
    state,
    COUNT(*) as customer_count,
    COUNT(DISTINCT city) as unique_cities,
    ROUND(AVG(order_count), 2) as avg_orders_per_customer,
    ROUND(SUM(total_orders), 2) as total_state_revenue
FROM source_customers c
LEFT JOIN (
    SELECT 
        customer_id,
        COUNT(*) as order_count,
        SUM(total_amount) as total_orders
    FROM source_orders
    GROUP BY customer_id
) o ON c.customer_id = o.customer_id
GROUP BY state
ORDER BY customer_count DESC;

-- Expected Result: Geographic distribution analysis

-- ===============================================================================
-- SECTION 8: ERROR HANDLING & MONITORING (5 Questions)
-- ===============================================================================

-- -------------------------------------------------------------------------------
-- Q41: How do you monitor ETL job execution history?
-- -------------------------------------------------------------------------------
-- A41: Query job log table

SELECT 
    job_name,
    status,
    start_time,
    end_time,
    TIMESTAMPDIFF(MINUTE, start_time, end_time) as duration_minutes,
    records_processed,
    records_failed,
    ROUND(100.0 * records_failed / NULLIF(records_processed + records_failed, 0), 2) as failure_rate,
    error_message
FROM etl_job_log
ORDER BY start_time DESC;

-- Expected Result: Complete job execution history

-- -------------------------------------------------------------------------------
-- Q42: How do you calculate ETL job success rate?
-- -------------------------------------------------------------------------------
-- A42: Aggregate by job name and status

SELECT 
    job_name,
    COUNT(*) as total_runs,
    SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) as successful_runs,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed_runs,
    SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END) as running_jobs,
    ROUND(100.0 * SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) / COUNT(*), 2) as success_rate_pct,
    AVG(TIMESTAMPDIFF(MINUTE, start_time, end_time)) as avg_duration_minutes,
    SUM(records_processed) as total_records_processed
FROM etl_job_log
WHERE end_time IS NOT NULL
GROUP BY job_name
ORDER BY success_rate_pct DESC;

-- Expected Result: Job performance metrics

-- -------------------------------------------------------------------------------
-- Q43: How do you identify long-running jobs?
-- -------------------------------------------------------------------------------
-- A43: Analyze job duration trends

SELECT 
    job_name,
    start_time,
    end_time,
    TIMESTAMPDIFF(MINUTE, start_time, end_time) as duration_minutes,
    records_processed,
    ROUND(records_processed / NULLIF(TIMESTAMPDIFF(MINUTE, start_time, end_time), 0), 2) as records_per_minute,
    CASE 
        WHEN TIMESTAMPDIFF(MINUTE, start_time, end_time) > 60 THEN 'Slow'
        WHEN TIMESTAMPDIFF(MINUTE, start_time, end_time) > 30 THEN 'Medium'
        ELSE 'Fast'
    END as performance_category
FROM etl_job_log
WHERE end_time IS NOT NULL
  AND status = 'SUCCESS'
ORDER BY duration_minutes DESC;

-- Expected Result: Jobs sorted by duration

-- -------------------------------------------------------------------------------
-- Q44: How do you detect data quality threshold breaches?
-- -------------------------------------------------------------------------------
-- A44: Set thresholds and check violations

WITH quality_checks AS (
    SELECT 
        'NULL Email Check' as check_name,
        COUNT(*) - COUNT(email) as actual_value,
        COUNT(*) * 0.10 as threshold_value,
        CASE 
            WHEN (COUNT(*) - COUNT(email)) > COUNT(*) * 0.10 THEN 'FAIL'
            ELSE 'PASS'
        END as status
    FROM source_customers
    
    UNION ALL
    
    SELECT 
        'Duplicate Email Check' as check_name,
        COUNT(*) - COUNT(DISTINCT email) as actual_value,
        COUNT(*) * 0.05 as threshold_value,
        CASE 
            WHEN (COUNT(*) - COUNT(DISTINCT email)) > COUNT(*) * 0.05 THEN 'FAIL'
            ELSE 'PASS'
        END as status
    FROM source_customers
    WHERE email IS NOT NULL
    
    UNION ALL
    
    SELECT 
        'Orphan Order Check' as check_name,
        COUNT(*) as actual_value,
        0 as threshold_value,
        CASE 
            WHEN COUNT(*) > 0 THEN 'FAIL'
            ELSE 'PASS'
        END as status
    FROM source_orders o
    LEFT JOIN source_customers c ON o.customer_id = c.customer_id
    WHERE c.customer_id IS NULL
)
SELECT 
    check_name,
    actual_value,
    threshold_value,
    status,
    CURRENT_TIMESTAMP as check_timestamp
FROM quality_checks;

-- Expected Result: Quality threshold checks

-- -------------------------------------------------------------------------------
-- Q45: How do you track error patterns over time?
-- -------------------------------------------------------------------------------
-- A45: Analyze error types and frequency

SELECT 
    DATE(start_time) as execution_date,
    COUNT(*) as total_jobs,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed_jobs,
    COUNT(DISTINCT CASE WHEN status = 'FAILED' THEN job_name END) as distinct_failed_jobs,
    SUM(records_failed) as total_failed_records,
    GROUP_CONCAT(DISTINCT error_message SEPARATOR '; ') as error_messages
FROM etl_job_log
GROUP BY DATE(start_time)
ORDER BY execution_date DESC;

-- Expected Result: Daily error summary

-- ===============================================================================
-- SECTION 9: COMPLEX VALIDATIONS (5 Questions)
-- ===============================================================================

-- -------------------------------------------------------------------------------
-- Q46: How do you validate business rules (order total = sum of items)?
-- -------------------------------------------------------------------------------
-- A46: Compare calculated vs stored values

SELECT 
    o.order_id,
    o.total_amount as stored_total,
    COALESCE(SUM(oi.quantity * oi.unit_price), 0) as calculated_total,
    ROUND(ABS(o.total_amount - COALESCE(SUM(oi.quantity * oi.unit_price), 0)), 2) as difference,
    CASE 
        WHEN ABS(o.total_amount - COALESCE(SUM(oi.quantity * oi.unit_price), 0)) < 0.01 THEN 'PASS'
        WHEN ABS(o.total_amount - COALESCE(SUM(oi.quantity * oi.unit_price), 0)) < 1.00 THEN 'WARNING'
        ELSE 'FAIL'
    END as validation_status
FROM source_orders o
LEFT JOIN source_order_items oi ON o.order_id = oi.order_id
GROUP BY o.order_id, o.total_amount
HAVING ABS(o.total_amount - COALESCE(SUM(oi.quantity * oi.unit_price), 0)) > 0.01
ORDER BY difference DESC;

-- Expected Result: Orders with total mismatches

-- -------------------------------------------------------------------------------
-- Q47: How do you validate cross-table consistency?
-- -------------------------------------------------------------------------------
-- A47: Check related records exist

SELECT 
    'Orders without Items' as check_type,
    COUNT(*) as violation_count
FROM source_orders o
LEFT JOIN source_order_items oi ON o.order_id = oi.order_id
WHERE oi.order_id IS NULL

UNION ALL

SELECT 
    'Orders without Customers' as check_type,
    COUNT(*) as violation_count
FROM source_orders o
LEFT JOIN source_customers c ON o.customer_id = c.customer_id
WHERE c.customer_id IS NULL

UNION ALL

SELECT 
    'Completed Orders without Payment' as check_type,
    COUNT(*) as violation_count
FROM source_orders o
LEFT JOIN payments p ON o.order_id = p.order_id
WHERE o.status = 'Completed'
  AND p.payment_id IS NULL;

-- Expected Result: Cross-table consistency issues

-- -------------------------------------------------------------------------------
-- Q48: How do you validate data consistency across time periods?
-- -------------------------------------------------------------------------------
-- A48: Compare metrics across dates

SELECT 
    DATE(order_date) as order_date,
    COUNT(*) as daily_orders,
    SUM(total_amount) as daily_revenue,
    AVG(total_amount) as avg_order_value,
    LAG(COUNT(*)) OVER (ORDER BY DATE(order_date)) as previous_day_orders,
    LAG(SUM(total_amount)) OVER (ORDER BY DATE(order_date)) as previous_day_revenue,
    COUNT(*) - LAG(COUNT(*)) OVER (ORDER BY DATE(order_date)) as order_change,
    ROUND(100.0 * (COUNT(*) - LAG(COUNT(*)) OVER (ORDER BY DATE(order_date))) / 
          NULLIF(LAG(COUNT(*)) OVER (ORDER BY DATE(order_date)), 0), 2) as pct_change
FROM source_orders
GROUP BY DATE(order_date)
ORDER BY order_date;

-- Expected Result: Day-over-day trend analysis

-- -------------------------------------------------------------------------------
-- Q49: How do you validate hierarchical data integrity?
-- -------------------------------------------------------------------------------
-- A49: Check parent-child relationships

WITH order_hierarchy AS (
    SELECT 
        c.customer_id,
        c.first_name || ' ' || c.last_name as customer_name,
        COUNT(DISTINCT o.order_id) as order_count,
        COUNT(DISTINCT oi.item_id) as item_count,
        SUM(o.total_amount) as total_spent
    FROM source_customers c
    LEFT JOIN source_orders o ON c.customer_id = o.customer_id
    LEFT JOIN source_order_items oi ON o.order_id = oi.order_id
    GROUP BY c.customer_id, customer_name
)
SELECT 
    customer_id,
    customer_name,
    order_count,
    item_count,
    ROUND(total_spent, 2) as total_spent,
    ROUND(item_count / NULLIF(order_count, 0), 2) as avg_items_per_order,
    CASE 
        WHEN order_count = 0 THEN 'No Orders'
        WHEN item_count = 0 THEN 'Orders Without Items'
        WHEN item_count / order_count > 10 THEN 'High Item Count'
        ELSE 'Normal'
    END as hierarchy_status
FROM order_hierarchy
ORDER BY total_spent DESC NULLS LAST;

-- Expected Result: Customer hierarchy analysis

-- -------------------------------------------------------------------------------
-- Q50: How do you create a master validation report?
-- -------------------------------------------------------------------------------
-- A50: Combine all validation checks

WITH validation_suite AS (
    -- Check 1: Record Count
    SELECT 
        'Record Count Validation' as category,
        'Customer Count Match' as check_name,
        (SELECT COUNT(*) FROM source_customers) as expected_value,
        (SELECT COUNT(*) FROM target_customers) as actual_value,
        CASE 
            WHEN (SELECT COUNT(*) FROM source_customers) = (SELECT COUNT(*) FROM target_customers) 
            THEN 'PASS' 
            ELSE 'FAIL' 
        END as status
    
    UNION ALL
    
    -- Check 2: NULL Validation
    SELECT 
        'Data Quality' as category,
        'NULL Email Percentage' as check_name,
        10.0 as expected_value,
        ROUND(100.0 * (COUNT(*) - COUNT(email)) / COUNT(*), 2) as actual_value,
        CASE 
            WHEN 100.0 * (COUNT(*) - COUNT(email)) / COUNT(*) <= 10.0 
            THEN 'PASS' 
            ELSE 'FAIL' 
        END as status
    FROM source_customers
    
    UNION ALL
    
    -- Check 3: Duplicate Check
    SELECT 
        'Data Quality' as category,
        'Duplicate Email Count' as check_name,
        0 as expected_value,
        COUNT(*) - COUNT(DISTINCT email) as actual_value,
        CASE 
            WHEN COUNT(*) - COUNT(DISTINCT email) = 0 
            THEN 'PASS' 
            ELSE 'FAIL' 
        END as status
    FROM source_customers
    WHERE email IS NOT NULL
    
    UNION ALL
    
    -- Check 4: Orphan Records
    SELECT 
        'Referential Integrity' as category,
        'Orphan Orders' as check_name,
        0 as expected_value,
        COUNT(*) as actual_value,
        CASE 
            WHEN COUNT(*) = 0 
            THEN 'PASS' 
            ELSE 'FAIL' 
        END as status
    FROM source_orders o
    LEFT JOIN source_customers c ON o.customer_id = c.customer_id
    WHERE c.customer_id IS NULL
    
    UNION ALL
    
    -- Check 5: Amount Reconciliation
    SELECT 
        'Amount Validation' as category,
        'Total Amount Match' as check_name,
        (SELECT SUM(total_amount) FROM source_orders WHERE order_id IN (SELECT order_id FROM target_orders)) as expected_value,
        (SELECT SUM(total_amount) FROM target_orders) as actual_value,
        CASE 
            WHEN ABS((SELECT SUM(total_amount) FROM source_orders WHERE order_id IN (SELECT order_id FROM target_orders)) - 
                     (SELECT SUM(total_amount) FROM target_orders)) < 1.0 
            THEN 'PASS' 
            ELSE 'FAIL' 
        END as status
)
SELECT 
    category,
    check_name,
    expected_value,
    actual_value,
    ROUND(ABS(expected_value - actual_value), 2) as variance,
    status,
    CURRENT_TIMESTAMP as validation_timestamp
FROM validation_suite
ORDER BY 
    CASE category
        WHEN 'Record Count Validation' THEN 1
        WHEN 'Data Quality' THEN 2
        WHEN 'Referential Integrity' THEN 3
        WHEN 'Amount Validation' THEN 4
        ELSE 5
    END,
    check_name;

-- Expected Result: Complete master validation report

-- ===============================================================================
-- END OF 50 ETL QA SQL INTERVIEW QUESTIONS & ANSWERS
-- ===============================================================================

/*
===============================================================================
SUMMARY: YOU NOW HAVE 50 COMPLETE QUESTIONS COVERING:
===============================================================================

SECTION 1: Data Quality & Validation (Q1-Q10)
  ✓ Duplicate detection
  ✓ NULL value validation
  ✓ Record count comparison
  ✓ Orphan records
  ✓ Data cleansing validation
  ✓ Missing/extra records
  ✓ Format validation
  ✓ Date validation
  ✓ Window functions for duplicates

SECTION 2: Data Reconciliation (Q11-Q15)
  ✓ Full reconciliation
  ✓ Aggregate validation
  ✓ Category-wise reconciliation
  ✓ EXCEPT for mismatches
  ✓ Reconciliation reports

SECTION 3: Data Transformation (Q16-Q20)
  ✓ String transformations
  ✓ Date transformations
  ✓ Numeric transformations
  ✓ Data type conversions
  ✓ Concatenation and splitting

SECTION 4: Incremental Loads (Q21-Q25)
  ✓ Change detection
  ✓ Change type counting
  ✓ Watermark-based loading
  ✓ Load completeness
  ✓ Late-arriving data

SECTION 5: Slowly Changing Dimensions (Q26-Q30)
  ✓ Current records query
  ✓ Historical tracking
  ✓ SCD integrity validation
  ✓ Type 1 validation
  ✓ Change frequency analysis

SECTION 6: Performance & Optimization (Q31-Q35)
  ✓ Performance analysis
  ✓ Join cardinality
  ✓ EXISTS vs IN
  ✓ Index recommendations
  ✓ Query optimization

SECTION 7: Data Profiling (Q36-Q40)
  ✓ Comprehensive profiling
  ✓ Value distribution
  ✓ Outlier detection
  ✓ Quality scorecards
  ✓ Correlation analysis

SECTION 8: Error Handling (Q41-Q45)
  ✓ Job monitoring
  ✓ Success rate calculation
  ✓ Long-running jobs
  ✓ Threshold breaches
  ✓ Error patterns

SECTION 9: Complex Validations (Q46-Q50)
  ✓ Business rules validation
  ✓ Cross-table consistency
  ✓ Time period consistency
  ✓ Hierarchical data
  ✓ Master validation report

===============================================================================
INTERVIEW PREPARATION TIPS:
===============================================================================

1. UNDERSTAND THE DATA
   - Review the sample data structure
   - Understand intentional data quality issues
   - Know the relationships between tables

2. PRACTICE EXPLAINING
   - Don't just write the query
   - Explain your approach
   - Discuss edge cases

3. KNOW THE RESULTS
   - Each query has expected results documented
   - Understand what makes data "bad"
   - Know how to fix issues

4. PERFORMANCE MATTERS
   - Discuss index usage
   - Explain JOIN choices
   - Talk about scalability

5. BUSINESS CONTEXT
   - Understand why validation matters
   - Know data quality impact
   - Discuss real-world scenarios

===============================================================================
HOW TO USE THIS FILE:
===============================================================================

1. SET UP DATABASE
   - Run Section 0 to create tables and insert sample data
   - Verify data loaded correctly

2. PRACTICE QUERIES
   - Try each question without looking at the answer
   - Run the provided solution
   - Verify expected results

3. MODIFY AND EXPERIMENT
   - Change filter conditions
   - Add new validation checks
   - Create your own scenarios

4. INTERVIEW PREPARATION
   - Pick 10-15 key questions
   - Practice explaining your approach
   - Time yourself

5. COMMON MISTAKES TO AVOID
   - Not handling NULLs
   - Forgetting about duplicates
   - Ignoring performance
   - Not validating edge cases
   - Missing business context

===============================================================================
GOOD LUCK WITH YOUR ETL QA INTERVIEW!
===============================================================================
*/
