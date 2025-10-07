{{ config(
    materialized='view',
    description='Cleaned and standardized product data from raw source'
) }}

WITH source_data AS (
    SELECT * FROM {{ source('raw_data', 'products') }}
),

cleaned_products AS (
    SELECT
        -- Primary key
        product_id,
        
        -- Product information - clean and standardize
        TRIM(product_name) AS product_name,
        UPPER(TRIM(category)) AS category,
        
        -- Pricing - convert from cents to dollars
        ROUND(CAST(price AS NUMERIC) / 100, 2) AS price_dollars,
        CAST(price AS INT64) AS price_cents,
        
        -- Dates
        CAST(created_date AS DATE) AS created_date,
        EXTRACT(YEAR FROM CAST(created_date AS DATE)) AS created_year,
        EXTRACT(MONTH FROM CAST(created_date AS DATE)) AS created_month,
        
        -- Derived fields
        DATE_DIFF(CURRENT_DATE(), CAST(created_date AS DATE), DAY) AS days_since_created,
        
        CASE 
            WHEN CAST(price AS NUMERIC) < 1000 THEN 'Low'  -- < $10
            WHEN CAST(price AS NUMERIC) < 5000 THEN 'Medium'  -- $10-$50
            WHEN CAST(price AS NUMERIC) < 20000 THEN 'High'  -- $50-$200
            ELSE 'Premium'  -- > $200
        END AS price_tier,
        
        -- Category grouping
        CASE 
            WHEN UPPER(TRIM(category)) IN ('ELECTRONICS', 'COMPUTERS', 'PHONES') THEN 'Technology'
            WHEN UPPER(TRIM(category)) IN ('CLOTHING', 'SHOES', 'ACCESSORIES') THEN 'Fashion'
            WHEN UPPER(TRIM(category)) IN ('BOOKS', 'MUSIC', 'MOVIES') THEN 'Media'
            WHEN UPPER(TRIM(category)) IN ('HOME', 'GARDEN', 'FURNITURE') THEN 'Home & Garden'
            ELSE 'Other'
        END AS category_group,
        
        -- Audit fields
        CURRENT_TIMESTAMP() AS dbt_loaded_at,
        CURRENT_DATE() AS dbt_loaded_date
        
    FROM source_data
    
    -- Data quality filters
    WHERE 
        product_id IS NOT NULL
        AND product_name IS NOT NULL
        AND price IS NOT NULL
        AND CAST(price AS NUMERIC) > 0  -- Only positive prices
        AND created_date IS NOT NULL
)

SELECT * FROM cleaned_products