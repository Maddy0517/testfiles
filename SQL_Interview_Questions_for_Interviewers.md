# SQL Interview Questions for Interviewers
## ETL QA / Data Engineer (5+ Years Experience)

This guide provides practical SQL problems to ask candidates, expected solutions, and evaluation criteria.

---

## 🎯 How to Use This Guide

1. **Start with easier questions** to build confidence
2. **Ask follow-up questions** based on their answers
3. **Look for optimization** and production-ready thinking
4. **Evaluate communication** - can they explain their logic?
5. **Check for BigQuery-specific knowledge** vs generic SQL

---

## Question 1: Find Duplicate Records (Easy - Warmup)

### Problem Statement
```
Given a table 'orders' with columns: order_id, customer_id, order_date, amount
Some orders are accidentally duplicated in the system.
Write a query to find all duplicate orders (same order_id) and show how many times each appears.
```

### Sample Data
```sql
CREATE TEMP TABLE orders AS
SELECT 1001 AS order_id, 'C001' AS customer_id, DATE('2025-01-01') AS order_date, 100.0 AS amount
UNION ALL SELECT 1001, 'C001', DATE('2025-01-01'), 100.0
UNION ALL SELECT 1002, 'C002', DATE('2025-01-02'), 200.0
UNION ALL SELECT 1003, 'C003', DATE('2025-01-03'), 150.0
UNION ALL SELECT 1003, 'C003', DATE('2025-01-03'), 150.0
UNION ALL SELECT 1003, 'C003', DATE('2025-01-03'), 150.0;
```

### Expected Solution - Basic
```sql
SELECT 
  order_id,
  COUNT(*) AS duplicate_count
FROM orders
GROUP BY order_id
HAVING COUNT(*) > 1
ORDER BY duplicate_count DESC;
```

### Expected Solution - Advanced
```sql
-- Show all duplicate records with row numbers
SELECT 
  order_id,
  customer_id,
  order_date,
  amount,
  COUNT(*) OVER (PARTITION BY order_id) AS duplicate_count,
  ROW_NUMBER() OVER (PARTITION BY order_id ORDER BY order_date) AS row_num
FROM orders
QUALIFY duplicate_count > 1;
```

### Key Concepts Being Tested
- ✅ GROUP BY and HAVING
- ✅ Window functions (ROW_NUMBER, PARTITION BY)
- ✅ QUALIFY clause (BigQuery specific)
- ✅ Understanding of duplicates

### Follow-up Questions
1. "How would you delete the duplicates and keep only one record?"
2. "What if you need to keep the most recent duplicate based on a timestamp column?"
3. "How would you identify duplicates across multiple columns?"

### Expected Answer to Follow-up #1
```sql
-- Using ROW_NUMBER to delete duplicates
DELETE FROM orders
WHERE order_id IN (
  SELECT order_id
  FROM (
    SELECT 
      order_id,
      ROW_NUMBER() OVER (PARTITION BY order_id ORDER BY order_date DESC) AS row_num
    FROM orders
  )
  WHERE row_num > 1
);
```

### Red Flags 🚩
- Cannot solve without using DISTINCT (inefficient)
- Doesn't understand window functions for 5+ years experience
- No consideration for NULL values

### Green Flags ✅
- Uses window functions effectively
- Mentions QUALIFY clause (BigQuery specific)
- Asks about business rules for keeping records
- Considers performance with large datasets

---

## Question 2: Calculate Running Totals (Medium)

### Problem Statement
```
Given a 'sales' table with columns: sale_date, product_id, daily_revenue
Calculate the running total of revenue for each product over time.
Also calculate the 7-day moving average.
```

### Sample Data
```sql
CREATE TEMP TABLE sales AS
SELECT DATE('2025-01-01') AS sale_date, 'P001' AS product_id, 100.0 AS daily_revenue
UNION ALL SELECT DATE('2025-01-02'), 'P001', 150.0
UNION ALL SELECT DATE('2025-01-03'), 'P001', 200.0
UNION ALL SELECT DATE('2025-01-04'), 'P001', 120.0
UNION ALL SELECT DATE('2025-01-05'), 'P001', 180.0
UNION ALL SELECT DATE('2025-01-01'), 'P002', 300.0
UNION ALL SELECT DATE('2025-01-02'), 'P002', 250.0
UNION ALL SELECT DATE('2025-01-03'), 'P002', 280.0;
```

### Expected Solution
```sql
SELECT 
  sale_date,
  product_id,
  daily_revenue,
  -- Running total
  SUM(daily_revenue) OVER (
    PARTITION BY product_id 
    ORDER BY sale_date
    ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
  ) AS running_total,
  -- 7-day moving average
  AVG(daily_revenue) OVER (
    PARTITION BY product_id 
    ORDER BY sale_date
    ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
  ) AS moving_avg_7d,
  -- Day-over-day change
  daily_revenue - LAG(daily_revenue, 1) OVER (
    PARTITION BY product_id 
    ORDER BY sale_date
  ) AS day_over_day_change
FROM sales
ORDER BY product_id, sale_date;
```

### Key Concepts Being Tested
- ✅ Window functions with ROWS BETWEEN
- ✅ PARTITION BY for grouping
- ✅ LAG/LEAD functions
- ✅ Understanding of moving averages
- ✅ Multiple window functions in single query

### Follow-up Questions
1. "What's the difference between ROWS and RANGE in window functions?"
2. "How would you optimize this query for a table with billions of rows?"
3. "Calculate month-over-month percentage change in revenue."

### Expected Answer to Follow-up #3
```sql
WITH monthly_revenue AS (
  SELECT 
    DATE_TRUNC(sale_date, MONTH) AS month,
    product_id,
    SUM(daily_revenue) AS monthly_revenue
  FROM sales
  GROUP BY month, product_id
)
SELECT 
  month,
  product_id,
  monthly_revenue,
  LAG(monthly_revenue) OVER (PARTITION BY product_id ORDER BY month) AS prev_month_revenue,
  SAFE_DIVIDE(
    monthly_revenue - LAG(monthly_revenue) OVER (PARTITION BY product_id ORDER BY month),
    LAG(monthly_revenue) OVER (PARTITION BY product_id ORDER BY month)
  ) * 100 AS pct_change
FROM monthly_revenue
ORDER BY product_id, month;
```

