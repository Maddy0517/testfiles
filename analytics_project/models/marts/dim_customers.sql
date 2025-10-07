{{ config(
    materialized='table',
    partition_by={
        "field": "registration_date",
        "data_type": "date"
    },
    cluster_by=["customer_segment", "customer_tenure_group"],
    description='Customer dimension table with enriched attributes and behavioral segments'
) }}

WITH customers AS (
    SELECT * FROM {{ ref('stg_customers') }}
),

customer_order_summary AS (
    SELECT * FROM {{ ref('int_customer_order_summary') }}
),

customer_dimension AS (
    SELECT
        -- Customer identifiers
        c.customer_id,
        c.customer_email,
        c.first_name,
        c.last_name,
        c.full_name,
        
        -- Registration information
        c.registration_date,
        c.registration_year,
        c.registration_month,
        c.days_since_registration,
        c.customer_tenure_group,
        
        -- Contact information analysis
        c.email_domain,
        c.email_type,
        
        -- Order behavior metrics (with safe defaults for customers without orders)
        COALESCE(cos.total_orders, 0) AS total_orders,
        COALESCE(cos.completed_orders, 0) AS completed_orders,
        COALESCE(cos.cancelled_orders, 0) AS cancelled_orders,
        COALESCE(cos.in_progress_orders, 0) AS in_progress_orders,
        
        -- Financial metrics
        COALESCE(cos.total_order_value, 0) AS total_order_value,
        COALESCE(cos.completed_order_value, 0) AS completed_order_value,
        COALESCE(cos.avg_order_value, 0) AS avg_order_value,
        COALESCE(cos.min_order_value, 0) AS min_order_value,
        COALESCE(cos.max_order_value, 0) AS max_order_value,
        
        -- Customer lifecycle dates
        cos.first_order_date,
        cos.last_order_date,
        COALESCE(cos.customer_lifespan_days, 0) AS customer_lifespan_days,
        COALESCE(cos.days_since_last_order, 999999) AS days_since_last_order,
        
        -- Behavioral segments
        COALESCE(cos.order_frequency_segment, 'No Orders') AS order_frequency_segment,
        COALESCE(cos.customer_segment, 'No Orders') AS customer_segment,
        
        -- RFM Analysis
        COALESCE(cos.recency_score, 1) AS recency_score,
        COALESCE(cos.frequency_score, 1) AS frequency_score,
        COALESCE(cos.monetary_score, 1) AS monetary_score,
        COALESCE(cos.rfm_score, '111') AS rfm_score,
        
        -- Activity patterns
        COALESCE(cos.active_years, 0) AS active_years,
        COALESCE(cos.active_months, 0) AS active_months,
        
        -- Seasonal preferences
        COALESCE(cos.winter_orders, 0) AS winter_orders,
        COALESCE(cos.spring_orders, 0) AS spring_orders,
        COALESCE(cos.summer_orders, 0) AS summer_orders,
        COALESCE(cos.fall_orders, 0) AS fall_orders,
        
        -- Shopping patterns
        COALESCE(cos.weekend_orders, 0) AS weekend_orders,
        COALESCE(cos.weekday_orders, 0) AS weekday_orders,
        
        -- Derived insights
        CASE 
            WHEN cos.weekend_orders > cos.weekday_orders THEN 'Weekend Shopper'
            WHEN cos.weekday_orders > cos.weekend_orders THEN 'Weekday Shopper'
            WHEN cos.weekend_orders = cos.weekday_orders AND cos.weekend_orders > 0 THEN 'Balanced Shopper'
            ELSE 'No Shopping Pattern'
        END AS shopping_time_preference,
        
        CASE 
            WHEN cos.winter_orders = GREATEST(cos.winter_orders, cos.spring_orders, cos.summer_orders, cos.fall_orders) THEN 'Winter'
            WHEN cos.spring_orders = GREATEST(cos.winter_orders, cos.spring_orders, cos.summer_orders, cos.fall_orders) THEN 'Spring'
            WHEN cos.summer_orders = GREATEST(cos.winter_orders, cos.spring_orders, cos.summer_orders, cos.fall_orders) THEN 'Summer'
            WHEN cos.fall_orders = GREATEST(cos.winter_orders, cos.spring_orders, cos.summer_orders, cos.fall_orders) THEN 'Fall'
            ELSE 'No Seasonal Preference'
        END AS preferred_season,
        
        -- Customer status
        CASE 
            WHEN cos.customer_id IS NULL THEN 'Registered - No Orders'
            WHEN cos.days_since_last_order <= 30 THEN 'Active'
            WHEN cos.days_since_last_order <= 90 THEN 'Recently Active'
            WHEN cos.days_since_last_order <= 365 THEN 'Dormant'
            ELSE 'Churned'
        END AS customer_status,
        
        -- Value tier
        CASE 
            WHEN COALESCE(cos.completed_order_value, 0) >= 1000 THEN 'High Value'
            WHEN COALESCE(cos.completed_order_value, 0) >= 500 THEN 'Medium Value'
            WHEN COALESCE(cos.completed_order_value, 0) >= 100 THEN 'Low Value'
            WHEN COALESCE(cos.completed_order_value, 0) > 0 THEN 'Minimal Value'
            ELSE 'No Value'
        END AS customer_value_tier,
        
        -- Audit fields
        c.dbt_loaded_at,
        c.dbt_loaded_date
        
    FROM customers c
    LEFT JOIN customer_order_summary cos ON c.customer_id = cos.customer_id
)

SELECT * FROM customer_dimension