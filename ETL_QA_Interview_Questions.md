# ETL QA Interview Questions & Answers
## For 5+ Years Experience (BigQuery SQL, Data Warehousing, ETL/ELT, Tableau)

---

## Section 1: BigQuery SQL (Advanced)

### Question 1: Window Functions and Data Quality
**Question:** Write a BigQuery SQL query to identify duplicate records in a table `orders` based on `order_id` and `customer_id`, and rank them by `created_timestamp`. Return only the latest record for each duplicate group.

**Answer:**
```sql
WITH ranked_orders AS (
  SELECT 
    *,
    ROW_NUMBER() OVER (
      PARTITION BY order_id, customer_id 
      ORDER BY created_timestamp DESC
    ) AS row_num
  FROM `project.dataset.orders`
)
SELECT * EXCEPT(row_num)
FROM ranked_orders
WHERE row_num = 1;
```

**Key Points:**
- Uses window function `ROW_NUMBER()` for deduplication
- `PARTITION BY` groups duplicates
- `ORDER BY DESC` ensures latest record gets row_num = 1
- `EXCEPT` clause excludes the ranking column from output

---

### Question 2: Complex Joins and Aggregations
**Question:** Given three tables - `sales`, `products`, and `customers` - write a query to find the top 5 products by revenue for each customer segment, excluding returns. Include running total of revenue.

**Answer:**
```sql
WITH sales_summary AS (
  SELECT 
    c.customer_segment,
    p.product_name,
    p.product_id,
    SUM(s.quantity * s.unit_price) AS total_revenue,
    COUNT(DISTINCT s.order_id) AS order_count
  FROM `project.dataset.sales` s
  JOIN `project.dataset.products` p ON s.product_id = p.product_id
  JOIN `project.dataset.customers` c ON s.customer_id = c.customer_id
  WHERE s.is_returned = FALSE
  GROUP BY c.customer_segment, p.product_name, p.product_id
),
ranked_products AS (
  SELECT 
    *,
    ROW_NUMBER() OVER (PARTITION BY customer_segment ORDER BY total_revenue DESC) AS rank,
    SUM(total_revenue) OVER (
      PARTITION BY customer_segment 
      ORDER BY total_revenue DESC 
      ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS running_total
  FROM sales_summary
)
SELECT 
  customer_segment,
  product_name,
  total_revenue,
  order_count,
  running_total,
  rank
FROM ranked_products
WHERE rank <= 5
ORDER BY customer_segment, rank;
```

---

### Question 3: Date Partitioning and Clustering
**Question:** Explain the difference between partitioning and clustering in BigQuery. When would you use one over the other? Provide a CREATE TABLE example.

**Answer:**

**Partitioning:**
- Divides table into segments based on column values (date, timestamp, integer)
- Reduces data scanned by filtering partitions
- Limit: 4,000 partitions per table
- Best for: Date/time-based queries with specific time ranges

**Clustering:**
- Sorts data within partitions based on clustering columns
- Improves query performance on filter/aggregation columns
- Can specify up to 4 clustering columns
- Best for: High cardinality columns frequently used in WHERE clauses

**Example:**
```sql
CREATE TABLE `project.dataset.sales_partitioned_clustered` (
  order_id STRING,
  customer_id STRING,
  product_id STRING,
  order_date DATE,
  order_timestamp TIMESTAMP,
  amount NUMERIC,
  region STRING,
  status STRING
)
PARTITION BY DATE(order_timestamp)
CLUSTER BY region, customer_id, status
OPTIONS(
  partition_expiration_days = 730,
  require_partition_filter = TRUE,
  description = "Sales table partitioned by date and clustered by frequently queried columns"
);
```

**When to use:**
- Use partitioning when queries filter on date/timestamp ranges
- Add clustering when filtering on high-cardinality columns within partitions
- Combine both for optimal performance on large tables (>1GB)

---

### Question 4: Incremental Load Strategy
**Question:** Design a SQL query for incremental data loading in BigQuery using MERGE statement. Handle INSERT, UPDATE, and soft DELETE scenarios.

**Answer:**
```sql
MERGE `project.dataset.target_table` T
USING (
  SELECT 
    record_id,
    customer_name,
    email,
    phone,
    address,
    updated_at,
    is_deleted
  FROM `project.dataset.staging_table`
  WHERE DATE(updated_at) >= CURRENT_DATE() - 1  -- Incremental filter
) S
ON T.record_id = S.record_id

-- UPDATE existing active records
WHEN MATCHED AND S.is_deleted = FALSE THEN
  UPDATE SET
    customer_name = S.customer_name,
    email = S.email,
    phone = S.phone,
    address = S.address,
    updated_at = S.updated_at,
    etl_updated_timestamp = CURRENT_TIMESTAMP()

-- SOFT DELETE
WHEN MATCHED AND S.is_deleted = TRUE THEN
  UPDATE SET
    is_active = FALSE,
    deleted_at = S.updated_at,
    etl_updated_timestamp = CURRENT_TIMESTAMP()

-- INSERT new records
WHEN NOT MATCHED BY TARGET AND S.is_deleted = FALSE THEN
  INSERT (
    record_id,
    customer_name,
    email,
    phone,
    address,
    is_active,
    created_at,
    updated_at,
    etl_created_timestamp,
    etl_updated_timestamp
  )
  VALUES (
    S.record_id,
    S.customer_name,
    S.email,
    S.phone,
    S.address,
    TRUE,
    S.updated_at,
    S.updated_at,
    CURRENT_TIMESTAMP(),
    CURRENT_TIMESTAMP()
  );
```

---

### Question 5: Array and Struct Operations
**Question:** Write a query to unnest a nested array of structs and calculate aggregated metrics. Given a table with schema: `order_id STRING, items ARRAY<STRUCT<product_id STRING, quantity INT64, price FLOAT64>>`.

**Answer:**
```sql
WITH unnested_orders AS (
  SELECT 
    order_id,
    item.product_id,
    item.quantity,
    item.price,
    item.quantity * item.price AS line_total
  FROM `project.dataset.orders`,
  UNNEST(items) AS item
)
SELECT 
  order_id,
  COUNT(DISTINCT product_id) AS unique_products,
  SUM(quantity) AS total_items,
  SUM(line_total) AS order_total,
  AVG(price) AS avg_item_price,
  ARRAY_AGG(
    STRUCT(product_id, quantity, line_total) 
    ORDER BY line_total DESC 
    LIMIT 3
  ) AS top_3_items
FROM unnested_orders
GROUP BY order_id
HAVING order_total > 100;
```

---

## Section 2: Data Warehousing Concepts

### Question 6: Slowly Changing Dimensions (SCD)
**Question:** Explain SCD Type 2 and provide a SQL implementation to handle historical tracking in BigQuery.

**Answer:**

**SCD Type 2** maintains full history by creating new records for changes while keeping old records intact.

**Characteristics:**
- Each record has `effective_date` and `end_date`
- Current records have `end_date = '9999-12-31'` or `is_current = TRUE`
- Enables historical analysis and point-in-time queries

**Implementation:**
```sql
-- Step 1: Identify changes
CREATE TEMP TABLE changes AS
SELECT 
  S.customer_id,
  S.customer_name,
  S.email,
  S.address,
  S.updated_date
FROM `project.dataset.staging_customers` S
LEFT JOIN `project.dataset.dim_customer` D
  ON S.customer_id = D.customer_id
  AND D.is_current = TRUE
WHERE 
  -- New records
  D.customer_id IS NULL
  OR
  -- Changed records
  (S.customer_name != D.customer_name OR
   S.email != D.email OR
   S.address != D.address);

-- Step 2: Close out current records that have changes
UPDATE `project.dataset.dim_customer` D
SET 
  is_current = FALSE,
  end_date = CURRENT_DATE(),
  updated_timestamp = CURRENT_TIMESTAMP()
WHERE is_current = TRUE
AND customer_id IN (SELECT customer_id FROM changes);

-- Step 3: Insert new records (both new and changed)
INSERT INTO `project.dataset.dim_customer` (
  customer_key,  -- Surrogate key
  customer_id,   -- Natural key
  customer_name,
  email,
  address,
  start_date,
  end_date,
  is_current,
  created_timestamp
)
SELECT 
  GENERATE_UUID() AS customer_key,
  customer_id,
  customer_name,
  email,
  address,
  CURRENT_DATE() AS start_date,
  DATE('9999-12-31') AS end_date,
  TRUE AS is_current,
  CURRENT_TIMESTAMP() AS created_timestamp
FROM changes;
```

---

### Question 7: Star Schema vs Snowflake Schema
**Question:** Compare Star Schema and Snowflake Schema. When would you choose one over the other in a BigQuery environment?

**Answer:**

**Star Schema:**
- Denormalized dimension tables
- Single level of dimension tables around fact table
- Pros: Simpler queries, better query performance, easier for BI tools
- Cons: Data redundancy, larger dimension tables

**Snowflake Schema:**
- Normalized dimension tables with sub-dimensions
- Multiple levels of dimension hierarchies
- Pros: Reduced storage, easier maintenance, no data redundancy
- Cons: Complex joins, potentially slower queries

**BigQuery Recommendation:**
- **Prefer Star Schema** due to:
  - BigQuery's columnar storage minimizes storage concerns
  - Faster query performance (fewer joins)
  - Better for Tableau and BI tool integration
  - Simpler ETL processes
  - Join cost in BigQuery is higher than storage cost

**Example:**
```sql
-- Star Schema (RECOMMENDED for BigQuery)
fact_sales
  - sale_id
  - date_key → dim_date (date, month, quarter, year)
  - customer_key → dim_customer (customer_id, name, segment, region, country)
  - product_key → dim_product (product_id, name, category, subcategory, brand)
  - amount

-- Snowflake Schema (Generally NOT recommended for BigQuery)
fact_sales
  - sale_id
  - date_key → dim_date
  - customer_key → dim_customer → dim_region → dim_country
  - product_key → dim_product → dim_category → dim_brand
  - amount
```

