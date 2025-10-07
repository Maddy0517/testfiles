-- Product Performance Analysis
-- Analyze top performing products by various metrics

with order_items as (
    select * from {{ ref('int_order_items_enriched') }}
    where is_completed = true  -- Only completed orders
      and order_date >= date_sub(current_date(), interval 90 day)  -- Last 90 days
),

product_metrics as (
    select
        product_id,
        product_name,
        product_category,
        product_subcategory,
        brand,
        
        -- Sales Metrics
        count(distinct order_id) as orders_containing_product,
        sum(quantity) as total_quantity_sold,
        sum(net_amount_usd) as total_revenue_usd,
        sum(gross_margin_usd) as total_margin_usd,
        
        -- Average Metrics
        avg(unit_price) as avg_selling_price_usd,
        avg(discount_percentage) as avg_discount_given,
        avg(gross_margin_percentage) as avg_margin_percentage,
        
        -- Customer Metrics
        count(distinct customer_id) as unique_customers,
        
        -- Time-based Metrics
        min(order_date) as first_sold_date,
        max(order_date) as last_sold_date,
        count(distinct order_date) as days_with_sales

    from order_items
    group by 1, 2, 3, 4, 5
),

product_rankings as (
    select
        *,
        -- Rankings
        rank() over (order by total_revenue_usd desc) as revenue_rank,
        rank() over (order by total_quantity_sold desc) as volume_rank,
        rank() over (order by total_margin_usd desc) as margin_rank,
        rank() over (order by unique_customers desc) as popularity_rank,
        
        -- Percentiles
        percent_rank() over (order by total_revenue_usd) as revenue_percentile,
        
        -- Categories
        case
            when total_revenue_usd >= 100000 then 'Star Product'
            when total_revenue_usd >= 50000 then 'High Performer'
            when total_revenue_usd >= 10000 then 'Good Performer'
            when total_revenue_usd >= 1000 then 'Average Performer'
            else 'Low Performer'
        end as performance_category,
        
        -- Calculate sell-through rate (simplified)
        total_quantity_sold / nullif(days_with_sales, 0) as daily_velocity

    from product_metrics
)

select 
    product_id,
    product_name,
    product_category,
    brand,
    performance_category,
    
    -- Key Metrics
    total_revenue_usd,
    total_quantity_sold,
    total_margin_usd,
    unique_customers,
    
    -- Rankings
    revenue_rank,
    volume_rank,
    margin_rank,
    popularity_rank,
    
    -- Averages
    round(avg_selling_price_usd, 2) as avg_selling_price_usd,
    round(avg_discount_given * 100, 2) as avg_discount_percentage,
    round(avg_margin_percentage * 100, 2) as avg_margin_percentage,
    
    -- Velocity
    round(daily_velocity, 2) as units_sold_per_day

from product_rankings
where revenue_rank <= 100  -- Top 100 products by revenue
order by revenue_rank