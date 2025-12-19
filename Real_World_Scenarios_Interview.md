# Real-World ETL QA Scenarios
## Practical Interview Questions for Senior Roles

These scenario-based questions assess problem-solving, system design, and production experience.

---

## Scenario 1: Production Data Quality Issue 🚨

### Situation
```
It's Monday morning. Your dashboard shows that yesterday's sales data loaded successfully 
(10,000 records), but the total revenue is $50M, whereas the average for Sundays is $2M.

The business team is questioning the data accuracy before making decisions.
```

### Interview Questions

**Q1:** Walk me through your immediate troubleshooting steps.

**Expected Answer:**
```
1. Check if issue is in source or transformation:
   - Compare source system totals with DW totals
   - Check reconciliation reports
   
2. Investigate potential causes:
   - Duplicate records loaded?
   - Currency conversion error?
   - Wrong date filter (multiple days loaded)?
   - Outlier orders (test orders, bulk orders)?
   
3. Query to investigate:
```

```sql
-- Check for duplicates
SELECT 
  order_id,
  COUNT(*) AS duplicate_count,
  SUM(amount) AS total_amount
FROM sales_fact
WHERE DATE(load_timestamp) = CURRENT_DATE()
GROUP BY order_id
HAVING COUNT(*) > 1
ORDER BY total_amount DESC
LIMIT 20;

-- Check for outliers
SELECT 
  order_id,
  customer_id,
  amount,
  PERCENTILE_CONT(amount, 0.99) OVER () AS p99_amount
FROM sales_fact
WHERE sale_date = DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY)
QUALIFY amount > p99_amount * 10;

-- Verify date range loaded
SELECT 
  sale_date,
  COUNT(*) AS record_count,
  SUM(amount) AS total_revenue
FROM sales_fact
WHERE DATE(load_timestamp) = CURRENT_DATE()
GROUP BY sale_date;

-- Compare with source
SELECT 
  'SOURCE' AS source,
  COUNT(*) AS records,
  SUM(amount) AS total
FROM source_system.sales
WHERE sale_date = DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY)
UNION ALL
SELECT 
  'TARGET',
  COUNT(*),
  SUM(amount)
FROM warehouse.sales_fact
WHERE sale_date = DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY);
```

**Q2:** How would you prevent this in the future?

**Expected Answer:**
```sql
-- Implement automated data quality checks
CREATE OR REPLACE PROCEDURE check_revenue_anomaly()
BEGIN
  DECLARE current_revenue FLOAT64;
  DECLARE avg_revenue FLOAT64;
  DECLARE std_dev FLOAT64;
  
  -- Get current day revenue
  SET current_revenue = (
    SELECT SUM(amount)
    FROM sales_fact
    WHERE sale_date = CURRENT_DATE()
  );
  
  -- Get historical average and std dev for same day of week
  SET (avg_revenue, std_dev) = (
    SELECT AS STRUCT AVG(daily_revenue), STDDEV(daily_revenue)
    FROM (
      SELECT 
        sale_date,
        SUM(amount) AS daily_revenue
      FROM sales_fact
      WHERE EXTRACT(DAYOFWEEK FROM sale_date) = EXTRACT(DAYOFWEEK FROM CURRENT_DATE())
        AND sale_date >= DATE_SUB(CURRENT_DATE(), INTERVAL 90 DAY)
        AND sale_date < CURRENT_DATE()
      GROUP BY sale_date
    )
  );
  
  -- Alert if more than 3 standard deviations from mean
  IF current_revenue > avg_revenue + (3 * std_dev) THEN
    INSERT INTO alerts (alert_type, message, severity, created_at)
    VALUES (
      'REVENUE_ANOMALY',
      FORMAT('Current revenue $%t is significantly higher than expected $%t', 
             current_revenue, avg_revenue),
      'HIGH',
      CURRENT_TIMESTAMP()
    );
  END IF;
END;
```

**Key Concepts Tested:**
- ✅ Troubleshooting methodology
- ✅ SQL investigation queries
- ✅ Understanding of data reconciliation
- ✅ Proactive monitoring design
- ✅ Statistical anomaly detection

---

## Scenario 2: Performance Degradation 🐌

### Situation
```
A Tableau dashboard that was loading in 5 seconds now takes 3 minutes.
The underlying BigQuery table has grown from 10M to 500M rows over 6 months.
No changes were made to the query.

Users are complaining and threatening to stop using the dashboard.
```

### Interview Questions

**Q1:** How would you diagnose and fix this performance issue?

**Expected Answer:**