---

### Question 8: Data Mart vs Data Warehouse
**Question:** What's the difference between a Data Mart and Data Warehouse? How would you design a data mart in BigQuery?

**Answer:**

**Data Warehouse:**
- Enterprise-wide, centralized repository
- Integrates data from multiple sources
- Subject-agnostic, serves entire organization
- Typically uses dimensional modeling
- Larger scale, more complex ETL

**Data Mart:**
- Department/subject-specific subset
- Focused on specific business area (Sales, Finance, Marketing)
- Smaller scope, faster queries
- Can be dependent (from DW) or independent (from source)
- Optimized for specific analytics needs

**BigQuery Data Mart Design:**
```sql
-- Create dedicated dataset for Sales Data Mart
-- project.sales_mart

-- Aggregated fact table for daily sales
CREATE OR REPLACE TABLE `project.sales_mart.fact_daily_sales` AS
SELECT 
  DATE(order_timestamp) AS sale_date,
  customer_key,
  product_key,
  region_key,
  COUNT(DISTINCT order_id) AS order_count,
  SUM(quantity) AS total_quantity,
  SUM(revenue) AS total_revenue,
  SUM(cost) AS total_cost,
  SUM(revenue - cost) AS total_profit
FROM `project.data_warehouse.fact_sales`
WHERE DATE(order_timestamp) >= DATE_SUB(CURRENT_DATE(), INTERVAL 2 YEAR)
GROUP BY sale_date, customer_key, product_key, region_key;

-- Create materialized views for common queries
CREATE MATERIALIZED VIEW `project.sales_mart.mv_monthly_sales_by_region` AS
SELECT 
  DATE_TRUNC(sale_date, MONTH) AS month,
  r.region_name,
  SUM(total_revenue) AS revenue,
  SUM(total_profit) AS profit
FROM `project.sales_mart.fact_daily_sales` f
JOIN `project.data_warehouse.dim_region` r ON f.region_key = r.region_key
GROUP BY month, region_name;
```

---

## Section 3: ETL/ELT Processes

### Question 9: Data Quality Checks in ETL
**Question:** Design a comprehensive data quality framework for ETL testing. What checks would you implement and how?

**Answer:**

**Data Quality Dimensions:**

1. **Completeness** - No missing critical data
2. **Accuracy** - Data is correct and precise
3. **Consistency** - Data is consistent across systems
4. **Validity** - Data conforms to business rules
5. **Uniqueness** - No duplicates
6. **Timeliness** - Data is up-to-date

**Implementation:**
```sql
-- Data Quality Check Framework
CREATE OR REPLACE TABLE `project.etl_metadata.data_quality_checks` (
  check_id STRING,
  check_name STRING,
  check_type STRING,
  table_name STRING,
  check_sql STRING,
  threshold_value FLOAT64,
  severity STRING
);

-- Example: Completeness Check
CREATE OR REPLACE PROCEDURE `project.etl_metadata.run_dq_checks`(
  target_table STRING,
  batch_id STRING
)
BEGIN
  -- 1. NULL Check (Completeness)
  INSERT INTO `project.etl_metadata.dq_results`
  SELECT 
    GENERATE_UUID() AS result_id,
    batch_id,
    'NULL_CHECK' AS check_type,
    target_table,
    column_name,
    null_count,
    total_count,
    SAFE_DIVIDE(null_count, total_count) * 100 AS null_percentage,
    CASE 
      WHEN SAFE_DIVIDE(null_count, total_count) > 0.05 THEN 'FAIL'
      ELSE 'PASS'
    END AS status,
    CURRENT_TIMESTAMP() AS check_timestamp
  FROM (
    SELECT 
      'customer_id' AS column_name,
      COUNTIF(customer_id IS NULL) AS null_count,
      COUNT(*) AS total_count
    FROM `project.dataset.target_table`
    UNION ALL
    SELECT 
      'order_date',
      COUNTIF(order_date IS NULL),
      COUNT(*)
    FROM `project.dataset.target_table`
  );

  -- 2. Duplicate Check (Uniqueness)
  INSERT INTO `project.etl_metadata.dq_results`
  SELECT 
    GENERATE_UUID(),
    batch_id,
    'DUPLICATE_CHECK',
    target_table,
    'order_id',
    duplicate_count,
    total_count,
    NULL,
    CASE WHEN duplicate_count > 0 THEN 'FAIL' ELSE 'PASS' END,
    CURRENT_TIMESTAMP()
  FROM (
    SELECT 
      COUNT(*) - COUNT(DISTINCT order_id) AS duplicate_count,
      COUNT(*) AS total_count
    FROM `project.dataset.target_table`
  );

  -- 3. Referential Integrity (Consistency)
  INSERT INTO `project.etl_metadata.dq_results`
  SELECT 
    GENERATE_UUID(),
    batch_id,
    'REFERENTIAL_INTEGRITY',
    target_table,
    'customer_id',
    orphan_count,
    total_count,
    NULL,
    CASE WHEN orphan_count > 0 THEN 'FAIL' ELSE 'PASS' END,
    CURRENT_TIMESTAMP()
  FROM (
    SELECT 
      COUNT(DISTINCT t.customer_id) AS orphan_count,
      COUNT(*) AS total_count
    FROM `project.dataset.target_table` t
    LEFT JOIN `project.dataset.dim_customer` c 
      ON t.customer_id = c.customer_id
    WHERE c.customer_id IS NULL
  );

  -- 4. Range/Validity Check
  INSERT INTO `project.etl_metadata.dq_results`
  SELECT 
    GENERATE_UUID(),
    batch_id,
    'RANGE_CHECK',
    target_table,
    'amount',
    invalid_count,
    total_count,
    SAFE_DIVIDE(invalid_count, total_count) * 100,
    CASE WHEN invalid_count > 0 THEN 'FAIL' ELSE 'PASS' END,
    CURRENT_TIMESTAMP()
  FROM (
    SELECT 
      COUNTIF(amount < 0 OR amount > 1000000) AS invalid_count,
      COUNT(*) AS total_count
    FROM `project.dataset.target_table`
  );

  -- 5. Timeliness Check
  INSERT INTO `project.etl_metadata.dq_results`
  SELECT 
    GENERATE_UUID(),
    batch_id,
    'TIMELINESS_CHECK',
    target_table,
    'order_date',
    stale_count,
    total_count,
    NULL,
    CASE 
      WHEN TIMESTAMP_DIFF(CURRENT_TIMESTAMP(), max_timestamp, HOUR) > 24 
      THEN 'FAIL' 
      ELSE 'PASS' 
    END,
    CURRENT_TIMESTAMP()
  FROM (
    SELECT 
      COUNTIF(order_date < CURRENT_DATE() - 365) AS stale_count,
      COUNT(*) AS total_count,
      MAX(etl_timestamp) AS max_timestamp
    FROM `project.dataset.target_table`
  );
END;
```

---

### Question 10: Full Load vs Incremental Load
**Question:** Compare Full Load and Incremental Load strategies. Provide a scenario and SQL for implementing incremental load with watermark.

**Answer:**

**Full Load:**
- Reloads entire dataset every time
- Pros: Simple logic, guaranteed data consistency
- Cons: Resource intensive, slow, expensive
- Use case: Small tables, dimension tables with few changes

**Incremental Load:**
- Loads only new/changed records since last run
- Pros: Faster, cost-effective, efficient
- Cons: Complex logic, requires change tracking
- Use case: Large fact tables, real-time updates

**Incremental Load with Watermark Implementation:**
```sql
-- 1. Create watermark table
CREATE TABLE IF NOT EXISTS `project.etl_metadata.watermarks` (
  table_name STRING,
  watermark_column STRING,
  last_watermark_value TIMESTAMP,
  load_timestamp TIMESTAMP
);

-- 2. Get last watermark
DECLARE last_watermark TIMESTAMP;
SET last_watermark = (
  SELECT last_watermark_value
  FROM `project.etl_metadata.watermarks`
  WHERE table_name = 'sales'
  ORDER BY load_timestamp DESC
  LIMIT 1
);

-- Handle first run
IF last_watermark IS NULL THEN
  SET last_watermark = TIMESTAMP('2020-01-01 00:00:00');
END IF;

-- 3. Load incremental data
INSERT INTO `project.dataset.target_sales` (
  order_id,
  customer_id,
  product_id,
  order_timestamp,
  amount,
  etl_insert_timestamp
)
SELECT 
  order_id,
  customer_id,
  product_id,
  order_timestamp,
  amount,
  CURRENT_TIMESTAMP() AS etl_insert_timestamp
FROM `project.source.sales`
WHERE order_timestamp > last_watermark
  AND order_timestamp <= CURRENT_TIMESTAMP();

-- 4. Update watermark
INSERT INTO `project.etl_metadata.watermarks` (
  table_name,
  watermark_column,
  last_watermark_value,
  load_timestamp
)
SELECT 
  'sales' AS table_name,
  'order_timestamp' AS watermark_column,
  MAX(order_timestamp) AS last_watermark_value,
  CURRENT_TIMESTAMP() AS load_timestamp
FROM `project.source.sales`
WHERE order_timestamp > last_watermark;
```

---

### Question 11: Error Handling and Recovery
**Question:** How would you implement error handling and recovery mechanism in BigQuery ETL pipeline? Provide an example.

**Answer:**

**Error Handling Strategy:**
1. **Error Logging** - Capture all errors with context
2. **Transaction Control** - Use transactions where possible
3. **Retry Logic** - Automatic retry for transient errors
4. **Error Quarantine** - Isolate bad records
5. **Alerting** - Notify on failures
6. **Rollback Capability** - Restore previous state

