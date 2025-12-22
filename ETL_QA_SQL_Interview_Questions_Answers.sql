/*
================================================================================
ETL & QA SQL INTERVIEW QUESTIONS WITH ANSWERS
================================================================================
This file contains comprehensive SQL interview questions commonly asked for
ETL Developer and QA positions, along with sample data and working queries.

Topics Covered:
1. Data Quality Checks
2. Duplicate Detection and Removal
3. Data Validation
4. Data Transformation
5. Slowly Changing Dimensions (SCD)
6. Incremental Load Patterns
7. Data Reconciliation
8. Performance Optimization
9. Complex Joins and Aggregations
10. Window Functions in ETL

Database: MySQL/PostgreSQL compatible (with notes for differences)
================================================================================
*/

-- ============================================================================
-- SECTION 1: DATA QUALITY CHECKS
-- ============================================================================

-- Question 1: Check for NULL values in critical columns
-- Scenario: You need to validate that all customer records have essential fields

DROP TABLE IF EXISTS customers;
CREATE TABLE customers (
    customer_id INT PRIMARY KEY,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20),
    registration_date DATE,
    country VARCHAR(50)
);

-- Sample Data
INSERT INTO customers VALUES
(1, 'John', 'Doe', 'john.doe@email.com', '555-0101', '2024-01-15', 'USA'),
(2, 'Jane', NULL, 'jane.smith@email.com', '555-0102', '2024-01-16', 'USA'),
(3, 'Mike', 'Johnson', NULL, '555-0103', '2024-01-17', 'Canada'),
(4, 'Sarah', 'Williams', 'sarah.w@email.com', NULL, '2024-01-18', 'UK'),
(5, 'Tom', 'Brown', 'tom.brown@email.com', '555-0105', NULL, 'USA'),
(6, NULL, 'Davis', 'davis@email.com', '555-0106', '2024-01-20', NULL);

-- Answer: QA Query to check for NULL values
SELECT 
    'customers' as table_name,
    'first_name' as column_name,
    COUNT(*) as null_count
FROM customers 
WHERE first_name IS NULL
UNION ALL
SELECT 'customers', 'last_name', COUNT(*) FROM customers WHERE last_name IS NULL
UNION ALL
SELECT 'customers', 'email', COUNT(*) FROM customers WHERE email IS NULL
UNION ALL
SELECT 'customers', 'phone', COUNT(*) FROM customers WHERE phone IS NULL
UNION ALL
SELECT 'customers', 'registration_date', COUNT(*) FROM customers WHERE registration_date IS NULL
UNION ALL
SELECT 'customers', 'country', COUNT(*) FROM customers WHERE country IS NULL;

-- Alternative: More dynamic approach
SELECT 
    customer_id,
    CASE WHEN first_name IS NULL THEN 'first_name, ' ELSE '' END ||
    CASE WHEN last_name IS NULL THEN 'last_name, ' ELSE '' END ||
    CASE WHEN email IS NULL THEN 'email, ' ELSE '' END ||
    CASE WHEN phone IS NULL THEN 'phone, ' ELSE '' END ||
    CASE WHEN registration_date IS NULL THEN 'registration_date, ' ELSE '' END ||
    CASE WHEN country IS NULL THEN 'country' ELSE '' END as missing_fields
FROM customers
WHERE first_name IS NULL 
   OR last_name IS NULL 
   OR email IS NULL 
   OR phone IS NULL
   OR registration_date IS NULL
   OR country IS NULL;


-- ============================================================================
-- SECTION 2: DUPLICATE DETECTION AND REMOVAL
-- ============================================================================

-- Question 2: Find and remove duplicate records
-- Scenario: Source system sent duplicate customer records

DROP TABLE IF EXISTS customer_staging;
CREATE TABLE customer_staging (
    id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT,
    name VARCHAR(100),
    email VARCHAR(100),
    load_date DATE
);

-- Sample Data with duplicates
INSERT INTO customer_staging (customer_id, name, email, load_date) VALUES
(101, 'Alice Green', 'alice@email.com', '2024-01-15'),
(101, 'Alice Green', 'alice@email.com', '2024-01-16'),
(102, 'Bob Martin', 'bob@email.com', '2024-01-15'),
(103, 'Charlie White', 'charlie@email.com', '2024-01-15'),
(103, 'Charlie White', 'charlie@email.com', '2024-01-15'),
(103, 'Charlie White', 'charlie@email.com', '2024-01-17'),
(104, 'Diana Black', 'diana@email.com', '2024-01-16');

-- Answer 2a: Find duplicates
SELECT 
    customer_id, 
    name, 
    email, 
    COUNT(*) as duplicate_count
FROM customer_staging
GROUP BY customer_id, name, email
HAVING COUNT(*) > 1;

-- Answer 2b: Keep only the latest record (using ROW_NUMBER)
WITH ranked_records AS (
    SELECT 
        *,
        ROW_NUMBER() OVER (
            PARTITION BY customer_id, name, email 
            ORDER BY load_date DESC, id DESC
        ) as rn
    FROM customer_staging
)
SELECT * 
FROM ranked_records 
WHERE rn = 1;

-- Answer 2c: Delete duplicates, keep latest (if supported)
-- For MySQL 8.0+/PostgreSQL
DELETE FROM customer_staging
WHERE id NOT IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY customer_id, name, email 
                   ORDER BY load_date DESC, id DESC
               ) as rn
        FROM customer_staging
    ) t
    WHERE rn = 1
);


-- ============================================================================
-- SECTION 3: DATA VALIDATION AND BUSINESS RULES
-- ============================================================================

-- Question 3: Validate data against business rules
-- Scenario: Ensure order data meets business constraints

