# SQL Quick Reference Cheat Sheet
## For ETL QA Interviews (BigQuery Focus)

---

## 🎯 Core Concepts to Test

### 1. Window Functions
```sql
-- ROW_NUMBER: Unique sequential number
ROW_NUMBER() OVER (PARTITION BY category ORDER BY sale_date)

-- RANK: Same rank for ties, skips next rank
RANK() OVER (ORDER BY revenue DESC)

-- DENSE_RANK: Same rank for ties, consecutive ranks
DENSE_RANK() OVER (ORDER BY revenue DESC)

-- Running Total
SUM(amount) OVER (
  PARTITION BY customer_id 
  ORDER BY order_date
  ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
)

-- Moving Average (7 days)
AVG(amount) OVER (
  PARTITION BY customer_id 
  ORDER BY order_date
  ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
)

-- LAG/LEAD: Access previous/next row
LAG(amount, 1) OVER (PARTITION BY account_id ORDER BY date)
LEAD(amount, 1, 0) OVER (PARTITION BY account_id ORDER BY date)

-- FIRST_VALUE/LAST_VALUE
FIRST_VALUE(product_name) OVER (PARTITION BY category ORDER BY revenue DESC)

-- PERCENTILE
PERCENTILE_CONT(amount, 0.5) OVER (PARTITION BY region)  -- Median
```

### 2. Deduplication Techniques
```sql
-- Method 1: Using ROW_NUMBER
SELECT * EXCEPT(row_num)
FROM (
  SELECT 
    *,
    ROW_NUMBER() OVER (
      PARTITION BY order_id 
      ORDER BY updated_timestamp DESC
    ) AS row_num
  FROM orders
)
WHERE row_num = 1;

-- Method 2: Using QUALIFY (BigQuery specific)
SELECT *
FROM orders
QUALIFY ROW_NUMBER() OVER (
  PARTITION BY order_id 
  ORDER BY updated_timestamp DESC
) = 1;

-- Method 3: Using GROUP BY (for exact duplicates)
SELECT DISTINCT * FROM orders;

-- Method 4: ARRAY_AGG to keep all versions
SELECT 
  order_id,
  ARRAY_AGG(
    STRUCT(order_date, amount, updated_timestamp) 
    ORDER BY updated_timestamp DESC 
    LIMIT 5
  ) AS order_history
FROM orders
GROUP BY order_id;
```

### 3. Date Functions (BigQuery)
```sql
-- Current date/time
CURRENT_DATE()
CURRENT_TIMESTAMP()
CURRENT_DATETIME()

-- Date arithmetic
DATE_ADD(order_date, INTERVAL 7 DAY)
DATE_SUB(order_date, INTERVAL 1 MONTH)
TIMESTAMP_ADD(ts, INTERVAL 2 HOUR)

-- Date difference
DATE_DIFF(end_date, start_date, DAY)
TIMESTAMP_DIFF(ts1, ts2, HOUR)

-- Date truncation
DATE_TRUNC(order_date, MONTH)
DATE_TRUNC(order_date, WEEK(MONDAY))
TIMESTAMP_TRUNC(timestamp_col, HOUR)

-- Extract parts
EXTRACT(YEAR FROM order_date)
EXTRACT(QUARTER FROM order_date)
EXTRACT(DAYOFWEEK FROM order_date)  -- 1=Sunday, 7=Saturday
FORMAT_DATE('%Y-%m', order_date)

-- Generate date range
GENERATE_DATE_ARRAY(
  DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY),
  CURRENT_DATE()
)

-- Last day of month
LAST_DAY(order_date, MONTH)
```

### 4. String Functions
```sql
-- Concatenation
CONCAT(first_name, ' ', last_name)
CONCAT_WS(', ', city, state, zip)  -- With separator
ARRAY_TO_STRING(['a', 'b', 'c'], '|')

-- Case conversion
UPPER(name)
LOWER(email)
INITCAP(title)

-- Trimming
TRIM(name)
LTRIM(name)
RTRIM(name)

-- Substring
SUBSTR(product_id, 1, 3)
LEFT(order_id, 4)
RIGHT(order_id, 6)

-- Pattern matching
LIKE '%gmail.com'
REGEXP_CONTAINS(email, r'^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}$')
REGEXP_EXTRACT(phone, r'(\d{3})-(\d{3})-(\d{4})')
REGEXP_REPLACE(text, r'[^a-zA-Z0-9]', '')

-- Splitting
SPLIT(email, '@')[OFFSET(0)]  -- Get part before @
SPLIT(email, '@')[SAFE_OFFSET(1)]  -- Safe version
```

