{{ config(
    materialized='view',
    description='Product-level performance metrics and analytics'
) }}

WITH order_items AS (
    SELECT * FROM {{ ref('stg_order_items') }}
),

orders AS (
    SELECT * FROM {{ ref('stg_orders') }}
),

products AS (
    SELECT * FROM {{ ref('stg_products') }}
),

order_items_with_order_info AS (
    SELECT 
        oi.*,
        o.order_date,
        o.order_year,
        o.order_month,
        o.order_status_group,
        o.customer_id
    FROM order_items oi
    LEFT JOIN orders o ON oi.order_id = o.order_id
),

product_metrics AS (
    SELECT
        product_id,
        
        -- Sales volume metrics
        COUNT(DISTINCT order_id) AS total_orders_with_product,
        COUNT(DISTINCT customer_id) AS unique_customers,
        SUM(quantity) AS total_units_sold,
        SUM(CASE WHEN order_status_group = 'completed' THEN quantity ELSE 0 END) AS completed_units_sold,
        
        -- Revenue metrics
        SUM(line_total_dollars) AS total_revenue,
        SUM(CASE WHEN order_status_group = 'completed' THEN line_total_dollars ELSE 0 END) AS completed_revenue,
        AVG(line_total_dollars) AS avg_line_total,
        AVG(quantity) AS avg_quantity_per_order,
        
        -- Date metrics
        MIN(order_date) AS first_sale_date,
        MAX(order_date) AS last_sale_date,
        DATE_DIFF(MAX(order_date), MIN(order_date), DAY) AS product_sales_lifespan_days,
        DATE_DIFF(CURRENT_DATE(), MAX(order_date), DAY) AS days_since_last_sale,
        
        -- Temporal patterns
        COUNT(DISTINCT order_year) AS active_sales_years,
        COUNT(DISTINCT order_month) AS active_sales_months,
        
        -- Seasonal analysis
        SUM(CASE WHEN EXTRACT(MONTH FROM order_date) IN (12, 1, 2) THEN quantity ELSE 0 END) AS winter_units,
        SUM(CASE WHEN EXTRACT(MONTH FROM order_date) IN (3, 4, 5) THEN quantity ELSE 0 END) AS spring_units,
        SUM(CASE WHEN EXTRACT(MONTH FROM order_date) IN (6, 7, 8) THEN quantity ELSE 0 END) AS summer_units,
        SUM(CASE WHEN EXTRACT(MONTH FROM order_date) IN (9, 10, 11) THEN quantity ELSE 0 END) AS fall_units,
        
        -- Recent performance (last 30, 90, 365 days)
        SUM(CASE WHEN DATE_DIFF(CURRENT_DATE(), order_date, DAY) <= 30 THEN quantity ELSE 0 END) AS units_last_30_days,
        SUM(CASE WHEN DATE_DIFF(CURRENT_DATE(), order_date, DAY) <= 90 THEN quantity ELSE 0 END) AS units_last_90_days,
        SUM(CASE WHEN DATE_DIFF(CURRENT_DATE(), order_date, DAY) <= 365 THEN quantity ELSE 0 END) AS units_last_365_days,
        
        SUM(CASE WHEN DATE_DIFF(CURRENT_DATE(), order_date, DAY) <= 30 THEN line_total_dollars ELSE 0 END) AS revenue_last_30_days,
        SUM(CASE WHEN DATE_DIFF(CURRENT_DATE(), order_date, DAY) <= 90 THEN line_total_dollars ELSE 0 END) AS revenue_last_90_days,
        SUM(CASE WHEN DATE_DIFF(CURRENT_DATE(), order_date, DAY) <= 365 THEN line_total_dollars ELSE 0 END) AS revenue_last_365_days
        
    FROM order_items_with_order_info
    GROUP BY product_id
),

product_metrics_with_product_info AS (
    SELECT 
        pm.*,
        p.product_name,
        p.category,
        p.category_group,
        p.price_dollars,
        p.price_tier,
        p.created_date,
        p.days_since_created
    FROM product_metrics pm
    LEFT JOIN products p ON pm.product_id = p.product_id
),

product_performance_scores AS (
    SELECT
        *,
        
        -- Performance scoring (1-5 scale)
        CASE 
            WHEN completed_units_sold >= 1000 THEN 5
            WHEN completed_units_sold >= 500 THEN 4
            WHEN completed_units_sold >= 100 THEN 3
            WHEN completed_units_sold >= 10 THEN 2
            ELSE 1
        END AS volume_score,
        
        CASE 
            WHEN completed_revenue >= 10000 THEN 5
            WHEN completed_revenue >= 5000 THEN 4
            WHEN completed_revenue >= 1000 THEN 3
            WHEN completed_revenue >= 100 THEN 2
            ELSE 1
        END AS revenue_score,
        
        CASE 
            WHEN days_since_last_sale <= 7 THEN 5
            WHEN days_since_last_sale <= 30 THEN 4
            WHEN days_since_last_sale <= 90 THEN 3
            WHEN days_since_last_sale <= 180 THEN 2
            ELSE 1
        END AS recency_score,
        
        -- Growth trends (comparing recent vs historical performance)
        CASE 
            WHEN units_last_30_days > 0 AND completed_units_sold > units_last_30_days * 12 THEN 'Growing'
            WHEN units_last_30_days > 0 AND completed_units_sold < units_last_30_days * 6 THEN 'Declining'
            ELSE 'Stable'
        END AS trend_direction
        
    FROM product_metrics_with_product_info
),

final AS (
    SELECT
        *,
        
        -- Overall performance classification
        CASE 
            WHEN volume_score >= 4 AND revenue_score >= 4 AND recency_score >= 4 THEN 'Star Products'
            WHEN volume_score >= 3 AND revenue_score >= 3 AND recency_score >= 3 THEN 'Solid Performers'
            WHEN recency_score <= 2 AND (volume_score >= 3 OR revenue_score >= 3) THEN 'Declining Stars'
            WHEN recency_score >= 4 AND volume_score <= 2 AND revenue_score <= 2 THEN 'New Launches'
            WHEN volume_score <= 2 AND revenue_score <= 2 AND recency_score <= 2 THEN 'Underperformers'
            ELSE 'Average Performers'
        END AS product_classification,
        
        -- Market position within category
        ROW_NUMBER() OVER (PARTITION BY category ORDER BY completed_revenue DESC) AS category_revenue_rank,
        ROW_NUMBER() OVER (PARTITION BY category ORDER BY completed_units_sold DESC) AS category_volume_rank
        
    FROM product_performance_scores
)

SELECT * FROM final