### Red Flags 🚩
- Uses self-join instead of window functions (inefficient)
- Doesn't understand PARTITION BY
- Cannot explain ROWS vs RANGE
- Doesn't handle NULL values in calculations

### Green Flags ✅
- Uses appropriate window frame clause
- Mentions SAFE_DIVIDE to handle division by zero
- Suggests partitioning/clustering for optimization
- Considers edge cases (first row, NULL values)

---

## Question 3: Find Missing Dates (Medium)

### Problem Statement
```
You have a table 'daily_sales' that should have one record per day.
Find all missing dates in the last 30 days where no sales were recorded.
```

### Sample Data
```sql
CREATE TEMP TABLE daily_sales AS
SELECT DATE('2025-01-01') AS sale_date, 1000.0 AS total_sales
UNION ALL SELECT DATE('2025-01-02'), 1500.0
UNION ALL SELECT DATE('2025-01-04'), 1200.0  -- Missing Jan 3
UNION ALL SELECT DATE('2025-01-05'), 1800.0
UNION ALL SELECT DATE('2025-01-08'), 2000.0  -- Missing Jan 6, 7
UNION ALL SELECT DATE('2025-01-10'), 1600.0;
```

### Expected Solution - Method 1 (GENERATE_DATE_ARRAY)
```sql
WITH date_range AS (
  SELECT date_value
  FROM UNNEST(
    GENERATE_DATE_ARRAY(
      DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY),
      CURRENT_DATE()
    )
  ) AS date_value
)
SELECT 
  d.date_value AS missing_date
FROM date_range d
LEFT JOIN daily_sales s ON d.date_value = s.sale_date
WHERE s.sale_date IS NULL
ORDER BY d.date_value;
```

### Expected Solution - Method 2 (Recursive CTE)
```sql
WITH RECURSIVE date_series AS (
  SELECT DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY) AS date_value
  UNION ALL
  SELECT DATE_ADD(date_value, INTERVAL 1 DAY)
  FROM date_series
  WHERE date_value < CURRENT_DATE()
)
SELECT 
  d.date_value AS missing_date
FROM date_series d
LEFT JOIN daily_sales s ON d.date_value = s.sale_date
WHERE s.sale_date IS NULL
ORDER BY d.date_value;
```

### Key Concepts Being Tested
- ✅ GENERATE_DATE_ARRAY (BigQuery specific)
- ✅ UNNEST for array operations
- ✅ LEFT JOIN to find missing records
- ✅ Date manipulation functions
- ✅ Alternative approaches (CTE vs array)

### Follow-up Questions
1. "How would you fill in the missing dates with 0 for sales?"
2. "What if you need to find missing dates per product_id?"
3. "Which method is more efficient for large date ranges?"

### Expected Answer to Follow-up #1
```sql
WITH date_range AS (
  SELECT date_value
  FROM UNNEST(
    GENERATE_DATE_ARRAY(
      DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY),
      CURRENT_DATE()
    )
  ) AS date_value
)
SELECT 
  d.date_value AS sale_date,
  COALESCE(s.total_sales, 0) AS total_sales
FROM date_range d
LEFT JOIN daily_sales s ON d.date_value = s.sale_date
ORDER BY d.date_value;
```

### Red Flags 🚩
- Cannot think of a date generation approach
- Hardcodes dates instead of using dynamic ranges
- Doesn't know BigQuery-specific functions (for 5+ years)
- No consideration for timezone issues

### Green Flags ✅
- Knows multiple approaches
- Uses GENERATE_DATE_ARRAY efficiently
- Mentions performance implications
- Asks about business context (weekends, holidays)

---

## Question 4: Hierarchical Data Query (Hard)

### Problem Statement
```
Given an 'employees' table with employee_id, employee_name, and manager_id,
write a query to show the full reporting hierarchy from CEO down to all employees.
Include the level in the hierarchy and the full path from CEO.
```

### Sample Data
```sql
CREATE TEMP TABLE employees AS
SELECT 1 AS employee_id, 'Alice (CEO)' AS employee_name, NULL AS manager_id
UNION ALL SELECT 2, 'Bob', 1
UNION ALL SELECT 3, 'Carol', 1
UNION ALL SELECT 4, 'David', 2
UNION ALL SELECT 5, 'Eve', 2
UNION ALL SELECT 6, 'Frank', 3
UNION ALL SELECT 7, 'Grace', 4;
```

### Expected Solution
```sql
WITH RECURSIVE employee_hierarchy AS (
  -- Base case: CEO (no manager)
  SELECT 
    employee_id,
    employee_name,
    manager_id,
    1 AS level,
    employee_name AS hierarchy_path,
    CAST(employee_id AS STRING) AS id_path
  FROM employees
  WHERE manager_id IS NULL
  
  UNION ALL
  
  -- Recursive case: All other employees
  SELECT 
    e.employee_id,
    e.employee_name,
    e.manager_id,
    eh.level + 1,
    CONCAT(eh.hierarchy_path, ' > ', e.employee_name) AS hierarchy_path,
    CONCAT(eh.id_path, ' > ', CAST(e.employee_id AS STRING)) AS id_path
  FROM employees e
  INNER JOIN employee_hierarchy eh ON e.manager_id = eh.employee_id
)
SELECT 
  employee_id,
  employee_name,
  manager_id,
  level,
  hierarchy_path,
  REPEAT('  ', level - 1) || employee_name AS indented_name
FROM employee_hierarchy
ORDER BY id_path;
```

### Alternative Solution - Self-Joins (Limited Depth)
```sql
SELECT 
  e1.employee_id,
  e1.employee_name AS employee,
  e2.employee_name AS manager,
  e3.employee_name AS manager_of_manager,
  e4.employee_name AS top_manager
FROM employees e1
LEFT JOIN employees e2 ON e1.manager_id = e2.employee_id
LEFT JOIN employees e3 ON e2.manager_id = e3.employee_id
LEFT JOIN employees e4 ON e3.manager_id = e4.employee_id;
```

### Key Concepts Being Tested
- ✅ Recursive CTEs
- ✅ Hierarchical data modeling
- ✅ String manipulation (CONCAT, REPEAT)
- ✅ Understanding of graph traversal
- ✅ Self-joins vs recursion