### 5. Aggregation Functions
```sql
-- Standard aggregations
COUNT(*), COUNT(DISTINCT customer_id)
SUM(revenue), AVG(revenue)
MIN(order_date), MAX(order_date)

-- Conditional aggregation
COUNT(CASE WHEN status = 'completed' THEN 1 END)
SUM(IF(region = 'East', revenue, 0))
COUNTIF(amount > 1000)

-- Approximate functions (faster for large datasets)
APPROX_COUNT_DISTINCT(customer_id)
APPROX_QUANTILES(amount, 100)  -- Percentiles

-- Statistical functions
STDDEV(amount)
VARIANCE(amount)
CORR(revenue, profit)

-- String aggregation
STRING_AGG(product_name, ', ' ORDER BY product_name)
ARRAY_AGG(order_id ORDER BY order_date DESC LIMIT 10)
```

### 6. JOIN Types
```sql
-- INNER JOIN: Only matching records
SELECT *
FROM orders o
INNER JOIN customers c ON o.customer_id = c.customer_id;

-- LEFT JOIN: All from left, matching from right
SELECT *
FROM orders o
LEFT JOIN customers c ON o.customer_id = c.customer_id;

-- RIGHT JOIN: All from right, matching from left
SELECT *
FROM orders o
RIGHT JOIN customers c ON o.customer_id = c.customer_id;

-- FULL OUTER JOIN: All records from both
SELECT *
FROM orders o
FULL OUTER JOIN customers c ON o.customer_id = c.customer_id;

-- CROSS JOIN: Cartesian product
SELECT *
FROM date_dim d
CROSS JOIN product_dim p;

-- Self-join: Compare rows within same table
SELECT 
  e1.employee_name,
  e2.employee_name AS manager_name
FROM employees e1
LEFT JOIN employees e2 ON e1.manager_id = e2.employee_id;
```

### 7. Subqueries & CTEs
```sql
-- Subquery in WHERE
SELECT *
FROM orders
WHERE customer_id IN (
  SELECT customer_id 
  FROM customers 
  WHERE region = 'North'
);

-- Subquery in SELECT
SELECT 
  customer_id,
  order_date,
  amount,
  (SELECT AVG(amount) FROM orders) AS overall_avg
FROM orders;

-- CTE (Common Table Expression)
WITH high_value_customers AS (
  SELECT customer_id, SUM(amount) AS total_spent
  FROM orders
  GROUP BY customer_id
  HAVING total_spent > 10000
)
SELECT o.*
FROM orders o
INNER JOIN high_value_customers hvc 
  ON o.customer_id = hvc.customer_id;

-- Recursive CTE
WITH RECURSIVE numbers AS (
  SELECT 1 AS n
  UNION ALL
  SELECT n + 1
  FROM numbers
  WHERE n < 10
)
SELECT * FROM numbers;
```

### 8. CASE Statements
```sql
-- Simple CASE
CASE status
  WHEN 'pending' THEN 'In Progress'
  WHEN 'completed' THEN 'Done'
  ELSE 'Unknown'
END

-- Searched CASE
CASE 
  WHEN amount > 10000 THEN 'High'
  WHEN amount > 1000 THEN 'Medium'
  ELSE 'Low'
END AS value_tier

-- Nested CASE
CASE 
  WHEN region = 'North' THEN
    CASE 
      WHEN amount > 5000 THEN 'North-High'
      ELSE 'North-Low'
    END
  ELSE 'Other'
END

-- CASE in aggregation
SUM(CASE WHEN status = 'completed' THEN amount ELSE 0 END) AS completed_revenue
```

### 9. NULL Handling
```sql
-- Check for NULL
WHERE column_name IS NULL
WHERE column_name IS NOT NULL

-- Replace NULL
COALESCE(phone, mobile, 'N/A')  -- First non-null value
IFNULL(discount, 0)
NULLIF(value1, value2)  -- Returns NULL if equal

-- Safe division
SAFE_DIVIDE(numerator, denominator)  -- Returns NULL instead of error

-- Safe array access
array_column[SAFE_OFFSET(0)]

-- NULL in comparisons
CASE WHEN col1 = col2 OR (col1 IS NULL AND col2 IS NULL) THEN 'Match' END
```