```sql
-- 1. Check current query execution
SELECT 
  query,
  creation_time,
  total_bytes_processed / POW(1024, 3) AS gb_processed,
  total_slot_ms / 1000 AS slot_seconds,
  TIMESTAMP_DIFF(end_time, start_time, SECOND) AS duration_seconds
FROM `region-us`.INFORMATION_SCHEMA.JOBS_BY_PROJECT
WHERE user_email = 'tableau@company.com'
  AND creation_time >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 DAY)
ORDER BY total_bytes_processed DESC
LIMIT 10;

-- 2. Check table structure
SELECT 
  table_name,
  row_count,
  size_bytes / POW(1024, 3) AS size_gb,
  TIMESTAMP_DIFF(CURRENT_TIMESTAMP(), creation_time, DAY) AS age_days
FROM `project.dataset.__TABLES__`
WHERE table_id = 'sales_table';

-- 3. Check if table is partitioned/clustered
SELECT 
  table_name,
  partition_expiration_days,
  clustering_fields
FROM `project.dataset.INFORMATION_SCHEMA.TABLES`
WHERE table_name = 'sales_table';
```

**Solutions to implement:**

```sql
-- Solution 1: Add partitioning and clustering
CREATE OR REPLACE TABLE `project.dataset.sales_table_optimized`
PARTITION BY DATE(order_date)
CLUSTER BY customer_id, region
AS SELECT * FROM `project.dataset.sales_table`;

-- Solution 2: Create aggregated table for dashboard
CREATE MATERIALIZED VIEW `project.tableau_mart.sales_summary` AS
SELECT 
  DATE(order_date) AS date,
  region,
  product_category,
  COUNT(*) AS order_count,
  SUM(revenue) AS total_revenue,
  SUM(profit) AS total_profit
FROM `project.dataset.sales_table_optimized`
WHERE order_date >= DATE_SUB(CURRENT_DATE(), INTERVAL 2 YEAR)
GROUP BY date, region, product_category;

-- Solution 3: Create data extract schedule
-- In Tableau, switch to extract mode with incremental refresh
-- Or create pre-aggregated daily table:
CREATE OR REPLACE TABLE `project.tableau_mart.sales_daily` AS
SELECT 
  DATE(order_date) AS date,
  region,
  product_category,
  SUM(revenue) AS revenue,
  SUM(profit) AS profit,
  COUNT(*) AS orders
FROM `project.dataset.sales_table_optimized`
GROUP BY date, region, product_category;

-- Schedule refresh daily
```

**Q2:** What metrics would you track going forward?

**Expected Answer:**
```sql
-- Dashboard performance tracking
CREATE OR REPLACE TABLE `project.monitoring.dashboard_performance` AS
SELECT 
  CURRENT_TIMESTAMP() AS check_time,
  'sales_dashboard' AS dashboard_name,
  -- Query performance
  (SELECT AVG(TIMESTAMP_DIFF(end_time, start_time, SECOND))
   FROM `region-us`.INFORMATION_SCHEMA.JOBS_BY_PROJECT
   WHERE query LIKE '%sales_table%'
     AND creation_time >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 HOUR)
  ) AS avg_query_duration_sec,
  -- Data freshness
  (SELECT TIMESTAMP_DIFF(CURRENT_TIMESTAMP(), MAX(etl_timestamp), MINUTE)
   FROM `project.dataset.sales_table`
  ) AS data_freshness_minutes,
  -- Table size
  (SELECT size_bytes / POW(1024, 3)
   FROM `project.dataset.__TABLES__`
   WHERE table_id = 'sales_table'
  ) AS table_size_gb,
  -- Row count
  (SELECT row_count
   FROM `project.dataset.__TABLES__`
   WHERE table_id = 'sales_table'
  ) AS row_count;
```

**Key Concepts Tested:**
- ✅ Performance troubleshooting
- ✅ BigQuery optimization techniques
- ✅ Partitioning and clustering
- ✅ Materialized views
- ✅ Dashboard optimization strategies
- ✅ Proactive monitoring

---

## Scenario 3: Source System Changes 🔄

### Situation
```
The source system team just informed you (via email, 2 hours before go-live) that they're 
changing the customer table structure:
- Splitting 'address' field into street, city, state, zip
- Changing customer_type from VARCHAR to INT (1=retail, 2=wholesale, 3=enterprise)
- Adding a new field 'loyalty_tier'

Your ETL runs nightly at 2 AM. It's now 8 PM.
```

### Interview Questions

**Q1:** What are your immediate actions?

**Expected Answer:**

**Immediate Actions:**
1. Request schema comparison (old vs new)
2. Ask for sample data
3. Confirm exact deployment time
4. Request rollback plan
5. Identify downstream impact

**Q2:** How do you handle this with minimal disruption?

**Expected Answer:**