### Follow-up Questions
1. "What are the limitations of recursive CTEs in BigQuery?"
2. "How would you find all employees reporting to a specific manager (direct and indirect)?"
3. "How would you detect circular references in the hierarchy?"

### Expected Answer to Follow-up #2
```sql
WITH RECURSIVE subordinates AS (
  -- Base: The specific manager
  SELECT employee_id, employee_name, manager_id, 0 AS level
  FROM employees
  WHERE employee_id = 2  -- Bob's team
  
  UNION ALL
  
  -- Recursive: All subordinates
  SELECT 
    e.employee_id,
    e.employee_name,
    e.manager_id,
    s.level + 1
  FROM employees e
  INNER JOIN subordinates s ON e.manager_id = s.employee_id
)
SELECT *
FROM subordinates
WHERE level > 0  -- Exclude the manager themselves
ORDER BY level, employee_name;
```

### Red Flags 🚩
- Cannot solve hierarchical queries (critical for 5+ years)
- Only knows self-join approach (limited depth)
- Doesn't understand recursion
- No mention of cycle detection

### Green Flags ✅
- Comfortable with recursive CTEs
- Mentions BigQuery recursion limits (500 iterations)
- Discusses performance with large hierarchies
- Suggests alternative approaches (graph databases, materialized paths)

---

## Question 5: Complex Aggregation with PIVOT (Medium-Hard)

### Problem Statement
```
Given a 'sales' table with: product_id, month, region, revenue
Create a report showing products as rows, months as columns, with revenue values.
Also add a total column.
```

### Sample Data
```sql
CREATE TEMP TABLE sales AS
SELECT 'P001' AS product_id, '2025-01' AS month, 'East' AS region, 1000.0 AS revenue
UNION ALL SELECT 'P001', '2025-02', 'East', 1500.0
UNION ALL SELECT 'P001', '2025-03', 'East', 1200.0
UNION ALL SELECT 'P002', '2025-01', 'West', 2000.0
UNION ALL SELECT 'P002', '2025-02', 'West', 2500.0
UNION ALL SELECT 'P002', '2025-03', 'West', 2200.0;
```

### Expected Solution - Using PIVOT
```sql
SELECT *
FROM (
  SELECT 
    product_id,
    month,
    revenue
  FROM sales
)
PIVOT (
  SUM(revenue) AS total_revenue
  FOR month IN ('2025-01', '2025-02', '2025-03')
);
```

### Expected Solution - Manual Pivot (More Control)
```sql
SELECT 
  product_id,
  SUM(CASE WHEN month = '2025-01' THEN revenue ELSE 0 END) AS jan_2025,
  SUM(CASE WHEN month = '2025-02' THEN revenue ELSE 0 END) AS feb_2025,
  SUM(CASE WHEN month = '2025-03' THEN revenue ELSE 0 END) AS mar_2025,
  SUM(revenue) AS total_revenue,
  COUNT(DISTINCT month) AS months_with_sales
FROM sales
GROUP BY product_id
ORDER BY product_id;
```

### Expected Solution - Dynamic Pivot (Advanced)
```sql
-- First, get distinct months
DECLARE month_list STRING;
SET month_list = (
  SELECT STRING_AGG(DISTINCT CONCAT("'", month, "'"))
  FROM sales
);

-- Create dynamic SQL (in real implementation, use EXECUTE IMMEDIATE)
SELECT 
  product_id,
  -- Use the month_list in PIVOT
FROM sales
PIVOT (
  SUM(revenue)
  FOR month IN (UNNEST([month_list]))
);
```

### Key Concepts Being Tested
- ✅ PIVOT operations
- ✅ CASE WHEN for conditional aggregation
- ✅ Dynamic SQL understanding
- ✅ Multiple aggregation methods
- ✅ Report formatting

### Follow-up Questions
1. "How would you handle unpivoting data (reverse operation)?"
2. "What if you need to pivot multiple metrics (revenue and units)?"
3. "How would you make this dynamic for any number of months?"

### Expected Answer to Follow-up #1 (UNPIVOT)
```sql
-- Convert columns back to rows
SELECT *
FROM sales_pivoted
UNPIVOT (
  revenue FOR month IN (jan_2025, feb_2025, mar_2025)
);
```

### Red Flags 🚩
- Cannot create pivot without using PIVOT keyword (inflexible)
- Doesn't understand CASE WHEN approach
- No consideration for NULL values
- Cannot explain when to use PIVOT vs GROUP BY

### Green Flags ✅
- Knows multiple approaches (PIVOT, CASE WHEN)
- Understands dynamic SQL concepts
- Discusses performance trade-offs
- Mentions UNPIVOT for reverse operation

---

## Question 6: Data Quality Check (Practical)

### Problem Statement
```
You're loading data from a source table 'staging_customers' to 'target_customers'.
Write SQL queries to validate:
1. No duplicate customer_ids
2. Email format is valid
3. All customer_ids exist in the customer_master table
4. No critical fields (name, email) are NULL
5. Created_date is not in the future
```

### Sample Data
```sql
CREATE TEMP TABLE staging_customers AS
SELECT 'C001' AS customer_id, 'John Doe' AS name, 'john@email.com' AS email, DATE('2025-01-01') AS created_date
UNION ALL SELECT 'C002', 'Jane Smith', 'jane.email.com', DATE('2025-01-15')  -- Invalid email
UNION ALL SELECT 'C003', NULL, 'bob@email.com', DATE('2025-01-20')  -- NULL name
UNION ALL SELECT 'C004', 'Alice Brown', 'alice@email.com', DATE('2026-01-01')  -- Future date
UNION ALL SELECT 'C002', 'Jane Duplicate', 'jane2@email.com', DATE('2025-01-16');  -- Duplicate ID
```

