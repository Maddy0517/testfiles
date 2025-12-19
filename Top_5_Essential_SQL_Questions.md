# Top 5 Essential SQL Interview Questions
## ETL QA Interview - Most Important Questions

---

## Question 1: Find and Remove Duplicate Records ⭐

### Problem Statement
```
You have an 'orders' table where some records are accidentally duplicated.
Find all duplicates and write a query to keep only the most recent record 
based on 'updated_timestamp'.

Table: orders
Columns: order_id, customer_id, order_date, amount, updated_timestamp
```

### Sample Data
```sql
CREATE TEMP TABLE orders AS
SELECT 1001 AS order_id, 'C001' AS customer_id, DATE('2025-01-01') AS order_date, 
       100.0 AS amount, TIMESTAMP('2025-01-01 10:00:00') AS updated_timestamp
UNION ALL SELECT 1001, 'C001', DATE('2025-01-01'), 100.0, TIMESTAMP('2025-01-01 14:00:00')  -- Duplicate (keep this)
UNION ALL SELECT 1002, 'C002', DATE('2025-01-02'), 200.0, TIMESTAMP('2025-01-02 09:00:00')
UNION ALL SELECT 1003, 'C003', DATE('2025-01-03'), 150.0, TIMESTAMP('2025-01-03 08:00:00')
UNION ALL SELECT 1003, 'C003', DATE('2025-01-03'), 150.0, TIMESTAMP('2025-01-03 10:00:00')  -- Duplicate (keep this)
UNION ALL SELECT 1003, 'C003', DATE('2025-01-03'), 150.0, TIMESTAMP('2025-01-03 06:00:00'); -- Duplicate (delete)
```

### Solution 1: Identify Duplicates
```sql
-- Find all duplicate records
SELECT 
  order_id,
  customer_id,
  order_date,
  amount,
  updated_timestamp,
  COUNT(*) OVER (PARTITION BY order_id) AS duplicate_count,
  ROW_NUMBER() OVER (PARTITION BY order_id ORDER BY updated_timestamp DESC) AS row_rank
FROM orders
QUALIFY duplicate_count > 1
ORDER BY order_id, updated_timestamp DESC;
```

### Solution 2: Keep Only Latest Records
```sql
-- Method 1: Using QUALIFY (BigQuery specific - most efficient)
SELECT * 
FROM orders
QUALIFY ROW_NUMBER() OVER (PARTITION BY order_id ORDER BY updated_timestamp DESC) = 1;

-- Method 2: Using CTE and ROW_NUMBER
WITH ranked_orders AS (
  SELECT 
    *,
    ROW_NUMBER() OVER (PARTITION BY order_id ORDER BY updated_timestamp DESC) AS row_num
  FROM orders
)
SELECT * EXCEPT(row_num)
FROM ranked_orders
WHERE row_num = 1;

-- Method 3: Create deduplicated table
CREATE OR REPLACE TABLE orders_clean AS
SELECT *
FROM orders
QUALIFY ROW_NUMBER() OVER (PARTITION BY order_id ORDER BY updated_timestamp DESC) = 1;
```

### Key Concepts Tested
- ✅ Window functions (ROW_NUMBER, PARTITION BY)
- ✅ QUALIFY clause (BigQuery specific)
- ✅ Handling duplicates in ETL
- ✅ Data quality understanding

---

## Question 2: Calculate Running Totals and Moving Averages ⭐

### Problem Statement
```
Given daily sales data, calculate:
1. Running total of revenue for each product
2. 7-day moving average
3. Day-over-day percentage change
4. Rank products by daily revenue

Table: daily_sales
Columns: sale_date, product_id, daily_revenue
```

### Sample Data
```sql
CREATE TEMP TABLE daily_sales AS
SELECT DATE('2025-01-01') AS sale_date, 'P001' AS product_id, 1000.0 AS daily_revenue
UNION ALL SELECT DATE('2025-01-02'), 'P001', 1500.0
UNION ALL SELECT DATE('2025-01-03'), 'P001', 1200.0
UNION ALL SELECT DATE('2025-01-04'), 'P001', 1800.0
UNION ALL SELECT DATE('2025-01-05'), 'P001', 1600.0
UNION ALL SELECT DATE('2025-01-06'), 'P001', 2000.0
UNION ALL SELECT DATE('2025-01-07'), 'P001', 1900.0
UNION ALL SELECT DATE('2025-01-08'), 'P001', 2100.0
UNION ALL SELECT DATE('2025-01-01'), 'P002', 500.0
UNION ALL SELECT DATE('2025-01-02'), 'P002', 600.0
UNION ALL SELECT DATE('2025-01-03'), 'P002', 550.0;
```