**Implementation:**
```sql
-- Error Logging Table
CREATE TABLE IF NOT EXISTS `project.etl_metadata.error_log` (
  error_id STRING DEFAULT GENERATE_UUID(),
  batch_id STRING,
  pipeline_name STRING,
  step_name STRING,
  error_message STRING,
  error_details STRING,
  affected_records INT64,
  error_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
  severity STRING,
  status STRING  -- NEW, IN_PROGRESS, RESOLVED
);

-- ETL Procedure with Error Handling
CREATE OR REPLACE PROCEDURE `project.etl.load_sales_with_error_handling`(
  batch_id STRING
)
BEGIN
  DECLARE row_count INT64;
  DECLARE error_count INT64 DEFAULT 0;
  
  BEGIN
    -- Start transaction
    BEGIN TRANSACTION;
    
    -- Step 1: Validate source data
    CREATE TEMP TABLE validation_errors AS
    SELECT 
      order_id,
      'Missing customer_id' AS error_reason
    FROM `project.staging.sales`
    WHERE customer_id IS NULL
    UNION ALL
    SELECT 
      order_id,
      'Invalid amount (negative)' AS error_reason
    FROM `project.staging.sales`
    WHERE amount < 0
    UNION ALL
    SELECT 
      order_id,
      'Future date' AS error_reason
    FROM `project.staging.sales`
    WHERE order_date > CURRENT_DATE();
    
    SET error_count = (SELECT COUNT(*) FROM validation_errors);
    
    -- Log validation errors
    IF error_count > 0 THEN
      INSERT INTO `project.etl_metadata.error_log` (
        batch_id,
        pipeline_name,
        step_name,
        error_message,
        error_details,
        affected_records,
        severity,
        status
      )
      SELECT 
        batch_id,
        'SALES_LOAD',
        'VALIDATION',
        'Data validation failed',
        STRING_AGG(CONCAT(order_id, ': ', error_reason), '; ' LIMIT 100),
        error_count,
        CASE WHEN error_count > 100 THEN 'CRITICAL' ELSE 'WARNING' END,
        'NEW'
      FROM validation_errors;
      
      -- Move bad records to quarantine
      INSERT INTO `project.etl_metadata.quarantine_records`
      SELECT 
        batch_id,
        'sales' AS table_name,
        order_id AS record_id,
        TO_JSON_STRING(s) AS record_data,
        v.error_reason,
        CURRENT_TIMESTAMP()
      FROM `project.staging.sales` s
      JOIN validation_errors v ON s.order_id = v.order_id;
    END IF;
    
    -- Step 2: Load valid records only
    INSERT INTO `project.dataset.target_sales`
    SELECT s.*
    FROM `project.staging.sales` s
    LEFT JOIN validation_errors v ON s.order_id = v.order_id
    WHERE v.order_id IS NULL;
    
    SET row_count = @@row_count;
    
    -- Step 3: Log success
    INSERT INTO `project.etl_metadata.batch_log` (
      batch_id,
      pipeline_name,
      status,
      records_processed,
      records_failed,
      start_time,
      end_time
    )
    VALUES (
      batch_id,
      'SALES_LOAD',
      CASE WHEN error_count = 0 THEN 'SUCCESS' ELSE 'PARTIAL_SUCCESS' END,
      row_count,
      error_count,
      CURRENT_TIMESTAMP(),
      CURRENT_TIMESTAMP()
    );
    
    -- Commit transaction
    COMMIT TRANSACTION;
    
  EXCEPTION WHEN ERROR THEN
    -- Rollback on any error
    ROLLBACK TRANSACTION;
    
    -- Log critical error
    INSERT INTO `project.etl_metadata.error_log` (
      batch_id,
      pipeline_name,
      step_name,
      error_message,
      error_details,
      severity,
      status
    )
    VALUES (
      batch_id,
      'SALES_LOAD',
      'EXECUTION',
      'Pipeline failed',
      @@error.message,
      'CRITICAL',
      'NEW'
    );
    
    -- Re-raise the error
    RAISE USING MESSAGE = @@error.message;
  END;
END;
```

---

### Question 12: CDC (Change Data Capture)
**Question:** Explain CDC and how you would implement it in BigQuery for tracking source system changes.

**Answer:**

**CDC (Change Data Capture):**
- Tracks and captures changes (INSERT, UPDATE, DELETE) from source systems
- Enables incremental data sync
- Reduces processing time and costs
- Common methods: Triggers, Log-based, Timestamp-based

**BigQuery CDC Implementation:**

```sql
-- 1. CDC Tracking Table
CREATE TABLE `project.dataset.customer_cdc` (
  cdc_id STRING DEFAULT GENERATE_UUID(),
  operation_type STRING,  -- INSERT, UPDATE, DELETE
  customer_id STRING,
  customer_name STRING,
  email STRING,
  phone STRING,
  change_timestamp TIMESTAMP,
  source_timestamp TIMESTAMP,
  etl_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP()
)
PARTITION BY DATE(etl_timestamp)
CLUSTER BY customer_id, operation_type;

-- 2. Capture Changes from Staging
CREATE OR REPLACE PROCEDURE `project.etl.capture_customer_changes`()
BEGIN
  -- Identify INSERTS (new records)
  INSERT INTO `project.dataset.customer_cdc` (
    operation_type,
    customer_id,
    customer_name,
    email,
    phone,
    change_timestamp,
    source_timestamp
  )
  SELECT 
    'INSERT' AS operation_type,
    s.customer_id,
    s.customer_name,
    s.email,
    s.phone,
    CURRENT_TIMESTAMP() AS change_timestamp,
    s.updated_at AS source_timestamp
  FROM `project.staging.customers` s
  LEFT JOIN `project.dataset.target_customers` t
    ON s.customer_id = t.customer_id
  WHERE t.customer_id IS NULL;
  
  -- Identify UPDATES (changed records)
  INSERT INTO `project.dataset.customer_cdc` (
    operation_type,
    customer_id,
    customer_name,
    email,
    phone,
    change_timestamp,
    source_timestamp
  )
  SELECT 
    'UPDATE' AS operation_type,
    s.customer_id,
    s.customer_name,
    s.email,
    s.phone,
    CURRENT_TIMESTAMP() AS change_timestamp,
    s.updated_at AS source_timestamp
  FROM `project.staging.customers` s
  INNER JOIN `project.dataset.target_customers` t
    ON s.customer_id = t.customer_id
  WHERE 
    s.customer_name != t.customer_name OR
    s.email != t.email OR
    s.phone != t.phone OR
    s.updated_at > t.updated_at;
  
  -- Identify DELETES (missing records)
  INSERT INTO `project.dataset.customer_cdc` (
    operation_type,
    customer_id,
    customer_name,
    email,
    phone,
    change_timestamp,
    source_timestamp
  )
  SELECT 
    'DELETE' AS operation_type,
    t.customer_id,
    t.customer_name,
    t.email,
    t.phone,
    CURRENT_TIMESTAMP() AS change_timestamp,
    NULL AS source_timestamp
  FROM `project.dataset.target_customers` t
  LEFT JOIN `project.staging.customers` s
    ON t.customer_id = s.customer_id
  WHERE s.customer_id IS NULL
    AND t.is_active = TRUE;
END;

-- 3. Apply CDC Changes to Target
CREATE OR REPLACE PROCEDURE `project.etl.apply_cdc_changes`()
BEGIN
  DECLARE last_processed_timestamp TIMESTAMP;
  
  -- Get last processed CDC timestamp
  SET last_processed_timestamp = (
    SELECT MAX(etl_timestamp) 
    FROM `project.etl_metadata.cdc_watermarks`
    WHERE table_name = 'customers'
  );
  
  -- Apply INSERT operations
  INSERT INTO `project.dataset.target_customers`
  SELECT 
    customer_id,
    customer_name,
    email,
    phone,
    TRUE AS is_active,
    change_timestamp AS created_at,
    change_timestamp AS updated_at
  FROM `project.dataset.customer_cdc`
  WHERE operation_type = 'INSERT'
    AND etl_timestamp > last_processed_timestamp;
  
  -- Apply UPDATE operations
  UPDATE `project.dataset.target_customers` t
  SET 
    customer_name = c.customer_name,
    email = c.email,
    phone = c.phone,
    updated_at = c.change_timestamp
  FROM `project.dataset.customer_cdc` c
  WHERE t.customer_id = c.customer_id
    AND c.operation_type = 'UPDATE'
    AND c.etl_timestamp > last_processed_timestamp;
  
  -- Apply DELETE operations (soft delete)
  UPDATE `project.dataset.target_customers` t
  SET 
    is_active = FALSE,
    deleted_at = c.change_timestamp
  FROM `project.dataset.customer_cdc` c
  WHERE t.customer_id = c.customer_id
    AND c.operation_type = 'DELETE'
    AND c.etl_timestamp > last_processed_timestamp;
  
  -- Update watermark
  INSERT INTO `project.etl_metadata.cdc_watermarks`
  VALUES ('customers', CURRENT_TIMESTAMP());
END;
```

---

## Section 4: Performance Optimization

### Question 13: Query Optimization Techniques
**Question:** What are the key techniques for optimizing BigQuery queries? Provide examples of poorly written and optimized versions.

**Answer:**

**Key Optimization Techniques:**

1. **Avoid SELECT * **
2. **Filter early and reduce data scanned**
3. **Use partitioning and clustering**
4. **Avoid self-joins when possible**
5. **Use APPROX functions for large datasets**
6. **Materialize intermediate results**
7. **Use ARRAY_AGG instead of multiple queries**

**Example 1: Avoiding SELECT ***