### Expected Solution - Comprehensive Validation
```sql
-- 1. Check for duplicates
SELECT 
  'Duplicate Check' AS check_name,
  COUNT(*) - COUNT(DISTINCT customer_id) AS failed_count,
  CASE 
    WHEN COUNT(*) = COUNT(DISTINCT customer_id) THEN 'PASS'
    ELSE 'FAIL'
  END AS status
FROM staging_customers

UNION ALL

-- 2. Email format validation
SELECT 
  'Email Format Check' AS check_name,
  COUNTIF(NOT REGEXP_CONTAINS(email, r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$')) AS failed_count,
  CASE 
    WHEN COUNTIF(NOT REGEXP_CONTAINS(email, r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$')) = 0 
    THEN 'PASS'
    ELSE 'FAIL'
  END AS status
FROM staging_customers

UNION ALL

-- 3. NULL check for critical fields
SELECT 
  'NULL Check - Critical Fields' AS check_name,
  COUNTIF(name IS NULL OR email IS NULL) AS failed_count,
  CASE 
    WHEN COUNTIF(name IS NULL OR email IS NULL) = 0 THEN 'PASS'
    ELSE 'FAIL'
  END AS status
FROM staging_customers

UNION ALL

-- 4. Future date check
SELECT 
  'Future Date Check' AS check_name,
  COUNTIF(created_date > CURRENT_DATE()) AS failed_count,
  CASE 
    WHEN COUNTIF(created_date > CURRENT_DATE()) = 0 THEN 'PASS'
    ELSE 'FAIL'
  END AS status
FROM staging_customers;
```

### Expected Solution - Detailed Error Report
```sql
-- Show all validation errors with record details
SELECT 
  customer_id,
  name,
  email,
  created_date,
  ARRAY_TO_STRING([
    IF(customer_id IN (
      SELECT customer_id 
      FROM staging_customers 
      GROUP BY customer_id 
      HAVING COUNT(*) > 1
    ), 'DUPLICATE_ID', NULL),
    IF(NOT REGEXP_CONTAINS(email, r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'), 
       'INVALID_EMAIL', NULL),
    IF(name IS NULL, 'NULL_NAME', NULL),
    IF(email IS NULL, 'NULL_EMAIL', NULL),
    IF(created_date > CURRENT_DATE(), 'FUTURE_DATE', NULL)
  ], ', ') AS validation_errors
FROM staging_customers
WHERE 
  customer_id IN (
    SELECT customer_id FROM staging_customers 
    GROUP BY customer_id HAVING COUNT(*) > 1
  )
  OR NOT REGEXP_CONTAINS(email, r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$')
  OR name IS NULL
  OR email IS NULL
  OR created_date > CURRENT_DATE();
```

### Key Concepts Being Tested
- ✅ Data quality validation techniques
- ✅ REGEXP_CONTAINS for pattern matching
- ✅ NULL handling
- ✅ COUNTIF for conditional counting
- ✅ Production-ready validation logic
- ✅ Error reporting

### Follow-up Questions
1. "How would you automate these checks in an ETL pipeline?"
2. "What would you do with records that fail validation?"
3. "How would you track data quality metrics over time?"

### Expected Answer to Follow-up #2
```sql
-- Separate good and bad records
CREATE TEMP TABLE valid_records AS
SELECT *
FROM staging_customers
WHERE 
  customer_id NOT IN (
    SELECT customer_id FROM staging_customers 
    GROUP BY customer_id HAVING COUNT(*) > 1
  )
  AND REGEXP_CONTAINS(email, r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$')
  AND name IS NOT NULL
  AND email IS NOT NULL
  AND created_date <= CURRENT_DATE();

-- Move invalid records to quarantine
INSERT INTO quarantine_table (
  table_name,
  record_id,
  record_data,
  validation_errors,
  quarantine_timestamp
)
SELECT 
  'staging_customers' AS table_name,
  customer_id AS record_id,
  TO_JSON_STRING(s) AS record_data,
  -- validation errors logic here
  CURRENT_TIMESTAMP()
FROM staging_customers s
WHERE customer_id NOT IN (SELECT customer_id FROM valid_records);

-- Load only valid records
INSERT INTO target_customers
SELECT * FROM valid_records;
```

### Red Flags 🚩
- Only checks one validation rule
- Doesn't provide detailed error information
- No strategy for handling failed records
- Doesn't use regex for email validation
- No consideration for logging/monitoring

### Green Flags ✅
- Comprehensive validation framework
- Detailed error reporting
- Quarantine strategy for bad records
- Mentions automation and monitoring
- Uses appropriate SQL functions (REGEXP_CONTAINS, COUNTIF)
- Discusses business rules and thresholds

---

## Question 7: Slowly Changing Dimension (Hard)

### Problem Statement
```
Implement SCD Type 2 logic. Given:
- staging_product table (latest data from source)
- dim_product table (historical data warehouse)

Handle:
- New products (insert)
- Changed products (close old record, insert new)
- Unchanged products (do nothing)

Track effective_date, end_date, and is_current flag.
```

### Sample Data
```sql
-- Existing dimension table
CREATE TEMP TABLE dim_product AS
SELECT 1 AS product_key, 'P001' AS product_id, 'Widget' AS product_name, 
       'Electronics' AS category, 10.00 AS price,
       DATE('2024-01-01') AS effective_date, DATE('9999-12-31') AS end_date, 
       TRUE AS is_current
UNION ALL SELECT 2, 'P002', 'Gadget', 'Electronics', 20.00,
       DATE('2024-01-01'), DATE('9999-12-31'), TRUE;

-- New staging data
CREATE TEMP TABLE staging_product AS
SELECT 'P001' AS product_id, 'Widget' AS product_name, 'Electronics' AS category, 15.00 AS price  -- Price changed
UNION ALL SELECT 'P002', 'Gadget', 'Electronics', 20.00  -- No change
UNION ALL SELECT 'P003', 'Doohickey', 'Home', 25.00;  -- New product
```