```sql
-- Step 1: Create staging table with flexible schema
CREATE OR REPLACE TABLE `project.staging.customers_new` AS
SELECT 
  customer_id,
  customer_name,
  email,
  -- Handle address field (old or new format)
  CASE 
    WHEN street IS NOT NULL THEN street  -- New format
    ELSE SPLIT(address, ',')[SAFE_OFFSET(0)]  -- Old format
  END AS street,
  CASE 
    WHEN city IS NOT NULL THEN city
    ELSE SPLIT(address, ',')[SAFE_OFFSET(1)]
  END AS city,
  CASE 
    WHEN state IS NOT NULL THEN state
    ELSE SPLIT(address, ',')[SAFE_OFFSET(2)]
  END AS state,
  CASE 
    WHEN zip IS NOT NULL THEN zip
    ELSE SPLIT(address, ',')[SAFE_OFFSET(3)]
  END AS zip,
  -- Handle customer_type conversion
  CASE 
    WHEN SAFE_CAST(customer_type AS INT64) IS NOT NULL THEN
      CASE SAFE_CAST(customer_type AS INT64)
        WHEN 1 THEN 'retail'
        WHEN 2 THEN 'wholesale'
        WHEN 3 THEN 'enterprise'
      END
    ELSE customer_type  -- Already string format
  END AS customer_type_mapped,
  -- Handle new field with default
  COALESCE(loyalty_tier, 'STANDARD') AS loyalty_tier,
  updated_at
FROM `project.source.customers`;

-- Step 2: Validation query to detect which format is being used
SELECT 
  'Address format' AS check_type,
  COUNTIF(street IS NOT NULL) AS new_format_count,
  COUNTIF(street IS NULL AND address IS NOT NULL) AS old_format_count,
  CASE 
    WHEN COUNTIF(street IS NOT NULL) > 0 THEN 'NEW_FORMAT_DETECTED'
    ELSE 'OLD_FORMAT'
  END AS status
FROM `project.source.customers`;

-- Step 3: Add data quality checks
CREATE TEMP TABLE validation_results AS
SELECT 
  'Missing addresses' AS check_name,
  COUNTIF(street IS NULL AND address IS NULL) AS failed_count
FROM `project.staging.customers_new`
UNION ALL
SELECT 
  'Invalid customer_type',
  COUNTIF(customer_type_mapped IS NULL)
FROM `project.staging.customers_new`;

-- Alert if validation fails
IF (SELECT MAX(failed_count) FROM validation_results) > 0 THEN
  RAISE USING MESSAGE = 'Validation failed - check validation_results table';
END IF;
```

**Q3:** How would you design the ETL to be resilient to schema changes?

**Expected Answer:**

```sql
-- Design Pattern: Schema evolution handling

-- 1. Use INFORMATION_SCHEMA to detect schema changes
CREATE OR REPLACE PROCEDURE detect_schema_changes()
BEGIN
  CREATE TEMP TABLE current_schema AS
  SELECT column_name, data_type, ordinal_position
  FROM `project.source.INFORMATION_SCHEMA.COLUMNS`
  WHERE table_name = 'customers';
  
  CREATE TEMP TABLE expected_schema AS
  SELECT column_name, data_type, ordinal_position
  FROM `project.metadata.table_schemas`
  WHERE table_name = 'customers'
    AND is_current = TRUE;
  
  -- Detect differences
  CREATE TEMP TABLE schema_diff AS
  SELECT 
    COALESCE(c.column_name, e.column_name) AS column_name,
    e.data_type AS expected_type,
    c.data_type AS current_type,
    CASE 
      WHEN c.column_name IS NULL THEN 'COLUMN_REMOVED'
      WHEN e.column_name IS NULL THEN 'COLUMN_ADDED'
      WHEN c.data_type != e.data_type THEN 'TYPE_CHANGED'
      ELSE 'NO_CHANGE'
    END AS change_type
  FROM current_schema c
  FULL OUTER JOIN expected_schema e 
    ON c.column_name = e.column_name
  WHERE c.column_name IS NULL 
     OR e.column_name IS NULL 
     OR c.data_type != e.data_type;
  
  -- Log and alert if changes detected
  IF (SELECT COUNT(*) FROM schema_diff) > 0 THEN
    INSERT INTO `project.metadata.schema_change_log`
    SELECT 
      'customers' AS table_name,
      column_name,
      change_type,
      expected_type,
      current_type,
      CURRENT_TIMESTAMP()
    FROM schema_diff;
    
    -- Send alert
    RAISE USING MESSAGE = 'Schema changes detected in customers table';
  END IF;
END;

-- 2. Implement Column Mapping layer
CREATE TABLE `project.metadata.column_mappings` (
  source_table STRING,
  source_column STRING,
  target_column STRING,
  transformation_logic STRING,
  is_active BOOL,
  version INT64
);

-- Example mappings
INSERT INTO `project.metadata.column_mappings` VALUES
('source.customers', 'address', 'street', 'SPLIT(address, ",")[SAFE_OFFSET(0)]', FALSE, 1),
('source.customers', 'street', 'street', 'street', TRUE, 2),
('source.customers', 'customer_type', 'customer_type_name', 
 'CASE customer_type WHEN 1 THEN "retail" WHEN 2 THEN "wholesale" WHEN 3 THEN "enterprise" END', 
 TRUE, 2);
```