### Complete Solution
```sql
SELECT 
  sale_date,
  product_id,
  daily_revenue,
  
  -- 1. Running total
  SUM(daily_revenue) OVER (
    PARTITION BY product_id 
    ORDER BY sale_date
    ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
  ) AS running_total,
  
  -- 2. 7-day moving average
  ROUND(AVG(daily_revenue) OVER (
    PARTITION BY product_id 
    ORDER BY sale_date
    ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
  ), 2) AS moving_avg_7day,
  
  -- 3. Previous day revenue
  LAG(daily_revenue, 1) OVER (
    PARTITION BY product_id 
    ORDER BY sale_date
  ) AS prev_day_revenue,
  
  -- 4. Day-over-day change (absolute)
  daily_revenue - LAG(daily_revenue, 1) OVER (
    PARTITION BY product_id 
    ORDER BY sale_date
  ) AS dod_change,
  
  -- 5. Day-over-day change (percentage)
  ROUND(SAFE_DIVIDE(
    daily_revenue - LAG(daily_revenue, 1) OVER (PARTITION BY product_id ORDER BY sale_date),
    LAG(daily_revenue, 1) OVER (PARTITION BY product_id ORDER BY sale_date)
  ) * 100, 2) AS dod_change_pct,
  
  -- 6. Rank by revenue within each day (across products)
  RANK() OVER (
    PARTITION BY sale_date 
    ORDER BY daily_revenue DESC
  ) AS daily_rank,
  
  -- 7. Overall rank for product
  DENSE_RANK() OVER (
    PARTITION BY product_id 
    ORDER BY daily_revenue DESC
  ) AS product_rank

FROM daily_sales
ORDER BY product_id, sale_date;
```

### Key Concepts Tested
- ✅ Multiple window functions in single query
- ✅ ROWS BETWEEN clause
- ✅ LAG/LEAD functions
- ✅ PARTITION BY for grouping
- ✅ SAFE_DIVIDE for NULL handling
- ✅ RANK vs DENSE_RANK

---

## Question 3: Data Quality Validation ⭐

### Problem Statement
```
You're loading customer data from staging to production.
Write comprehensive data quality checks to validate:
1. No duplicate customer_ids
2. No NULL values in critical fields (customer_id, email, name)
3. Valid email format
4. Phone numbers are 10 digits
5. Created_date is not in the future
6. Report detailed validation results

Table: staging_customers
Columns: customer_id, name, email, phone, created_date
```

### Sample Data
```sql
CREATE TEMP TABLE staging_customers AS
SELECT 'C001' AS customer_id, 'John Doe' AS name, 'john@email.com' AS email, 
       '1234567890' AS phone, DATE('2025-01-01') AS created_date
UNION ALL SELECT 'C002', 'Jane Smith', 'invalid-email', '9876543210', DATE('2025-01-15')
UNION ALL SELECT 'C003', NULL, 'bob@email.com', '5555555555', DATE('2025-01-20')
UNION ALL SELECT 'C004', 'Alice Brown', 'alice@email.com', '123', DATE('2026-01-01')
UNION ALL SELECT 'C002', 'Jane Duplicate', 'jane@email.com', '9876543210', DATE('2025-01-16')
UNION ALL SELECT 'C005', 'Mike Wilson', NULL, '7777777777', DATE('2025-01-18');
```