```sql
-- BAD: Scans all columns
SELECT * 
FROM `project.dataset.large_table`
WHERE date = '2025-01-01';

-- GOOD: Select only needed columns
SELECT 
  order_id,
  customer_id,
  amount
FROM `project.dataset.large_table`
WHERE date = '2025-01-01';
```

**Example 2: Early Filtering**

```sql
-- BAD: Joins then filters
SELECT 
  o.order_id,
  c.customer_name,
  SUM(o.amount) AS total
FROM `project.dataset.orders` o
JOIN `project.dataset.customers` c ON o.customer_id = c.customer_id
WHERE o.order_date >= '2025-01-01'
GROUP BY o.order_id, c.customer_name;

-- GOOD: Filters before join
WITH filtered_orders AS (
  SELECT 
    order_id,
    customer_id,
    amount
  FROM `project.dataset.orders`
  WHERE order_date >= '2025-01-01'
)
SELECT 
  f.order_id,
  c.customer_name,
  SUM(f.amount) AS total
FROM filtered_orders f
JOIN `project.dataset.customers` c ON f.customer_id = c.customer_id
GROUP BY f.order_id, c.customer_name;
```

**Example 3: Using Approximate Functions**

```sql
-- BAD: Exact count on billions of rows (slow, expensive)
SELECT 
  product_category,
  COUNT(DISTINCT customer_id) AS unique_customers
FROM `project.dataset.large_sales_table`
GROUP BY product_category;

-- GOOD: Approximate count (much faster, ~98% accurate)
SELECT 
  product_category,
  APPROX_COUNT_DISTINCT(customer_id) AS unique_customers
FROM `project.dataset.large_sales_table`
GROUP BY product_category;
```

**Example 4: Avoiding Self-Joins**

```sql
-- BAD: Self-join for running total
SELECT 
  a.date,
  a.amount,
  SUM(b.amount) AS running_total
FROM `project.dataset.sales` a
JOIN `project.dataset.sales` b 
  ON b.date <= a.date
GROUP BY a.date, a.amount;

-- GOOD: Window function
SELECT 
  date,
  amount,
  SUM(amount) OVER (ORDER BY date ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_total
FROM `project.dataset.sales`;
```

---

## Section 5: Tableau & Reporting

### Question 14: Tableau Data Extract vs Live Connection
**Question:** When would you use Tableau Data Extract vs Live Connection with BigQuery? What are performance implications?

**Answer:**

**Live Connection:**
- Queries BigQuery directly in real-time
- Always shows latest data
- Performance depends on BigQuery query optimization
- Can be slow for complex queries or large datasets
- Costs: Pay per query execution

**Pros:**
- Real-time data
- No storage on Tableau side
- Centralized data governance
- Good for frequently changing data

**Cons:**
- Network latency
- Dependent on BigQuery performance
- Can be expensive with many users

**Data Extract (.hyper file):**
- Pre-aggregated snapshot stored in Tableau
- Fast performance (in-memory)
- Requires scheduled refreshes
- Limited to extract size

**Pros:**
- Very fast dashboard performance
- Works offline
- Reduced BigQuery costs
- Consistent user experience

**Cons:**
- Not real-time (stale data between refreshes)
- Additional storage needed
- Refresh overhead

**Best Practices:**

```sql
-- 1. Create optimized view for Tableau
CREATE OR REPLACE VIEW `project.tableau_views.sales_dashboard` AS
SELECT 
  DATE(order_timestamp) AS order_date,
  customer_segment,
  product_category,
  region,
  COUNT(DISTINCT order_id) AS order_count,
  COUNT(DISTINCT customer_id) AS customer_count,
  SUM(quantity) AS total_quantity,
  SUM(revenue) AS total_revenue,
  SUM(profit) AS total_profit
FROM `project.dataset.fact_sales` f
JOIN `project.dataset.dim_customer` c USING (customer_key)
JOIN `project.dataset.dim_product` p USING (product_key)
WHERE order_timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 2 YEAR)
GROUP BY order_date, customer_segment, product_category, region;

-- 2. Create materialized view for better performance
CREATE MATERIALIZED VIEW `project.tableau_views.sales_dashboard_mv` AS
SELECT 
  DATE(order_timestamp) AS order_date,
  customer_segment,
  product_category,
  region,
  COUNT(DISTINCT order_id) AS order_count,
  SUM(revenue) AS total_revenue
FROM `project.dataset.fact_sales` f
JOIN `project.dataset.dim_customer` c USING (customer_key)
JOIN `project.dataset.dim_product` p USING (product_key)
WHERE order_timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 2 YEAR)
GROUP BY order_date, customer_segment, product_category, region;
```

**Decision Matrix:**
- Use **Extract** for: Historical analysis, static reports, executive dashboards
- Use **Live** for: Real-time monitoring, operational dashboards, highly dynamic data
- Use **Hybrid**: Live for recent data + Extract for historical data

---

### Question 15: Aggregated Tables for Tableau Performance
**Question:** How would you design aggregated tables in BigQuery to optimize Tableau dashboard performance? Provide an example.

**Answer:**

**Strategy:**
- Pre-aggregate data at different granularities
- Create mart-specific tables/views
- Use materialized views for automatic updates
- Implement incremental refresh

**Example Implementation:**

```sql
-- Level 1: Daily Grain (Most Detailed)
CREATE TABLE `project.tableau_mart.sales_daily` 
PARTITION BY order_date
CLUSTER BY region, product_category
AS
SELECT 
  order_date,
  region,
  customer_segment,
  product_category,
  product_subcategory,
  COUNT(DISTINCT order_id) AS orders,
  COUNT(DISTINCT customer_id) AS customers,
  SUM(quantity) AS quantity,
  SUM(revenue) AS revenue,
  SUM(cost) AS cost,
  SUM(profit) AS profit,
  AVG(discount_pct) AS avg_discount
FROM `project.data_warehouse.fact_sales_detailed`
WHERE order_date >= DATE_SUB(CURRENT_DATE(), INTERVAL 730 DAY)
GROUP BY 
  order_date,
  region,
  customer_segment,
  product_category,
  product_subcategory;

-- Level 2: Weekly Grain (Aggregated)
CREATE MATERIALIZED VIEW `project.tableau_mart.sales_weekly` AS
SELECT 
  DATE_TRUNC(order_date, WEEK) AS order_week,
  region,
  customer_segment,
  product_category,
  SUM(orders) AS orders,
  SUM(customers) AS customers,
  SUM(quantity) AS quantity,
  SUM(revenue) AS revenue,
  SUM(cost) AS cost,
  SUM(profit) AS profit,
  AVG(avg_discount) AS avg_discount
FROM `project.tableau_mart.sales_daily`
GROUP BY 
  order_week,
  region,
  customer_segment,
  product_category;

-- Level 3: Monthly Grain (Highly Aggregated)
CREATE MATERIALIZED VIEW `project.tableau_mart.sales_monthly` AS
SELECT 
  DATE_TRUNC(order_date, MONTH) AS order_month,
  region,
  customer_segment,
  product_category,
  SUM(orders) AS orders,
  SUM(customers) AS customers,
  SUM(quantity) AS quantity,
  SUM(revenue) AS revenue,
  SUM(cost) AS cost,
  SUM(profit) AS profit,
  SAFE_DIVIDE(SUM(profit), SUM(revenue)) * 100 AS profit_margin_pct
FROM `project.tableau_mart.sales_daily`
GROUP BY 
  order_month,
  region,
  customer_segment,
  product_category;

-- Level 4: Summary Table for KPIs
CREATE OR REPLACE TABLE `project.tableau_mart.sales_kpi_summary` AS
SELECT 
  'MTD' AS period_type,
  DATE_TRUNC(CURRENT_DATE(), MONTH) AS period_start,
  CURRENT_DATE() AS period_end,
  SUM(revenue) AS revenue,
  SUM(profit) AS profit,
  COUNT(DISTINCT order_date) AS days_count
FROM `project.tableau_mart.sales_daily`
WHERE order_date >= DATE_TRUNC(CURRENT_DATE(), MONTH)
UNION ALL
SELECT 
  'YTD' AS period_type,
  DATE_TRUNC(CURRENT_DATE(), YEAR) AS period_start,
  CURRENT_DATE() AS period_end,
  SUM(revenue) AS revenue,
  SUM(profit) AS profit,
  COUNT(DISTINCT order_date) AS days_count
FROM `project.tableau_mart.sales_daily`
WHERE order_date >= DATE_TRUNC(CURRENT_DATE(), YEAR);

-- Incremental Refresh Procedure
CREATE OR REPLACE PROCEDURE `project.tableau_mart.refresh_daily_sales`()
BEGIN
  DECLARE last_refresh_date DATE;
  
  -- Get last refresh date
  SET last_refresh_date = (
    SELECT MAX(order_date) 
    FROM `project.tableau_mart.sales_daily`
  );
  
  -- Incremental insert
  INSERT INTO `project.tableau_mart.sales_daily`
  SELECT 
    order_date,
    region,
    customer_segment,
    product_category,
    product_subcategory,
    COUNT(DISTINCT order_id) AS orders,
    COUNT(DISTINCT customer_id) AS customers,
    SUM(quantity) AS quantity,
    SUM(revenue) AS revenue,
    SUM(cost) AS cost,
    SUM(profit) AS profit,
    AVG(discount_pct) AS avg_discount
  FROM `project.data_warehouse.fact_sales_detailed`
  WHERE order_date > last_refresh_date
  GROUP BY 
    order_date,
    region,
    customer_segment,
    product_category,
    product_subcategory;
END;
```

**Tableau Best Practices:**
1. Use the most aggregated table that meets requirements
2. Create custom SQL data sources for complex calculations
3. Push calculations to BigQuery (not Tableau)
4. Use context filters to reduce data scanned
5. Schedule extract refreshes during off-peak hours