DROP TABLE IF EXISTS orders;
CREATE TABLE orders (
    order_id INT PRIMARY KEY,
    customer_id INT,
    order_date DATE,
    ship_date DATE,
    order_amount DECIMAL(10,2),
    discount_percent DECIMAL(5,2),
    status VARCHAR(20)
);

-- Sample Data
INSERT INTO orders VALUES
(1001, 101, '2024-01-15', '2024-01-16', 150.00, 10.00, 'SHIPPED'),
(1002, 102, '2024-01-16', '2024-01-14', 200.00, 5.00, 'SHIPPED'),  -- Ship before order!
(1003, 103, '2024-01-17', '2024-01-20', -50.00, 15.00, 'PENDING'),  -- Negative amount!
(1004, 104, '2024-01-18', '2024-01-22', 300.00, 150.00, 'SHIPPED'), -- Discount > 100%!
(1005, 105, '2024-01-19', '2024-01-21', 0.00, 0.00, 'CANCELLED'),
(1006, 106, '2024-01-20', NULL, 100.00, 10.00, 'PENDING');

-- Answer 3: Business rule validation query
SELECT 
    order_id,
    customer_id,
    order_date,
    ship_date,
    order_amount,
    discount_percent,
    status,
    CASE 
        WHEN ship_date < order_date THEN 'ERROR: Ship date before order date'
        WHEN order_amount < 0 THEN 'ERROR: Negative order amount'
        WHEN discount_percent > 100 THEN 'ERROR: Discount exceeds 100%'
        WHEN discount_percent < 0 THEN 'ERROR: Negative discount'
        WHEN status = 'SHIPPED' AND ship_date IS NULL THEN 'ERROR: Shipped without ship date'
        ELSE 'VALID'
    END as validation_status
FROM orders
HAVING validation_status != 'VALID';


-- ============================================================================
-- SECTION 4: DATA TRANSFORMATION - PIVOT AND UNPIVOT
-- ============================================================================

-- Question 4: Transform data from rows to columns (PIVOT)
-- Scenario: Convert monthly sales data to columnar format

DROP TABLE IF EXISTS monthly_sales;
CREATE TABLE monthly_sales (
    product_id INT,
    product_name VARCHAR(50),
    month VARCHAR(10),
    sales_amount DECIMAL(10,2)
);

-- Sample Data
INSERT INTO monthly_sales VALUES
(1, 'Laptop', 'January', 15000.00),
(1, 'Laptop', 'February', 18000.00),
(1, 'Laptop', 'March', 16500.00),
(2, 'Mouse', 'January', 2500.00),
(2, 'Mouse', 'February', 2800.00),
(2, 'Mouse', 'March', 2600.00),
(3, 'Keyboard', 'January', 3500.00),
(3, 'Keyboard', 'February', 3700.00),
(3, 'Keyboard', 'March', 3600.00);

-- Answer 4: PIVOT query (manual pivot for MySQL)
SELECT 
    product_id,
    product_name,
    SUM(CASE WHEN month = 'January' THEN sales_amount ELSE 0 END) as January,
    SUM(CASE WHEN month = 'February' THEN sales_amount ELSE 0 END) as February,
    SUM(CASE WHEN month = 'March' THEN sales_amount ELSE 0 END) as March,
    SUM(sales_amount) as Total
FROM monthly_sales
GROUP BY product_id, product_name;


-- ============================================================================
-- SECTION 5: SLOWLY CHANGING DIMENSIONS (SCD TYPE 2)
-- ============================================================================

-- Question 5: Implement SCD Type 2 logic
-- Scenario: Track customer address changes over time

DROP TABLE IF EXISTS customer_dimension;
CREATE TABLE customer_dimension (
    surrogate_key INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT,
    name VARCHAR(100),
    address VARCHAR(200),
    city VARCHAR(50),
    state VARCHAR(50),
    start_date DATE,
    end_date DATE,
    is_current BOOLEAN
);

-- Sample Data - Historical records
INSERT INTO customer_dimension (customer_id, name, address, city, state, start_date, end_date, is_current) VALUES
(201, 'Emma Wilson', '123 Main St', 'Boston', 'MA', '2023-01-01', '2023-06-30', FALSE),
(201, 'Emma Wilson', '456 Oak Ave', 'Boston', 'MA', '2023-07-01', '2023-12-31', FALSE),
(201, 'Emma Wilson', '789 Pine Rd', 'Cambridge', 'MA', '2024-01-01', '9999-12-31', TRUE),
(202, 'Oliver Smith', '321 Elm St', 'Chicago', 'IL', '2023-01-01', '9999-12-31', TRUE),
(203, 'Sophia Brown', '654 Maple Dr', 'Austin', 'TX', '2023-01-01', '2023-11-30', FALSE),
(203, 'Sophia Brown', '987 Cedar Ln', 'Austin', 'TX', '2023-12-01', '9999-12-31', TRUE);

-- Answer 5a: Query to get current records only
SELECT 
    customer_id,
    name,
    address,
    city,
    state,
    start_date
FROM customer_dimension
WHERE is_current = TRUE;

-- Answer 5b: Query to get customer history
SELECT 
    customer_id,
    name,
    address,
    city,
    state,
    start_date,
    end_date,
    DATEDIFF(end_date, start_date) as days_at_address
FROM customer_dimension
WHERE customer_id = 201
ORDER BY start_date;

-- Answer 5c: Identify customers who changed address
SELECT 
    customer_id,
    name,
    COUNT(*) as number_of_changes,
    MIN(start_date) as first_address_date,
    MAX(start_date) as latest_address_date
FROM customer_dimension
GROUP BY customer_id, name
HAVING COUNT(*) > 1;


-- ============================================================================
-- SECTION 6: INCREMENTAL LOAD PATTERN
-- ============================================================================

