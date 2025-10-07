# dbt + BigQuery Complete Guide

## Table of Contents
1. [Introduction to dbt](#introduction-to-dbt)
2. [Setting Up dbt with BigQuery](#setting-up-dbt-with-bigquery)
3. [Project Structure](#project-structure)
4. [Configuration](#configuration)
5. [Models](#models)
6. [Tests](#tests)
7. [Macros](#macros)
8. [Seeds](#seeds)
9. [Snapshots](#snapshots)
10. [BigQuery-Specific Features](#bigquery-specific-features)
11. [Best Practices](#best-practices)
12. [Common Commands](#common-commands)
13. [Troubleshooting](#troubleshooting)

## Introduction to dbt

**dbt (data build tool)** is a command-line tool that enables data analysts and engineers to transform data in their warehouse more effectively. It allows you to:

- Transform raw data into analytics-ready datasets
- Write modular, reusable SQL code
- Test your data transformations
- Document your data models
- Version control your analytics code
- Collaborate with your team

### Key Concepts

- **Models**: SQL files that define transformations
- **Tests**: Assertions about your data quality
- **Macros**: Reusable SQL snippets (like functions)
- **Seeds**: CSV files loaded into your warehouse
- **Snapshots**: Capture point-in-time data for slowly changing dimensions

## Setting Up dbt with BigQuery

### Prerequisites

1. **Google Cloud Platform Account** with BigQuery enabled
2. **Python 3.7+** installed
3. **Service Account** with BigQuery permissions

### Installation

```bash
# Install dbt with BigQuery adapter
pip install dbt-bigquery

# Verify installation
dbt --version
```

### BigQuery Authentication

#### Option 1: Service Account (Recommended for Production)

1. Create a service account in GCP Console
2. Grant these IAM roles:
   - BigQuery Data Editor
   - BigQuery Job User
   - BigQuery User
3. Download the JSON key file
4. Update `profiles.yml` with the key file path

#### Option 2: OAuth (Good for Development)

```bash
# Authenticate using gcloud
gcloud auth application-default login
```

### Project Initialization

```bash
# Initialize new dbt project
dbt init my_project

# Navigate to project directory
cd my_project

# Test connection
dbt debug
```

## Project Structure

```
analytics_project/
├── dbt_project.yml          # Project configuration
├── profiles.yml             # Connection profiles
├── models/                  # SQL model files
│   ├── staging/            # Raw data cleaning
│   ├── intermediate/       # Business logic
│   └── marts/              # Final analytics tables
├── macros/                 # Reusable SQL functions
├── tests/                  # Custom data tests
├── seeds/                  # CSV reference data
├── snapshots/              # SCD Type 2 tables
├── analyses/               # Analytical queries
└── target/                 # Compiled SQL (auto-generated)
```

## Configuration

### dbt_project.yml

Key configurations for BigQuery:

```yaml
name: 'analytics_project'
version: '1.0.0'
config-version: 2

profile: 'analytics_project'

models:
  analytics_project:
    staging:
      +materialized: view
      +schema: staging
    marts:
      +materialized: table
      +schema: marts

vars:
  timezone: 'America/New_York'
  
# BigQuery-specific optimizations
seeds:
  analytics_project:
    +quote_columns: false
```

### profiles.yml

Connection configuration:

```yaml
analytics_project:
  target: dev
  outputs:
    dev:
      type: bigquery
      method: service-account
      project: your-gcp-project-id
      dataset: dbt_dev
      threads: 4
      timeout_seconds: 300
      location: US
      keyfile: /path/to/service-account-key.json
```

## Models

### Model Types

1. **Staging Models**: Clean and standardize raw data
2. **Intermediate Models**: Business logic and calculations
3. **Mart Models**: Final analytics-ready tables

### Example Staging Model

```sql
-- models/staging/stg_orders.sql
{{ config(materialized='view') }}

SELECT
    order_id,
    customer_id,
    CAST(order_date AS DATE) as order_date,
    CAST(order_amount AS NUMERIC) as order_amount,
    order_status,
    CURRENT_TIMESTAMP() as loaded_at
FROM {{ source('raw_data', 'orders') }}
WHERE order_date >= '2020-01-01'
```

### Example Intermediate Model

```sql
-- models/intermediate/int_customer_orders.sql
{{ config(materialized='view') }}

SELECT
    customer_id,
    COUNT(*) as total_orders,
    SUM(order_amount) as total_spent,
    AVG(order_amount) as avg_order_value,
    MIN(order_date) as first_order_date,
    MAX(order_date) as last_order_date
FROM {{ ref('stg_orders') }}
WHERE order_status = 'completed'
GROUP BY customer_id
```

### Example Mart Model

```sql
-- models/marts/dim_customers.sql
{{ config(
    materialized='table',
    partition_by={
        "field": "first_order_date",
        "data_type": "date"
    },
    cluster_by=["customer_segment"]
) }}

WITH customer_metrics AS (
    SELECT * FROM {{ ref('int_customer_orders') }}
),

customer_segments AS (
    SELECT
        *,
        CASE
            WHEN total_spent >= 1000 THEN 'High Value'
            WHEN total_spent >= 500 THEN 'Medium Value'
            ELSE 'Low Value'
        END as customer_segment
    FROM customer_metrics
)

SELECT * FROM customer_segments
```

## Tests

### Built-in Tests

```yaml
# models/schema.yml
version: 2

models:
  - name: dim_customers
    description: "Customer dimension table"
    columns:
      - name: customer_id
        description: "Unique customer identifier"
        tests:
          - unique
          - not_null
      - name: total_spent
        description: "Total amount spent by customer"
        tests:
          - not_null
          - dbt_utils.accepted_range:
              min_value: 0
```

### Custom Tests

```sql
-- tests/assert_positive_order_amounts.sql
SELECT *
FROM {{ ref('stg_orders') }}
WHERE order_amount <= 0
```

## Macros

### Example Macro

```sql
-- macros/generate_schema_name.sql
{% macro generate_schema_name(custom_schema_name, node) -%}
    {%- set default_schema = target.schema -%}
    {%- if custom_schema_name is none -%}
        {{ default_schema }}
    {%- else -%}
        {{ default_schema }}_{{ custom_schema_name | trim }}
    {%- endif -%}
{%- endmacro %}
```

### Utility Macro

```sql
-- macros/cents_to_dollars.sql
{% macro cents_to_dollars(column_name, precision=2) %}
    ROUND({{ column_name }} / 100.0, {{ precision }})
{% endmacro %}
```

Usage in model:
```sql
SELECT
    order_id,
    {{ cents_to_dollars('order_amount_cents') }} as order_amount_dollars
FROM {{ source('raw_data', 'orders') }}
```

## Seeds

### Example Seed File

```csv
-- seeds/product_categories.csv
product_id,category_name,category_type
1,Electronics,Physical
2,Software,Digital
3,Books,Physical
```

### Seed Configuration

```yaml
# dbt_project.yml
seeds:
  analytics_project:
    product_categories:
      +column_types:
        product_id: INT64
        category_name: STRING
        category_type: STRING
```

## Snapshots

### Example Snapshot

```sql
-- snapshots/customers_snapshot.sql
{% snapshot customers_snapshot %}
    {{
        config(
          target_schema='snapshots',
          unique_key='customer_id',
          strategy='timestamp',
          updated_at='updated_at',
        )
    }}
    
    SELECT * FROM {{ source('raw_data', 'customers') }}
    
{% endsnapshot %}
```

## BigQuery-Specific Features

### Partitioning

```sql
{{ config(
    materialized='table',
    partition_by={
        "field": "order_date",
        "data_type": "date",
        "granularity": "day"
    }
) }}
```

### Clustering

```sql
{{ config(
    materialized='table',
    cluster_by=["customer_id", "product_category"]
) }}
```

### BigQuery Functions

```sql
-- Using BigQuery-specific functions
SELECT
    customer_id,
    ARRAY_AGG(product_id) as purchased_products,
    APPROX_COUNT_DISTINCT(order_id) as approx_order_count,
    FORMAT_DATE('%Y-%m', order_date) as order_month
FROM {{ ref('stg_orders') }}
GROUP BY customer_id, order_month
```

### Labels

```sql
{{ config(
    materialized='table',
    labels={'team': 'analytics', 'env': 'prod'}
) }}
```

## Best Practices

### 1. Naming Conventions

- **Staging**: `stg_<source>_<table>`
- **Intermediate**: `int_<business_concept>`
- **Marts**: `dim_<entity>` or `fct_<event>`

### 2. Model Organization

```
models/
├── staging/
│   └── source_system/
│       ├── _source_system__models.yml
│       ├── stg_source_system__table1.sql
│       └── stg_source_system__table2.sql
├── intermediate/
│   ├── _int_models.yml
│   └── int_business_concept.sql
└── marts/
    ├── core/
    │   ├── _core__models.yml
    │   ├── dim_customers.sql
    │   └── fct_orders.sql
    └── finance/
        ├── _finance__models.yml
        └── revenue_summary.sql
```

### 3. Performance Optimization

- Use appropriate materializations
- Implement partitioning and clustering
- Limit data scanned with WHERE clauses
- Use `{{ limit_data_in_dev() }}` for development

### 4. Documentation

```yaml
# models/schema.yml
version: 2

sources:
  - name: raw_data
    description: "Raw data from our application database"
    tables:
      - name: orders
        description: "Order transactions"
        columns:
          - name: order_id
            description: "Unique order identifier"
            tests:
              - unique
              - not_null

models:
  - name: dim_customers
    description: "Customer dimension with calculated metrics"
    columns:
      - name: customer_id
        description: "Unique customer identifier"
```

## Common Commands

### Development Workflow

```bash
# Check connection and configuration
dbt debug

# Run all models
dbt run

# Run specific model
dbt run --select dim_customers

# Run models and downstream dependencies
dbt run --select +dim_customers

# Run tests
dbt test

# Run tests for specific model
dbt test --select dim_customers

# Generate documentation
dbt docs generate
dbt docs serve

# Load seeds
dbt seed

# Run snapshots
dbt snapshot

# Clean compiled files
dbt clean

# Compile without running
dbt compile
```

### Advanced Selections

```bash
# Run staging models only
dbt run --select staging.*

# Run models in marts folder
dbt run --select marts.*

# Run models with specific tag
dbt run --select tag:daily

# Run modified models and downstream
dbt run --select state:modified+

# Run models that failed in last run
dbt run --select result:error
```

## Troubleshooting

### Common Issues

#### 1. Authentication Errors
```bash
# Check authentication
gcloud auth application-default login
dbt debug
```

#### 2. Permission Errors
Ensure service account has:
- BigQuery Data Editor
- BigQuery Job User
- BigQuery User

#### 3. Dataset Not Found
```sql
-- Check if dataset exists
SELECT schema_name 
FROM `your-project.INFORMATION_SCHEMA.SCHEMATA`
WHERE schema_name = 'your_dataset'
```

#### 4. Model Compilation Errors
```bash
# Compile to see generated SQL
dbt compile --select problematic_model
# Check target/compiled/ folder
```

#### 5. Performance Issues
- Check query execution plan in BigQuery console
- Implement partitioning/clustering
- Use `APPROX_` functions for large datasets
- Limit data with WHERE clauses

### Debugging Tips

1. **Use `dbt compile`** to see generated SQL
2. **Check logs** in `logs/dbt.log`
3. **Use `--debug` flag** for verbose output
4. **Test in BigQuery console** before adding to dbt
5. **Use `limit_data_in_dev()` macro** for faster development

### Environment Variables

```bash
# Set environment variables for different configs
export DBT_PROFILES_DIR=/path/to/profiles
export DBT_PROJECT_DIR=/path/to/project

# Use in CI/CD
dbt run --target prod --vars '{"start_date": "2023-01-01"}'
```

## Advanced Topics

### Incremental Models

```sql
{{ config(
    materialized='incremental',
    unique_key='order_id',
    on_schema_change='fail'
) }}

SELECT * FROM {{ source('raw_data', 'orders') }}

{% if is_incremental() %}
    WHERE order_date > (SELECT MAX(order_date) FROM {{ this }})
{% endif %}
```

### Hooks

```yaml
# dbt_project.yml
on-run-start:
  - "CREATE SCHEMA IF NOT EXISTS {{ target.schema }}_audit"

on-run-end:
  - "GRANT SELECT ON ALL TABLES IN SCHEMA {{ target.schema }} TO GROUP analysts"
```

### Variables

```yaml
# dbt_project.yml
vars:
  start_date: '2020-01-01'
  timezone: 'America/New_York'
```

```sql
-- In models
WHERE order_date >= '{{ var("start_date") }}'
```

This guide provides a comprehensive foundation for using dbt with BigQuery. Start with simple models and gradually incorporate more advanced features as your project grows.