### 10. Array & Struct Operations
```sql
-- Create array
ARRAY[1, 2, 3, 4, 5]
ARRAY_AGG(product_id)
GENERATE_ARRAY(1, 100, 10)  -- Start, end, step

-- Access array elements
array_col[OFFSET(0)]  -- First element (0-based)
array_col[SAFE_OFFSET(5)]  -- Safe access
array_col[ORDINAL(1)]  -- First element (1-based)

-- Array functions
ARRAY_LENGTH(tags)
ARRAY_CONCAT(array1, array2)
ARRAY_TO_STRING(tags, ', ')

-- UNNEST: Convert array to rows
SELECT tag
FROM products,
UNNEST(tags) AS tag;

-- Struct operations
STRUCT(name, age, city) AS person
person.name
person.*

-- Array of structs
ARRAY_AGG(STRUCT(product_id, quantity, price))

-- Filter array
ARRAY(
  SELECT x 
  FROM UNNEST(array_col) AS x 
  WHERE x > 10
)
```

### 11. Partitioning & Clustering (BigQuery)
```sql
-- Create partitioned table
CREATE TABLE dataset.table_name (
  column1 STRING,
  column2 INT64,
  partition_date DATE
)
PARTITION BY partition_date
OPTIONS(
  partition_expiration_days = 90,
  require_partition_filter = TRUE
);

-- Partition by timestamp
PARTITION BY DATE(timestamp_column)

-- Partition by integer range
PARTITION BY RANGE_BUCKET(customer_id, GENERATE_ARRAY(0, 100000, 1000))

-- Clustering
CREATE TABLE dataset.table_name (
  column1 STRING,
  column2 INT64,
  partition_date DATE
)
PARTITION BY partition_date
CLUSTER BY column1, column2;

-- Query with partition filter
SELECT *
FROM dataset.table_name
WHERE partition_date = '2025-01-01';  -- Scans only one partition
```

### 12. Data Quality Checks
```sql
-- Null check
SELECT 
  'customer_id' AS column_name,
  COUNT(*) AS total_rows,
  COUNTIF(customer_id IS NULL) AS null_count,
  COUNTIF(customer_id IS NULL) / COUNT(*) * 100 AS null_pct
FROM orders;

-- Duplicate check
SELECT 
  order_id,
  COUNT(*) AS duplicate_count
FROM orders
GROUP BY order_id
HAVING COUNT(*) > 1;

-- Referential integrity
SELECT COUNT(DISTINCT o.customer_id) AS orphan_count
FROM orders o
LEFT JOIN customers c ON o.customer_id = c.customer_id
WHERE c.customer_id IS NULL;

-- Range check
SELECT COUNT(*) AS invalid_count
FROM orders
WHERE amount < 0 OR amount > 1000000;

-- Format validation
SELECT COUNT(*) AS invalid_emails
FROM customers
WHERE NOT REGEXP_CONTAINS(email, r'^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}$');

-- Completeness check
SELECT 
  COUNT(*) AS total_records,
  COUNT(*) - COUNT(DISTINCT order_id) AS duplicate_count,
  COUNTIF(customer_id IS NULL) AS missing_customer,
  COUNTIF(order_date IS NULL) AS missing_date
FROM orders;
```

### 13. MERGE Statement (Upsert)
```sql
MERGE target_table T
USING source_table S
ON T.id = S.id

-- Update existing records
WHEN MATCHED THEN
  UPDATE SET
    T.name = S.name,
    T.updated_at = CURRENT_TIMESTAMP()

-- Insert new records
WHEN NOT MATCHED BY TARGET THEN
  INSERT (id, name, created_at)
  VALUES (S.id, S.name, CURRENT_TIMESTAMP())

-- Delete records not in source
WHEN NOT MATCHED BY SOURCE THEN
  DELETE;
```

### 14. Transactions & Error Handling
```sql
BEGIN TRANSACTION;

-- Your operations
INSERT INTO table1 VALUES (...);
UPDATE table2 SET ...;

COMMIT TRANSACTION;

-- With error handling
BEGIN
  BEGIN TRANSACTION;
  
  -- Operations
  
  COMMIT TRANSACTION;
  
EXCEPTION WHEN ERROR THEN
  ROLLBACK TRANSACTION;
  RAISE USING MESSAGE = @@error.message;
END;
```