### Expected Solution
```sql
-- Step 1: Identify changes (new and modified records)
CREATE TEMP TABLE product_changes AS
SELECT 
  s.product_id,
  s.product_name,
  s.category,
  s.price,
  d.product_key,
  CASE 
    WHEN d.product_id IS NULL THEN 'INSERT'
    WHEN (s.product_name != d.product_name OR 
          s.category != d.category OR 
          s.price != d.price) THEN 'UPDATE'
    ELSE 'NO_CHANGE'
  END AS change_type
FROM staging_product s
LEFT JOIN dim_product d 
  ON s.product_id = d.product_id 
  AND d.is_current = TRUE;

-- Step 2: Close out current records that have changes
UPDATE dim_product
SET 
  end_date = CURRENT_DATE(),
  is_current = FALSE
WHERE product_key IN (
  SELECT product_key 
  FROM product_changes 
  WHERE change_type = 'UPDATE'
);

-- Step 3: Insert new versions (both NEW and UPDATED records)
INSERT INTO dim_product (
  product_key,
  product_id,
  product_name,
  category,
  price,
  effective_date,
  end_date,
  is_current
)
SELECT 
  ROW_NUMBER() OVER (ORDER BY product_id) + 
    (SELECT COALESCE(MAX(product_key), 0) FROM dim_product) AS product_key,
  product_id,
  product_name,
  category,
  price,
  CURRENT_DATE() AS effective_date,
  DATE('9999-12-31') AS end_date,
  TRUE AS is_current
FROM product_changes
WHERE change_type IN ('INSERT', 'UPDATE');

-- Step 4: Verify results
SELECT 
  product_key,
  product_id,
  product_name,
  price,
  effective_date,
  end_date,
  is_current
FROM dim_product
ORDER BY product_id, effective_date;
```

### Alternative Solution - Using MERGE
```sql
MERGE dim_product AS target
USING (
  SELECT 
    s.*,
    d.product_key,
    d.is_current
  FROM staging_product s
  LEFT JOIN dim_product d 
    ON s.product_id = d.product_id 
    AND d.is_current = TRUE
) AS source
ON target.product_id = source.product_id 
   AND target.is_current = TRUE

-- When matched and data changed: Close current record
WHEN MATCHED AND (
  source.product_name != target.product_name OR
  source.category != target.category OR
  source.price != target.price
) THEN UPDATE SET
  end_date = CURRENT_DATE(),
  is_current = FALSE

-- When not matched: Insert new product
WHEN NOT MATCHED BY TARGET THEN INSERT (
  product_key,
  product_id,
  product_name,
  category,
  price,
  effective_date,
  end_date,
  is_current
) VALUES (
  GENERATE_UUID(),
  source.product_id,
  source.product_name,
  source.category,
  source.price,
  CURRENT_DATE(),
  DATE('9999-12-31'),
  TRUE
);

-- Insert new versions for updated records (separate statement needed)
-- This is a limitation of MERGE - cannot insert AND update same record
```

### Key Concepts Being Tested
- ✅ SCD Type 2 understanding
- ✅ Change detection logic
- ✅ Surrogate key management
- ✅ Effective dating strategy
- ✅ MERGE statement vs multi-step approach
- ✅ Historical data preservation

### Follow-up Questions
1. "How would you handle deletes from the source system?"
2. "What's the difference between SCD Type 1, 2, and 3?"
3. "How would you query this table to get data as of a specific date?"
4. "What are the storage implications of SCD Type 2?"

### Expected Answer to Follow-up #3
```sql
-- Point-in-time query: Get product data as of 2024-06-01
SELECT 
  product_id,
  product_name,
  category,
  price
FROM dim_product
WHERE DATE('2024-06-01') BETWEEN effective_date AND end_date;

-- Current snapshot
SELECT 
  product_id,
  product_name,
  category,
  price
FROM dim_product
WHERE is_current = TRUE;

-- Historical changes for a product
SELECT 
  product_id,
  product_name,
  price,
  effective_date,
  end_date,
  DATE_DIFF(end_date, effective_date, DAY) AS days_active
FROM dim_product
WHERE product_id = 'P001'
ORDER BY effective_date;
```

### Red Flags 🚩
- Doesn't understand SCD concepts (critical for 5+ years)
- Modifies natural keys instead of using surrogate keys
- Deletes historical records (data loss)
- No consideration for NULL values in comparisons
- Cannot explain business need for Type 2

### Green Flags ✅
- Clear understanding of SCD Type 2
- Proper use of surrogate keys
- Considers all scenarios (insert, update, no change)
- Mentions hash comparison for efficient change detection
- Discusses storage and performance trade-offs
- Can explain alternative SCD types

---

## Question 8: Performance Optimization (Hard)

### Problem Statement
```
Given this slow-running query, identify performance issues and rewrite it for optimal performance in BigQuery:

```sql
SELECT 
  c.customer_name,
  p.product_name,
  SUM(o.amount) AS total_spent
FROM orders o
JOIN customers c ON o.customer_id = c.customer_id
JOIN products p ON o.product_id = p.product_id
WHERE o.order_date >= '2020-01-01'
  AND c.region = 'North'
GROUP BY c.customer_name, p.product_name
ORDER BY total_spent DESC;
```

Assume:
- orders table: 1 billion rows
- customers table: 10 million rows
- products table: 100,000 rows
```

### Expected Analysis by Candidate
The candidate should identify these issues:
1. ❌ No partitioning/clustering mentioned
2. ❌ Filtering after JOIN (should filter before)
3. ❌ SELECT * might be used upstream (not shown here but common)
4. ❌ No consideration for NULL values
5. ❌ Large date range without partition filter

### Expected Optimized Solution
```sql
-- Optimized version with best practices
WITH filtered_orders AS (
  SELECT 
    customer_id,
    product_id,
    amount
  FROM `project.dataset.orders`
  WHERE DATE(order_date) >= '2020-01-01'  -- Partition filter
    AND order_date IS NOT NULL
),
filtered_customers AS (
  SELECT 
    customer_id,
    customer_name
  FROM `project.dataset.customers`
  WHERE region = 'North'
)
SELECT 
  c.customer_name,
  p.product_name,
  SUM(o.amount) AS total_spent,
  COUNT(*) AS order_count
FROM filtered_orders o
INNER JOIN filtered_customers c 
  ON o.customer_id = c.customer_id
INNER JOIN `project.dataset.products` p 
  ON o.product_id = p.product_id
GROUP BY c.customer_name, p.product_name
ORDER BY total_spent DESC
LIMIT 1000;  -- Add LIMIT if showing top results
```

