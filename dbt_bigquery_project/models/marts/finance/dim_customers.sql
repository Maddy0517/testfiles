{{
  config(
    materialized = 'table',
    cluster_by = ['customer_segment', 'country_code'],
    tags = ['marts', 'finance', 'customers']
  )
}}

with customers as (
    select * from {{ ref('stg_customers') }}
),

customer_orders as (
    select * from {{ ref('fct_orders') }}
    where is_cancelled = false
      and is_returned = false
),

customer_order_stats as (
    select
        customer_id,
        
        -- Order Counts
        count(distinct order_id) as total_orders,
        count(distinct case when is_completed then order_id end) as completed_orders,
        
        -- Date Metrics
        min(order_date) as first_order_date,
        max(order_date) as last_order_date,
        date_diff(max(order_date), min(order_date), day) as customer_lifespan_days,
        date_diff(current_date(), max(order_date), day) as days_since_last_order,
        
        -- Financial Metrics
        sum(total_revenue_usd) as lifetime_revenue_usd,
        sum(gross_margin_usd) as lifetime_margin_usd,
        sum(total_discount_usd) as lifetime_discounts_usd,
        avg(total_revenue_usd) as avg_order_value_usd,
        
        -- Product Metrics
        sum(total_quantity) as total_items_purchased,
        count(distinct product_categories) as unique_categories_purchased,
        
        -- Behavioral Metrics
        countif(is_weekend_order) as weekend_orders,
        mode() within group (order by payment_method_group) as preferred_payment_method,
        mode() within group (order by shipping_country) as primary_shipping_country

    from customer_orders
    group by 1
),

rfm_calc as (
    select
        customer_id,
        
        -- RFM Components
        days_since_last_order as recency,
        total_orders as frequency,
        lifetime_revenue_usd as monetary,
        
        -- RFM Scoring (1-5 scale, 5 being best)
        ntile(5) over (order by days_since_last_order desc) as recency_score,
        ntile(5) over (order by total_orders) as frequency_score,
        ntile(5) over (order by lifetime_revenue_usd) as monetary_score

    from customer_order_stats
),

customer_segments as (
    select
        customer_id,
        recency,
        frequency,
        monetary,
        recency_score,
        frequency_score,
        monetary_score,
        
        -- RFM Combined Score
        cast(recency_score as string) || cast(frequency_score as string) || cast(monetary_score as string) as rfm_score,
        
        -- Customer Segment based on RFM
        case
            when recency_score >= 4 and frequency_score >= 4 and monetary_score >= 4 then 'Champions'
            when recency_score >= 3 and frequency_score >= 4 and monetary_score >= 4 then 'Loyal Customers'
            when recency_score >= 3 and frequency_score >= 3 and monetary_score >= 3 then 'Potential Loyalists'
            when recency_score >= 4 and frequency_score <= 2 then 'New Customers'
            when recency_score >= 3 and frequency_score <= 2 then 'Promising'
            when recency_score >= 2 and frequency_score >= 3 and monetary_score >= 3 then 'Need Attention'
            when recency_score <= 2 and frequency_score >= 3 then 'At Risk'
            when recency_score <= 2 and monetary_score >= 4 then 'Cant Lose Them'
            when recency_score <= 2 and frequency_score >= 2 and monetary_score >= 2 then 'Hibernating'
            else 'Lost'
        end as rfm_segment

    from rfm_calc
),

final as (
    select
        -- Customer Attributes
        c.customer_id,
        c.email,
        c.first_name,
        c.last_name,
        c.full_name,
        c.customer_segment as original_segment,
        c.country_code,
        c.state_province,
        c.city,
        c.customer_created_at,
        c.days_since_signup,
        c.customer_tenure_bucket,
        c.email_domain,
        c.is_business_email,
        c.is_high_value_customer,
        
        -- Order Statistics
        coalesce(cos.total_orders, 0) as total_orders,
        coalesce(cos.completed_orders, 0) as completed_orders,
        cos.first_order_date,
        cos.last_order_date,
        coalesce(cos.customer_lifespan_days, 0) as customer_lifespan_days,
        coalesce(cos.days_since_last_order, 999) as days_since_last_order,
        
        -- Financial Metrics
        coalesce(cos.lifetime_revenue_usd, 0) as lifetime_revenue_usd,
        coalesce(cos.lifetime_margin_usd, 0) as lifetime_margin_usd,
        coalesce(cos.lifetime_discounts_usd, 0) as lifetime_discounts_usd,
        coalesce(cos.avg_order_value_usd, 0) as avg_order_value_usd,
        
        -- Product Metrics
        coalesce(cos.total_items_purchased, 0) as total_items_purchased,
        coalesce(cos.unique_categories_purchased, 0) as unique_categories_purchased,
        
        -- Behavioral Metrics
        coalesce(cos.weekend_orders, 0) as weekend_orders,
        cos.preferred_payment_method,
        cos.primary_shipping_country,
        
        -- RFM Analysis
        cs.recency_score,
        cs.frequency_score,
        cs.monetary_score,
        cs.rfm_score,
        cs.rfm_segment,
        
        -- Churn Prediction
        case
            when cos.days_since_last_order > {{ var('churn_days_threshold') }} then true
            when cos.days_since_last_order is null and c.days_since_signup > {{ var('churn_days_threshold') }} then true
            else false
        end as is_churned,
        
        -- Customer Value Tier
        case
            when cos.lifetime_revenue_usd >= 10000 then 'Platinum'
            when cos.lifetime_revenue_usd >= 5000 then 'Gold'
            when cos.lifetime_revenue_usd >= 1000 then 'Silver'
            when cos.lifetime_revenue_usd >= 100 then 'Bronze'
            else 'Prospect'
        end as customer_value_tier,
        
        -- Metadata
        current_timestamp() as dbt_updated_at

    from customers c
    left join customer_order_stats cos
        on c.customer_id = cos.customer_id
    left join customer_segments cs
        on c.customer_id = cs.customer_id
)

select * from final