---

### Question 16: Calculated Fields in BigQuery vs Tableau
**Question:** When should calculations be done in BigQuery vs Tableau? Provide examples and performance considerations.

**Answer:**

**General Rule:** Push calculations to BigQuery whenever possible.

**Do in BigQuery:**
1. Aggregations (SUM, COUNT, AVG)
2. Complex joins and unions
3. Date transformations
4. String manipulations
5. Row-level calculations on large datasets

**Do in Tableau:**
1. User-specific filters/parameters
2. Visual formatting (colors, labels)
3. Quick table calculations (running totals, rank within viz)
4. Ad-hoc analysis requiring flexibility

**Examples:**

```sql
-- ============================================
-- IN BIGQUERY (Recommended)
-- ============================================

-- 1. Complex Date Calculations
CREATE OR REPLACE VIEW `project.tableau_views.sales_with_periods` AS
SELECT 
  order_id,
  order_date,
  revenue,
  -- Fiscal calculations
  CASE 
    WHEN EXTRACT(MONTH FROM order_date) <= 3 THEN EXTRACT(YEAR FROM order_date)
    ELSE EXTRACT(YEAR FROM order_date) + 1
  END AS fiscal_year,
  -- Period comparisons
  DATE_DIFF(CURRENT_DATE(), order_date, DAY) AS days_ago,
  CASE 
    WHEN order_date >= DATE_TRUNC(CURRENT_DATE(), MONTH) THEN 'Current Month'
    WHEN order_date >= DATE_SUB(DATE_TRUNC(CURRENT_DATE(), MONTH), INTERVAL 1 MONTH) THEN 'Last Month'
    ELSE 'Prior Months'
  END AS period_bucket,
  -- Customer segmentation
  CASE 
    WHEN customer_lifetime_value > 10000 THEN 'High Value'
    WHEN customer_lifetime_value > 1000 THEN 'Medium Value'
    ELSE 'Low Value'
  END AS customer_value_segment
FROM `project.dataset.fact_sales`;

-- 2. Pre-aggregated Metrics
CREATE MATERIALIZED VIEW `project.tableau_views.customer_metrics` AS
SELECT 
  customer_id,
  customer_name,
  customer_segment,
  -- Aggregated metrics
  COUNT(DISTINCT order_id) AS total_orders,
  SUM(revenue) AS lifetime_revenue,
  AVG(revenue) AS avg_order_value,
  MAX(order_date) AS last_order_date,
  MIN(order_date) AS first_order_date,
  DATE_DIFF(MAX(order_date), MIN(order_date), DAY) AS customer_tenure_days,
  -- Calculated metrics
  SAFE_DIVIDE(SUM(revenue), COUNT(DISTINCT order_id)) AS revenue_per_order,
  CASE 
    WHEN MAX(order_date) >= DATE_SUB(CURRENT_DATE(), INTERVAL 90 DAY) THEN 'Active'
    WHEN MAX(order_date) >= DATE_SUB(CURRENT_DATE(), INTERVAL 180 DAY) THEN 'At Risk'
    ELSE 'Churned'
  END AS customer_status
FROM `project.dataset.fact_sales`
GROUP BY customer_id, customer_name, customer_segment;

-- 3. Complex Business Logic
CREATE OR REPLACE VIEW `project.tableau_views.product_performance` AS
SELECT 
  product_id,
  product_name,
  category,
  SUM(revenue) AS total_revenue,
  SUM(quantity) AS total_quantity,
  COUNT(DISTINCT order_id) AS order_count,
  -- Profitability
  SAFE_DIVIDE(SUM(profit), SUM(revenue)) * 100 AS profit_margin_pct,
  -- Performance tier
  CASE 
    WHEN SAFE_DIVIDE(SUM(profit), SUM(revenue)) >= 0.3 AND SUM(revenue) > 100000 THEN 'Star'
    WHEN SAFE_DIVIDE(SUM(profit), SUM(revenue)) >= 0.3 AND SUM(revenue) <= 100000 THEN 'High Margin'
    WHEN SAFE_DIVIDE(SUM(profit), SUM(revenue)) < 0.3 AND SUM(revenue) > 100000 THEN 'High Volume'
    ELSE 'Low Performer'
  END AS product_tier,
  -- Inventory calculations
  CASE 
    WHEN AVG(inventory_days) <= 30 THEN 'Fast Moving'
    WHEN AVG(inventory_days) <= 60 THEN 'Normal'
    ELSE 'Slow Moving'
  END AS inventory_status
FROM `project.dataset.fact_sales`
GROUP BY product_id, product_name, category;
```

**In Tableau (When Appropriate):**

```
// Year-over-Year Comparison (Table Calc)
(SUM([Revenue]) - LOOKUP(SUM([Revenue]), -1)) / LOOKUP(SUM([Revenue]), -1)

// Running Total (Table Calc)
RUNNING_SUM(SUM([Revenue]))

// Percentile Ranking (Table Calc)
RANK_PERCENTILE(SUM([Revenue]))

// Parameter-based Dynamic Measure
CASE [Metric Parameter]
  WHEN 'Revenue' THEN SUM([Revenue])
  WHEN 'Profit' THEN SUM([Profit])
  WHEN 'Orders' THEN COUNT([Order ID])
END

// User-specific Date Range (Parameter Filter)
[Order Date] >= [Start Date Parameter] 
AND [Order Date] <= [End Date Parameter]
```

**Performance Impact:**

| Calculation Type | BigQuery | Tableau |
|-----------------|----------|---------|
| 10M row aggregation | 5 seconds | 60+ seconds |
| Complex joins | Optimized | Can hang |
| Date logic on 10M rows | 3 seconds | 30+ seconds |
| Simple table calc | N/A | <1 second |
| Parameter filter | N/A | Instant |

---

## Section 6: Advanced Scenarios

### Question 17: Handling Late-Arriving Data
**Question:** How would you handle late-arriving facts in a data warehouse? Provide a SQL solution.

**Answer:**

**Late-Arriving Data:** When fact records arrive after their dimension records or after the reporting period closes.

**Solution Strategy:**
1. Use surrogate keys (not natural keys)
2. Implement default/placeholder dimension records
3. Re-process affected periods
4. Track data lineage

**Implementation:**

```sql
-- 1. Create inferred dimension record
CREATE OR REPLACE PROCEDURE `project.etl.handle_late_arriving_dimension`(
  missing_customer_id STRING
)
BEGIN
  -- Check if customer exists
  IF NOT EXISTS (
    SELECT 1 
    FROM `project.dataset.dim_customer` 
    WHERE customer_id = missing_customer_id
  ) THEN
    -- Insert inferred member with default values
    INSERT INTO `project.dataset.dim_customer` (
      customer_key,  -- Surrogate key
      customer_id,   -- Natural key
      customer_name,
      customer_segment,
      region,
      is_inferred,
      effective_date,
      end_date,
      is_current
    )
    VALUES (
      GENERATE_UUID(),
      missing_customer_id,
      'Unknown - Pending',
      'Unknown',
      'Unknown',
      TRUE,  -- Mark as inferred
      CURRENT_DATE(),
      DATE('9999-12-31'),
      TRUE
    );
  END IF;
END;

-- 2. Load facts with late-arriving dimension handling
CREATE OR REPLACE PROCEDURE `project.etl.load_sales_with_late_arriving`()
BEGIN
  -- Step 1: Identify missing dimensions
  CREATE TEMP TABLE missing_customers AS
  SELECT DISTINCT s.customer_id
  FROM `project.staging.sales` s
  LEFT JOIN `project.dataset.dim_customer` d 
    ON s.customer_id = d.customer_id AND d.is_current = TRUE
  WHERE d.customer_id IS NULL;
  
  -- Step 2: Create inferred dimension records
  INSERT INTO `project.dataset.dim_customer` (
    customer_key,
    customer_id,
    customer_name,
    customer_segment,
    region,
    is_inferred,
    effective_date,
    end_date,
    is_current,
    created_timestamp
  )
  SELECT 
    GENERATE_UUID(),
    customer_id,
    CONCAT('Inferred-', customer_id),
    'Unknown',
    'Unknown',
    TRUE,
    CURRENT_DATE(),
    DATE('9999-12-31'),
    TRUE,
    CURRENT_TIMESTAMP()
  FROM missing_customers;
  
  -- Step 3: Load facts with surrogate keys
  INSERT INTO `project.dataset.fact_sales` (
    sale_key,
    order_id,
    customer_key,  -- Use surrogate key
    product_key,
    date_key,
    order_date,
    quantity,
    revenue,
    profit,
    is_late_arriving
  )
  SELECT 
    GENERATE_UUID(),
    s.order_id,
    d.customer_key,  -- Join to get surrogate key
    p.product_key,
    dt.date_key,
    s.order_date,
    s.quantity,
    s.revenue,
    s.profit,
    d.is_inferred AS is_late_arriving  -- Flag late-arriving records
  FROM `project.staging.sales` s
  LEFT JOIN `project.dataset.dim_customer` d 
    ON s.customer_id = d.customer_id AND d.is_current = TRUE
  LEFT JOIN `project.dataset.dim_product` p 
    ON s.product_id = p.product_id AND p.is_current = TRUE
  LEFT JOIN `project.dataset.dim_date` dt 
    ON s.order_date = dt.date;
  
  -- Step 4: Log late-arriving records
  INSERT INTO `project.etl_metadata.late_arriving_log`
  SELECT 
    GENERATE_UUID(),
    'sales',
    s.order_id,
    s.customer_id,
    'Missing customer dimension',
    CURRENT_TIMESTAMP()
  FROM `project.staging.sales` s
  INNER JOIN missing_customers m ON s.customer_id = m.customer_id;
END;

-- 3. Update inferred records when actual data arrives
CREATE OR REPLACE PROCEDURE `project.etl.update_inferred_dimensions`()
BEGIN
  -- Update inferred customer records with actual data
  UPDATE `project.dataset.dim_customer` d
  SET 
    customer_name = s.customer_name,
    customer_segment = s.customer_segment,
    region = s.region,
    email = s.email,
    phone = s.phone,
    is_inferred = FALSE,
    updated_timestamp = CURRENT_TIMESTAMP()
  FROM `project.staging.customer_master` s
  WHERE d.customer_id = s.customer_id
    AND d.is_inferred = TRUE
    AND d.is_current = TRUE;
  
  -- Update fact records flag
  UPDATE `project.dataset.fact_sales` f
  SET is_late_arriving = FALSE
  FROM `project.dataset.dim_customer` d
  WHERE f.customer_key = d.customer_key
    AND f.is_late_arriving = TRUE
    AND d.is_inferred = FALSE;
END;

-- 4. Monitoring query for late-arriving data
SELECT 
  DATE(f.created_timestamp) AS load_date,
  COUNT(*) AS total_records,
  COUNTIF(f.is_late_arriving) AS late_arriving_records,
  SAFE_DIVIDE(COUNTIF(f.is_late_arriving), COUNT(*)) * 100 AS late_arriving_pct
FROM `project.dataset.fact_sales` f
WHERE DATE(f.created_timestamp) >= DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY)
GROUP BY load_date
ORDER BY load_date DESC;
```