### Additional Optimizations to Suggest
```sql
-- 1. Pre-aggregation if appropriate
WITH order_summary AS (
  SELECT 
    customer_id,
    product_id,
    SUM(amount) AS total_amount,
    COUNT(*) AS order_count
  FROM `project.dataset.orders`
  WHERE DATE(order_date) >= '2020-01-01'
  GROUP BY customer_id, product_id
)
SELECT 
  c.customer_name,
  p.product_name,
  o.total_amount,
  o.order_count
FROM order_summary o
INNER JOIN `project.dataset.customers` c 
  ON o.customer_id = c.customer_id
  AND c.region = 'North'
INNER JOIN `project.dataset.products` p 
  ON o.product_id = p.product_id
ORDER BY total_amount DESC;

-- 2. Using APPROX_COUNT_DISTINCT for large datasets
SELECT 
  c.customer_name,
  APPROX_COUNT_DISTINCT(o.order_id) AS approx_order_count,
  SUM(o.amount) AS total_spent
FROM filtered_orders o
INNER JOIN filtered_customers c USING (customer_id)
GROUP BY c.customer_name;
```

### Table Design Recommendations
```sql
-- Optimal table structure for orders
CREATE TABLE `project.dataset.orders_optimized` (
  order_id STRING,
  customer_id STRING,
  product_id STRING,
  amount NUMERIC,
  order_date DATE,
  order_timestamp TIMESTAMP
)
PARTITION BY order_date
CLUSTER BY customer_id, product_id
OPTIONS (
  require_partition_filter = TRUE,
  partition_expiration_days = 1095  -- 3 years
);
```

### Key Concepts Being Tested
- ✅ BigQuery performance optimization
- ✅ Understanding of partitioning and clustering
- ✅ Query execution order
- ✅ Pre-filtering vs post-filtering
- ✅ CTE usage for readability and performance
- ✅ APPROX functions for large datasets
- ✅ Cost optimization awareness

### Follow-up Questions
1. "How would you determine if partitioning helps this specific query?"
2. "Explain the difference between INNER JOIN and LEFT JOIN performance."
3. "What BigQuery features would you use to monitor query performance?"
4. "How would you handle this if results need to be real-time?"

### Expected Answer to Follow-up #3
```
- Query Execution Plan in BigQuery UI
- INFORMATION_SCHEMA.JOBS for query statistics
- Bytes processed and bytes billed
- Slot usage and execution time
- Table scan metrics
- Query plan stages and shuffle operations
```

### Red Flags 🚩
- Cannot identify obvious performance issues
- Doesn't know about partitioning/clustering (critical)
- No mention of filtering early
- Doesn't understand BigQuery pricing model
- Cannot explain query execution order
- Suggests irrelevant optimizations

### Green Flags ✅
- Systematic approach to identifying issues
- Mentions specific BigQuery features (partitioning, clustering)
- Discusses cost vs performance trade-offs
- Suggests monitoring and testing
- Knows about APPROX functions
- Recommends table redesign if needed
- Asks about query frequency and SLA requirements

---

## Question 9: Incremental Load Strategy (Practical)

### Problem Statement
```
Design an incremental load process for a 'customer_orders' table.
Requirements:
- Load only new/updated records since last run
- Track load history with watermarks
- Handle scenario where source system doesn't have updated_timestamp
- Ensure no data loss if process fails mid-way
```

### Expected Solution - With Timestamp
```sql
-- 1. Watermark table
CREATE TABLE IF NOT EXISTS `project.metadata.watermarks` (
  table_name STRING,
  last_watermark TIMESTAMP,
  load_timestamp TIMESTAMP,
  records_loaded INT64,
  status STRING
);

-- 2. Incremental load procedure
CREATE OR REPLACE PROCEDURE `project.etl.incremental_load_orders`()
BEGIN
  DECLARE last_watermark TIMESTAMP;
  DECLARE current_watermark TIMESTAMP;
  DECLARE rows_loaded INT64;
  
  -- Get last successful watermark
  SET last_watermark = (
    SELECT MAX(last_watermark)
    FROM `project.metadata.watermarks`
    WHERE table_name = 'customer_orders'
      AND status = 'SUCCESS'
  );
  
  -- First run: load all historical data
  IF last_watermark IS NULL THEN
    SET last_watermark = TIMESTAMP('1900-01-01');
  END IF;
  
  -- Get max timestamp from source (before loading)
  SET current_watermark = (
    SELECT MAX(updated_timestamp)
    FROM `project.source.customer_orders`
  );
  
  -- Load incremental data with transaction
  BEGIN TRANSACTION;
  
  INSERT INTO `project.target.customer_orders` (
    order_id,
    customer_id,
    order_date,
    amount,
    updated_timestamp,
    etl_load_timestamp
  )
  SELECT 
    order_id,
    customer_id,
    order_date,
    amount,
    updated_timestamp,
    CURRENT_TIMESTAMP() AS etl_load_timestamp
  FROM `project.source.customer_orders`
  WHERE updated_timestamp > last_watermark
    AND updated_timestamp <= current_watermark;
  
  SET rows_loaded = @@row_count;
  
  -- Update watermark
  INSERT INTO `project.metadata.watermarks` (
    table_name,
    last_watermark,
    load_timestamp,
    records_loaded,
    status
  )
  VALUES (
    'customer_orders',
    current_watermark,
    CURRENT_TIMESTAMP(),
    rows_loaded,
    'SUCCESS'
  );
  
  COMMIT TRANSACTION;
  
EXCEPTION WHEN ERROR THEN
  ROLLBACK TRANSACTION;
  
  -- Log error
  INSERT INTO `project.metadata.watermarks` (
    table_name,
    load_timestamp,
    status
  )
  VALUES (
    'customer_orders',
    CURRENT_TIMESTAMP(),
    CONCAT('FAILED: ', @@error.message)
  );
  
  RAISE USING MESSAGE = @@error.message;
END;
```

### Expected Solution - Without Timestamp (Using CDC)
```sql
-- Alternative: Using hash-based change detection
CREATE OR REPLACE PROCEDURE `project.etl.incremental_load_no_timestamp`()
BEGIN
  -- Load full source to staging
  CREATE OR REPLACE TEMP TABLE staging_orders AS
  SELECT 
    *,
    TO_HEX(MD5(CONCAT(
      CAST(order_id AS STRING),
      CAST(customer_id AS STRING),
      CAST(amount AS STRING),
      CAST(order_date AS STRING)
    ))) AS row_hash
  FROM `project.source.customer_orders`;
  
  -- Compare with target
  CREATE OR REPLACE TEMP TABLE target_with_hash AS
  SELECT 
    *,
    TO_HEX(MD5(CONCAT(
      CAST(order_id AS STRING),
      CAST(customer_id AS STRING),
      CAST(amount AS STRING),
      CAST(order_date AS STRING)
    ))) AS row_hash
  FROM `project.target.customer_orders`;
  
  -- Identify new and changed records
  INSERT INTO `project.target.customer_orders`
  SELECT 
    s.* EXCEPT(row_hash),
    CURRENT_TIMESTAMP() AS etl_load_timestamp
  FROM staging_orders s
  LEFT JOIN target_with_hash t 
    ON s.order_id = t.order_id
  WHERE t.order_id IS NULL  -- New records
    OR s.row_hash != t.row_hash;  -- Changed records
END;
```

