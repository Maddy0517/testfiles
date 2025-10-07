# dbt with Google BigQuery - Complete Guide

## Table of Contents
1. [Introduction to dbt](#introduction-to-dbt)
2. [Setting Up dbt with BigQuery](#setting-up-dbt-with-bigquery)
3. [Project Structure](#project-structure)
4. [Core Concepts](#core-concepts)
5. [Working with Models](#working-with-models)
6. [Testing](#testing)
7. [Documentation](#documentation)
8. [Best Practices](#best-practices)
9. [Common Commands](#common-commands)
10. [Troubleshooting](#troubleshooting)

## Introduction to dbt

dbt (data build tool) is a transformation tool that enables data analysts and engineers to transform data in their warehouse using SQL. It's particularly powerful when used with BigQuery.

### Why dbt?
- **Version Control**: All transformations are defined in SQL files that can be version controlled
- **Testing**: Built-in data testing framework
- **Documentation**: Auto-generated documentation for your data models
- **Modularity**: Reusable models and macros
- **Dependency Management**: Automatic dependency resolution between models

## Setting Up dbt with BigQuery

### Prerequisites
1. Google Cloud Platform account with BigQuery enabled
2. Python 3.7+ installed
3. Git for version control

### Installation

```bash
# Install dbt-bigquery
pip install dbt-bigquery

# Verify installation
dbt --version
```

### Authentication Methods

#### Method 1: Service Account Key (Recommended for Production)
1. Create a service account in GCP Console
2. Download the JSON key file
3. Grant BigQuery Data Editor and Job User roles

#### Method 2: OAuth (Good for Development)
```bash
# Initialize OAuth authentication
gcloud auth application-default login
```

### Configuration

Create `~/.dbt/profiles.yml`:

```yaml
my_bigquery_project:
  target: dev
  outputs:
    dev:
      type: bigquery
      method: service-account  # or 'oauth' for OAuth
      project: your-gcp-project-id
      dataset: dbt_dev  # This will be your default schema
      threads: 4
      keyfile: /path/to/service-account-key.json  # Only for service-account method
      location: US  # or EU, asia-northeast1, etc.
      timeout_seconds: 300
      priority: interactive
      retries: 1
    
    prod:
      type: bigquery
      method: service-account
      project: your-gcp-project-id
      dataset: dbt_prod
      threads: 8
      keyfile: /path/to/service-account-key.json
      location: US
      timeout_seconds: 300
      priority: interactive
      retries: 1
```

## Project Structure

```
dbt_bigquery_project/
├── analyses/              # Analytical SQL files for ad-hoc queries
├── data/                  # CSV seed files
├── macros/                # Reusable SQL functions
├── models/                # Your dbt models (SQL transformations)
│   ├── staging/          # Raw data cleaning and renaming
│   ├── intermediate/     # Business logic transformations
│   └── marts/            # Final tables for analytics
├── snapshots/            # Slowly changing dimension tables
├── tests/                # Custom data tests
├── dbt_project.yml       # Project configuration
└── profiles.yml          # Connection configuration (usually in ~/.dbt/)
```

## Core Concepts

### 1. Models
Models are the core of dbt - they're SQL SELECT statements that transform data.

**Example Model** (`models/staging/stg_customers.sql`):
```sql
{{ config(
    materialized='view',
    partition_by={
      "field": "created_date",
      "data_type": "date",
      "granularity": "day"
    },
    cluster_by = ["country", "customer_segment"]
) }}

with source_data as (
    select 
        customer_id,
        customer_name,
        email,
        country,
        customer_segment,
        DATE(created_at) as created_date,
        created_at,
        updated_at
    from {{ source('raw_data', 'customers') }}
)

select * from source_data
```

### 2. Sources
Sources represent raw data tables in your warehouse.

**Define sources** (`models/staging/sources.yml`):
```yaml
version: 2

sources:
  - name: raw_data
    database: your-gcp-project-id
    schema: raw_data_dataset
    tables:
      - name: customers
        description: Raw customer data from production database
        columns:
          - name: customer_id
            description: Primary key
            tests:
              - unique
              - not_null
      - name: orders
        description: Raw orders data
        freshness:
          warn_after: {count: 12, period: hour}
          error_after: {count: 24, period: hour}
        loaded_at_field: created_at
```

### 3. Materializations in BigQuery

dbt supports four materialization strategies in BigQuery:

- **view**: Creates a view (default)
- **table**: Creates a table
- **incremental**: Appends/updates only new records
- **ephemeral**: CTEs that exist only within a query

**Incremental Model Example**:
```sql
{{ config(
    materialized='incremental',
    partition_by={
      "field": "order_date",
      "data_type": "date",
      "granularity": "day"
    },
    cluster_by = ["customer_id"],
    unique_key='order_id'
) }}

select 
    order_id,
    customer_id,
    order_date,
    amount,
    status,
    updated_at
from {{ source('raw_data', 'orders') }}

{% if is_incremental() %}
  -- this filter will only be applied on an incremental run
  where updated_at > (select max(updated_at) from {{ this }})
{% endif %}
```

### 4. Tests
Tests ensure data quality and integrity.

**Schema Tests** (`models/staging/schema.yml`):
```yaml
version: 2

models:
  - name: stg_customers
    description: Staged customer data
    columns:
      - name: customer_id
        description: Primary key
        tests:
          - unique
          - not_null
      - name: email
        tests:
          - unique
          - not_null
      - name: country
        tests:
          - accepted_values:
              values: ['USA', 'UK', 'Canada', 'Australia']

  - name: stg_orders
    tests:
      - dbt_utils.unique_combination_of_columns:
          combination_of_columns:
            - order_id
            - customer_id
```

**Custom Tests** (`tests/assert_positive_values.sql`):
```sql
-- Test that all order amounts are positive
select *
from {{ ref('stg_orders') }}
where amount <= 0
```

### 5. Macros
Macros are reusable pieces of SQL code.

**Example Macro** (`macros/generate_alias_name.sql`):
```sql
{% macro generate_alias_name(custom_alias_name=none, node=none) -%}
    {%- if custom_alias_name is none -%}
        {{ node.name }}
    {%- else -%}
        {{ custom_alias_name | trim }}
    {%- endif -%}
{%- endmacro %}

{% macro cents_to_dollars(column_name, decimal_places=2) %}
    round({{ column_name }} / 100, {{ decimal_places }})
{% endmacro %}

{% macro generate_date_spine(start_date, end_date) %}
    select 
        date_day
    from unnest(
        generate_date_array(
            date('{{ start_date }}'),
            date('{{ end_date }}'),
            interval 1 day
        )
    ) as date_day
{% endmacro %}
```

### 6. Documentation
Document your models for better understanding.

**Example Documentation** (`models/marts/schema.yml`):
```yaml
version: 2

models:
  - name: fct_orders
    description: |
      This model contains one row per order. It includes all order-level metrics
      and dimensions needed for analytics.
      
      **Data Quality Notes:**
      - Orders before 2020-01-01 may have incomplete data
      - Currency is always in USD
      
    columns:
      - name: order_id
        description: Primary key for orders
        tests:
          - unique
          - not_null
      - name: customer_id
        description: Foreign key to dim_customers
        tests:
          - relationships:
              to: ref('dim_customers')
              field: customer_id
      - name: order_amount
        description: Total order amount in USD
      - name: order_status
        description: |
          Current status of the order:
          - pending: Order placed but not confirmed
          - confirmed: Order confirmed by customer
          - shipped: Order shipped to customer
          - delivered: Order delivered to customer
          - cancelled: Order cancelled
```

## Working with Models

### Staging Models
Clean and standardize raw data.

**Example** (`models/staging/stg_products.sql`):
```sql
with source as (
    select * from {{ source('raw_data', 'products') }}
),

renamed as (
    select
        -- ids
        product_id,
        category_id,
        
        -- strings
        lower(trim(product_name)) as product_name,
        lower(trim(category_name)) as category_name,
        
        -- numerics
        cast(price as numeric) as product_price,
        cast(cost as numeric) as product_cost,
        
        -- booleans
        case 
            when is_active = 'Y' then true
            else false
        end as is_active,
        
        -- timestamps
        cast(created_at as timestamp) as created_at,
        cast(updated_at as timestamp) as updated_at
        
    from source
)

select * from renamed
```

### Intermediate Models
Apply business logic and join data.

**Example** (`models/intermediate/int_order_items.sql`):
```sql
with orders as (
    select * from {{ ref('stg_orders') }}
),

order_items as (
    select * from {{ ref('stg_order_items') }}
),

products as (
    select * from {{ ref('stg_products') }}
),

joined as (
    select
        oi.order_item_id,
        oi.order_id,
        o.customer_id,
        o.order_date,
        oi.product_id,
        p.product_name,
        p.category_name,
        oi.quantity,
        oi.unit_price,
        oi.quantity * oi.unit_price as line_total,
        p.product_cost * oi.quantity as line_cost,
        (oi.quantity * oi.unit_price) - (p.product_cost * oi.quantity) as line_profit
        
    from order_items oi
    left join orders o on oi.order_id = o.order_id
    left join products p on oi.product_id = p.product_id
)

select * from joined
```

### Mart Models
Create final analytical tables.

**Example** (`models/marts/fct_daily_sales.sql`):
```sql
{{ config(
    materialized='table',
    partition_by={
      "field": "sale_date",
      "data_type": "date",
      "granularity": "day"
    }
) }}

with order_items as (
    select * from {{ ref('int_order_items') }}
),

daily_sales as (
    select
        order_date as sale_date,
        category_name,
        count(distinct order_id) as total_orders,
        count(distinct customer_id) as unique_customers,
        sum(quantity) as total_quantity,
        sum(line_total) as total_revenue,
        sum(line_cost) as total_cost,
        sum(line_profit) as total_profit,
        avg(line_total) as avg_order_value
        
    from order_items
    group by 1, 2
)

select * from daily_sales
```

## Testing

### Running Tests
```bash
# Run all tests
dbt test

# Run tests for a specific model
dbt test --select stg_customers

# Run tests for models and their children
dbt test --select stg_customers+

# Run only schema tests
dbt test --select test_type:schema

# Run only data tests
dbt test --select test_type:data
```

### Custom Generic Tests
Create reusable test macros (`macros/test_not_negative.sql`):
```sql
{% macro test_not_negative(model, column_name) %}

select *
from {{ model }}
where {{ column_name }} < 0

{% endmacro %}
```

Use in schema.yml:
```yaml
models:
  - name: fct_orders
    columns:
      - name: order_amount
        tests:
          - not_negative
```

## Documentation

### Generating Documentation
```bash
# Generate documentation
dbt docs generate

# Serve documentation locally
dbt docs serve --port 8080
```

### Adding Descriptions
Use markdown in your schema.yml files:
```yaml
models:
  - name: dim_customers
    description: |
      # Customer Dimension
      
      This table contains the current state of all customers.
      
      ## Update Frequency
      - Updated daily at 2 AM UTC
      
      ## Data Sources
      - CRM System
      - E-commerce Platform
      
      ## Business Rules
      - Customer segments are calculated based on lifetime value
      - VIP status requires >$10,000 in lifetime purchases
```

## Best Practices

### 1. Naming Conventions
- **Sources**: `src_[source]__[table]` (e.g., `src_stripe__payments`)
- **Staging**: `stg_[source]__[table]` (e.g., `stg_stripe__payments`)
- **Intermediate**: `int_[entity]_[verb]` (e.g., `int_payments_pivoted`)
- **Facts**: `fct_[entity]` (e.g., `fct_orders`)
- **Dimensions**: `dim_[entity]` (e.g., `dim_customers`)

### 2. Project Organization
```yaml
# dbt_project.yml
models:
  your_project:
    staging:
      +materialized: view
      +schema: staging
    intermediate:
      +materialized: ephemeral
      +schema: intermediate
    marts:
      +materialized: table
      +schema: marts
      finance:
        +schema: finance
      marketing:
        +schema: marketing
```

### 3. BigQuery-Specific Optimizations

**Partitioning and Clustering**:
```sql
{{ config(
    materialized='table',
    partition_by={
      "field": "created_date",
      "data_type": "date",
      "granularity": "month"
    },
    cluster_by = ["user_id", "product_category"],
    partition_expiration_days=90
) }}
```

**Cost Optimization**:
```sql
-- Use incremental models for large tables
{{ config(
    materialized='incremental',
    on_schema_change='fail',
    unique_key='id',
    partition_by={
      "field": "updated_at",
      "data_type": "timestamp",
      "granularity": "day"
    }
) }}

select *
from {{ source('raw', 'large_table') }}
{% if is_incremental() %}
  where updated_at >= (select max(updated_at) from {{ this }})
{% endif %}
```

### 4. Variables and Environment Management
```yaml
# dbt_project.yml
vars:
  # Default values
  start_date: '2020-01-01'
  end_date: '2023-12-31'
  
  # Environment-specific settings
  dev:
    sample_rate: 0.01  # Sample 1% in dev
  prod:
    sample_rate: 1.0   # Full data in prod
```

Use in models:
```sql
select *
from {{ source('raw', 'events') }}
where event_date >= '{{ var("start_date") }}'
  and event_date <= '{{ var("end_date") }}'
  {% if target.name == 'dev' %}
  and rand() <= {{ var("sample_rate") }}
  {% endif %}
```

## Common Commands

### Development Workflow
```bash
# Debug connection
dbt debug

# Run specific models
dbt run --select my_model

# Run models and their dependencies
dbt run --select +my_model

# Run models and their children
dbt run --select my_model+

# Run all models in a directory
dbt run --select models/staging

# Run using tags
dbt run --select tag:daily

# Full refresh of incremental models
dbt run --full-refresh --select my_incremental_model

# Compile SQL without running
dbt compile --select my_model

# Run and test together
dbt build --select my_model
```

### Production Deployment
```bash
# Run all models in production
dbt run --target prod

# Run tests in production
dbt test --target prod

# Generate documentation
dbt docs generate --target prod

# Create snapshot
dbt snapshot --target prod

# Run everything (models, tests, snapshots, seeds)
dbt build --target prod
```

### Debugging
```bash
# Show compiled SQL
dbt show --select my_model

# List all models
dbt list --select models/*

# Parse project without running
dbt parse

# Clean generated files
dbt clean
```

## Troubleshooting

### Common BigQuery Issues

#### 1. Authentication Errors
```bash
# Check your authentication
gcloud auth list

# Set default project
gcloud config set project YOUR_PROJECT_ID

# Test BigQuery access
bq ls
```

#### 2. Permission Errors
Required BigQuery permissions:
- bigquery.datasets.create
- bigquery.datasets.get
- bigquery.jobs.create
- bigquery.tables.create
- bigquery.tables.delete
- bigquery.tables.get
- bigquery.tables.getData
- bigquery.tables.list
- bigquery.tables.update
- bigquery.tables.updateData

#### 3. Query Exceeded Limit
```sql
-- Add partitioning to reduce data scanned
{{ config(
    materialized='incremental',
    partition_by={
      "field": "date_column",
      "data_type": "date"
    }
) }}

-- Use clustering for better performance
{{ config(
    cluster_by = ["frequently_filtered_column"]
) }}
```

#### 4. Incremental Model Issues
```sql
-- Handle schema changes
{{ config(
    on_schema_change='sync_all_columns'
) }}

-- Force rebuild if needed
-- dbt run --full-refresh --select problematic_model
```

### Performance Tips

1. **Use appropriate materializations**:
   - Views for simple transformations
   - Tables for complex transformations used frequently
   - Incremental for large, append-only datasets

2. **Leverage BigQuery features**:
   - Partitioning for time-series data
   - Clustering for frequently filtered columns
   - Materialized views for real-time requirements

3. **Optimize queries**:
   - Filter early in CTEs
   - Use approximate aggregation functions when possible
   - Avoid SELECT * in production

4. **Monitor costs**:
   ```sql
   -- Check query costs
   SELECT
     user_email,
     SUM(total_bytes_processed)/1e12 as tb_processed,
     SUM(total_bytes_processed)/1e12 * 5 as estimated_cost_usd
   FROM `region-us`.INFORMATION_SCHEMA.JOBS_BY_USER
   WHERE DATE(creation_time) = CURRENT_DATE()
   GROUP BY 1
   ORDER BY 2 DESC
   ```

## Next Steps

1. **Set up your environment**: Install dbt and configure BigQuery connection
2. **Initialize your project**: Run `dbt init your_project_name`
3. **Create your first model**: Start with staging models for your raw data
4. **Add tests**: Ensure data quality with schema and data tests
5. **Document**: Add descriptions to your models and columns
6. **Deploy**: Set up a schedule for production runs (e.g., using Cloud Composer or Cloud Scheduler)

## Additional Resources

- [dbt Documentation](https://docs.getdbt.com/)
- [dbt BigQuery Documentation](https://docs.getdbt.com/reference/warehouse-setups/bigquery-setup)
- [dbt Best Practices](https://docs.getdbt.com/guides/best-practices)
- [BigQuery Best Practices](https://cloud.google.com/bigquery/docs/best-practices-performance-overview)
- [dbt Slack Community](https://www.getdbt.com/community/)

---

This guide provides a foundation for working with dbt and BigQuery. As you become more comfortable with the basics, explore advanced features like exposures, metrics, and semantic models to further enhance your data transformation workflows.