**Key Concepts Tested:**
- ✅ Change management
- ✅ Schema evolution handling
- ✅ Defensive programming
- ✅ Data validation
- ✅ ETL design patterns
- ✅ Communication skills

---

## Scenario 4: Data Reconciliation Failure 📊

### Situation
```
Your daily reconciliation report shows:
- Source system: 50,000 orders, $2.5M total
- Data warehouse: 49,500 orders, $2.3M total
- 500 orders and $200K missing

Finance team needs explanation within 1 hour for board meeting.
```

### Interview Questions

**Q1:** Walk me through your investigation process.

**Expected Answer:**

```sql
-- Step 1: Identify missing orders
CREATE TEMP TABLE missing_orders AS
SELECT s.order_id
FROM `project.source.orders` s
LEFT JOIN `project.warehouse.orders` w 
  ON s.order_id = w.order_id
WHERE w.order_id IS NULL;

-- Step 2: Analyze missing orders characteristics
SELECT 
  DATE(order_date) AS order_date,
  order_status,
  payment_method,
  COUNT(*) AS missing_count,
  SUM(amount) AS missing_amount
FROM `project.source.orders`
WHERE order_id IN (SELECT order_id FROM missing_orders)
GROUP BY order_date, order_status, payment_method
ORDER BY missing_count DESC;

-- Step 3: Check ETL logs for errors
SELECT 
  batch_id,
  pipeline_name,
  start_time,
  end_time,
  status,
  records_processed,
  records_failed,
  error_message
FROM `project.metadata.etl_logs`
WHERE DATE(start_time) = CURRENT_DATE()
  AND pipeline_name = 'orders_load'
ORDER BY start_time DESC;

-- Step 4: Check for data quality issues in missing records
SELECT 
  'NULL customer_id' AS issue,
  COUNT(*) AS count
FROM `project.source.orders`
WHERE order_id IN (SELECT order_id FROM missing_orders)
  AND customer_id IS NULL
UNION ALL
SELECT 
  'Invalid amount',
  COUNT(*)
FROM `project.source.orders`
WHERE order_id IN (SELECT order_id FROM missing_orders)
  AND (amount IS NULL OR amount <= 0)
UNION ALL
SELECT 
  'Missing required fields',
  COUNT(*)
FROM `project.source.orders`
WHERE order_id IN (SELECT order_id FROM missing_orders)
  AND (product_id IS NULL OR order_date IS NULL);

-- Step 5: Check quarantine table
SELECT 
  record_id,
  validation_error,
  quarantine_timestamp,
  record_data
FROM `project.metadata.quarantine_records`
WHERE table_name = 'orders'
  AND DATE(quarantine_timestamp) = CURRENT_DATE()
ORDER BY quarantine_timestamp DESC
LIMIT 100;

-- Step 6: Quick summary report
SELECT 
  'Total Missing' AS category,
  COUNT(*) AS order_count,
  SUM(amount) AS total_amount
FROM `project.source.orders`
WHERE order_id IN (SELECT order_id FROM missing_orders)
UNION ALL
SELECT 
  'In Quarantine',
  COUNT(*),
  SUM(SAFE_CAST(JSON_EXTRACT_SCALAR(record_data, '$.amount') AS FLOAT64))
FROM `project.metadata.quarantine_records`
WHERE table_name = 'orders'
  AND DATE(quarantine_timestamp) = CURRENT_DATE()
UNION ALL
SELECT 
  'ETL Failed',
  records_failed,
  NULL
FROM `project.metadata.etl_logs`
WHERE DATE(start_time) = CURRENT_DATE()
  AND pipeline_name = 'orders_load';
```

**Q2:** How would you implement a robust reconciliation framework?

**Expected Answer:**