### Key Concepts Being Tested
- ✅ Watermark pattern for incremental loads
- ✅ Transaction management
- ✅ Error handling and recovery
- ✅ Change detection strategies
- ✅ Idempotency
- ✅ Metadata tracking

### Follow-up Questions
1. "How would you handle late-arriving data?"
2. "What if source system deletes records - how would you capture that?"
3. "How would you backfill historical data without disrupting incremental loads?"
4. "What's the difference between ETL and ELT in this context?"

### Expected Answer to Follow-up #2
```sql
-- Soft delete detection
CREATE TEMP TABLE deleted_records AS
SELECT t.order_id
FROM `project.target.customer_orders` t
LEFT JOIN `project.source.customer_orders` s 
  ON t.order_id = s.order_id
WHERE s.order_id IS NULL
  AND t.is_active = TRUE;

-- Mark as deleted
UPDATE `project.target.customer_orders`
SET 
  is_active = FALSE,
  deleted_timestamp = CURRENT_TIMESTAMP()
WHERE order_id IN (SELECT order_id FROM deleted_records);
```

### Red Flags 🚩
- Only knows full reload approach
- No error handling or recovery mechanism
- Doesn't understand watermarks
- Cannot handle missing timestamp scenario
- No consideration for idempotency
- Doesn't track metadata

### Green Flags ✅
- Complete understanding of incremental patterns
- Implements proper error handling
- Uses transactions appropriately
- Multiple strategies (timestamp, hash, CDC)
- Considers edge cases (first run, failures, late data)
- Mentions monitoring and alerting
- Discusses testing strategy

---

## Question 10: Window Functions Advanced (Hard)

### Problem Statement
```
Given a 'transactions' table, calculate:
1. Running balance for each account
2. 7-day rolling average transaction amount
3. Rank transactions by amount within each account
4. Identify gaps > 30 days between transactions
5. Calculate the difference from the account's average
```

### Sample Data
```sql
CREATE TEMP TABLE transactions AS
SELECT 1 AS account_id, DATE('2025-01-01') AS txn_date, 100.0 AS amount, 'DEPOSIT' AS type
UNION ALL SELECT 1, DATE('2025-01-05'), -50.0, 'WITHDRAWAL'
UNION ALL SELECT 1, DATE('2025-01-10'), 200.0, 'DEPOSIT'
UNION ALL SELECT 1, DATE('2025-01-15'), -75.0, 'WITHDRAWAL'
UNION ALL SELECT 1, DATE('2025-02-20'), 150.0, 'DEPOSIT'
UNION ALL SELECT 2, DATE('2025-01-02'), 500.0, 'DEPOSIT'
UNION ALL SELECT 2, DATE('2025-01-08'), -100.0, 'WITHDRAWAL';
```

### Expected Solution
```sql
WITH transaction_analysis AS (
  SELECT 
    account_id,
    txn_date,
    amount,
    type,
    -- 1. Running balance
    SUM(amount) OVER (
      PARTITION BY account_id 
      ORDER BY txn_date
      ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS running_balance,
    -- 2. 7-day rolling average
    AVG(amount) OVER (
      PARTITION BY account_id 
      ORDER BY txn_date
      RANGE BETWEEN INTERVAL 6 DAY PRECEDING AND CURRENT ROW
    ) AS rolling_avg_7d,
    -- 3. Rank by amount
    RANK() OVER (
      PARTITION BY account_id 
      ORDER BY ABS(amount) DESC
    ) AS amount_rank,
    -- 4. Days since last transaction
    DATE_DIFF(
      txn_date,
      LAG(txn_date) OVER (PARTITION BY account_id ORDER BY txn_date),
      DAY
    ) AS days_since_last_txn,
    -- 5. Difference from account average
    amount - AVG(amount) OVER (PARTITION BY account_id) AS diff_from_avg,
    -- Additional: Transaction number
    ROW_NUMBER() OVER (PARTITION BY account_id ORDER BY txn_date) AS txn_number,
    -- Total transactions for account
    COUNT(*) OVER (PARTITION BY account_id) AS total_txns
  FROM transactions
)
SELECT 
  account_id,
  txn_date,
  type,
  amount,
  running_balance,
  ROUND(rolling_avg_7d, 2) AS rolling_avg_7d,
  amount_rank,
  days_since_last_txn,
  CASE 
    WHEN days_since_last_txn > 30 THEN 'ALERT: Large Gap'
    ELSE 'Normal'
  END AS gap_alert,
  ROUND(diff_from_avg, 2) AS diff_from_avg,
  txn_number,
  total_txns
FROM transaction_analysis
ORDER BY account_id, txn_date;
```

### Advanced Follow-up
```sql
-- Identify suspicious patterns
WITH patterns AS (
  SELECT 
    account_id,
    txn_date,
    amount,
    -- Detect rapid succession of withdrawals
    COUNT(*) OVER (
      PARTITION BY account_id
      ORDER BY txn_date
      RANGE BETWEEN INTERVAL 1 DAY PRECEDING AND CURRENT ROW
    ) AS txns_in_24h,
    -- Large deviation from normal
    ABS(amount - AVG(amount) OVER (PARTITION BY account_id)) / 
      STDDEV(amount) OVER (PARTITION BY account_id) AS z_score,
    -- First transaction flag
    ROW_NUMBER() OVER (PARTITION BY account_id ORDER BY txn_date) AS txn_seq
  FROM transactions
  WHERE type = 'WITHDRAWAL'
)
SELECT 
  account_id,
  txn_date,
  amount,
  'Suspicious: Multiple withdrawals in 24h' AS alert_reason
FROM patterns
WHERE txns_in_24h >= 3

UNION ALL

SELECT 
  account_id,
  txn_date,
  amount,
  'Suspicious: Abnormal amount' AS alert_reason
FROM patterns
WHERE z_score > 2
AND txn_seq > 5;  -- Exclude first few transactions (establishing baseline)
```