### Solution: Comprehensive Data Quality Check
```sql
-- Create validation summary
WITH validation_checks AS (
  -- Check 1: Duplicate customer_ids
  SELECT 
    'Duplicate Check' AS check_name,
    'CRITICAL' AS severity,
    COUNT(*) - COUNT(DISTINCT customer_id) AS failed_count,
    COUNT(*) AS total_count,
    ROUND(SAFE_DIVIDE(COUNT(*) - COUNT(DISTINCT customer_id), COUNT(*)) * 100, 2) AS failure_rate_pct,
    CASE 
      WHEN COUNT(*) = COUNT(DISTINCT customer_id) THEN 'PASS'
      ELSE 'FAIL'
    END AS status
  FROM staging_customers
  
  UNION ALL
  
  -- Check 2: NULL in critical fields
  SELECT 
    'NULL Check - Critical Fields',
    'CRITICAL',
    COUNTIF(customer_id IS NULL OR email IS NULL OR name IS NULL),
    COUNT(*),
    ROUND(SAFE_DIVIDE(COUNTIF(customer_id IS NULL OR email IS NULL OR name IS NULL), COUNT(*)) * 100, 2),
    CASE 
      WHEN COUNTIF(customer_id IS NULL OR email IS NULL OR name IS NULL) = 0 THEN 'PASS'
      ELSE 'FAIL'
    END
  FROM staging_customers
  
  UNION ALL
  
  -- Check 3: Email format validation
  SELECT 
    'Email Format Check',
    'HIGH',
    COUNTIF(email IS NOT NULL AND NOT REGEXP_CONTAINS(email, r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$')),
    COUNT(*),
    ROUND(SAFE_DIVIDE(COUNTIF(email IS NOT NULL AND NOT REGEXP_CONTAINS(email, r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$')), COUNT(*)) * 100, 2),
    CASE 
      WHEN COUNTIF(email IS NOT NULL AND NOT REGEXP_CONTAINS(email, r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$')) = 0 THEN 'PASS'
      ELSE 'FAIL'
    END
  FROM staging_customers
  
  UNION ALL
  
  -- Check 4: Phone number validation (10 digits)
  SELECT 
    'Phone Format Check',
    'MEDIUM',
    COUNTIF(phone IS NOT NULL AND LENGTH(phone) != 10),
    COUNT(*),
    ROUND(SAFE_DIVIDE(COUNTIF(phone IS NOT NULL AND LENGTH(phone) != 10), COUNT(*)) * 100, 2),
    CASE 
      WHEN COUNTIF(phone IS NOT NULL AND LENGTH(phone) != 10) = 0 THEN 'PASS'
      WHEN COUNTIF(phone IS NOT NULL AND LENGTH(phone) != 10) <= 2 THEN 'WARNING'
      ELSE 'FAIL'
    END
  FROM staging_customers
  
  UNION ALL
  
  -- Check 5: Future date check
  SELECT 
    'Future Date Check',
    'HIGH',
    COUNTIF(created_date > CURRENT_DATE()),
    COUNT(*),
    ROUND(SAFE_DIVIDE(COUNTIF(created_date > CURRENT_DATE()), COUNT(*)) * 100, 2),
    CASE 
      WHEN COUNTIF(created_date > CURRENT_DATE()) = 0 THEN 'PASS'
      ELSE 'FAIL'
    END
  FROM staging_customers
)
SELECT 
  check_name,
  severity,
  failed_count,
  total_count,
  failure_rate_pct,
  status
FROM validation_checks
ORDER BY 
  CASE severity
    WHEN 'CRITICAL' THEN 1
    WHEN 'HIGH' THEN 2
    WHEN 'MEDIUM' THEN 3
    ELSE 4
  END,
  CASE status
    WHEN 'FAIL' THEN 1
    WHEN 'WARNING' THEN 2
    ELSE 3
  END;
```

### Detailed Error Report
```sql
-- Show all records with validation errors
SELECT 
  customer_id,
  name,
  email,
  phone,
  created_date,
  -- Concatenate all validation errors
  ARRAY_TO_STRING(
    ARRAY_CONCAT(
      [IF(customer_id IN (SELECT customer_id FROM staging_customers GROUP BY customer_id HAVING COUNT(*) > 1), 
          'DUPLICATE_ID', NULL)],
      [IF(customer_id IS NULL, 'NULL_CUSTOMER_ID', NULL)],
      [IF(name IS NULL, 'NULL_NAME', NULL)],
      [IF(email IS NULL, 'NULL_EMAIL', NULL)],
      [IF(email IS NOT NULL AND NOT REGEXP_CONTAINS(email, r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'), 
          'INVALID_EMAIL', NULL)],
      [IF(phone IS NOT NULL AND LENGTH(phone) != 10, 'INVALID_PHONE', NULL)],
      [IF(created_date > CURRENT_DATE(), 'FUTURE_DATE', NULL)]
    ),
    ' | '
  ) AS validation_errors
FROM staging_customers
WHERE 
  -- Has any validation error
  customer_id IN (SELECT customer_id FROM staging_customers GROUP BY customer_id HAVING COUNT(*) > 1)
  OR customer_id IS NULL
  OR name IS NULL
  OR email IS NULL
  OR (email IS NOT NULL AND NOT REGEXP_CONTAINS(email, r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'))
  OR (phone IS NOT NULL AND LENGTH(phone) != 10)
  OR created_date > CURRENT_DATE()
ORDER BY customer_id;
```