```sql
-- Comprehensive Reconciliation Framework
CREATE OR REPLACE PROCEDURE run_daily_reconciliation(
  recon_date DATE
)
BEGIN
  -- 1. Record-level reconciliation
  CREATE TEMP TABLE recon_details AS
  SELECT 
    COALESCE(s.order_id, w.order_id) AS order_id,
    CASE 
      WHEN s.order_id IS NULL THEN 'MISSING_FROM_SOURCE'
      WHEN w.order_id IS NULL THEN 'MISSING_FROM_WAREHOUSE'
      WHEN s.amount != w.amount THEN 'AMOUNT_MISMATCH'
      WHEN s.order_date != w.order_date THEN 'DATE_MISMATCH'
      ELSE 'MATCH'
    END AS recon_status,
    s.amount AS source_amount,
    w.amount AS warehouse_amount,
    ABS(COALESCE(s.amount, 0) - COALESCE(w.amount, 0)) AS difference
  FROM `project.source.orders` s
  FULL OUTER JOIN `project.warehouse.orders` w 
    ON s.order_id = w.order_id
  WHERE DATE(COALESCE(s.order_date, w.order_date)) = recon_date;
  
  -- 2. Summary-level reconciliation
  INSERT INTO `project.metadata.daily_reconciliation` (
    recon_date,
    check_type,
    source_value,
    warehouse_value,
    difference,
    difference_pct,
    status,
    recon_timestamp
  )
  SELECT 
    recon_date,
    'RECORD_COUNT' AS check_type,
    (SELECT COUNT(*) FROM `project.source.orders` 
     WHERE DATE(order_date) = recon_date),
    (SELECT COUNT(*) FROM `project.warehouse.orders` 
     WHERE DATE(order_date) = recon_date),
    (SELECT COUNT(*) FROM `project.source.orders` WHERE DATE(order_date) = recon_date) -
    (SELECT COUNT(*) FROM `project.warehouse.orders` WHERE DATE(order_date) = recon_date),
    NULL,
    CASE 
      WHEN (SELECT COUNT(*) FROM recon_details WHERE recon_status != 'MATCH') = 0 
      THEN 'PASS' 
      ELSE 'FAIL' 
    END,
    CURRENT_TIMESTAMP()
  
  UNION ALL
  
  SELECT 
    recon_date,
    'AMOUNT_TOTAL',
    (SELECT SUM(amount) FROM `project.source.orders` 
     WHERE DATE(order_date) = recon_date),
    (SELECT SUM(amount) FROM `project.warehouse.orders` 
     WHERE DATE(order_date) = recon_date),
    (SELECT SUM(amount) FROM `project.source.orders` WHERE DATE(order_date) = recon_date) -
    (SELECT SUM(amount) FROM `project.warehouse.orders` WHERE DATE(order_date) = recon_date),
    SAFE_DIVIDE(
      ABS((SELECT SUM(amount) FROM `project.source.orders` WHERE DATE(order_date) = recon_date) -
          (SELECT SUM(amount) FROM `project.warehouse.orders` WHERE DATE(order_date) = recon_date)),
      (SELECT SUM(amount) FROM `project.source.orders` WHERE DATE(order_date) = recon_date)
    ) * 100,
    CASE 
      WHEN ABS(
        (SELECT SUM(amount) FROM `project.source.orders` WHERE DATE(order_date) = recon_date) -
        (SELECT SUM(amount) FROM `project.warehouse.orders` WHERE DATE(order_date) = recon_date)
      ) < 100 THEN 'PASS'
      ELSE 'FAIL'
    END,
    CURRENT_TIMESTAMP();
  
  -- 3. Store detailed mismatches
  INSERT INTO `project.metadata.reconciliation_details`
  SELECT 
    recon_date,
    order_id,
    recon_status,
    source_amount,
    warehouse_amount,
    difference,
    CURRENT_TIMESTAMP()
  FROM recon_details
  WHERE recon_status != 'MATCH';
  
  -- 4. Send alerts if failed
  IF (SELECT COUNT(*) FROM recon_details WHERE recon_status != 'MATCH') > 100 THEN
    INSERT INTO `project.metadata.alerts` (
      alert_type,
      severity,
      message,
      details,
      created_at
    )
    VALUES (
      'RECONCILIATION_FAILED',
      'CRITICAL',
      FORMAT('Reconciliation failed for %t with %d mismatches', 
             recon_date, 
             (SELECT COUNT(*) FROM recon_details WHERE recon_status != 'MATCH')),
      TO_JSON_STRING((SELECT ARRAY_AGG(STRUCT(order_id, recon_status) LIMIT 100) 
                      FROM recon_details WHERE recon_status != 'MATCH')),
      CURRENT_TIMESTAMP()
    );
  END IF;
END;
```

**Key Concepts Tested:**
- ✅ Reconciliation methodology
- ✅ Root cause analysis
- ✅ Data quality investigation
- ✅ Production debugging
- ✅ Communication under pressure
- ✅ Framework design

---

## Scenario 5: Late Arriving Data ⏰