-- Question 6: Implement incremental load logic
-- Scenario: Load only new or modified transactions

DROP TABLE IF EXISTS transactions_source;
DROP TABLE IF EXISTS transactions_target;

CREATE TABLE transactions_source (
    transaction_id INT PRIMARY KEY,
    customer_id INT,
    transaction_date TIMESTAMP,
    amount DECIMAL(10,2),
    modified_timestamp TIMESTAMP
);

CREATE TABLE transactions_target (
    transaction_id INT PRIMARY KEY,
    customer_id INT,
    transaction_date TIMESTAMP,
    amount DECIMAL(10,2),
    modified_timestamp TIMESTAMP,
    load_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Sample Data - Source table
INSERT INTO transactions_source VALUES
(1, 101, '2024-01-15 10:30:00', 100.00, '2024-01-15 10:30:00'),
(2, 102, '2024-01-15 11:00:00', 200.00, '2024-01-15 11:00:00'),
(3, 103, '2024-01-16 09:15:00', 150.00, '2024-01-16 09:15:00'),
(4, 104, '2024-01-16 14:30:00', 300.00, '2024-01-16 14:30:00'),
(5, 105, '2024-01-17 08:00:00', 250.00, '2024-01-17 08:00:00');

-- Sample Data - Target table (already loaded)
INSERT INTO transactions_target (transaction_id, customer_id, transaction_date, amount, modified_timestamp) VALUES
(1, 101, '2024-01-15 10:30:00', 100.00, '2024-01-15 10:30:00'),
(2, 102, '2024-01-15 11:00:00', 200.00, '2024-01-15 11:00:00'),
(3, 103, '2024-01-16 09:15:00', 150.00, '2024-01-16 09:15:00');

-- Answer 6: Incremental load query (Insert new, Update modified)
-- Step 1: Find new records
SELECT s.*
FROM transactions_source s
LEFT JOIN transactions_target t ON s.transaction_id = t.transaction_id
WHERE t.transaction_id IS NULL;

-- Step 2: Find modified records
SELECT s.*
FROM transactions_source s
INNER JOIN transactions_target t ON s.transaction_id = t.transaction_id
WHERE s.modified_timestamp > t.modified_timestamp;

-- Step 3: Get high watermark
SELECT COALESCE(MAX(modified_timestamp), '1900-01-01') as last_load_timestamp
FROM transactions_target;


-- ============================================================================
-- SECTION 7: DATA RECONCILIATION
-- ============================================================================

-- Question 7: Reconcile data between source and target
-- Scenario: Compare record counts and amounts after ETL

DROP TABLE IF EXISTS sales_source;
DROP TABLE IF EXISTS sales_target;

CREATE TABLE sales_source (
    sale_id INT PRIMARY KEY,
    sale_date DATE,
    product_id INT,
    quantity INT,
    unit_price DECIMAL(10,2),
    total_amount DECIMAL(10,2)
);

CREATE TABLE sales_target (
    sale_id INT PRIMARY KEY,
    sale_date DATE,
    product_id INT,
    quantity INT,
    unit_price DECIMAL(10,2),
    total_amount DECIMAL(10,2)
);

-- Sample Data - Source
INSERT INTO sales_source VALUES
(1, '2024-01-15', 101, 5, 20.00, 100.00),
(2, '2024-01-15', 102, 3, 30.00, 90.00),
(3, '2024-01-16', 103, 2, 50.00, 100.00),
(4, '2024-01-16', 104, 4, 25.00, 100.00),
(5, '2024-01-17', 105, 6, 15.00, 90.00);

-- Sample Data - Target (with one missing and one different)
INSERT INTO sales_target VALUES
(1, '2024-01-15', 101, 5, 20.00, 100.00),
(2, '2024-01-15', 102, 3, 30.00, 90.00),
(3, '2024-01-16', 103, 2, 50.00, 100.00),
(4, '2024-01-16', 104, 4, 25.00, 99.00);  -- Different amount!
-- Record 5 is missing

-- Answer 7a: Reconciliation summary
SELECT 
    'Source' as table_name,
    COUNT(*) as record_count,
    SUM(total_amount) as total_amount,
    MIN(sale_date) as min_date,
    MAX(sale_date) as max_date
FROM sales_source
UNION ALL
SELECT 
    'Target',
    COUNT(*),
    SUM(total_amount),
    MIN(sale_date),
    MAX(sale_date)
FROM sales_target;

-- Answer 7b: Find missing records
SELECT 'Missing in Target' as issue, s.*
FROM sales_source s
LEFT JOIN sales_target t ON s.sale_id = t.sale_id
WHERE t.sale_id IS NULL
UNION ALL
SELECT 'Extra in Target', t.*
FROM sales_target t
LEFT JOIN sales_source s ON t.sale_id = s.sale_id
WHERE s.sale_id IS NULL;

-- Answer 7c: Find mismatched records
SELECT 
    s.sale_id,
    s.total_amount as source_amount,
    t.total_amount as target_amount,
    s.total_amount - t.total_amount as difference
FROM sales_source s
INNER JOIN sales_target t ON s.sale_id = t.sale_id
WHERE s.total_amount != t.total_amount
   OR s.quantity != t.quantity
   OR s.unit_price != t.unit_price;


-- ============================================================================
-- SECTION 8: WINDOW FUNCTIONS IN ETL
-- ============================================================================

-- Question 8: Use window functions for ETL transformations
-- Scenario: Calculate running totals, ranking, and lead/lag

DROP TABLE IF EXISTS daily_revenue;
CREATE TABLE daily_revenue (
    revenue_date DATE,
    region VARCHAR(50),
    revenue DECIMAL(10,2)
);

-- Sample Data
INSERT INTO daily_revenue VALUES
('2024-01-01', 'North', 1000.00),
('2024-01-02', 'North', 1200.00),
('2024-01-03', 'North', 1100.00),
('2024-01-04', 'North', 1300.00),
('2024-01-01', 'South', 800.00),
('2024-01-02', 'South', 900.00),
('2024-01-03', 'South', 850.00),
('2024-01-04', 'South', 950.00),
('2024-01-01', 'East', 1500.00),
('2024-01-02', 'East', 1600.00),
('2024-01-03', 'East', 1550.00),
('2024-01-04', 'East', 1700.00);

-- Answer 8: Multiple window function examples
SELECT 
    revenue_date,
    region,
    revenue,
    -- Running total by region
    SUM(revenue) OVER (
        PARTITION BY region 
        ORDER BY revenue_date
    ) as running_total,
    -- Moving average (3-day)
    AVG(revenue) OVER (
        PARTITION BY region 
        ORDER BY revenue_date 
        ROWS BETWEEN 2 PRECEDING AND CURRENT ROW
    ) as moving_avg_3day,
    -- Previous day revenue
    LAG(revenue, 1) OVER (
        PARTITION BY region 
        ORDER BY revenue_date
    ) as prev_day_revenue,
    -- Revenue growth from previous day
    revenue - LAG(revenue, 1) OVER (
        PARTITION BY region 
        ORDER BY revenue_date
    ) as day_over_day_growth,
    -- Rank within region
    RANK() OVER (
        PARTITION BY region 
        ORDER BY revenue DESC
    ) as revenue_rank,
    -- Percent of total by region
    ROUND(revenue * 100.0 / SUM(revenue) OVER (PARTITION BY region), 2) as percent_of_region_total
FROM daily_revenue
ORDER BY region, revenue_date;


-- ============================================================================
-- SECTION 9: COMPLEX JOINS AND AGGREGATIONS
-- ============================================================================

-- Question 9: Multiple table joins with aggregations
-- Scenario: Generate comprehensive sales report

DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS order_details;
DROP TABLE IF EXISTS order_header;

CREATE TABLE products (
    product_id INT PRIMARY KEY,
    product_name VARCHAR(100),
    category VARCHAR(50),
    unit_cost DECIMAL(10,2)
);

CREATE TABLE order_header (
    order_id INT PRIMARY KEY,
    customer_id INT,
    order_date DATE,
    status VARCHAR(20)
);

CREATE TABLE order_details (
    order_detail_id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT,
    product_id INT,
    quantity INT,
    unit_price DECIMAL(10,2)
);

-- Sample Data
INSERT INTO products VALUES
(1, 'Laptop Pro', 'Electronics', 800.00),
(2, 'Wireless Mouse', 'Electronics', 15.00),
(3, 'USB Cable', 'Accessories', 5.00),
(4, 'Monitor 24"', 'Electronics', 200.00),
(5, 'Keyboard', 'Electronics', 50.00);

INSERT INTO order_header VALUES
(1001, 101, '2024-01-15', 'COMPLETED'),
(1002, 102, '2024-01-16', 'COMPLETED'),
(1003, 103, '2024-01-17', 'CANCELLED'),
(1004, 104, '2024-01-18', 'COMPLETED'),
(1005, 105, '2024-01-19', 'PENDING');

INSERT INTO order_details (order_id, product_id, quantity, unit_price) VALUES
(1001, 1, 2, 1000.00),
(1001, 2, 1, 25.00),
(1002, 3, 5, 8.00),
(1002, 4, 1, 250.00),
(1003, 1, 1, 1000.00),
(1003, 5, 2, 60.00),
(1004, 2, 3, 25.00),
(1004, 3, 10, 8.00),
(1005, 4, 2, 250.00);

-- Answer 9a: Comprehensive sales report
SELECT 
    p.category,
    p.product_name,
    COUNT(DISTINCT oh.order_id) as order_count,
    SUM(od.quantity) as total_quantity_sold,
    SUM(od.quantity * od.unit_price) as total_revenue,
    SUM(od.quantity * p.unit_cost) as total_cost,
    SUM(od.quantity * od.unit_price) - SUM(od.quantity * p.unit_cost) as profit,
    ROUND(AVG(od.unit_price), 2) as avg_selling_price,
    p.unit_cost as cost_price
FROM products p
LEFT JOIN order_details od ON p.product_id = od.product_id
LEFT JOIN order_header oh ON od.order_id = oh.order_id
WHERE oh.status = 'COMPLETED'
GROUP BY p.category, p.product_name, p.unit_cost
ORDER BY total_revenue DESC;

-- Answer 9b: Find orders without complete product information
SELECT 
    oh.order_id,
    oh.order_date,
    oh.status,
    COUNT(od.order_detail_id) as detail_count
FROM order_header oh
LEFT JOIN order_details od ON oh.order_id = od.order_id
GROUP BY oh.order_id, oh.order_date, oh.status
HAVING detail_count = 0;


-- ============================================================================
-- SECTION 10: PERFORMANCE AND OPTIMIZATION
-- ============================================================================

-- Question 10: Identify and optimize slow queries
-- Scenario: Large fact table analysis

DROP TABLE IF EXISTS sales_fact;
CREATE TABLE sales_fact (
    sale_id INT PRIMARY KEY,
    sale_date DATE,
    customer_id INT,
    product_id INT,
    store_id INT,
    quantity INT,
    amount DECIMAL(10,2)
);

-- Create indexes for performance
CREATE INDEX idx_sales_date ON sales_fact(sale_date);
CREATE INDEX idx_sales_customer ON sales_fact(customer_id);
CREATE INDEX idx_sales_product ON sales_fact(product_id);
CREATE INDEX idx_sales_composite ON sales_fact(sale_date, store_id);

-- Sample Data
INSERT INTO sales_fact VALUES
(1, '2024-01-01', 1001, 101, 1, 5, 500.00),
(2, '2024-01-01', 1002, 102, 1, 3, 300.00),
(3, '2024-01-02', 1003, 103, 2, 2, 200.00),
(4, '2024-01-02', 1001, 101, 1, 4, 400.00),
(5, '2024-01-03', 1004, 104, 2, 6, 600.00),
(6, '2024-01-03', 1002, 105, 1, 1, 100.00),
(7, '2024-01-04', 1005, 101, 3, 8, 800.00),
(8, '2024-01-04', 1003, 102, 2, 2, 200.00),
(9, '2024-01-05', 1001, 103, 1, 5, 500.00),
(10, '2024-01-05', 1006, 104, 3, 3, 300.00);

-- Answer 10a: Optimized aggregation query
SELECT 
    sale_date,
    store_id,
    COUNT(*) as transaction_count,
    SUM(quantity) as total_quantity,
    SUM(amount) as total_amount
FROM sales_fact
WHERE sale_date BETWEEN '2024-01-01' AND '2024-01-31'
GROUP BY sale_date, store_id
ORDER BY sale_date, store_id;

-- Answer 10b: Query to identify missing indexes
-- Note: This is a sample query pattern for analysis
SELECT 
    customer_id,
    COUNT(*) as purchase_count,
    SUM(amount) as total_spent
FROM sales_fact
WHERE sale_date >= '2024-01-01'
GROUP BY customer_id
HAVING total_spent > 1000;


-- ============================================================================
-- SECTION 11: DATE DIMENSION AND TIME-BASED QUERIES
-- ============================================================================

-- Question 11: Create and use date dimension table
-- Scenario: Common date dimension for reporting

DROP TABLE IF EXISTS date_dimension;
CREATE TABLE date_dimension (
    date_key INT PRIMARY KEY,
    full_date DATE,
    year INT,
    quarter INT,
    month INT,
    month_name VARCHAR(20),
    week INT,
    day_of_month INT,
    day_of_week INT,
    day_name VARCHAR(20),
    is_weekend BOOLEAN,
    is_holiday BOOLEAN
);

-- Sample Data - Date dimension
INSERT INTO date_dimension VALUES
(20240101, '2024-01-01', 2024, 1, 1, 'January', 1, 1, 2, 'Monday', FALSE, TRUE),
(20240102, '2024-01-02', 2024, 1, 1, 'January', 1, 2, 3, 'Tuesday', FALSE, FALSE),
(20240103, '2024-01-03', 2024, 1, 1, 'January', 1, 3, 4, 'Wednesday', FALSE, FALSE),
(20240106, '2024-01-06', 2024, 1, 1, 'January', 1, 6, 7, 'Saturday', TRUE, FALSE),
(20240107, '2024-01-07', 2024, 1, 1, 'January', 1, 7, 1, 'Sunday', TRUE, FALSE),
(20240115, '2024-01-15', 2024, 1, 1, 'January', 3, 15, 2, 'Monday', FALSE, FALSE);

-- Answer 11: Query with date dimension
SELECT 
    d.month_name,
    d.is_weekend,
    COUNT(*) as number_of_sales,
    SUM(s.amount) as total_amount,
    AVG(s.amount) as avg_amount
FROM sales_fact s
INNER JOIN date_dimension d ON s.sale_date = d.full_date
GROUP BY d.month_name, d.is_weekend
ORDER BY d.month, d.is_weekend;


-- ============================================================================
-- SECTION 12: ERROR HANDLING AND LOGGING
-- ============================================================================

-- Question 12: Create ETL audit and error logging tables
-- Scenario: Track ETL job execution and errors

DROP TABLE IF EXISTS etl_job_log;
DROP TABLE IF EXISTS etl_error_log;

CREATE TABLE etl_job_log (
    job_log_id INT AUTO_INCREMENT PRIMARY KEY,
    job_name VARCHAR(100),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(20),
    rows_read INT,
    rows_inserted INT,
    rows_updated INT,
    rows_rejected INT
);

CREATE TABLE etl_error_log (
    error_log_id INT AUTO_INCREMENT PRIMARY KEY,
    job_log_id INT,
    error_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    error_message TEXT,
    error_query TEXT,
    record_data TEXT
);

-- Sample Data
INSERT INTO etl_job_log (job_name, start_time, end_time, status, rows_read, rows_inserted, rows_updated, rows_rejected) VALUES
('LOAD_CUSTOMER_DAILY', '2024-01-15 01:00:00', '2024-01-15 01:15:00', 'SUCCESS', 10000, 9500, 450, 50),
('LOAD_SALES_DAILY', '2024-01-15 02:00:00', '2024-01-15 02:45:00', 'SUCCESS', 50000, 49800, 150, 50),
('LOAD_PRODUCT_DAILY', '2024-01-15 03:00:00', '2024-01-15 03:05:00', 'FAILED', 1000, 0, 0, 1000),
('LOAD_CUSTOMER_DAILY', '2024-01-16 01:00:00', '2024-01-16 01:12:00', 'SUCCESS', 9500, 9200, 280, 20);

INSERT INTO etl_error_log (job_log_id, error_message, record_data) VALUES
(3, 'Duplicate key violation', '{"product_id": 101, "name": "Test Product"}'),
(3, 'Foreign key constraint failed', '{"product_id": 102, "category_id": 999}'),
(3, 'Data type mismatch', '{"product_id": "ABC", "price": 19.99}');

-- Answer 12a: Job execution summary
SELECT 
    job_name,
    COUNT(*) as execution_count,
    SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) as success_count,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failure_count,
    SUM(rows_read) as total_rows_read,
    SUM(rows_inserted) as total_rows_inserted,
    SUM(rows_rejected) as total_rows_rejected,
    AVG(TIMESTAMPDIFF(MINUTE, start_time, end_time)) as avg_duration_minutes
