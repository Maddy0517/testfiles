{{ config(
    materialized='table',
    partition_by={
        "field": "created_date",
        "data_type": "date"
    },
    cluster_by=["category", "product_classification"],
    description='Product dimension table with performance metrics and classifications'
) }}

WITH products AS (
    SELECT * FROM {{ ref('stg_products') }}
),

product_performance AS (
    SELECT * FROM {{ ref('int_product_performance') }}
),

product_dimension AS (
    SELECT
        -- Product identifiers and basic info
        p.product_id,
        p.product_name,
        p.category,
        p.category_group,
        
        -- Pricing information
        p.price_dollars,
        p.price_cents,
        p.price_tier,
        
        -- Product lifecycle
        p.created_date,
        p.created_year,
        p.created_month,
        p.days_since_created,
        
        -- Performance metrics (with safe defaults for products never sold)
        COALESCE(pp.total_orders_with_product, 0) AS total_orders_with_product,
        COALESCE(pp.unique_customers, 0) AS unique_customers,
        COALESCE(pp.total_units_sold, 0) AS total_units_sold,
        COALESCE(pp.completed_units_sold, 0) AS completed_units_sold,
        
        -- Revenue metrics
        COALESCE(pp.total_revenue, 0) AS total_revenue,
        COALESCE(pp.completed_revenue, 0) AS completed_revenue,
        COALESCE(pp.avg_line_total, 0) AS avg_line_total,
        COALESCE(pp.avg_quantity_per_order, 0) AS avg_quantity_per_order,
        
        -- Sales lifecycle
        pp.first_sale_date,
        pp.last_sale_date,
        COALESCE(pp.product_sales_lifespan_days, 0) AS product_sales_lifespan_days,
        COALESCE(pp.days_since_last_sale, 999999) AS days_since_last_sale,
        
        -- Activity patterns
        COALESCE(pp.active_sales_years, 0) AS active_sales_years,
        COALESCE(pp.active_sales_months, 0) AS active_sales_months,
        
        -- Seasonal performance
        COALESCE(pp.winter_units, 0) AS winter_units,
        COALESCE(pp.spring_units, 0) AS spring_units,
        COALESCE(pp.summer_units, 0) AS summer_units,
        COALESCE(pp.fall_units, 0) AS fall_units,
        
        -- Recent performance
        COALESCE(pp.units_last_30_days, 0) AS units_last_30_days,
        COALESCE(pp.units_last_90_days, 0) AS units_last_90_days,
        COALESCE(pp.units_last_365_days, 0) AS units_last_365_days,
        COALESCE(pp.revenue_last_30_days, 0) AS revenue_last_30_days,
        COALESCE(pp.revenue_last_90_days, 0) AS revenue_last_90_days,
        COALESCE(pp.revenue_last_365_days, 0) AS revenue_last_365_days,
        
        -- Performance scores
        COALESCE(pp.volume_score, 1) AS volume_score,
        COALESCE(pp.revenue_score, 1) AS revenue_score,
        COALESCE(pp.recency_score, 1) AS recency_score,
        
        -- Classifications
        COALESCE(pp.product_classification, 'Never Sold') AS product_classification,
        COALESCE(pp.trend_direction, 'Unknown') AS trend_direction,
        
        -- Market position
        pp.category_revenue_rank,
        pp.category_volume_rank,
        
        -- Derived insights
        CASE 
            WHEN pp.winter_units = GREATEST(COALESCE(pp.winter_units, 0), COALESCE(pp.spring_units, 0), COALESCE(pp.summer_units, 0), COALESCE(pp.fall_units, 0)) THEN 'Winter'
            WHEN pp.spring_units = GREATEST(COALESCE(pp.winter_units, 0), COALESCE(pp.spring_units, 0), COALESCE(pp.summer_units, 0), COALESCE(pp.fall_units, 0)) THEN 'Spring'
            WHEN pp.summer_units = GREATEST(COALESCE(pp.winter_units, 0), COALESCE(pp.spring_units, 0), COALESCE(pp.summer_units, 0), COALESCE(pp.fall_units, 0)) THEN 'Summer'
            WHEN pp.fall_units = GREATEST(COALESCE(pp.winter_units, 0), COALESCE(pp.spring_units, 0), COALESCE(pp.summer_units, 0), COALESCE(pp.fall_units, 0)) THEN 'Fall'
            ELSE 'No Seasonal Pattern'
        END AS peak_season,
        
        -- Product status
        CASE 
            WHEN pp.product_id IS NULL THEN 'Never Sold'
            WHEN pp.days_since_last_sale <= 30 THEN 'Active'
            WHEN pp.days_since_last_sale <= 90 THEN 'Recently Active'
            WHEN pp.days_since_last_sale <= 365 THEN 'Slow Moving'
            ELSE 'Dormant'
        END AS product_status,
        
        -- Inventory insights
        CASE 
            WHEN COALESCE(pp.units_last_30_days, 0) = 0 AND COALESCE(pp.units_last_90_days, 0) = 0 THEN 'Consider Discontinuing'
            WHEN COALESCE(pp.units_last_30_days, 0) > COALESCE(pp.units_last_90_days, 0) / 3 THEN 'Trending Up'
            WHEN COALESCE(pp.units_last_30_days, 0) < COALESCE(pp.units_last_90_days, 0) / 6 THEN 'Trending Down'
            ELSE 'Stable'
        END AS inventory_recommendation,
        
        -- Customer reach
        CASE 
            WHEN COALESCE(pp.unique_customers, 0) >= 100 THEN 'Broad Appeal'
            WHEN COALESCE(pp.unique_customers, 0) >= 20 THEN 'Moderate Appeal'
            WHEN COALESCE(pp.unique_customers, 0) >= 5 THEN 'Niche Appeal'
            WHEN COALESCE(pp.unique_customers, 0) > 0 THEN 'Limited Appeal'
            ELSE 'No Appeal'
        END AS customer_appeal,
        
        -- Audit fields
        p.dbt_loaded_at,
        p.dbt_loaded_date
        
    FROM products p
    LEFT JOIN product_performance pp ON p.product_id = pp.product_id
)

SELECT * FROM product_dimension