### Situation
```
Your dimension table (dim_customer) is loaded at 1 AM.
Your fact table (fact_orders) is loaded at 2 AM.

Today, 100 new customers placed orders, but their customer records arrived 2 hours late 
due to source system issues. 

Your fact load failed because customer_ids weren't found in the dimension table.
```

### Interview Questions

**Q1:** How would you handle this situation?

**Expected Answer:**

```sql
-- Solution 1: Inferred Dimension Members
CREATE OR REPLACE PROCEDURE load_fact_with_inferred_dimensions()
BEGIN
  -- Step 1: Identify missing customers
  CREATE TEMP TABLE missing_customers AS
  SELECT DISTINCT o.customer_id
  FROM `project.staging.orders` o
  LEFT JOIN `project.warehouse.dim_customer` c 
    ON o.customer_id = c.customer_id 
    AND c.is_current = TRUE
  WHERE c.customer_id IS NULL;
  
  -- Step 2: Create inferred dimension records
  INSERT INTO `project.warehouse.dim_customer` (
    customer_key,  -- Surrogate key
    customer_id,   -- Natural key
    customer_name,
    segment,
    region,
    is_inferred,
    effective_date,
    end_date,
    is_current
  )
  SELECT 
    GENERATE_UUID() AS customer_key,
    customer_id,
    CONCAT('Inferred-', customer_id) AS customer_name,
    'UNKNOWN' AS segment,
    'UNKNOWN' AS region,
    TRUE AS is_inferred,
    CURRENT_DATE() AS effective_date,
    DATE('9999-12-31') AS end_date,
    TRUE AS is_current
  FROM missing_customers;
  
  -- Step 3: Load fact table (now all dimension keys exist)
  INSERT INTO `project.warehouse.fact_orders` (
    order_key,
    order_id,
    customer_key,
    product_key,
    order_date,
    amount,
    is_inferred_customer
  )
  SELECT 
    GENERATE_UUID(),
    o.order_id,
    c.customer_key,
    p.product_key,
    o.order_date,
    o.amount,
    c.is_inferred  -- Flag for tracking
  FROM `project.staging.orders` o
  INNER JOIN `project.warehouse.dim_customer` c 
    ON o.customer_id = c.customer_id 
    AND c.is_current = TRUE
  INNER JOIN `project.warehouse.dim_product` p 
    ON o.product_id = p.product_id 
    AND p.is_current = TRUE;
  
  -- Step 4: Log inferred members
  INSERT INTO `project.metadata.inferred_members_log`
  SELECT 
    'dim_customer' AS dimension_table,
    customer_id AS member_id,
    CURRENT_TIMESTAMP() AS inferred_timestamp,
    'Fact load required customer not yet in dimension' AS reason
  FROM missing_customers;
END;

-- Solution 2: Update inferred members when actual data arrives
CREATE OR REPLACE PROCEDURE update_inferred_customers()
BEGIN
  -- Update inferred records with actual data
  UPDATE `project.warehouse.dim_customer` d
  SET 
    customer_name = s.customer_name,
    segment = s.segment,
    region = s.region,
    email = s.email,
    phone = s.phone,
    is_inferred = FALSE,
    updated_timestamp = CURRENT_TIMESTAMP()
  FROM `project.staging.customers` s
  WHERE d.customer_id = s.customer_id
    AND d.is_inferred = TRUE
    AND d.is_current = TRUE;
  
  -- Update fact table flag
  UPDATE `project.warehouse.fact_orders` f
  SET is_inferred_customer = FALSE
  WHERE is_inferred_customer = TRUE
    AND customer_key IN (
      SELECT customer_key 
      FROM `project.warehouse.dim_customer`
      WHERE is_inferred = FALSE
    );
  
  -- Log updates
  INSERT INTO `project.metadata.inferred_members_log`
  SELECT 
    'dim_customer',
    customer_id,
    CURRENT_TIMESTAMP(),
    'Inferred member updated with actual data'
  FROM `project.warehouse.dim_customer`
  WHERE is_inferred = FALSE
    AND customer_key IN (
      SELECT customer_key FROM @@row_mutations_updated
    );
END;
```

**Q2:** What design changes would prevent this issue?

**Expected Answer:**

```
1. Dependency Management:
   - Implement DAG (Directed Acyclic Graph) for job dependencies
   - Ensure dimension loads complete before fact loads
   - Add retry logic with backoff

2. Data Freshness Checks:
   - Verify dimension data freshness before fact load
   - Compare source vs dimension timestamps

3. Alerting:
   - Alert when dimension load is delayed
   - Track inferred member creation rate

4. Alternative Architectures:
   - Real-time streaming for dimension updates
   - Event-driven architecture
   - Separate pipelines for late-arriving data

5. Business Rules:
   - Define acceptable latency for dimension updates
   - Establish SLAs between teams
```