### 15. Performance Optimization Patterns
```sql
-- 1. Select only needed columns
SELECT customer_id, order_date, amount  -- Good
-- vs
SELECT *  -- Bad

-- 2. Filter early with WHERE
WITH filtered_data AS (
  SELECT * 
  FROM large_table
  WHERE date >= '2025-01-01'
)
SELECT * FROM filtered_data WHERE region = 'North';

-- 3. Use LIMIT for testing
SELECT * FROM orders LIMIT 1000;

-- 4. Materialize intermediate results
CREATE TEMP TABLE temp_results AS
SELECT ...;

-- 5. Use approximate functions
APPROX_COUNT_DISTINCT(customer_id)  -- vs COUNT(DISTINCT customer_id)

-- 6. Avoid self-joins with window functions
-- Bad: Self-join
SELECT a.date, SUM(b.amount)
FROM sales a
JOIN sales b ON b.date <= a.date
GROUP BY a.date;

-- Good: Window function
SELECT 
  date,
  SUM(amount) OVER (ORDER BY date)
FROM sales;

-- 7. Partition filter
WHERE DATE(timestamp_col) BETWEEN '2025-01-01' AND '2025-01-31'
```

### 16. Common Table Patterns

#### Slowly Changing Dimension (Type 2)
```sql
-- Close old record
UPDATE dim_customer
SET end_date = CURRENT_DATE(), is_current = FALSE
WHERE customer_id = 'C001' AND is_current = TRUE;

-- Insert new record
INSERT INTO dim_customer (
  customer_key, customer_id, name, address,
  start_date, end_date, is_current
)
VALUES (
  GENERATE_UUID(), 'C001', 'New Name', 'New Address',
  CURRENT_DATE(), DATE('9999-12-31'), TRUE
);

-- Query point-in-time
SELECT *
FROM dim_customer
WHERE '2024-06-01' BETWEEN start_date AND end_date;
```

#### Incremental Load with Watermark
```sql
-- Get last watermark
DECLARE last_run TIMESTAMP;
SET last_run = (
  SELECT MAX(watermark) 
  FROM metadata.watermarks 
  WHERE table_name = 'orders'
);

-- Load incremental data
INSERT INTO target_orders
SELECT *
FROM source_orders
WHERE updated_timestamp > last_run;

-- Update watermark
INSERT INTO metadata.watermarks
VALUES ('orders', CURRENT_TIMESTAMP());
```

#### Change Data Capture (CDC)
```sql
-- Identify changes
CREATE TEMP TABLE changes AS
SELECT 
  s.*,
  CASE 
    WHEN t.id IS NULL THEN 'INSERT'
    WHEN s.hash != t.hash THEN 'UPDATE'
    ELSE 'NO_CHANGE'
  END AS change_type
FROM staging s
FULL OUTER JOIN target t ON s.id = t.id;

-- Apply changes
INSERT INTO target SELECT * FROM changes WHERE change_type = 'INSERT';
UPDATE target ... WHERE id IN (SELECT id FROM changes WHERE change_type = 'UPDATE');
```

---

## 🎯 Common Interview Patterns

### Pattern 1: Find Top N per Group
```sql
-- Top 3 products per category by revenue
SELECT *
FROM (
  SELECT 
    category,
    product_name,
    revenue,
    ROW_NUMBER() OVER (PARTITION BY category ORDER BY revenue DESC) AS rank
  FROM products
)
WHERE rank <= 3;
```

### Pattern 2: Fill Missing Dates
```sql
WITH date_range AS (
  SELECT date_value
  FROM UNNEST(GENERATE_DATE_ARRAY('2025-01-01', '2025-01-31')) AS date_value
)
SELECT 
  d.date_value,
  COALESCE(s.sales, 0) AS sales
FROM date_range d
LEFT JOIN sales s ON d.date_value = s.sale_date;
```

### Pattern 3: Running Totals
```sql
SELECT 
  date,
  amount,
  SUM(amount) OVER (ORDER BY date) AS running_total
FROM transactions;
```

