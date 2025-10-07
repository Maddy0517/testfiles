{{ config(
    materialized='view',
    description='Cleaned and standardized orders data from raw source'
) }}

WITH source_data AS (
    SELECT * FROM {{ source('raw_data', 'orders') }}
),

cleaned_orders AS (
    SELECT
        -- Primary key
        order_id,
        
        -- Foreign keys
        customer_id,
        
        -- Dates - convert to proper date types
        CAST(order_date AS DATE) AS order_date,
        EXTRACT(YEAR FROM CAST(order_date AS DATE)) AS order_year,
        EXTRACT(MONTH FROM CAST(order_date AS DATE)) AS order_month,
        EXTRACT(DAYOFWEEK FROM CAST(order_date AS DATE)) AS order_day_of_week,
        
        -- Financial amounts - convert from cents to dollars
        ROUND(CAST(order_amount AS NUMERIC) / 100, 2) AS order_amount_dollars,
        CAST(order_amount AS INT64) AS order_amount_cents,
        
        -- Status - standardize case
        LOWER(TRIM(order_status)) AS order_status,
        
        -- Derived fields
        CASE 
            WHEN LOWER(TRIM(order_status)) IN ('delivered', 'shipped') THEN 'completed'
            WHEN LOWER(TRIM(order_status)) = 'cancelled' THEN 'cancelled'
            ELSE 'in_progress'
        END AS order_status_group,
        
        -- Audit fields
        CURRENT_TIMESTAMP() AS dbt_loaded_at,
        CURRENT_DATE() AS dbt_loaded_date
        
    FROM source_data
    
    -- Data quality filters
    WHERE 
        order_id IS NOT NULL
        AND customer_id IS NOT NULL
        AND order_date IS NOT NULL
        AND order_amount IS NOT NULL
        AND order_date >= '2020-01-01'  -- Only include recent orders
        AND CAST(order_amount AS NUMERIC) > 0  -- Only positive amounts
)

SELECT * FROM cleaned_orders