**Key Concepts Tested:**
- ✅ Late-arriving dimension handling
- ✅ Inferred members pattern
- ✅ Surrogate key management
- ✅ Data lineage tracking
- ✅ Pipeline dependency design
- ✅ Error recovery strategies

---

## Scenario 6: Real-Time Requirements 🚀

### Situation
```
Business wants to move from daily batch loads to near real-time data (< 5 minute latency).

Current: Nightly batch load at 2 AM
Requested: Continuous updates throughout the day

Impact: 10 downstream reports, 5 Tableau dashboards, 3 ML models
```

### Interview Questions

**Q1:** What's your approach to migrating from batch to streaming?

**Expected Answer:**

**Phase 1: Assessment**
- Analyze query patterns and SLAs
- Identify critical vs non-critical data flows
- Estimate cost implications (streaming vs batch)
- Review infrastructure capabilities

**Phase 2: Design**
```sql
-- Implement Lambda Architecture (batch + streaming layers)

-- Streaming Layer (Near real-time)
CREATE OR REPLACE TABLE `project.realtime.orders_stream` (
  order_id STRING,
  customer_id STRING,
  product_id STRING,
  amount NUMERIC,
  order_timestamp TIMESTAMP,
  stream_insert_timestamp TIMESTAMP
)
PARTITION BY DATE(order_timestamp)
CLUSTER BY customer_id;

-- Batch Layer (Historical, more complex transformations)
CREATE OR REPLACE TABLE `project.warehouse.orders_batch` (
  order_id STRING,
  customer_id STRING,
  product_id STRING,
  amount NUMERIC,
  order_timestamp TIMESTAMP,
  -- Enriched fields (expensive transformations)
  customer_lifetime_value NUMERIC,
  customer_segment STRING,
  product_category STRING,
  batch_load_timestamp TIMESTAMP
)
PARTITION BY DATE(order_timestamp)
CLUSTER BY customer_id, product_id;

-- Unified View (Combines both layers)
CREATE OR REPLACE VIEW `project.warehouse.orders_unified` AS
-- Get batch data for completed days
SELECT * FROM `project.warehouse.orders_batch`
WHERE DATE(order_timestamp) < CURRENT_DATE()
UNION ALL
-- Get streaming data for today
SELECT 
  s.order_id,
  s.customer_id,
  s.product_id,
  s.amount,
  s.order_timestamp,
  -- Lookup enrichments (may be slightly stale)
  c.lifetime_value AS customer_lifetime_value,
  c.segment AS customer_segment,
  p.category AS product_category,
  s.stream_insert_timestamp AS batch_load_timestamp
FROM `project.realtime.orders_stream` s
LEFT JOIN `project.warehouse.dim_customer` c 
  ON s.customer_id = c.customer_id 
  AND c.is_current = TRUE
LEFT JOIN `project.warehouse.dim_product` p 
  ON s.product_id = p.product_id 
  AND p.is_current = TRUE
WHERE DATE(s.order_timestamp) = CURRENT_DATE();
```

**Phase 3: Implementation**
```sql
-- Incremental materialization for streaming data
CREATE MATERIALIZED VIEW `project.realtime.orders_summary_15min`
PARTITION BY DATE(window_start)
CLUSTER BY region, product_category
AS
SELECT 
  TIMESTAMP_TRUNC(order_timestamp, HOUR) AS window_start,
  region,
  product_category,
  COUNT(*) AS order_count,
  SUM(amount) AS total_revenue,
  AVG(amount) AS avg_order_value,
  MAX(order_timestamp) AS last_order_time
FROM `project.warehouse.orders_unified`
WHERE order_timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 24 HOUR)
GROUP BY window_start, region, product_category;

-- Auto-refresh every 15 minutes
ALTER MATERIALIZED VIEW `project.realtime.orders_summary_15min`
SET OPTIONS (enable_refresh = true, refresh_interval_minutes = 15);
```

**Q2:** How would you test and validate the migration?

**Expected Answer:**

