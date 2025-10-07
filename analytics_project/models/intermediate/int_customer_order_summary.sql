{{ config(
    materialized='view',
    description='Customer-level order summary metrics'
) }}

WITH orders AS (
    SELECT * FROM {{ ref('stg_orders') }}
),

customer_order_metrics AS (
    SELECT
        customer_id,
        
        -- Order counts
        COUNT(*) AS total_orders,
        COUNT(CASE WHEN order_status_group = 'completed' THEN 1 END) AS completed_orders,
        COUNT(CASE WHEN order_status_group = 'cancelled' THEN 1 END) AS cancelled_orders,
        COUNT(CASE WHEN order_status_group = 'in_progress' THEN 1 END) AS in_progress_orders,
        
        -- Financial metrics
        SUM(order_amount_dollars) AS total_order_value,
        SUM(CASE WHEN order_status_group = 'completed' THEN order_amount_dollars ELSE 0 END) AS completed_order_value,
        AVG(order_amount_dollars) AS avg_order_value,
        MIN(order_amount_dollars) AS min_order_value,
        MAX(order_amount_dollars) AS max_order_value,
        
        -- Date metrics
        MIN(order_date) AS first_order_date,
        MAX(order_date) AS last_order_date,
        DATE_DIFF(MAX(order_date), MIN(order_date), DAY) AS customer_lifespan_days,
        DATE_DIFF(CURRENT_DATE(), MAX(order_date), DAY) AS days_since_last_order,
        
        -- Order frequency
        CASE 
            WHEN COUNT(*) = 1 THEN 'One-time'
            WHEN COUNT(*) <= 5 THEN 'Occasional'
            WHEN COUNT(*) <= 15 THEN 'Regular'
            ELSE 'Frequent'
        END AS order_frequency_segment,
        
        -- Recency, Frequency, Monetary (RFM) components
        DATE_DIFF(CURRENT_DATE(), MAX(order_date), DAY) AS recency_days,
        COUNT(*) AS frequency_orders,
        SUM(CASE WHEN order_status_group = 'completed' THEN order_amount_dollars ELSE 0 END) AS monetary_value,
        
        -- Order patterns
        COUNT(DISTINCT order_year) AS active_years,
        COUNT(DISTINCT order_month) AS active_months,
        
        -- Seasonal analysis
        COUNT(CASE WHEN order_month IN (12, 1, 2) THEN 1 END) AS winter_orders,
        COUNT(CASE WHEN order_month IN (3, 4, 5) THEN 1 END) AS spring_orders,
        COUNT(CASE WHEN order_month IN (6, 7, 8) THEN 1 END) AS summer_orders,
        COUNT(CASE WHEN order_month IN (9, 10, 11) THEN 1 END) AS fall_orders,
        
        -- Day of week patterns
        COUNT(CASE WHEN order_day_of_week IN (1, 7) THEN 1 END) AS weekend_orders,
        COUNT(CASE WHEN order_day_of_week IN (2, 3, 4, 5, 6) THEN 1 END) AS weekday_orders
        
    FROM orders
    GROUP BY customer_id
),

customer_segments AS (
    SELECT
        *,
        
        -- RFM Scoring (1-5 scale, 5 being best)
        CASE 
            WHEN recency_days <= 30 THEN 5
            WHEN recency_days <= 90 THEN 4
            WHEN recency_days <= 180 THEN 3
            WHEN recency_days <= 365 THEN 2
            ELSE 1
        END AS recency_score,
        
        CASE 
            WHEN frequency_orders >= 20 THEN 5
            WHEN frequency_orders >= 10 THEN 4
            WHEN frequency_orders >= 5 THEN 3
            WHEN frequency_orders >= 2 THEN 2
            ELSE 1
        END AS frequency_score,
        
        CASE 
            WHEN monetary_value >= 1000 THEN 5
            WHEN monetary_value >= 500 THEN 4
            WHEN monetary_value >= 200 THEN 3
            WHEN monetary_value >= 50 THEN 2
            ELSE 1
        END AS monetary_score
        
    FROM customer_order_metrics
),

final AS (
    SELECT
        *,
        
        -- Combined RFM Score
        CONCAT(recency_score, frequency_score, monetary_score) AS rfm_score,
        
        -- Customer Value Segments
        CASE 
            WHEN recency_score >= 4 AND frequency_score >= 4 AND monetary_score >= 4 THEN 'Champions'
            WHEN recency_score >= 3 AND frequency_score >= 3 AND monetary_score >= 3 THEN 'Loyal Customers'
            WHEN recency_score >= 4 AND frequency_score <= 2 THEN 'New Customers'
            WHEN recency_score >= 3 AND frequency_score >= 3 AND monetary_score <= 2 THEN 'Potential Loyalists'
            WHEN recency_score <= 2 AND frequency_score >= 3 AND monetary_score >= 3 THEN 'At Risk'
            WHEN recency_score <= 2 AND frequency_score <= 2 AND monetary_score >= 3 THEN 'Cannot Lose Them'
            WHEN recency_score <= 2 AND frequency_score <= 2 AND monetary_score <= 2 THEN 'Hibernating'
            ELSE 'Others'
        END AS customer_segment
        
    FROM customer_segments
)

SELECT * FROM final