---

### Question 18: Data Reconciliation Strategy
**Question:** How would you implement data reconciliation between source and target systems in an ETL process? Provide a comprehensive approach.

**Answer:**

**Reconciliation Levels:**
1. **Record Count** - Total rows match
2. **Sum Reconciliation** - Totals match
3. **Hash Reconciliation** - Row-level data integrity
4. **Business Rule Validation** - Domain-specific checks

**Implementation:**

```sql
-- 1. Reconciliation Framework Table
CREATE TABLE `project.etl_metadata.reconciliation_results` (
  recon_id STRING DEFAULT GENERATE_UUID(),
  batch_id STRING,
  table_name STRING,
  recon_type STRING,  -- COUNT, SUM, HASH, BUSINESS_RULE
  source_value FLOAT64,
  target_value FLOAT64,
  difference FLOAT64,
  difference_pct FLOAT64,
  status STRING,  -- PASS, FAIL, WARNING
  recon_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
  details STRING
);

-- 2. Comprehensive Reconciliation Procedure
CREATE OR REPLACE PROCEDURE `project.etl.reconcile_sales_load`(
  batch_id STRING,
  load_date DATE
)
BEGIN
  DECLARE source_count INT64;
  DECLARE target_count INT64;
  DECLARE source_sum FLOAT64;
  DECLARE target_sum FLOAT64;
  
  -- ========================================
  -- 1. RECORD COUNT RECONCILIATION
  -- ========================================
  SET source_count = (
    SELECT COUNT(*) 
    FROM `project.staging.sales`
    WHERE DATE(order_timestamp) = load_date
  );
  
  SET target_count = (
    SELECT COUNT(*) 
    FROM `project.dataset.fact_sales`
    WHERE DATE(order_timestamp) = load_date
      AND batch_id = batch_id
  );
  
  INSERT INTO `project.etl_metadata.reconciliation_results` (
    batch_id,
    table_name,
    recon_type,
    source_value,
    target_value,
    difference,
    difference_pct,
    status
  )
  VALUES (
    batch_id,
    'fact_sales',
    'RECORD_COUNT',
    source_count,
    target_count,
    source_count - target_count,
    SAFE_DIVIDE(ABS(source_count - target_count), source_count) * 100,
    CASE 
      WHEN source_count = target_count THEN 'PASS'
      WHEN ABS(source_count - target_count) / source_count < 0.01 THEN 'WARNING'
      ELSE 'FAIL'
    END
  );
  
  -- ========================================
  -- 2. SUM RECONCILIATION (Control Totals)
  -- ========================================
  CREATE TEMP TABLE sum_recon AS
  SELECT 
    'revenue' AS metric_name,
    (SELECT SUM(revenue) FROM `project.staging.sales` 
     WHERE DATE(order_timestamp) = load_date) AS source_value,
    (SELECT SUM(revenue) FROM `project.dataset.fact_sales` 
     WHERE DATE(order_timestamp) = load_date AND batch_id = batch_id) AS target_value
  UNION ALL
  SELECT 
    'quantity',
    (SELECT SUM(quantity) FROM `project.staging.sales` 
     WHERE DATE(order_timestamp) = load_date),
    (SELECT SUM(quantity) FROM `project.dataset.fact_sales` 
     WHERE DATE(order_timestamp) = load_date AND batch_id = batch_id)
  UNION ALL
  SELECT 
    'profit',
    (SELECT SUM(profit) FROM `project.staging.sales` 
     WHERE DATE(order_timestamp) = load_date),
    (SELECT SUM(profit) FROM `project.dataset.fact_sales` 
     WHERE DATE(order_timestamp) = load_date AND batch_id = batch_id);
  
  INSERT INTO `project.etl_metadata.reconciliation_results` (
    batch_id,
    table_name,
    recon_type,
    source_value,
    target_value,
    difference,
    difference_pct,
    status,
    details
  )
  SELECT 
    batch_id,
    'fact_sales',
    CONCAT('SUM_', UPPER(metric_name)),
    source_value,
    target_value,
    source_value - target_value,
    SAFE_DIVIDE(ABS(source_value - target_value), source_value) * 100,
    CASE 
      WHEN ABS(source_value - target_value) < 0.01 THEN 'PASS'
      WHEN SAFE_DIVIDE(ABS(source_value - target_value), source_value) < 0.001 THEN 'WARNING'
      ELSE 'FAIL'
    END,
    metric_name
  FROM sum_recon;
  
  -- ========================================
  -- 3. HASH RECONCILIATION (Row-level)
  -- ========================================
  CREATE TEMP TABLE hash_comparison AS
  SELECT 
    'SOURCE' AS source_type,
    order_id,
    TO_HEX(MD5(CONCAT(
      COALESCE(CAST(order_id AS STRING), ''),
      COALESCE(CAST(customer_id AS STRING), ''),
      COALESCE(CAST(product_id AS STRING), ''),
      COALESCE(CAST(quantity AS STRING), ''),
      COALESCE(CAST(revenue AS STRING), '')
    ))) AS row_hash
  FROM `project.staging.sales`
  WHERE DATE(order_timestamp) = load_date
  
  UNION ALL
  
  SELECT 
    'TARGET' AS source_type,
    order_id,
    TO_HEX(MD5(CONCAT(
      COALESCE(CAST(order_id AS STRING), ''),
      COALESCE(CAST(customer_id AS STRING), ''),
      COALESCE(CAST(product_id AS STRING), ''),
      COALESCE(CAST(quantity AS STRING), ''),
      COALESCE(CAST(revenue AS STRING), '')
    ))) AS row_hash
  FROM `project.dataset.fact_sales`
  WHERE DATE(order_timestamp) = load_date
    AND batch_id = batch_id;
  
  -- Identify mismatches
  CREATE TEMP TABLE hash_mismatches AS
  SELECT 
    order_id,
    MAX(CASE WHEN source_type = 'SOURCE' THEN row_hash END) AS source_hash,
    MAX(CASE WHEN source_type = 'TARGET' THEN row_hash END) AS target_hash
  FROM hash_comparison
  GROUP BY order_id
  HAVING source_hash != target_hash OR source_hash IS NULL OR target_hash IS NULL;
  
  INSERT INTO `project.etl_metadata.reconciliation_results` (
    batch_id,
    table_name,
    recon_type,
    source_value,
    target_value,
    difference,
    status,
    details
  )
  SELECT 
    batch_id,
    'fact_sales',
    'HASH_COMPARISON',
    source_count,
    source_count - (SELECT COUNT(*) FROM hash_mismatches),
    (SELECT COUNT(*) FROM hash_mismatches),
    CASE 
      WHEN (SELECT COUNT(*) FROM hash_mismatches) = 0 THEN 'PASS'
      WHEN (SELECT COUNT(*) FROM hash_mismatches) < source_count * 0.01 THEN 'WARNING'
      ELSE 'FAIL'
    END,
    (SELECT STRING_AGG(order_id, ', ' LIMIT 10) FROM hash_mismatches);
  
  -- ========================================
  -- 4. BUSINESS RULE VALIDATION
  -- ========================================
  
  -- Example: Check for negative amounts
  INSERT INTO `project.etl_metadata.reconciliation_results` (
    batch_id,
    table_name,
    recon_type,
    target_value,
    status,
    details
  )
  SELECT 
    batch_id,
    'fact_sales',
    'BUSINESS_RULE_NEGATIVE_AMOUNT',
    COUNT(*),
    CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END,
    'Records with negative revenue or quantity'
  FROM `project.dataset.fact_sales`
  WHERE DATE(order_timestamp) = load_date
    AND batch_id = batch_id
    AND (revenue < 0 OR quantity < 0);
  
  -- Example: Check for orphaned records
  INSERT INTO `project.etl_metadata.reconciliation_results` (
    batch_id,
    table_name,
    recon_type,
    target_value,
    status,
    details
  )
  SELECT 
    batch_id,
    'fact_sales',
    'BUSINESS_RULE_ORPHAN_CHECK',
    COUNT(*),
    CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END,
    'Sales records without valid customer dimension'
  FROM `project.dataset.fact_sales` f
  LEFT JOIN `project.dataset.dim_customer` c ON f.customer_key = c.customer_key
  WHERE DATE(f.order_timestamp) = load_date
    AND f.batch_id = batch_id
    AND c.customer_key IS NULL;
  
  -- ========================================
  -- 5. GENERATE SUMMARY REPORT
  -- ========================================
  SELECT 
    recon_type,
    status,
    source_value,
    target_value,
    difference,
    difference_pct,
    details
  FROM `project.etl_metadata.reconciliation_results`
  WHERE batch_id = batch_id
  ORDER BY 
    CASE status 
      WHEN 'FAIL' THEN 1 
      WHEN 'WARNING' THEN 2 
      ELSE 3 
    END,
    recon_type;
  
  -- Raise error if critical reconciliation fails
  IF EXISTS (
    SELECT 1 
    FROM `project.etl_metadata.reconciliation_results`
    WHERE batch_id = batch_id
      AND status = 'FAIL'
      AND recon_type IN ('RECORD_COUNT', 'SUM_REVENUE')
  ) THEN
    RAISE USING MESSAGE = 'Critical reconciliation failed - review reconciliation_results table';
  END IF;
END;
```