```sql
-- Validation Framework
CREATE OR REPLACE PROCEDURE validate_streaming_vs_batch(
  validation_date DATE
)
BEGIN
  -- 1. Record count comparison
  CREATE TEMP TABLE validation_results AS
  SELECT 
    'Record Count' AS metric,
    (SELECT COUNT(*) FROM `project.realtime.orders_stream` 
     WHERE DATE(order_timestamp) = validation_date) AS streaming_value,
    (SELECT COUNT(*) FROM `project.warehouse.orders_batch` 
     WHERE DATE(order_timestamp) = validation_date) AS batch_value;
  
  -- 2. Sum comparison
  INSERT INTO validation_results
  SELECT 
    'Total Revenue',
    (SELECT SUM(amount) FROM `project.realtime.orders_stream` 
     WHERE DATE(order_timestamp) = validation_date),
    (SELECT SUM(amount) FROM `project.warehouse.orders_batch` 
     WHERE DATE(order_timestamp) = validation_date);
  
  -- 3. Hash comparison (sample)
  INSERT INTO validation_results
  SELECT 
    'Hash Match Rate',
    NULL,
    (SELECT 
       SUM(CASE WHEN s.hash = b.hash THEN 1 ELSE 0 END) / COUNT(*) * 100
     FROM (
       SELECT order_id, TO_HEX(MD5(TO_JSON_STRING(STRUCT(*)))) AS hash
       FROM `project.realtime.orders_stream`
       WHERE DATE(order_timestamp) = validation_date
       LIMIT 1000
     ) s
     INNER JOIN (
       SELECT order_id, TO_HEX(MD5(TO_JSON_STRING(STRUCT(*)))) AS hash
       FROM `project.warehouse.orders_batch`
       WHERE DATE(order_timestamp) = validation_date
     ) b ON s.order_id = b.order_id
    );
  
  -- 4. Latency monitoring
  INSERT INTO validation_results
  SELECT 
    'Avg Latency (minutes)',
    (SELECT AVG(TIMESTAMP_DIFF(stream_insert_timestamp, order_timestamp, MINUTE))
     FROM `project.realtime.orders_stream`
     WHERE DATE(order_timestamp) = validation_date),
    NULL;
  
  -- Store results
  INSERT INTO `project.metadata.validation_log`
  SELECT 
    validation_date,
    metric,
    streaming_value,
    batch_value,
    ABS(streaming_value - batch_value) AS difference,
    CASE 
      WHEN ABS(streaming_value - batch_value) / NULLIF(batch_value, 0) < 0.01 
      THEN 'PASS' 
      ELSE 'FAIL' 
    END AS status,
    CURRENT_TIMESTAMP()
  FROM validation_results;
  
  -- Alert if validation fails
  IF EXISTS (SELECT 1 FROM validation_results 
             WHERE ABS(streaming_value - batch_value) / NULLIF(batch_value, 0) > 0.01) 
  THEN
    RAISE USING MESSAGE = 'Validation failed - check validation_log';
  END IF;
END;
```

**Migration Strategy:**
1. **Parallel Run** (2-4 weeks)
   - Run both streaming and batch
   - Compare results daily
   - Fix discrepancies

2. **Gradual Cutover** (1-2 weeks)
   - Migrate non-critical dashboards first
   - Monitor performance and accuracy
   - Keep batch as backup

3. **Full Migration** (1 week)
   - Switch all consumers to streaming
   - Deprecate batch pipelines
   - Keep batch for historical loads

4. **Monitoring** (Ongoing)
   - Latency tracking
   - Cost monitoring
   - Data quality checks

**Key Concepts Tested:**
- ✅ Streaming architecture design
- ✅ Lambda architecture pattern
- ✅ Materialized views
- ✅ Migration strategy
- ✅ Testing methodology
- ✅ Risk management
- ✅ Cost vs performance trade-offs

---

## 💡 Evaluation Tips for These Scenarios

### Excellent Candidate Will:
1. **Ask Clarifying Questions**
   - "What's the SLA for data freshness?"
   - "What's our budget for BigQuery costs?"
   - "Who are the stakeholders affected?"

2. **Structured Approach**
   - Assess → Design → Implement → Validate → Monitor
   - Considers both short-term and long-term solutions

3. **Production Mindset**
   - Error handling
   - Monitoring and alerting
   - Rollback plans
   - Documentation

4. **Business Awareness**
   - Understands impact on stakeholders
   - Balances technical perfection with pragmatic solutions
   - Considers costs

5. **Communication**
   - Explains technical concepts clearly
   - Provides status updates
   - Documents decisions

### Red Flags:
- Panic or blame others
- Jumps to solution without investigation
- No consideration for monitoring
- Cannot explain their reasoning
- Overly complex solutions
- Doesn't consider cost implications
- No testing strategy

---

## 📋 Quick Scenario Selection Guide

| Candidate Level | Recommended Scenarios |
|----------------|----------------------|
| **Mid-Level (3-5 years)** | Scenarios 1, 2, 4 |
| **Senior (5-7 years)** | Scenarios 2, 3, 4, 5 |
| **Lead/Principal (7+ years)** | Scenarios 3, 5, 6 |

**Time Allocation:**
- Scenario presentation: 2 minutes
- Candidate thinking: 3-5 minutes
- Discussion: 10-15 minutes per scenario

**Total Interview Time:** 45-60 minutes for 2-3 scenarios

---

Good luck with your interviews! 🎯