FROM etl_job_log
GROUP BY job_name;

-- Answer 12b: Recent failed jobs with errors
SELECT 
    j.job_name,
    j.start_time,
    j.status,
    j.rows_rejected,
    e.error_message,
    e.record_data
FROM etl_job_log j
LEFT JOIN etl_error_log e ON j.job_log_id = e.job_log_id
WHERE j.status = 'FAILED'
ORDER BY j.start_time DESC;


-- ============================================================================
-- SECTION 13: DATA TYPE CONVERSIONS AND CLEANSING
-- ============================================================================

-- Question 13: Handle data type issues and cleansing
-- Scenario: Clean and standardize incoming data

DROP TABLE IF EXISTS raw_customer_data;
CREATE TABLE raw_customer_data (
    id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id VARCHAR(50),
    name VARCHAR(100),
    phone VARCHAR(50),
    email VARCHAR(100),
    annual_income VARCHAR(50),
    join_date VARCHAR(50)
);

-- Sample Data with quality issues
INSERT INTO raw_customer_data (customer_id, name, phone, email, annual_income, join_date) VALUES
('C001', 'John Doe', '(555) 123-4567', 'john.doe@email.com', '$50,000', '2024-01-15'),
('C002', 'jane smith', '555.234.5678', 'JANE.SMITH@EMAIL.COM', '60000', '01/16/2024'),
('C003', 'Bob  Johnson', '555-345-6789', 'bob@email', '75,000.50', '2024/01/17'),
('C004', 'Sarah Williams', '5554567890', ' sarah.w@email.com ', '   85000  ', '15-Jan-2024');

