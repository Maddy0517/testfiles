# ETL QA SQL Quick Reference Cheat Sheet

## 🚀 Quick Validation Queries

### 1. Record Count Check
```sql
SELECT COUNT(*) FROM source_table;
SELECT COUNT(*) FROM target_table;
```

### 2. Sum Validation
```sql
SELECT SUM(amount) FROM source_table;
SELECT SUM(amount) FROM target_table;
```

### 3. Find Missing Records (Source → Target)
```sql
SELECT * FROM source_table
WHERE id NOT IN (SELECT id FROM target_table);
```

### 4. Find Extra Records (Target)
```sql
SELECT * FROM target_table
WHERE id NOT IN (SELECT id FROM source_table);
```

### 5. Detect Duplicates
```sql
SELECT key_column, COUNT(*)
FROM table_name
GROUP BY key_column
HAVING COUNT(*) > 1;
```

### 6. NULL Value Count
```sql
SELECT COUNT(*) - COUNT(column_name) AS null_count
FROM table_name;
```

### 7. Orphan Records Check
```sql
SELECT child.*
FROM child_table child
LEFT JOIN parent_table parent ON child.fk = parent.pk
WHERE parent.pk IS NULL;
```

### 8. Data Mismatch Detection
```sql
SELECT s.id, s.value, t.value
FROM source_table s
JOIN target_table t ON s.id = t.id
WHERE s.value <> t.value;
```

### 9. Full Reconciliation
```sql
SELECT 
    COALESCE(s.id, t.id) as id,
    CASE 
        WHEN s.id IS NULL THEN 'Only in Target'
        WHEN t.id IS NULL THEN 'Only in Source'
        WHEN s.value <> t.value THEN 'Mismatch'
        ELSE 'Match'
    END as status
FROM source_table s
FULL OUTER JOIN target_table t ON s.id = t.id;
```

### 10. Aggregate by Group
```sql
SELECT category, COUNT(*), SUM(amount), AVG(amount)
FROM table_name
GROUP BY category;
```

---

## 📊 Common Validation Patterns

### Pattern 1: Three-Way Comparison
```sql
WITH source_data AS (SELECT 'Source' as src, COUNT(*) as cnt FROM source),
     staging_data AS (SELECT 'Staging' as src, COUNT(*) as cnt FROM staging),
     target_data AS (SELECT 'Target' as src, COUNT(*) as cnt FROM target)
SELECT * FROM source_data
UNION ALL SELECT * FROM staging_data
UNION ALL SELECT * FROM target_data;
```

### Pattern 2: Row-by-Row Comparison
```sql
SELECT * FROM source_table
EXCEPT
SELECT * FROM target_table;
```

### Pattern 3: Percentage Validation
```sql
SELECT 
    COUNT(*) as total,
    SUM(CASE WHEN column IS NULL THEN 1 ELSE 0 END) as null_count,
    ROUND(100.0 * SUM(CASE WHEN column IS NULL THEN 1 ELSE 0 END) / COUNT(*), 2) as null_pct
FROM table_name;
```

---

## 🔍 Data Quality Checks

### Check Leading/Trailing Spaces
```sql
SELECT * FROM table_name
WHERE column <> TRIM(column);
```

### Check Future Dates
```sql
SELECT * FROM table_name
WHERE date_column > CURRENT_DATE;
```

### Check Negative Values
```sql
SELECT * FROM table_name
WHERE amount < 0;
```

### Check Email Format
```sql
SELECT * FROM table_name
WHERE email NOT LIKE '%@%.%';
```

### Check for Special Characters
```sql
SELECT * FROM table_name
WHERE column REGEXP '[^a-zA-Z0-9 ]';
```

---

## 📈 Window Functions

### Row Number (Identify Duplicates)
```sql
SELECT *,
       ROW_NUMBER() OVER (PARTITION BY key ORDER BY date DESC) as rn
FROM table_name;
```

### Rank
```sql
SELECT *,
       RANK() OVER (ORDER BY amount DESC) as rank
FROM table_name;
```

### Running Total
```sql
SELECT *,
       SUM(amount) OVER (ORDER BY date) as running_total
FROM table_name;
```

### Lag/Lead (Compare with Previous/Next Row)
```sql
SELECT *,
       LAG(amount) OVER (ORDER BY date) as previous_amount,
       LEAD(amount) OVER (ORDER BY date) as next_amount
FROM table_name;
```

---

## 🔄 Incremental Load Patterns

