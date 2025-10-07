{{
  config(
    materialized = 'incremental',
    unique_key = 'order_id',
    partition_by = {
      "field": "order_date",
      "data_type": "date",
      "granularity": "day"
    },
    cluster_by = ['customer_id', 'order_status'],
    tags = ['marts', 'finance', 'orders'],
    on_schema_change = 'sync_all_columns'
  )
}}

with order_items as (
    select * from {{ ref('int_order_items_enriched') }}
    {% if is_incremental() %}
    -- Only process orders updated in the last N days for incremental runs
    where order_date >= date_sub(current_date(), interval {{ var('incremental_lookback') }} day)
    {% endif %}
),

order_aggregates as (
    select
        order_id,
        customer_id,
        order_date,
        order_status,
        currency_code,
        payment_method,
        payment_method_group,
        shipping_country,
        shipping_state,
        order_year,
        order_quarter,
        order_month,
        order_week,
        order_day_of_week,
        order_day_name,
        is_weekend_order,
        is_completed,
        is_cancelled,
        is_returned,
        
        -- Order Metrics
        count(distinct order_item_id) as item_count,
        count(distinct product_id) as unique_product_count,
        sum(quantity) as total_quantity,
        
        -- Financial Metrics (USD)
        sum(gross_amount_usd) as gross_revenue_usd,
        sum(discount_amount_usd) as total_discount_usd,
        sum(net_amount_usd) as net_revenue_usd,
        sum(tax_amount_usd) as total_tax_usd,
        sum(total_amount_usd) as total_revenue_usd,
        sum(total_cost_usd) as total_cost_usd,
        sum(gross_margin_usd) as gross_margin_usd,
        
        -- Average Metrics
        avg(unit_price * exchange_rate_to_usd) as avg_unit_price_usd,
        avg(discount_percentage) as avg_discount_percentage,
        avg(gross_margin_percentage) as avg_margin_percentage,
        
        -- Product Categories in Order
        string_agg(distinct product_category, ', ' order by product_category) as product_categories,
        string_agg(distinct brand, ', ' order by brand) as brands_purchased,
        
        -- Exchange Rate (for reference)
        max(exchange_rate_to_usd) as exchange_rate_used

    from order_items
    group by 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19
),

add_order_metrics as (
    select
        *,
        -- Additional Calculated Metrics
        safe_divide(gross_margin_usd, net_revenue_usd) as order_margin_percentage,
        safe_divide(total_discount_usd, gross_revenue_usd) as order_discount_percentage,
        net_revenue_usd / nullif(item_count, 0) as avg_item_value_usd,
        
        -- Order Size Classification
        case
            when total_revenue_usd < 100 then 'Small'
            when total_revenue_usd < 500 then 'Medium'
            when total_revenue_usd < 1000 then 'Large'
            else 'Extra Large'
        end as order_size_category,
        
        -- Profitability Flag
        case
            when gross_margin_usd > 0 then 'Profitable'
            when gross_margin_usd = 0 then 'Break Even'
            else 'Loss'
        end as profitability_status,
        
        -- Current timestamp for tracking
        current_timestamp() as dbt_updated_at

    from order_aggregates
)

select * from add_order_metrics