-- Answer 13: Data cleansing and transformation
SELECT 
    customer_id,
    -- Clean name: proper case, remove extra spaces
    CONCAT(
        UPPER(SUBSTRING(TRIM(name), 1, 1)),
        LOWER(SUBSTRING(TRIM(name), 2))
    ) as cleaned_name,
    -- Standardize phone: remove all non-numeric characters
    CONCAT(
        SUBSTRING(REGEXP_REPLACE(phone, '[^0-9]', ''), 1, 3),
        '-',
        SUBSTRING(REGEXP_REPLACE(phone, '[^0-9]', ''), 4, 3),
        '-',
        SUBSTRING(REGEXP_REPLACE(phone, '[^0-9]', ''), 7, 4)
    ) as cleaned_phone,
    -- Clean email: trim and lowercase
    LOWER(TRIM(email)) as cleaned_email,
    -- Convert income to numeric
    CAST(
        REGEXP_REPLACE(
            REGEXP_REPLACE(annual_income, '[$,]', ''),
            '[^0-9.]', ''
        ) AS DECIMAL(10,2)
    ) as cleaned_income,
    -- Standardize date format
    STR_TO_DATE(
        CASE 
            WHEN join_date LIKE '%/%' THEN 
                CONCAT(
                    SUBSTRING_INDEX(join_date, '/', -1), '-',
                    LPAD(SUBSTRING_INDEX(SUBSTRING_INDEX(join_date, '/', 1), '/', -1), 2, '0'), '-',
                    LPAD(SUBSTRING_INDEX(SUBSTRING_INDEX(join_date, '/', 2), '/', -1), 2, '0')
                )
            ELSE join_date
        END,
        '%Y-%m-%d'
    ) as cleaned_join_date