---

### Question 19: Implementing Data Lineage
**Question:** How would you implement data lineage tracking in BigQuery to trace data from source to reports? Why is it important for ETL QA?

**Answer:**

**Data Lineage Importance:**
- Tracks data transformation journey
- Enables impact analysis for changes
- Supports compliance and auditing
- Helps debug data quality issues
- Documents ETL dependencies

**Implementation:**

```sql
-- 1. Lineage Metadata Tables
CREATE TABLE `project.etl_metadata.data_lineage` (
  lineage_id STRING DEFAULT GENERATE_UUID(),
  source_table STRING,
  source_dataset STRING,
  source_project STRING,
  target_table STRING,
  target_dataset STRING,
  target_project STRING,
  transformation_type STRING,  -- LOAD, TRANSFORM, AGGREGATE, JOIN
  transformation_logic STRING,
  dependency_level INT64,  -- 1=source, 2=staging, 3=warehouse, 4=mart
  created_by STRING,
  created_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP()
);

CREATE TABLE `project.etl_metadata.column_lineage` (
  column_lineage_id STRING DEFAULT GENERATE_UUID(),
  lineage_id STRING,
  source_column STRING,
  target_column STRING,
  transformation_rule STRING,
  data_type STRING,
  is_key BOOL,
  created_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP()
);

CREATE TABLE `project.etl_metadata.execution_lineage` (
  execution_id STRING DEFAULT GENERATE_UUID(),
  lineage_id STRING,
  batch_id STRING,
  execution_timestamp TIMESTAMP,
  records_processed INT64,
  execution_status STRING,
  execution_duration_seconds INT64
);

-- 2. Register Lineage (Call in each ETL step)
CREATE OR REPLACE PROCEDURE `project.etl_metadata.register_lineage`(
  p_source_table STRING,
  p_target_table STRING,
  p_transformation_type STRING,
  p_transformation_logic STRING,
  p_dependency_level INT64
)
BEGIN
  INSERT INTO `project.etl_metadata.data_lineage` (
    source_table,
    source_dataset,
    source_project,
    target_table,
    target_dataset,
    target_project,
    transformation_type,
    transformation_logic,
    dependency_level,
    created_by
  )
  VALUES (
    SPLIT(p_source_table, '.')[SAFE_OFFSET(2)],
    SPLIT(p_source_table, '.')[SAFE_OFFSET(1)],
    SPLIT(p_source_table, '.')[SAFE_OFFSET(0)],
    SPLIT(p_target_table, '.')[SAFE_OFFSET(2)],
    SPLIT(p_target_table, '.')[SAFE_OFFSET(1)],
    SPLIT(p_target_table, '.')[SAFE_OFFSET(0)],
    p_transformation_type,
    p_transformation_logic,
    p_dependency_level,
    SESSION_USER()
  );
END;

-- 3. ETL Process with Lineage Tracking
CREATE OR REPLACE PROCEDURE `project.etl.load_sales_with_lineage`(
  batch_id STRING
)
BEGIN
  DECLARE execution_start TIMESTAMP;
  DECLARE rows_processed INT64;
  DECLARE current_lineage_id STRING;
  
  SET execution_start = CURRENT_TIMESTAMP();
  
  -- Register lineage
  CALL `project.etl_metadata.register_lineage`(
    'project.source.sales',
    'project.warehouse.fact_sales',
    'TRANSFORM_AND_LOAD',
    'Join with dimensions, calculate metrics, apply business rules',
    2
  );
  
  -- Get lineage ID
  SET current_lineage_id = (
    SELECT lineage_id 
    FROM `project.etl_metadata.data_lineage`
    ORDER BY created_timestamp DESC 
    LIMIT 1
  );
  
  -- Register column lineage
  INSERT INTO `project.etl_metadata.column_lineage` (
    lineage_id,
    source_column,
    target_column,
    transformation_rule,
    data_type,
    is_key
  )
  VALUES 
    (current_lineage_id, 'order_id', 'order_id', 'Direct mapping', 'STRING', TRUE),
    (current_lineage_id, 'customer_id', 'customer_key', 'Lookup dimension surrogate key', 'STRING', TRUE),
    (current_lineage_id, 'amount', 'revenue', 'Direct mapping', 'FLOAT64', FALSE),
    (current_lineage_id, 'amount, cost', 'profit', 'Calculated: amount - cost', 'FLOAT64', FALSE);
  
  -- Execute transformation
  INSERT INTO `project.warehouse.fact_sales` (
    sale_key,
    order_id,
    customer_key,
    product_key,
    revenue,
    profit,
    batch_id,
    lineage_id
  )
  SELECT 
    GENERATE_UUID(),
    s.order_id,
    c.customer_key,
    p.product_key,
    s.amount,
    s.amount - s.cost,
    batch_id,
    current_lineage_id
  FROM `project.source.sales` s
  JOIN `project.warehouse.dim_customer` c ON s.customer_id = c.customer_id
  JOIN `project.warehouse.dim_product` p ON s.product_id = p.product_id;
  
  SET rows_processed = @@row_count;
  
  -- Record execution lineage
  INSERT INTO `project.etl_metadata.execution_lineage` (
    lineage_id,
    batch_id,
    execution_timestamp,
    records_processed,
    execution_status,
    execution_duration_seconds
  )
  VALUES (
    current_lineage_id,
    batch_id,
    execution_start,
    rows_processed,
    'SUCCESS',
    TIMESTAMP_DIFF(CURRENT_TIMESTAMP(), execution_start, SECOND)
  );
END;

-- 4. Query Lineage - Trace data from source to target
CREATE OR REPLACE VIEW `project.etl_metadata.v_lineage_trace` AS
WITH RECURSIVE lineage_path AS (
  -- Base: Start with source tables (level 1)
  SELECT 
    lineage_id,
    CONCAT(source_project, '.', source_dataset, '.', source_table) AS source_full_name,
    CONCAT(target_project, '.', target_dataset, '.', target_table) AS target_full_name,
    transformation_type,
    dependency_level,
    CAST(CONCAT(source_project, '.', source_dataset, '.', source_table) AS STRING) AS lineage_path,
    1 AS path_length
  FROM `project.etl_metadata.data_lineage`
  WHERE dependency_level = 1
  
  UNION ALL
  
  -- Recursive: Follow the chain
  SELECT 
    l.lineage_id,
    lp.source_full_name,
    CONCAT(l.target_project, '.', l.target_dataset, '.', l.target_table),
    l.transformation_type,
    l.dependency_level,
    CONCAT(lp.lineage_path, ' -> ', l.target_project, '.', l.target_dataset, '.', l.target_table),
    lp.path_length + 1
  FROM `project.etl_metadata.data_lineage` l
  JOIN lineage_path lp 
    ON CONCAT(l.source_project, '.', l.source_dataset, '.', l.source_table) = lp.target_full_name
  WHERE lp.path_length < 10  -- Prevent infinite loops
)
SELECT 
  source_full_name,
  target_full_name,
  lineage_path,
  path_length,
  dependency_level
FROM lineage_path
ORDER BY path_length, dependency_level;

-- 5. Impact Analysis - Find downstream dependencies
CREATE OR REPLACE FUNCTION `project.etl_metadata.get_downstream_tables`(
  input_table STRING
)
RETURNS ARRAY<STRING>
AS (
  (
    WITH RECURSIVE downstream AS (
      SELECT CONCAT(target_project, '.', target_dataset, '.', target_table) AS table_name
      FROM `project.etl_metadata.data_lineage`
      WHERE CONCAT(source_project, '.', source_dataset, '.', source_table) = input_table
      
      UNION DISTINCT
      
      SELECT CONCAT(l.target_project, '.', l.target_dataset, '.', l.target_table)
      FROM `project.etl_metadata.data_lineage` l
      JOIN downstream d 
        ON CONCAT(l.source_project, '.', l.source_dataset, '.', l.source_table) = d.table_name
    )
    SELECT ARRAY_AGG(DISTINCT table_name)
    FROM downstream
  )
);

-- Example usage: Find all tables affected by changing source.sales
SELECT `project.etl_metadata.get_downstream_tables`('project.source.sales');
```

---

### Question 20: Designing a Robust ETL Monitoring Dashboard
**Question:** Design a comprehensive ETL monitoring solution. What metrics would you track and how would you visualize them in Tableau?

**Answer:**

**Key Metrics to Track:**
1. **Execution Metrics** - Success rate, duration, throughput
2. **Data Quality Metrics** - Error rates, anomaly detection
3. **Performance Metrics** - Query costs, slot usage
4. **Business Metrics** - Data freshness, SLA compliance

**SQL Implementation:**