### Key Concepts Tested
- ✅ Comprehensive data quality framework
- ✅ REGEXP_CONTAINS for pattern matching
- ✅ COUNTIF for conditional counting
- ✅ Multiple validation checks in single query
- ✅ Error severity classification
- ✅ Detailed error reporting

---

## Question 4: Incremental Load with Watermark ⭐

### Problem Statement
```
Design an incremental data load process that:
1. Loads only new/changed records since last run
2. Uses watermark (timestamp) to track progress
3. Handles first-time full load
4. Includes error handling

Tables:
- source.orders (source system)
- target.orders (data warehouse)
- metadata.watermarks (tracking table)
```

### Solution: Complete Incremental Load Procedure
```sql
-- Step 1: Create watermark tracking table
CREATE TABLE IF NOT EXISTS metadata.watermarks (
  table_name STRING,
  last_watermark_value TIMESTAMP,
  load_timestamp TIMESTAMP,
  records_loaded INT64,
  status STRING
);

-- Step 2: Incremental Load Procedure
CREATE OR REPLACE PROCEDURE load_orders_incremental()
BEGIN
  DECLARE last_watermark TIMESTAMP;
  DECLARE current_watermark TIMESTAMP;
  DECLARE rows_loaded INT64;
  
  -- Get last successful watermark
  SET last_watermark = (
    SELECT MAX(last_watermark_value)
    FROM metadata.watermarks
    WHERE table_name = 'orders'
      AND status = 'SUCCESS'
  );
  
  -- Handle first run (full load)
  IF last_watermark IS NULL THEN
    SET last_watermark = TIMESTAMP('1900-01-01 00:00:00');
  END IF;
  
  -- Get current max timestamp from source (before loading)
  SET current_watermark = (
    SELECT MAX(updated_timestamp)
    FROM source.orders
  );
  
  -- Validate we have new data
  IF current_watermark <= last_watermark THEN
    -- No new data, log and exit
    INSERT INTO metadata.watermarks (
      table_name,
      last_watermark_value,
      load_timestamp,
      records_loaded,
      status
    )
    VALUES (
      'orders',
      last_watermark,
      CURRENT_TIMESTAMP(),
      0,
      'NO_NEW_DATA'
    );
    RETURN;
  END IF;
  
  -- Begin transaction for atomic load
  BEGIN TRANSACTION;
  
  BEGIN
    -- Load incremental data
    INSERT INTO target.orders (
      order_id,
      customer_id,
      product_id,
      order_date,
      amount,
      status,
      updated_timestamp,
      etl_insert_timestamp
    )
    SELECT 
      order_id,
      customer_id,
      product_id,
      order_date,
      amount,
      status,
      updated_timestamp,
      CURRENT_TIMESTAMP() AS etl_insert_timestamp
    FROM source.orders
    WHERE updated_timestamp > last_watermark
      AND updated_timestamp <= current_watermark
      AND updated_timestamp IS NOT NULL;  -- Safety check
    
    SET rows_loaded = @@row_count;
    
    -- Update watermark on success
    INSERT INTO metadata.watermarks (
      table_name,
      last_watermark_value,
      load_timestamp,
      records_loaded,
      status
    )
    VALUES (
      'orders',
      current_watermark,
      CURRENT_TIMESTAMP(),
      rows_loaded,
      'SUCCESS'
    );
    
    -- Commit transaction
    COMMIT TRANSACTION;
    
    -- Log success message
    SELECT FORMAT('Successfully loaded %d records. Watermark updated to %t', 
                  rows_loaded, current_watermark) AS result;
    
  EXCEPTION WHEN ERROR THEN
    -- Rollback on error
    ROLLBACK TRANSACTION;
    
    -- Log error
    INSERT INTO metadata.watermarks (
      table_name,
      last_watermark_value,
      load_timestamp,
      records_loaded,
      status
    )
    VALUES (
      'orders',
      last_watermark,  -- Keep old watermark
      CURRENT_TIMESTAMP(),
      0,
      CONCAT('FAILED: ', @@error.message)
    );
    
    -- Re-raise error
    RAISE USING MESSAGE = CONCAT('Incremental load failed: ', @@error.message);
  END;
  
END TRANSACTION;
END;

-- Step 3: Query to monitor incremental loads
SELECT 
  table_name,
  last_watermark_value,
  load_timestamp,
  records_loaded,
  status,
  TIMESTAMP_DIFF(CURRENT_TIMESTAMP(), load_timestamp, HOUR) AS hours_since_load
FROM metadata.watermarks
WHERE table_name = 'orders'
ORDER BY load_timestamp DESC
LIMIT 10;
```