FROM raw_customer_data;


-- ============================================================================
-- SECTION 14: SURROGATE KEY GENERATION
-- ============================================================================

-- Question 14: Generate surrogate keys for dimension tables
-- Scenario: Create unique keys for dimension records

DROP TABLE IF EXISTS product_dimension;
CREATE TABLE product_dimension (
    product_key INT AUTO_INCREMENT PRIMARY KEY,
    product_id VARCHAR(50),
    product_name VARCHAR(100),
    category VARCHAR(50),
    subcategory VARCHAR(50),
    version_start_date DATE,
    version_end_date DATE,
    is_current BOOLEAN
);

-- Sample Data
INSERT INTO product_dimension (product_id, product_name, category, subcategory, version_start_date, version_end_date, is_current) VALUES
('PROD001', 'Laptop Pro 15', 'Electronics', 'Computers', '2023-01-01', '9999-12-31', TRUE),
('PROD002', 'Wireless Mouse', 'Electronics', 'Accessories', '2023-01-01', '2023-06-30', FALSE),
('PROD002', 'Wireless Mouse v2', 'Electronics', 'Accessories', '2023-07-01', '9999-12-31', TRUE),
('PROD003', 'USB-C Cable', 'Electronics', 'Cables', '2023-01-01', '9999-12-31', TRUE);

-- Answer 14: Query showing surrogate key usage
SELECT 
    product_key,
    product_id,
    product_name,
    CASE 
        WHEN is_current = TRUE THEN 'Current'
        ELSE 'Historical'
    END as record_status,
    version_start_date,
    version_end_date
FROM product_dimension
ORDER BY product_id, version_start_date;


-- ============================================================================
-- SECTION 15: COMMON TABLE EXPRESSIONS (CTEs) IN ETL
-- ============================================================================

-- Question 15: Use CTEs for complex ETL logic
-- Scenario: Multi-step transformation using CTEs