### Watermark-Based
```sql
SELECT * FROM source_table
WHERE last_modified > (SELECT MAX(load_timestamp) FROM metadata);
```

### Flag-Based
```sql
SELECT * FROM source_table
WHERE is_processed = 0;
```

### CDC Pattern
```sql
SELECT * FROM source_table
WHERE operation IN ('INSERT', 'UPDATE')
  AND change_date > :last_load_date;
```

---

## 🎯 SCD Type 2 Queries

### Get Current Records
```sql
SELECT * FROM dim_table
WHERE is_current = TRUE;
```

### Get Historical Records
```sql
SELECT * FROM dim_table
WHERE customer_id = 123
ORDER BY effective_start_date;
```

### Find Invalid SCD
```sql
SELECT customer_id, COUNT(*)
FROM dim_table
WHERE is_current = TRUE
GROUP BY customer_id
HAVING COUNT(*) > 1;
```

---

## 💡 Performance Tips

### Use EXISTS instead of IN
```sql
-- Slow
SELECT * FROM table1 WHERE id IN (SELECT id FROM table2);

-- Fast
SELECT * FROM table1 t1
WHERE EXISTS (SELECT 1 FROM table2 t2 WHERE t2.id = t1.id);
```

### Avoid Functions on Indexed Columns
```sql
-- Slow (index not used)
WHERE YEAR(date_column) = 2024

-- Fast (index used)
WHERE date_column >= '2024-01-01' AND date_column < '2025-01-01'
```

### Use UNION ALL vs UNION
```sql
-- UNION ALL is faster (no duplicate removal)
SELECT * FROM table1
UNION ALL
SELECT * FROM table2;
```

---

## 📋 Essential SQL Joins

| Join Type | Description | Use Case |
|-----------|-------------|----------|
| INNER | Matching records only | Standard data integration |
| LEFT | All from left + matches | Find missing records |
| RIGHT | All from right + matches | Reverse missing check |
| FULL OUTER | All from both | Complete reconciliation |
| CROSS | Cartesian product | Test data generation |

---

## 🎨 Data Profiling Template

```sql
SELECT 
    COUNT(*) as total_records,
    COUNT(DISTINCT key_column) as unique_keys,
    MIN(date_column) as earliest_date,
    MAX(date_column) as latest_date,
    AVG(amount) as avg_amount,
    MIN(amount) as min_amount,
    MAX(amount) as max_amount,
    STDDEV(amount) as stddev_amount,
    COUNT(*) - COUNT(column1) as null_column1,
    COUNT(*) - COUNT(column2) as null_column2
FROM table_name;
```

---

## 🚨 Common ETL Issues & Checks

| Issue | Check Query |
|-------|-------------|
| Missing records | `LEFT JOIN ... WHERE right.id IS NULL` |
| Duplicates | `GROUP BY ... HAVING COUNT(*) > 1` |
| NULLs | `WHERE column IS NULL` |
| Orphans | `LEFT JOIN ... WHERE parent.id IS NULL` |
| Type mismatch | Query information_schema |
| Amount variance | `ABS(source - target) > threshold` |
| Date issues | `WHERE date > CURRENT_DATE OR date < '1900-01-01'` |

---

## 📊 Validation Report Template

```sql
WITH validation AS (
    SELECT 'Check 1: Count' as check,
           CASE WHEN src = tgt THEN 'PASS' ELSE 'FAIL' END as status,
           src as source_value,
           tgt as target_value
    FROM (SELECT COUNT(*) as src FROM source, 
                 COUNT(*) as tgt FROM target)
    
    UNION ALL
    
    SELECT 'Check 2: Sum' as check,
           CASE WHEN ABS(src - tgt) < 0.01 THEN 'PASS' ELSE 'FAIL' END,
           src, tgt
    FROM (SELECT SUM(amt) as src FROM source,
                 SUM(amt) as tgt FROM target)
)
SELECT 
    check,
    status,
    source_value,
    target_value,
    CURRENT_TIMESTAMP as validated_at
FROM validation;
```

---

## 🔤 Important SQL Keywords

### Filtering
- `WHERE` - Row-level filter
- `HAVING` - Group-level filter
- `DISTINCT` - Remove duplicates

### Aggregation
- `COUNT()` - Count rows
- `SUM()` - Total
- `AVG()` - Average
- `MIN()` / `MAX()` - Extremes
- `STDDEV()` - Standard deviation

### Grouping
- `GROUP BY` - Group rows
- `PARTITION BY` - Window partition

### Ordering
- `ORDER BY` - Sort results
- `ASC` / `DESC` - Sort direction