### Alternative: MERGE for Upsert Pattern
```sql
-- Use MERGE when you need both INSERT and UPDATE
MERGE target.orders T
USING (
  SELECT *
  FROM source.orders
  WHERE updated_timestamp > (
    SELECT MAX(last_watermark_value) 
    FROM metadata.watermarks 
    WHERE table_name = 'orders' AND status = 'SUCCESS'
  )
) S
ON T.order_id = S.order_id

-- Update existing records
WHEN MATCHED THEN
  UPDATE SET
    customer_id = S.customer_id,
    product_id = S.product_id,
    amount = S.amount,
    status = S.status,
    updated_timestamp = S.updated_timestamp,
    etl_update_timestamp = CURRENT_TIMESTAMP()

-- Insert new records
WHEN NOT MATCHED THEN
  INSERT (
    order_id,
    customer_id,
    product_id,
    order_date,
    amount,
    status,
    updated_timestamp,
    etl_insert_timestamp
  )
  VALUES (
    S.order_id,
    S.customer_id,
    S.product_id,
    S.order_date,
    S.amount,
    S.status,
    S.updated_timestamp,
    CURRENT_TIMESTAMP()
  );
```

### Key Concepts Tested
- ✅ Watermark pattern for incremental loads
- ✅ Transaction management (BEGIN/COMMIT/ROLLBACK)
- ✅ Error handling (EXCEPTION WHEN ERROR)
- ✅ First run handling
- ✅ Metadata tracking
- ✅ MERGE statement for upserts

---

## Question 5: Performance Optimization ⭐

### Problem Statement
```
This query is running very slow (taking 5 minutes on 500M rows).
Identify performance issues and rewrite it optimally for BigQuery.

Current Query:
SELECT 
  c.customer_name,
  p.product_name,
  SUM(o.amount) AS total_spent,
  COUNT(*) AS order_count
FROM orders o
JOIN customers c ON o.customer_id = c.customer_id
JOIN products p ON o.product_id = p.product_id
WHERE o.order_date >= '2020-01-01'
  AND c.region = 'North America'
GROUP BY c.customer_name, p.product_name
ORDER BY total_spent DESC;

Table Sizes:
- orders: 500M rows, 100GB
- customers: 10M rows, 2GB
- products: 100K rows, 50MB
```

### Problems Identified
```
❌ 1. No partition filter on orders table
❌ 2. Filtering AFTER joins (processes all data first)
❌ 3. Table likely not partitioned/clustered
❌ 4. No LIMIT clause
❌ 5. Joining full customer table when only need North America
❌ 6. Possible full table scans
```

### Optimized Solution
```sql
-- OPTIMIZED VERSION
WITH filtered_customers AS (
  -- Pre-filter customers (reduces join size)
  SELECT 
    customer_id,
    customer_name
  FROM customers
  WHERE region = 'North America'
),
filtered_orders AS (
  -- Pre-filter and pre-aggregate orders (reduces data processed)
  SELECT 
    customer_id,
    product_id,
    SUM(amount) AS total_amount,
    COUNT(*) AS order_count
  FROM orders
  WHERE DATE(order_date) >= '2020-01-01'  -- Partition filter
    AND order_date IS NOT NULL
  GROUP BY customer_id, product_id
)
SELECT 
  c.customer_name,
  p.product_name,
  o.total_amount AS total_spent,
  o.order_count
FROM filtered_orders o
INNER JOIN filtered_customers c 
  ON o.customer_id = c.customer_id
INNER JOIN products p 
  ON o.product_id = p.product_id
ORDER BY total_spent DESC
LIMIT 1000;  -- Add limit for top results
```