### Key Concepts Being Tested
- ✅ Multiple window functions
- ✅ ROWS vs RANGE window frames
- ✅ LAG/LEAD for inter-row calculations
- ✅ Complex window specifications
- ✅ RANK vs DENSE_RANK vs ROW_NUMBER
- ✅ Statistical functions (STDDEV, AVG)
- ✅ Pattern detection

### Follow-up Questions
1. "Explain the difference between ROWS and RANGE in window functions."
2. "How would you optimize this query for billions of rows?"
3. "Calculate the median transaction amount per account."
4. "Find accounts with declining balance trends."

### Expected Answer to Follow-up #1
```
ROWS: Physical row-based window
- Counts actual rows
- Example: "3 rows before current row"
- More predictable performance

RANGE: Logical value-based window
- Groups rows with same ORDER BY value
- Example: "All rows within 7 days"
- Can include variable number of rows
- Better for time-series analysis
```

### Expected Answer to Follow-up #3
```sql
-- Median using PERCENTILE_CONT
SELECT 
  account_id,
  PERCENTILE_CONT(amount, 0.5) OVER (PARTITION BY account_id) AS median_amount,
  AVG(amount) OVER (PARTITION BY account_id) AS avg_amount
FROM transactions
QUALIFY ROW_NUMBER() OVER (PARTITION BY account_id) = 1;  -- One row per account
```

### Red Flags 🚩
- Cannot explain ROWS vs RANGE
- Doesn't know difference between RANK/DENSE_RANK/ROW_NUMBER
- Uses self-joins instead of window functions
- Cannot combine multiple window functions
- Doesn't understand window frame specification
- No consideration for performance

### Green Flags ✅
- Fluent with complex window functions
- Understands frame specifications (ROWS, RANGE)
- Uses QUALIFY clause efficiently
- Combines multiple analytics in single query
- Discusses performance implications
- Suggests appropriate indexes/clustering
- Can explain execution order

---

## 📋 Quick Interview Flow Template

### 1. Warm-up (5 minutes)
- Current role and responsibilities
- Daily SQL tasks
- Favorite SQL feature in BigQuery

### 2. Technical Questions (40-45 minutes)
Choose 4-5 questions based on role requirements:
- **Junior-Mid (2-4 years):** Q1, Q2, Q3, Q6
- **Mid-Senior (4-6 years):** Q2, Q4, Q6, Q7, Q8
- **Senior (6+ years):** Q4, Q7, Q8, Q9, Q10

### 3. Scenario-based Discussion (10 minutes)
Pick one:
- "Design an ETL pipeline for real-time customer data"
- "Debug a failing data quality check"
- "Optimize a slow-running dashboard query"

### 4. Questions from Candidate (5 minutes)

---

## 🎯 Evaluation Criteria

### Technical Skills (40%)
- ✅ SQL syntax accuracy
- ✅ Query optimization awareness
- ✅ BigQuery-specific knowledge
- ✅ Problem-solving approach

### Production Readiness (30%)
- ✅ Error handling
- ✅ Data quality considerations
- ✅ Monitoring and logging
- ✅ Scalability thinking

### Communication (20%)
- ✅ Explains thought process
- ✅ Asks clarifying questions
- ✅ Discusses trade-offs
- ✅ Clean, readable code

### Experience Level (10%)
- ✅ Real-world examples
- ✅ Lessons learned
- ✅ Best practices knowledge
- ✅ Tool familiarity

---

## 🚨 Red Flags Summary

1. **Cannot solve basic problems** (Q1, Q2) for 5+ years exp
2. **No BigQuery-specific knowledge** (partitioning, clustering)
3. **Hardcodes values** instead of parameterizing
4. **Ignores NULL handling**
5. **No error handling or logging**
6. **Cannot explain their code**
7. **Only knows one approach** to problems
8. **No consideration for scale/performance**
9. **Doesn't ask clarifying questions**
10. **Cannot discuss production scenarios**

---

## ✅ Green Flags Summary

1. **Asks clarifying questions** before coding
2. **Multiple solution approaches**
3. **Discusses trade-offs** (performance vs readability)
4. **Production-ready code** (error handling, logging)
5. **BigQuery expertise** (partitioning, clustering, materialized views)
6. **Optimization mindset** (cost and performance)
7. **Clean, commented code**
8. **Real-world examples** from experience
9. **Data quality focus**
10. **Troubleshooting methodology**

---

## 💡 Tips for Interviewers

1. **Allow candidates to ask questions** - shows they think about requirements
2. **Don't expect perfect syntax** - focus on logic and approach
3. **Ask "why"** questions - understand their reasoning
4. **Probe on optimization** - separate junior from senior
5. **Discuss failures** - "Tell me about a data pipeline that failed..."
6. **Real-world scenarios** - better than theoretical questions
7. **Watch for copy-paste** - ask them to modify their solution
8. **Time management** - give hints if stuck too long
9. **Collaborative approach** - interview is a conversation, not interrogation
10. **Provide context** - table sizes, SLAs, business requirements

---

## 📊 Scoring Sheet Template

| Category | Score (1-5) | Notes |
|----------|-------------|-------|
| SQL Fundamentals | | Joins, aggregations, subqueries |
| Window Functions | | Complex analytics, proper usage |
| BigQuery Knowledge | | Partitioning, clustering, cost awareness |
| Performance Optimization | | Query tuning, scalability |
| Data Quality | | Validation, testing, monitoring |
| ETL/ELT Concepts | | Incremental loads, CDC, SCD |
| Error Handling | | Production-ready code |
| Problem Solving | | Approach, creativity |
| Communication | | Explains clearly, asks questions |
| Experience Level | | Real-world examples |
| **Total** | **/50** | |

**Scoring Guide:**
- **40-50:** Strong hire
- **30-39:** Hire with reservations
- **20-29:** Maybe - depends on other factors
- **Below 20:** No hire

---

Good luck with your interviews! 🎯
