{{ config(
    materialized='table',
    partition_by={
        "field": "order_date",
        "data_type": "date"
    },
    cluster_by=["order_status_group", "customer_id"],
    description='Order fact table with enriched metrics and dimensional context'
) }}

WITH orders AS (
    SELECT * FROM {{ ref('stg_orders') }}
),

order_items AS (
    SELECT * FROM {{ ref('stg_order_items') }}
),

customers AS (
    SELECT 
        customer_id,
        customer_segment,
        customer_value_tier,
        registration_date,
        customer_tenure_group
    FROM {{ ref('dim_customers') }}
),

-- Aggregate order items to order level
order_item_aggregates AS (
    SELECT
        order_id,
        COUNT(*) AS total_line_items,
        SUM(quantity) AS total_items_quantity,
        SUM(line_total_dollars) AS calculated_order_total,
        COUNT(DISTINCT product_id) AS unique_products_count,
        AVG(unit_price_dollars) AS avg_unit_price,
        MIN(unit_price_dollars) AS min_unit_price,
        MAX(unit_price_dollars) AS max_unit_price
    FROM order_items
    GROUP BY order_id
),

order_facts AS (
    SELECT
        -- Order identifiers
        o.order_id,
        o.customer_id,
        
        -- Order dates and temporal attributes
        o.order_date,
        o.order_year,
        o.order_month,
        o.order_day_of_week,
        
        -- Order financial metrics
        o.order_amount_dollars,
        o.order_amount_cents,
        COALESCE(oia.calculated_order_total, 0) AS calculated_order_total,
        ABS(o.order_amount_dollars - COALESCE(oia.calculated_order_total, 0)) AS amount_variance,
        
        -- Order status
        o.order_status,
        o.order_status_group,
        
        -- Order composition metrics
        COALESCE(oia.total_line_items, 0) AS total_line_items,
        COALESCE(oia.total_items_quantity, 0) AS total_items_quantity,
        COALESCE(oia.unique_products_count, 0) AS unique_products_count,
        COALESCE(oia.avg_unit_price, 0) AS avg_unit_price,
        COALESCE(oia.min_unit_price, 0) AS min_unit_price,
        COALESCE(oia.max_unit_price, 0) AS max_unit_price,
        
        -- Customer context (at time of order)
        c.customer_segment,
        c.customer_value_tier,
        c.customer_tenure_group,
        DATE_DIFF(o.order_date, c.registration_date, DAY) AS days_since_customer_registration,
        
        -- Derived metrics
        CASE 
            WHEN COALESCE(oia.total_line_items, 0) > 0 
            THEN o.order_amount_dollars / oia.total_line_items 
            ELSE 0 
        END AS avg_amount_per_line_item,
        
        CASE 
            WHEN COALESCE(oia.total_items_quantity, 0) > 0 
            THEN o.order_amount_dollars / oia.total_items_quantity 
            ELSE 0 
        END AS avg_amount_per_item,
        
        -- Order size classification
        CASE 
            WHEN o.order_amount_dollars >= 500 THEN 'Large'
            WHEN o.order_amount_dollars >= 200 THEN 'Medium'
            WHEN o.order_amount_dollars >= 50 THEN 'Small'
            ELSE 'Micro'
        END AS order_size_tier,
        
        -- Order complexity
        CASE 
            WHEN COALESCE(oia.unique_products_count, 0) >= 10 THEN 'High Complexity'
            WHEN COALESCE(oia.unique_products_count, 0) >= 5 THEN 'Medium Complexity'
            WHEN COALESCE(oia.unique_products_count, 0) >= 2 THEN 'Low Complexity'
            WHEN COALESCE(oia.unique_products_count, 0) = 1 THEN 'Single Product'
            ELSE 'No Products'
        END AS order_complexity,
        
        -- Temporal classifications
        CASE 
            WHEN o.order_day_of_week IN (1, 7) THEN 'Weekend'
            ELSE 'Weekday'
        END AS day_type,
        
        CASE 
            WHEN o.order_month IN (12, 1, 2) THEN 'Winter'
            WHEN o.order_month IN (3, 4, 5) THEN 'Spring'
            WHEN o.order_month IN (6, 7, 8) THEN 'Summer'
            WHEN o.order_month IN (9, 10, 11) THEN 'Fall'
        END AS season,
        
        CASE 
            WHEN o.order_month IN (11, 12) THEN 'Holiday Season'
            WHEN o.order_month IN (6, 7, 8) THEN 'Summer Season'
            WHEN o.order_month = 1 THEN 'New Year'
            ELSE 'Regular Season'
        END AS business_season,
        
        -- Customer lifecycle context
        CASE 
            WHEN DATE_DIFF(o.order_date, c.registration_date, DAY) <= 7 THEN 'New Customer Order'
            WHEN DATE_DIFF(o.order_date, c.registration_date, DAY) <= 30 THEN 'Recent Customer Order'
            ELSE 'Established Customer Order'
        END AS customer_lifecycle_stage,
        
        -- Data quality flags
        CASE 
            WHEN ABS(o.order_amount_dollars - COALESCE(oia.calculated_order_total, 0)) > 0.01 
            THEN TRUE 
            ELSE FALSE 
        END AS has_amount_discrepancy,
        
        CASE 
            WHEN COALESCE(oia.total_line_items, 0) = 0 
            THEN TRUE 
            ELSE FALSE 
        END AS missing_line_items,
        
        -- Audit fields
        o.dbt_loaded_at,
        o.dbt_loaded_date
        
    FROM orders o
    LEFT JOIN order_item_aggregates oia ON o.order_id = oia.order_id
    LEFT JOIN customers c ON o.customer_id = c.customer_id
)

SELECT * FROM order_facts