```sql
-- 1. ETL Monitoring Tables
CREATE TABLE `project.etl_metadata.pipeline_executions` (
  execution_id STRING DEFAULT GENERATE_UUID(),
  pipeline_name STRING,
  batch_id STRING,
  start_timestamp TIMESTAMP,
  end_timestamp TIMESTAMP,
  duration_seconds INT64,
  status STRING,  -- RUNNING, SUCCESS, FAILED, WARNING
  records_processed INT64,
  records_failed INT64,
  bytes_processed INT64,
  slot_hours FLOAT64,
  cost_usd FLOAT64,
  error_message STRING
);

-- 2. Monitoring Dashboard View
CREATE OR REPLACE VIEW `project.tableau_mart.v_etl_monitoring_dashboard` AS
SELECT 
  -- Time dimensions
  DATE(start_timestamp) AS execution_date,
  FORMAT_TIMESTAMP('%Y-%m', start_timestamp) AS execution_month,
  FORMAT_TIMESTAMP('%H', start_timestamp) AS execution_hour,
  EXTRACT(DAYOFWEEK FROM start_timestamp) AS day_of_week,
  
  -- Pipeline info
  pipeline_name,
  status,
  
  -- Execution metrics
  COUNT(*) AS execution_count,
  COUNTIF(status = 'SUCCESS') AS successful_runs,
  COUNTIF(status = 'FAILED') AS failed_runs,
  SAFE_DIVIDE(COUNTIF(status = 'SUCCESS'), COUNT(*)) * 100 AS success_rate_pct,
  
  -- Performance metrics
  AVG(duration_seconds) AS avg_duration_seconds,
  MAX(duration_seconds) AS max_duration_seconds,
  PERCENTILE_CONT(duration_seconds, 0.95) OVER (
    PARTITION BY pipeline_name
  ) AS p95_duration_seconds,
  
  -- Data volume
  SUM(records_processed) AS total_records_processed,
  SUM(records_failed) AS total_records_failed,
  SUM(bytes_processed) / POW(1024, 3) AS total_gb_processed,
  
  -- Cost metrics
  SUM(cost_usd) AS total_cost_usd,
  AVG(cost_usd) AS avg_cost_per_run,
  
  -- Data quality
  SAFE_DIVIDE(SUM(records_failed), SUM(records_processed)) * 100 AS failure_rate_pct,
  
  -- Freshness (time since last successful run)
  TIMESTAMP_DIFF(
    CURRENT_TIMESTAMP(),
    MAX(CASE WHEN status = 'SUCCESS' THEN end_timestamp END),
    HOUR
  ) AS hours_since_last_success
FROM `project.etl_metadata.pipeline_executions`
WHERE start_timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 90 DAY)
GROUP BY 
  execution_date,
  execution_month,
  execution_hour,
  day_of_week,
  pipeline_name,
  status;

-- 3. SLA Monitoring
CREATE OR REPLACE VIEW `project.tableau_mart.v_sla_monitoring` AS
WITH sla_thresholds AS (
  SELECT 'sales_daily_load' AS pipeline_name, 3600 AS max_duration_seconds, 98.0 AS min_success_rate
  UNION ALL SELECT 'customer_sync', 1800, 99.0
  UNION ALL SELECT 'product_hierarchy_refresh', 900, 99.5
)
SELECT 
  p.pipeline_name,
  DATE(p.start_timestamp) AS execution_date,
  COUNT(*) AS total_runs,
  COUNTIF(p.status = 'SUCCESS') AS successful_runs,
  SAFE_DIVIDE(COUNTIF(p.status = 'SUCCESS'), COUNT(*)) * 100 AS actual_success_rate,
  s.min_success_rate AS sla_success_rate,
  AVG(p.duration_seconds) AS avg_duration,
  s.max_duration_seconds AS sla_max_duration,
  -- SLA Status
  CASE 
    WHEN SAFE_DIVIDE(COUNTIF(p.status = 'SUCCESS'), COUNT(*)) * 100 >= s.min_success_rate
         AND AVG(p.duration_seconds) <= s.max_duration_seconds THEN 'MET'
    ELSE 'BREACHED'
  END AS sla_status
FROM `project.etl_metadata.pipeline_executions` p
JOIN sla_thresholds s ON p.pipeline_name = s.pipeline_name
WHERE DATE(p.start_timestamp) >= DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY)
GROUP BY p.pipeline_name, execution_date, s.min_success_rate, s.max_duration_seconds;

-- 4. Anomaly Detection
CREATE OR REPLACE VIEW `project.tableau_mart.v_etl_anomaly_detection` AS
WITH daily_stats AS (
  SELECT 
    pipeline_name,
    DATE(start_timestamp) AS execution_date,
    AVG(duration_seconds) AS avg_duration,
    SUM(records_processed) AS total_records,
    COUNT(*) AS run_count
  FROM `project.etl_metadata.pipeline_executions`
  WHERE status = 'SUCCESS'
    AND start_timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 90 DAY)
  GROUP BY pipeline_name, execution_date
),
stats_with_baseline AS (
  SELECT 
    *,
    AVG(avg_duration) OVER (
      PARTITION BY pipeline_name 
      ORDER BY execution_date 
      ROWS BETWEEN 30 PRECEDING AND 1 PRECEDING
    ) AS baseline_duration,
    STDDEV(avg_duration) OVER (
      PARTITION BY pipeline_name 
      ORDER BY execution_date 
      ROWS BETWEEN 30 PRECEDING AND 1 PRECEDING
    ) AS stddev_duration,
    AVG(total_records) OVER (
      PARTITION BY pipeline_name 
      ORDER BY execution_date 
      ROWS BETWEEN 30 PRECEDING AND 1 PRECEDING
    ) AS baseline_records
  FROM daily_stats
)
SELECT 
  pipeline_name,
  execution_date,
  avg_duration,
  baseline_duration,
  total_records,
  baseline_records,
  -- Anomaly flags
  CASE 
    WHEN avg_duration > baseline_duration + (2 * stddev_duration) THEN 'SLOW'
    WHEN avg_duration < baseline_duration - (2 * stddev_duration) THEN 'FAST'
    ELSE 'NORMAL'
  END AS duration_anomaly,
  CASE 
    WHEN total_records < baseline_records * 0.8 THEN 'LOW_VOLUME'
    WHEN total_records > baseline_records * 1.2 THEN 'HIGH_VOLUME'
    ELSE 'NORMAL'
  END AS volume_anomaly,
  -- Anomaly score
  ABS(avg_duration - baseline_duration) / NULLIF(stddev_duration, 0) AS duration_z_score
FROM stats_with_baseline
WHERE execution_date >= DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY);

-- 5. Cost Analysis
CREATE OR REPLACE VIEW `project.tableau_mart.v_etl_cost_analysis` AS
SELECT 
  FORMAT_TIMESTAMP('%Y-%m', start_timestamp) AS month,
  pipeline_name,
  COUNT(*) AS total_executions,
  SUM(cost_usd) AS total_cost,
  AVG(cost_usd) AS avg_cost_per_execution,
  SUM(bytes_processed) / POW(1024, 4) AS total_tb_processed,
  SAFE_DIVIDE(SUM(cost_usd), SUM(bytes_processed) / POW(1024, 4)) AS cost_per_tb,
  SUM(slot_hours) AS total_slot_hours
FROM `project.etl_metadata.pipeline_executions`
WHERE start_timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 12 MONTH)
  AND status = 'SUCCESS'
GROUP BY month, pipeline_name;
```

**Tableau Dashboard Design:**

**Dashboard 1: Executive Summary**
- KPI Cards: Success Rate (gauge), Total Pipelines, Failed Runs Today
- Line Chart: Daily Success Rate Trend (30 days)
- Bar Chart: Top 5 Failed Pipelines
- Heat Map: Pipeline Execution Time by Hour of Day

**Dashboard 2: Performance Monitoring**
- Dual-Axis Chart: Execution Duration vs Record Volume
- Box Plot: Duration Distribution by Pipeline
- Scatter Plot: Cost vs Performance (bubble size = data volume)
- Table: P50, P95, P99 Duration by Pipeline

**Dashboard 3: SLA Compliance**
- Bullet Chart: Actual vs SLA Target Success Rate
- Gantt Chart: Pipeline Execution Timeline
- Treemap: SLA Breaches by Pipeline (size = breach count)
- Calendar Heat Map: Daily SLA Status

**Dashboard 4: Data Quality**
- Area Chart: Error Rate Trend
- Waterfall Chart: Record Flow (Processed, Failed, Success)
- Pareto Chart: Top Error Types
- Control Chart: Data Volume with Anomaly Bands

**Dashboard 5: Cost Optimization**
- Stacked Bar Chart: Monthly Cost by Pipeline
- Line Chart: Cost Trend with Forecast
- Table: Cost per TB by Pipeline
- Highlight Table: Cost per Successful Record

---

## Summary: Interview Success Tips

**For Candidates (You):**
1. **Understand the "Why"** - Don't just write SQL, explain design decisions
2. **Think Production-Ready** - Include error handling, logging, monitoring
3. **Optimize First** - Always consider performance and cost
4. **Data Quality is Key** - Show comprehensive testing approach
5. **Document Well** - Code should be self-explanatory with comments

**Red Flags to Avoid:**
- Using SELECT * in production code
- No error handling or logging
- Ignoring data quality checks
- Not considering incremental loads
- Pushing calculations to Tableau instead of BigQuery
- No partitioning/clustering strategy
- Missing reconciliation

**Green Flags to Demonstrate:**
- Proper use of window functions
- Understanding of SCD Type 2
- Knowledge of BigQuery optimizations (partitioning, clustering, materialized views)
- Comprehensive error handling
- Data lineage tracking
- Proactive monitoring

Good luck with your interview! 🎯