### Pattern 4: Month-over-Month Change
```sql
WITH monthly_sales AS (
  SELECT 
    DATE_TRUNC(order_date, MONTH) AS month,
    SUM(amount) AS total
  FROM orders
  GROUP BY month
)
SELECT 
  month,
  total,
  LAG(total) OVER (ORDER BY month) AS prev_month,
  total - LAG(total) OVER (ORDER BY month) AS change,
  SAFE_DIVIDE(
    total - LAG(total) OVER (ORDER BY month),
    LAG(total) OVER (ORDER BY month)
  ) * 100 AS pct_change
FROM monthly_sales;
```

### Pattern 5: Cohort Analysis
```sql
WITH first_order AS (
  SELECT 
    customer_id,
    MIN(DATE_TRUNC(order_date, MONTH)) AS cohort_month
  FROM orders
  GROUP BY customer_id
)
SELECT 
  f.cohort_month,
  DATE_TRUNC(o.order_date, MONTH) AS order_month,
  DATE_DIFF(
    DATE_TRUNC(o.order_date, MONTH),
    f.cohort_month,
    MONTH
  ) AS months_since_first,
  COUNT(DISTINCT o.customer_id) AS customers,
  SUM(o.amount) AS revenue
FROM orders o
INNER JOIN first_order f ON o.customer_id = f.customer_id
GROUP BY cohort_month, order_month;
```

### Pattern 6: Session Analysis
```sql
-- Identify session boundaries (gap > 30 minutes)
WITH session_starts AS (
  SELECT 
    user_id,
    timestamp,
    CASE 
      WHEN TIMESTAMP_DIFF(
        timestamp,
        LAG(timestamp) OVER (PARTITION BY user_id ORDER BY timestamp),
        MINUTE
      ) > 30 OR LAG(timestamp) OVER (PARTITION BY user_id ORDER BY timestamp) IS NULL
      THEN 1 
      ELSE 0 
    END AS is_new_session
  FROM events
)
SELECT 
  user_id,
  SUM(is_new_session) OVER (PARTITION BY user_id ORDER BY timestamp) AS session_id,
  timestamp
FROM session_starts;
```

---

## 🔥 Advanced Concepts

### Pivot & Unpivot
```sql
-- Pivot
SELECT *
FROM sales
PIVOT (
  SUM(revenue) FOR quarter IN ('Q1', 'Q2', 'Q3', 'Q4')
);

-- Unpivot
SELECT *
FROM quarterly_sales
UNPIVOT (
  revenue FOR quarter IN (Q1, Q2, Q3, Q4)
);
```

### Dynamic SQL (Conceptual)
```sql
-- Get column list dynamically
SELECT STRING_AGG(column_name, ', ')
FROM `project.dataset.INFORMATION_SCHEMA.COLUMNS`
WHERE table_name = 'orders';

-- Build dynamic query (use with EXECUTE IMMEDIATE)
DECLARE query STRING;
SET query = CONCAT(
  'SELECT ', 
  (SELECT STRING_AGG(column_name) FROM ...),
  ' FROM table'
);
```

### Table Metadata Queries
```sql
-- List tables in dataset
SELECT table_name, creation_time, row_count
FROM `project.dataset.__TABLES__`;

-- Column information
SELECT table_name, column_name, data_type
FROM `project.dataset.INFORMATION_SCHEMA.COLUMNS`
WHERE table_name = 'orders';

-- Query job history
SELECT 
  creation_time,
  query,
  total_bytes_processed,
  total_slot_ms
FROM `region-us`.INFORMATION_SCHEMA.JOBS_BY_PROJECT
WHERE creation_time >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 DAY)
ORDER BY total_bytes_processed DESC;
```

---

## ⚡ Quick Tips

1. **Always use SAFE functions** when uncertain: `SAFE_DIVIDE`, `SAFE_OFFSET`, `SAFE_CAST`
2. **Partition filters are crucial** in BigQuery for cost savings
3. **QUALIFY is powerful** for filtering window function results
4. **Use EXCEPT** to exclude columns: `SELECT * EXCEPT(column_to_exclude)`
5. **STRING_AGG and ARRAY_AGG** are your friends for aggregating text
6. **Test with LIMIT** before running on full dataset
7. **Use CTEs** for readability and debugging
8. **Window functions > self-joins** for performance
9. **Approximate functions** for large datasets (APPROX_COUNT_DISTINCT)
10. **Materialized views** for frequently queried aggregations

---

## 🎓 Study Resources

- BigQuery Standard SQL Reference
- Window Functions Deep Dive
- Partitioning and Clustering Best Practices
- Query Optimization Techniques
- ETL Design Patterns