-- Answer 15: Complex CTE example
WITH 
-- Step 1: Calculate customer purchase metrics
customer_metrics AS (
    SELECT 
        customer_id,
        COUNT(DISTINCT sale_id) as purchase_count,
        SUM(amount) as total_spent,
        AVG(amount) as avg_purchase
    FROM sales_fact
    GROUP BY customer_id
),
-- Step 2: Categorize customers
customer_segments AS (
    SELECT 
        customer_id,
        purchase_count,
        total_spent,
        avg_purchase,
        CASE 
            WHEN total_spent > 1000 THEN 'Premium'
            WHEN total_spent > 500 THEN 'Standard'
            ELSE 'Basic'
        END as customer_segment
    FROM customer_metrics
),
-- Step 3: Calculate segment statistics
segment_stats AS (
    SELECT 
        customer_segment,
        COUNT(*) as customer_count,
        AVG(total_spent) as avg_segment_spend,
        MIN(total_spent) as min_spend,
        MAX(total_spent) as max_spend
    FROM customer_segments
    GROUP BY customer_segment
)
-- Final output
SELECT 
    cs.customer_id,
    cs.customer_segment,
    cs.total_spent,
    cs.purchase_count,
    ss.customer_count as segment_size,
    ss.avg_segment_spend,
    ROUND(cs.total_spent * 100.0 / ss.avg_segment_spend, 2) as pct_of_segment_avg
FROM customer_segments cs
INNER JOIN segment_stats ss ON cs.customer_segment = ss.customer_segment
ORDER BY cs.total_spent DESC;


-- ============================================================================
-- SECTION 16: HANDLING LATE-ARRIVING DIMENSIONS
-- ============================================================================

-- Question 16: Handle late-arriving dimension records
-- Scenario: Fact arrives before dimension is available

DROP TABLE IF EXISTS fact_sales;
DROP TABLE IF EXISTS dim_customer_final;

CREATE TABLE dim_customer_final (
    customer_key INT AUTO_INCREMENT PRIMARY KEY,
    customer_id VARCHAR(50),
    customer_name VARCHAR(100),
    city VARCHAR(50),
    effective_date DATE
);

CREATE TABLE fact_sales (
    sales_key INT AUTO_INCREMENT PRIMARY KEY,
    customer_key INT,  -- Foreign key to dimension
    sale_date DATE,
    amount DECIMAL(10,2),
    is_inferred BOOLEAN DEFAULT FALSE
);

-- Sample Data
INSERT INTO dim_customer_final (customer_id, customer_name, city, effective_date) VALUES
('CUST001', 'Alice Johnson', 'New York', '2024-01-01'),
('CUST002', 'Bob Smith', 'Los Angeles', '2024-01-01');
-- CUST003 is missing (late-arriving)

INSERT INTO fact_sales (customer_key, sale_date, amount, is_inferred) VALUES
(1, '2024-01-15', 100.00, FALSE),
(2, '2024-01-16', 200.00, FALSE),
(-1, '2024-01-17', 150.00, TRUE);  -- Unknown customer, inferred record

-- Answer 16: Query to identify inferred dimension records
SELECT 
    f.sales_key,
    f.sale_date,
    f.amount,
    CASE 
        WHEN f.customer_key = -1 THEN 'Unknown/Inferred'
        ELSE d.customer_name
    END as customer_name,
    f.is_inferred
FROM fact_sales f
LEFT JOIN dim_customer_final d ON f.customer_key = d.customer_key
WHERE f.is_inferred = TRUE
   OR f.customer_key = -1;


-- ============================================================================
-- SECTION 17: DATA PROFILING QUERIES
-- ============================================================================

-- Question 17: Profile data to understand quality and distribution
-- Scenario: Analyze data before ETL design

-- Answer 17a: Column profiling
SELECT 
    'customer_id' as column_name,
    COUNT(*) as total_rows,
    COUNT(DISTINCT customer_id) as distinct_values,
    COUNT(customer_id) as non_null_count,
    COUNT(*) - COUNT(customer_id) as null_count,
    MIN(LENGTH(customer_id)) as min_length,
    MAX(LENGTH(customer_id)) as max_length
FROM customers
UNION ALL
SELECT 
    'email',
    COUNT(*),
    COUNT(DISTINCT email),
    COUNT(email),
    COUNT(*) - COUNT(email),
    MIN(LENGTH(email)),
    MAX(LENGTH(email))
FROM customers;

-- Answer 17b: Value distribution
SELECT 
    country,
    COUNT(*) as record_count,
    ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM customers), 2) as percentage
FROM customers
GROUP BY country
ORDER BY record_count DESC;


-- ============================================================================
-- SECTION 18: CHANGE DATA CAPTURE (CDC) PATTERN
-- ============================================================================

-- Question 18: Implement CDC logic
-- Scenario: Track changes in source system