### Table Design Optimization
```sql
-- Create optimally designed table
CREATE OR REPLACE TABLE orders_optimized (
  order_id STRING,
  customer_id STRING,
  product_id STRING,
  order_date DATE,
  order_timestamp TIMESTAMP,
  amount NUMERIC,
  status STRING
)
PARTITION BY order_date
CLUSTER BY customer_id, product_id
OPTIONS (
  description = "Orders table optimized for analytics",
  require_partition_filter = TRUE,
  partition_expiration_days = 1095  -- 3 years retention
);

-- Create materialized view for common aggregations
CREATE MATERIALIZED VIEW orders_customer_product_summary
PARTITION BY DATE(order_date)
CLUSTER BY customer_id, product_id
AS
SELECT 
  DATE(order_date) AS order_date,
  customer_id,
  product_id,
  COUNT(*) AS order_count,
  SUM(amount) AS total_amount,
  AVG(amount) AS avg_amount,
  MAX(order_timestamp) AS last_order_time
FROM orders_optimized
WHERE order_date >= DATE_SUB(CURRENT_DATE(), INTERVAL 2 YEAR)
GROUP BY order_date, customer_id, product_id;

-- Query the materialized view (much faster)
SELECT 
  c.customer_name,
  p.product_name,
  SUM(m.total_amount) AS total_spent,
  SUM(m.order_count) AS order_count
FROM orders_customer_product_summary m
INNER JOIN customers c 
  ON m.customer_id = c.customer_id
  AND c.region = 'North America'
INNER JOIN products p 
  ON m.product_id = p.product_id
WHERE m.order_date >= '2020-01-01'
GROUP BY c.customer_name, p.product_name
ORDER BY total_spent DESC
LIMIT 1000;
```

### Performance Comparison
```sql
-- Check query performance
SELECT 
  creation_time,
  query,
  total_bytes_processed / POW(1024, 3) AS gb_processed,
  total_bytes_billed / POW(1024, 3) AS gb_billed,
  TIMESTAMP_DIFF(end_time, start_time, SECOND) AS duration_seconds,
  total_slot_ms / 1000 AS slot_seconds
FROM `region-us`.INFORMATION_SCHEMA.JOBS_BY_PROJECT
WHERE user_email = CURRENT_USER()
  AND creation_time >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 HOUR)
  AND state = 'DONE'
ORDER BY creation_time DESC
LIMIT 5;
```

### Additional Optimizations
```sql
-- Use APPROX functions for large datasets
SELECT 
  c.customer_name,
  p.product_name,
  SUM(o.amount) AS total_spent,
  APPROX_COUNT_DISTINCT(o.order_id) AS approx_order_count  -- Much faster
FROM orders o
INNER JOIN customers c USING (customer_id)
INNER JOIN products p USING (product_id)
WHERE DATE(o.order_date) >= '2020-01-01'
  AND c.region = 'North America'
GROUP BY c.customer_name, p.product_name
ORDER BY total_spent DESC
LIMIT 1000;
```

### Key Concepts Tested
- ✅ Query optimization techniques
- ✅ Partitioning and clustering strategy
- ✅ Pre-filtering and pre-aggregation
- ✅ Materialized views
- ✅ CTE usage for readability
- ✅ BigQuery-specific optimizations
- ✅ Cost awareness (bytes processed)
- ✅ APPROX functions for performance

---

## 🎯 Quick Summary

### Question Coverage:
1. **Deduplication** → Data quality, window functions
2. **Window Functions** → Analytics, running totals, moving averages
3. **Data Quality** → Validation, regex, comprehensive checks
4. **Incremental Loads** → ETL patterns, watermarks, error handling
5. **Performance** → Query optimization, partitioning, materialized views

### Time Allocation (60-min interview):
- Question 1: 10 minutes
- Question 2: 12 minutes
- Question 3: 12 minutes
- Question 4: 13 minutes
- Question 5: 13 minutes

### Scoring (Each question = 20 points):
- **18-20**: Excellent solution with optimization
- **15-17**: Good solution, works correctly
- **10-14**: Partial solution, has issues
- **Below 10**: Cannot solve

**Total Score:**
- **90-100**: Strong hire
- **75-89**: Hire
- **60-74**: Maybe
- **Below 60**: No hire

---

## 💡 Interview Tips

### For Candidates:
1. ✅ **Ask clarifying questions** (table size, SLA, data characteristics)
2. ✅ **Think out loud** - explain your approach
3. ✅ **Start simple, then optimize** - working solution first
4. ✅ **Consider edge cases** (NULLs, duplicates, empty datasets)
5. ✅ **Use BigQuery features** (QUALIFY, SAFE functions, partitioning)

### For Interviewers:
1. ✅ **Provide sample data** - helps candidates understand
2. ✅ **Allow syntax errors** - focus on logic
3. ✅ **Ask follow-ups** - "How would you optimize this?"
4. ✅ **Check production thinking** - error handling, monitoring
5. ✅ **Be flexible** - multiple valid solutions exist

---

**Good luck! 🚀**
