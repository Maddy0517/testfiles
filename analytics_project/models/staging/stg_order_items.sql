{{ config(
    materialized='view',
    description='Cleaned and standardized order items data from raw source'
) }}

WITH source_data AS (
    SELECT * FROM {{ source('raw_data', 'order_items') }}
),

cleaned_order_items AS (
    SELECT
        -- Primary key
        order_item_id,
        
        -- Foreign keys
        order_id,
        product_id,
        
        -- Quantities and pricing
        CAST(quantity AS INT64) AS quantity,
        ROUND(CAST(unit_price AS NUMERIC) / 100, 2) AS unit_price_dollars,
        CAST(unit_price AS INT64) AS unit_price_cents,
        
        -- Calculated fields
        ROUND(CAST(quantity AS INT64) * CAST(unit_price AS NUMERIC) / 100, 2) AS line_total_dollars,
        CAST(quantity AS INT64) * CAST(unit_price AS INT64) AS line_total_cents,
        
        -- Audit fields
        CURRENT_TIMESTAMP() AS dbt_loaded_at,
        CURRENT_DATE() AS dbt_loaded_date
        
    FROM source_data
    
    -- Data quality filters
    WHERE 
        order_item_id IS NOT NULL
        AND order_id IS NOT NULL
        AND product_id IS NOT NULL
        AND quantity IS NOT NULL
        AND unit_price IS NOT NULL
        AND CAST(quantity AS INT64) > 0  -- Only positive quantities
        AND CAST(unit_price AS NUMERIC) > 0  -- Only positive prices
)

SELECT * FROM cleaned_order_items