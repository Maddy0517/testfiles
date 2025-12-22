# ETL QA SQL Interview Questions & Answers Guide

## Table of Contents
1. [Data Quality & Validation](#data-quality--validation)
2. [Data Reconciliation](#data-reconciliation)
3. [Data Transformation Validation](#data-transformation-validation)
4. [Incremental Load Validation](#incremental-load-validation)
5. [Slowly Changing Dimensions](#slowly-changing-dimensions)
6. [Performance & Optimization](#performance--optimization)
7. [Data Profiling](#data-profiling)
8. [ETL Failure & Error Handling](#etl-failure--error-handling)
9. [Complex Data Validation](#complex-data-validation)
10. [Advanced SQL for ETL QA](#advanced-sql-for-etl-qa)

---

## Data Quality & Validation

### Q1: How do you check for duplicate records in a table?

**Answer:** There are multiple approaches depending on requirements:

**Method 1: Find duplicates based on specific columns**
```sql
SELECT customer_id, email, COUNT(*) as duplicate_count
FROM customers
GROUP BY customer_id, email
HAVING COUNT(*) > 1;
```

**Method 2: Using window functions**
```sql
SELECT *,
       ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY created_date DESC) as row_num
FROM customers
WHERE row_num > 1;
```

**Use Cases:**
- Pre-ETL data quality checks
- Post-load validation
- Identifying data entry errors

---

### Q2: How do you validate NULL values in critical columns?

**Answer:** Check for NULLs and calculate NULL percentage

```sql
SELECT 
    COUNT(*) as total_records,
    COUNT(customer_id) as non_null_customer_id,
    COUNT(*) - COUNT(customer_id) as null_customer_id,
    ROUND(100.0 * (COUNT(*) - COUNT(customer_id)) / COUNT(*), 2) as null_percentage
FROM customers;
```

**Multiple columns check:**
```sql
SELECT 
    SUM(CASE WHEN customer_id IS NULL THEN 1 ELSE 0 END) as null_customer_id,
    SUM(CASE WHEN email IS NULL THEN 1 ELSE 0 END) as null_email,
    SUM(CASE WHEN phone IS NULL THEN 1 ELSE 0 END) as null_phone
FROM customers;
```

**Best Practices:**
- Define acceptable NULL thresholds
- Document which columns allow NULLs
- Implement validation rules in ETL

---

### Q3: How do you check data completeness between source and target?

**Answer:** Record count validation with detailed comparison

```sql
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
```

---

### Q4: How do you identify orphaned records?

**Answer:** Find records without valid foreign key references

```sql
-- Find orders without valid customers
SELECT o.*
FROM orders o
LEFT JOIN customers c ON o.customer_id = c.customer_id
WHERE c.customer_id IS NULL;
```

**Impact:** Orphaned records indicate:
- Referential integrity issues
- Missing parent records
- ETL job failures
- Data synchronization problems

---

## Data Reconciliation

### Q5: How do you reconcile data between source and target after ETL?

**Answer:** Compare records to find mismatches

**Full reconciliation report:**
```sql
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
```

---

### Q6: How do you validate aggregate reconciliation?

**Answer:** Compare aggregated values

```sql
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
```

**Key Metrics to Validate:**
- Sum/Total amounts
- Record counts
- Average values
- Min/Max values
- Distinct counts

---

## Data Transformation Validation

### Q7: How do you validate date transformations?

**Answer:** Check date format conversions and calculations

```sql
-- Validate date range
SELECT 
    MIN(order_date) as earliest_date,
    MAX(order_date) as latest_date,
    COUNT(CASE WHEN order_date > CURRENT_DATE THEN 1 END) as future_dates,
    COUNT(CASE WHEN order_date < '2000-01-01' THEN 1 END) as old_dates
FROM orders;
```

**Common Date Issues:**
- Timezone conversions
- Format changes (MM/DD/YYYY vs DD/MM/YYYY)
- NULL dates replaced with default values
- Future dates in historical data

---

### Q8: How do you validate string transformations?

**Answer:** Compare transformed values

```sql
-- Check for leading/trailing spaces
SELECT 
    id,
    name,
    LENGTH(name) as length_with_spaces,
    LENGTH(TRIM(name)) as length_trimmed
FROM customers
WHERE LENGTH(name) <> LENGTH(TRIM(name));
```

**Common String Transformations:**
- TRIM (remove spaces)
- UPPER/LOWER case conversion
- String concatenation
- Substring extraction
- Pattern matching/replacement

---

## Incremental Load Validation

### Q9: How do you identify new, updated, and unchanged records?

**Answer:** Use timestamps and change detection

```sql
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
```

---

### Q10: How do you validate incremental loads using watermark?

**Answer:** Check records based on last load timestamp

```sql
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
```

**Incremental Load Strategies:**
- Timestamp-based (most common)
- Flag-based (is_processed flag)
- CDC (Change Data Capture)
- Log-based replication

---

## Slowly Changing Dimensions

### Q11: How do you implement and validate SCD Type 2?

**Answer:** Track historical changes with effective dates

```sql
-- Find current records
SELECT *
FROM dim_customer
WHERE is_current = TRUE;

-- Find historical changes for a customer
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

-- Validate SCD Type 2 integrity (each customer should have exactly 1 current record)
SELECT 
    customer_id,
    COUNT(*) as version_count,
    SUM(CASE WHEN is_current THEN 1 ELSE 0 END) as current_count
FROM dim_customer
GROUP BY customer_id
HAVING SUM(CASE WHEN is_current THEN 1 ELSE 0 END) <> 1;
```

**SCD Types:**
- **Type 0:** No changes allowed
- **Type 1:** Overwrite (no history)
- **Type 2:** Add new row with version (full history)
- **Type 3:** Add new column (limited history)
- **Type 4:** Separate history table
- **Type 6:** Hybrid (1+2+3)

---

## Performance & Optimization

### Q12: How do you identify slow queries?

**Answer:** Use database-specific tools and explain plans

**Query Optimization Checklist:**
1. Add appropriate indexes
2. Avoid SELECT *
3. Use WHERE instead of HAVING when possible
4. Avoid functions in WHERE clause on indexed columns
5. Use EXISTS instead of IN for large datasets
6. Partition large tables
7. Update statistics regularly
8. Analyze execution plans

---

### Q13: How do you identify missing indexes?

**Answer:** Analyze query performance and table scans

```sql
-- Find tables without indexes
SELECT 
    t.table_schema,
    t.table_name,
    t.table_rows
FROM information_schema.tables t
LEFT JOIN information_schema.statistics s 
    ON t.table_schema = s.table_schema 
    AND t.table_name = s.table_name
WHERE t.table_schema NOT IN ('mysql', 'information_schema')
    AND s.index_name IS NULL
    AND t.table_rows > 1000;
```

---

## Data Profiling

### Q14: How do you perform data profiling?

**Answer:** Analyze data distribution, patterns, and quality

```sql
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
```

**Data Profiling Metrics:**
- Record counts
- Null counts/percentages
- Distinct values
- Min/Max values
- Mean/Median
- Standard deviation
- Data distributions
- Pattern analysis

---

### Q15: How do you identify outliers?

**Answer:** Use statistical methods

**IQR Method:**
```sql
WITH stats AS (
    SELECT 
        PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY amount) as Q1,
        PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY amount) as Q3
    FROM transactions
),
outlier_bounds AS (
    SELECT 
        Q1 - 1.5 * (Q3 - Q1) as lower_bound,
        Q3 + 1.5 * (Q3 - Q1) as upper_bound
    FROM stats
)
SELECT t.*
FROM transactions t, outlier_bounds o
WHERE t.amount < o.lower_bound OR t.amount > o.upper_bound;
```

---

## ETL Failure & Error Handling

### Q16: How do you track ETL job execution history?

**Answer:** Maintain ETL audit/metadata tables

```sql
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
```

**ETL Monitoring Best Practices:**
- Log start/end times
- Track records processed/failed
- Capture error messages
- Monitor data volumes
- Alert on failures
- Track SLA compliance

---

## Complex Data Validation

### Q17: How do you validate business rules?

**Answer:** Write complex validation queries

```sql
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
```

**Common Business Rules:**
- Totals must match details
- Foreign key integrity
- Date ranges validation
- Status transitions
- Balance equations

---

### Q18: How do you validate data consistency across tables?

**Answer:** Cross-reference validation

```sql
-- Find customers with orders but no payment
SELECT DISTINCT o.customer_id, c.customer_name
FROM orders o
JOIN customers c ON o.customer_id = c.customer_id
LEFT JOIN payments p ON o.order_id = p.order_id
WHERE p.payment_id IS NULL;
```

---

## Advanced SQL for ETL QA

### Q19: How do you compare schemas?

**Answer:** Query information_schema

```sql
SELECT 
    COALESCE(s.column_name, t.column_name) as column_name,
    s.data_type as source_data_type,
    t.data_type as target_data_type,
    CASE 
        WHEN s.column_name IS NULL THEN 'Only in Target'
        WHEN t.column_name IS NULL THEN 'Only in Source'
        WHEN s.data_type <> t.data_type THEN 'Data Type Mismatch'
        ELSE 'Match'
    END as comparison_status
FROM information_schema.columns s
FULL OUTER JOIN information_schema.columns t 
    ON s.column_name = t.column_name
WHERE s.table_name = 'source_table'
    AND t.table_name = 'target_table';
```

---

### Q20: How do you create comprehensive validation reports?

**Answer:** Combine multiple validation checks

```sql
WITH validation_checks AS (
    SELECT 'Record Count' as check_name,
           CASE WHEN source_cnt = target_cnt THEN 'PASS' ELSE 'FAIL' END as status
    UNION ALL
    SELECT 'Sum Validation' as check_name,
           CASE WHEN ABS(source_sum - target_sum) < 0.01 THEN 'PASS' ELSE 'FAIL' END
    -- ... more checks
)
SELECT 
    check_name,
    status,
    CURRENT_TIMESTAMP as validation_timestamp
FROM validation_checks;
```

---

## Conceptual Questions & Answers

### Q21: What is the difference between TRUNCATE and DELETE?

**Answer:**
| Feature | DELETE | TRUNCATE |
|---------|--------|----------|
| Type | DML | DDL |
| WHERE clause | Yes | No |
| Speed | Slower | Faster |
| Logging | Fully logged | Minimally logged |
| Rollback | Can rollback | Cannot rollback |
| Triggers | Fires triggers | Doesn't fire triggers |
| Identity reset | No | Yes |

---

### Q22: What are the different types of SQL joins?

**Answer:**
- **INNER JOIN**: Returns only matching records from both tables
- **LEFT JOIN**: All records from left + matching from right (orphan detection)
- **RIGHT JOIN**: All records from right + matching from left
- **FULL OUTER JOIN**: All records from both tables (reconciliation)
- **CROSS JOIN**: Cartesian product (test data generation)

---

### Q23: What is the difference between WHERE and HAVING?

**Answer:**
| WHERE | HAVING |
|-------|--------|
| Filters rows before grouping | Filters groups after aggregation |
| Cannot use aggregate functions | Can use aggregate functions |
| Applied to individual rows | Applied to grouped results |
| Executed before GROUP BY | Executed after GROUP BY |

**Example:**
```sql
-- WHERE: Filter before grouping
SELECT department, AVG(salary)
FROM employees
WHERE salary > 50000  -- Applied to each row
GROUP BY department;

-- HAVING: Filter after grouping
SELECT department, AVG(salary)
FROM employees
GROUP BY department
HAVING AVG(salary) > 75000;  -- Applied to groups
```

---

### Q24: Explain SQL query execution order

**Answer:**
1. **FROM** (including JOINs)
2. **WHERE**
3. **GROUP BY**
4. **HAVING**
5. **SELECT**
6. **DISTINCT**
7. **ORDER BY**
8. **LIMIT/OFFSET**

**Why it matters:** Understanding execution order helps write efficient queries and debug issues.

---

### Q25: How do you handle data quality issues in ETL?

**Answer:**
1. **Prevention:**
   - Data validation at source
   - Clear data quality rules
   - Schema validation

2. **Detection:**
   - Data profiling
   - Automated quality checks
   - Threshold monitoring

3. **Handling:**
   - Reject and log bad records
   - Error tables for investigation
   - Automated alerts

4. **Resolution:**
   - Root cause analysis
   - Source system fixes
   - Transformation adjustments

---

### Q26: What is data lineage and why is it important?

**Answer:**
Data lineage tracks the flow of data from source to target through all transformations.

**Importance:**
- **Debugging**: Trace issues back to source
- **Impact Analysis**: Understand downstream effects of changes
- **Compliance**: Meet regulatory requirements (GDPR, SOX)
- **Documentation**: Clear understanding of data flow
- **Root Cause Analysis**: Quick issue resolution

**Components:**
- Source systems
- Transformation rules
- Target systems
- Data mappings
- Business rules

---

### Q27: What are window functions?

**Answer:**
Window functions perform calculations across rows related to the current row without collapsing rows.

**Common Window Functions:**
- **ROW_NUMBER()**: Unique sequential number
- **RANK()**: Ranking with gaps
- **DENSE_RANK()**: Ranking without gaps
- **LEAD()**: Access following row
- **LAG()**: Access preceding row
- **SUM() OVER()**: Running total
- **AVG() OVER()**: Moving average

**Example:**
```sql
SELECT 
    employee_id,
    salary,
    ROW_NUMBER() OVER (ORDER BY salary DESC) as row_num,
    RANK() OVER (ORDER BY salary DESC) as rank,
    AVG(salary) OVER (PARTITION BY department) as dept_avg
FROM employees;
```

---

### Q28: How do you optimize slow-running queries?

**Answer:**
1. **Indexing:**
   - Create indexes on WHERE, JOIN, ORDER BY columns
   - Avoid over-indexing (maintenance overhead)

2. **Query Structure:**
   - Avoid SELECT *
   - Use specific columns needed
   - Filter early with WHERE

3. **Joins:**
   - Use appropriate join types
   - Join on indexed columns
   - Consider join order

4. **Aggregations:**
   - Use WHERE instead of HAVING when possible
   - Pre-aggregate in subqueries

5. **Functions:**
   - Avoid functions on indexed columns in WHERE
   - Example: `WHERE YEAR(date) = 2024` → `WHERE date >= '2024-01-01' AND date < '2025-01-01'`

6. **Analysis:**
   - Use EXPLAIN PLAN
   - Check execution statistics
   - Monitor query performance

---

### Q29: What is the difference between UNION and UNION ALL?

**Answer:**
| UNION | UNION ALL |
|-------|-----------|
| Removes duplicates | Keeps all rows |
| Slower (requires sorting) | Faster |
| Distinct results | All results |
| Higher resource usage | Lower resource usage |

**When to use:**
- **UNION**: When duplicates must be removed
- **UNION ALL**: When duplicates don't matter or don't exist (preferred for performance)

---

### Q30: How do you handle timezone conversions?

**Answer:**
**Best Practices:**
1. Store all timestamps in UTC
2. Convert to local timezone only for display
3. Document timezone assumptions
4. Handle DST transitions carefully

**SQL Examples:**
```sql
-- Convert to specific timezone
SELECT 
    timestamp_utc,
    timestamp_utc AT TIME ZONE 'America/New_York' as ny_time,
    timestamp_utc AT TIME ZONE 'Asia/Tokyo' as tokyo_time
FROM events;

-- MySQL
SELECT 
    CONVERT_TZ(timestamp_utc, '+00:00', '-05:00') as est_time
FROM events;
```

---

## ETL QA Testing Strategies

### 1. Pre-ETL Validation
- Source data profiling
- Schema validation
- Data quality assessment
- Volume estimation

### 2. During ETL Monitoring
- Job execution tracking
- Error logging
- Performance monitoring
- Resource utilization

### 3. Post-ETL Validation
- Record count reconciliation
- Data accuracy validation
- Business rule verification
- Performance metrics
- Data quality reports

### 4. Regression Testing
- Compare with previous loads
- Verify no data loss
- Check transformation consistency
- Validate historical data

---

## Common ETL Tools

### Data Integration Tools
- **Informatica PowerCenter**: Enterprise ETL
- **Talend**: Open source ETL
- **Microsoft SSIS**: SQL Server integration
- **Apache NiFi**: Data flow automation
- **Apache Airflow**: Workflow orchestration
- **AWS Glue**: Cloud-based ETL
- **Azure Data Factory**: Azure ETL service

### Data Quality Tools
- **Informatica Data Quality**
- **Talend Data Quality**
- **Great Expectations** (Python)
- **Deequ** (Scala/Spark)

---

## Interview Preparation Tips

### Technical Preparation
1. Practice writing SQL queries without IDE
2. Understand database differences (Oracle, SQL Server, PostgreSQL, MySQL)
3. Know data warehousing concepts (Star schema, Snowflake schema)
4. Study ETL best practices
5. Learn about data quality frameworks

### Behavioral Preparation
1. Prepare examples of ETL projects you've worked on
2. Discuss challenges faced and how you resolved them
3. Explain your approach to troubleshooting
4. Describe your testing methodology
5. Talk about automation and process improvements

### During Interview
1. Clarify requirements before writing queries
2. Think aloud - explain your approach
3. Consider edge cases
4. Discuss performance implications
5. Ask questions about data volume, frequency, and constraints

---

## Quick Reference Checklist

### Data Validation Checklist
- [ ] Record count match
- [ ] Sum/aggregate reconciliation
- [ ] Null value validation
- [ ] Duplicate check
- [ ] Orphaned records check
- [ ] Data type validation
- [ ] Date range validation
- [ ] Referential integrity
- [ ] Business rules validation
- [ ] Schema comparison

### ETL Testing Checklist
- [ ] Source to staging validation
- [ ] Staging to target validation
- [ ] Transformation logic verification
- [ ] Data quality rules
- [ ] Error handling verification
- [ ] Performance benchmarks
- [ ] Audit trail verification
- [ ] Incremental load validation
- [ ] Historical data accuracy
- [ ] End-to-end data flow test

---

## Additional Resources

### Books
- "The Data Warehouse Toolkit" by Ralph Kimball
- "SQL Performance Explained" by Markus Winand
- "Data Quality: The Field Guide" by Thomas Redman

### Online Resources
- SQL Tutorial: w3schools.com/sql
- Database specific documentation
- ETL testing best practices blogs
- Data quality frameworks documentation

---

## Practice Problems

### Problem 1: Data Migration Validation
You're migrating customer data from a legacy system to a new CRM. Write queries to:
1. Compare record counts
2. Validate email addresses are correctly migrated
3. Check for any missing customers
4. Verify the most recent order date for each customer

### Problem 2: Incremental Load Testing
Design a validation strategy for daily incremental loads that:
1. Identifies new records
2. Tracks updated records
3. Detects deletions
4. Validates aggregate metrics

### Problem 3: Data Quality Dashboard
Create SQL queries for a data quality dashboard showing:
1. Null percentages by column
2. Duplicate record counts
3. Referential integrity violations
4. Data freshness (latest record timestamp)
5. Daily load statistics

---

## Conclusion

ETL QA is critical for ensuring data accuracy, completeness, and reliability. Mastering SQL for ETL testing requires:
- Strong SQL fundamentals
- Understanding of ETL processes
- Data quality principles
- Problem-solving skills
- Attention to detail

Practice regularly, understand business context, and always validate your validations!

Good luck with your interview! 🎯