DROP TABLE IF EXISTS customer_cdc;
CREATE TABLE customer_cdc (
    cdc_id INT AUTO_INCREMENT PRIMARY KEY,
    operation_type VARCHAR(10),  -- INSERT, UPDATE, DELETE
    customer_id INT,
    name VARCHAR(100),
    email VARCHAR(100),
    change_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Sample Data - CDC records
INSERT INTO customer_cdc (operation_type, customer_id, name, email) VALUES
('INSERT', 301, 'Michael Brown', 'michael@email.com'),
('UPDATE', 301, 'Michael Brown Jr', 'michael.jr@email.com'),
('INSERT', 302, 'Lisa White', 'lisa@email.com'),
('DELETE', 301, 'Michael Brown Jr', 'michael.jr@email.com'),
('INSERT', 303, 'David Green', 'david@email.com');

-- Answer 18a: Get latest state for each customer
WITH ranked_changes AS (
    SELECT 
        *,
        ROW_NUMBER() OVER (
            PARTITION BY customer_id 
            ORDER BY change_timestamp DESC
        ) as rn
    FROM customer_cdc
)
SELECT 
    customer_id,
    operation_type as latest_operation,
    name,
    email,
    change_timestamp
FROM ranked_changes
WHERE rn = 1
  AND operation_type != 'DELETE';

-- Answer 18b: Audit trail by customer
SELECT 
    customer_id,
    operation_type,
    name,
    email,
    change_timestamp
FROM customer_cdc
WHERE customer_id = 301
ORDER BY change_timestamp;


-- ============================================================================
-- SECTION 19: FACT TABLE DESIGN PATTERNS
-- ============================================================================

-- Question 19: Design and populate fact tables
-- Scenario: Create transaction and accumulating snapshot fact tables

DROP TABLE IF EXISTS fact_order_transaction;
DROP TABLE IF EXISTS fact_order_snapshot;

-- Transaction Fact Table (immutable, one row per transaction)
CREATE TABLE fact_order_transaction (
    transaction_key INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT,
    customer_key INT,
    product_key INT,
    order_date_key INT,
    quantity INT,
    amount DECIMAL(10,2),
    created_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Accumulating Snapshot Fact Table (updateable, one row per order)
CREATE TABLE fact_order_snapshot (
    snapshot_key INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT,
    customer_key INT,
    order_date_key INT,
    ship_date_key INT,
    delivery_date_key INT,
    order_to_ship_days INT,
    ship_to_delivery_days INT,
    total_amount DECIMAL(10,2),
    current_status VARCHAR(50),
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Sample Data
INSERT INTO fact_order_transaction (order_id, customer_key, product_key, order_date_key, quantity, amount) VALUES
(2001, 1, 101, 20240115, 2, 100.00),
(2001, 1, 102, 20240115, 1, 50.00),
(2002, 2, 103, 20240116, 3, 150.00);

INSERT INTO fact_order_snapshot (order_id, customer_key, order_date_key, ship_date_key, delivery_date_key, order_to_ship_days, ship_to_delivery_days, total_amount, current_status) VALUES
(2001, 1, 20240115, 20240116, 20240118, 1, 2, 150.00, 'DELIVERED'),
(2002, 2, 20240116, 20240117, NULL, 1, NULL, 150.00, 'IN_TRANSIT');

-- Answer 19: Compare fact table types
SELECT 
    'Transaction Fact' as fact_type,
    COUNT(*) as row_count,
    COUNT(DISTINCT order_id) as distinct_orders,
    SUM(amount) as total_amount
FROM fact_order_transaction
UNION ALL
SELECT 
    'Snapshot Fact',
    COUNT(*),
    COUNT(DISTINCT order_id),
    SUM(total_amount)
FROM fact_order_snapshot;


-- ============================================================================
-- SECTION 20: INTERVIEW QUESTIONS - QUICK REFERENCE
-- ============================================================================

/*
COMMON ETL/QA INTERVIEW QUESTIONS - QUICK ANSWERS:

Q1: What is the difference between DELETE, TRUNCATE, and DROP?
A: - DELETE: Removes rows, can be rolled back, fires triggers, keeps structure
   - TRUNCATE: Removes all rows, faster, cannot be rolled back, keeps structure
   - DROP: Removes entire table including structure

Q2: What is normalization and denormalization?
A: - Normalization: Organize data to reduce redundancy (3NF, BCNF)
   - Denormalization: Add redundancy for query performance (star schema)

Q3: Explain ACID properties
A: - Atomicity: All or nothing
   - Consistency: Data integrity maintained
   - Isolation: Transactions don't interfere
   - Durability: Committed changes persist

Q4: What are the different types of SCDs?
A: - Type 0: No changes allowed
   - Type 1: Overwrite old values
   - Type 2: Create new row with version (most common)
   - Type 3: Add new column for previous value
   - Type 4: Separate history table
   - Type 6: Combination of 1+2+3

Q5: What is the difference between OLTP and OLAP?
A: - OLTP: Online Transaction Processing (normalized, fast inserts/updates)
   - OLAP: Online Analytical Processing (denormalized, fast queries)

Q6: Explain star schema vs snowflake schema
A: - Star: Denormalized dimensions, simpler queries, more storage
   - Snowflake: Normalized dimensions, complex queries, less storage

Q7: What is a surrogate key?
A: System-generated unique identifier (not business key), used for versioning

Q8: How do you handle NULL values in SQL?
A: Use COALESCE, IFNULL, IS NULL, IS NOT NULL, NVL (Oracle)

Q9: What is the difference between WHERE and HAVING?
A: - WHERE: Filters before grouping, cannot use aggregates
   - HAVING: Filters after grouping, can use aggregates

Q10: Explain INNER JOIN vs LEFT JOIN vs FULL OUTER JOIN
A: - INNER: Only matching rows from both tables
   - LEFT: All from left, matching from right
   - RIGHT: All from right, matching from left
   - FULL OUTER: All rows from both tables
*/

-- ============================================================================
-- END OF FILE
-- ============================================================================

/*
TIPS FOR ETL/QA INTERVIEWS:

1. Always validate your data before and after transformation
2. Consider data quality at every step
3. Understand the difference between full load and incremental load
4. Be familiar with window functions (ROW_NUMBER, RANK, LAG, LEAD)
5. Know how to write efficient queries with proper indexing
6. Understand slowly changing dimensions (especially Type 2)
7. Be comfortable with CTEs for complex transformations
8. Know how to reconcile data between source and target
9. Understand fact and dimension table design
10. Be prepared to explain your approach to handling errors and logging

PRACTICE AREAS:
- Write queries to find duplicates, NULLs, and data quality issues
- Implement SCD Type 2 logic
- Use window functions for ranking and running totals
- Join multiple tables with aggregations
- Optimize slow-running queries
- Handle date and time transformations
- Implement incremental load patterns
- Write data reconciliation queries
*/