### Limiting
- `LIMIT` - Restrict rows
- `OFFSET` - Skip rows
- `TOP` - SQL Server limit

---

## 💬 Interview Question Categories

### Category 1: SQL Basics (30%)
- Joins, WHERE vs HAVING
- Aggregations, GROUP BY
- Subqueries, CTEs

### Category 2: Data Validation (40%)
- Count reconciliation
- Sum validation
- Duplicate detection
- NULL handling
- Orphan records

### Category 3: ETL Concepts (20%)
- Incremental loads
- SCD types
- Data quality
- Performance

### Category 4: Scenario-Based (10%)
- Real-world problems
- Troubleshooting
- Design questions

---

## 🎯 Last-Minute Review

### Before Interview - Know These Cold:
1. ✅ How to count records
2. ✅ How to find missing records (LEFT JOIN)
3. ✅ How to detect duplicates (GROUP BY + HAVING)
4. ✅ How to check for NULLs
5. ✅ Difference between WHERE and HAVING
6. ✅ Different JOIN types
7. ✅ Window functions (ROW_NUMBER)
8. ✅ SCD Type 2 concept
9. ✅ Incremental load strategies
10. ✅ UNION vs UNION ALL

### Common Mistakes to Avoid:
- ❌ Not handling NULLs properly
- ❌ Forgetting about duplicates
- ❌ Not considering performance
- ❌ Using SELECT * in production
- ❌ Not testing edge cases

### Always Ask About:
- 📝 Data volume
- 📝 Load frequency
- 📝 Acceptable tolerance
- 📝 Database platform
- 📝 Time constraints

---

## 🔧 Database-Specific Syntax

### PostgreSQL
```sql
-- Limit
SELECT * FROM table LIMIT 10;

-- String concat
SELECT first_name || ' ' || last_name FROM customers;

-- Date functions
SELECT CURRENT_DATE, NOW();
```

### SQL Server
```sql
-- Limit
SELECT TOP 10 * FROM table;

-- String concat
SELECT first_name + ' ' + last_name FROM customers;

-- Date functions
SELECT GETDATE(), CONVERT(date, GETDATE());
```

### MySQL
```sql
-- Limit
SELECT * FROM table LIMIT 10;

-- String concat
SELECT CONCAT(first_name, ' ', last_name) FROM customers;

-- Date functions
SELECT CURDATE(), NOW();
```

---

## 📞 Interview Day Checklist

### 30 Minutes Before:
- [ ] Review top 10 queries
- [ ] Practice explaining approach verbally
- [ ] Recall 2-3 real examples from experience

### During Interview:
- [ ] Listen carefully to requirements
- [ ] Clarify before coding
- [ ] Think aloud
- [ ] Consider edge cases
- [ ] Test mentally

### Common Starter Questions:
1. "Tell me about your ETL testing experience"
2. "How do you validate an ETL load?"
3. "What's your approach to data quality?"
4. "Explain a challenging data issue you resolved"

---

## 🎓 Key Formulas to Remember

### NULL Percentage
```sql
100.0 * (COUNT(*) - COUNT(column)) / COUNT(*)
```

### Difference Calculation
```sql
ABS(source_value - target_value) < tolerance
```

### Success Rate
```sql
100.0 * successful_count / total_count
```

### Growth Rate
```sql
((current_value - previous_value) / previous_value) * 100
```

---

## 🚀 Confidence Boosters

### You know SQL well if you can:
1. ✅ Write a LEFT JOIN from memory
2. ✅ Explain the difference between WHERE and HAVING
3. ✅ Use window functions
4. ✅ Create a CTE
5. ✅ Validate data completeness

### Remember:
- 🎯 Interviewers want to see your thought process
- 🎯 It's okay to ask clarifying questions
- 🎯 Edge cases show attention to detail
- 🎯 Performance considerations show experience
- 🎯 Real examples demonstrate competence

---

## 📱 Mobile-Friendly Quick Commands

```sql
-- The Big 5
SELECT COUNT(*) FROM table;
SELECT SUM(amount) FROM table;
SELECT * FROM t1 LEFT JOIN t2 ON t1.id = t2.id WHERE t2.id IS NULL;
SELECT col, COUNT(*) FROM table GROUP BY col HAVING COUNT(*) > 1;
SELECT * FROM source EXCEPT SELECT * FROM target;
```

---

**Good Luck! You've got this! 🎉**

*Remember: Confidence comes from preparation. You've prepared well.*

---

*Pro Tip: Print this page and review it 10 minutes before your interview!*
