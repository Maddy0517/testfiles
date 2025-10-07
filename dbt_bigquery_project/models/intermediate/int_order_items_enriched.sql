{{
  config(
    materialized = 'ephemeral',
    tags = ['intermediate', 'orders']
  )
}}

with orders as (
    select * from {{ ref('stg_orders') }}
),

order_items as (
    select * from {{ ref('stg_order_items') }}
),

products as (
    select * from {{ ref('stg_products') }}
),

exchange_rates as (
    select * from {{ ref('stg_exchange_rates') }}
),

joined as (
    select
        -- Order Item Details
        oi.order_item_id,
        oi.order_id,
        oi.product_id,
        oi.quantity,
        oi.unit_price,
        oi.discount_amount,
        oi.tax_amount,
        
        -- Order Details
        o.customer_id,
        o.order_date,
        o.order_status,
        o.currency_code,
        o.payment_method,
        o.payment_method_group,
        o.shipping_country,
        o.shipping_state,
        o.order_year,
        o.order_quarter,
        o.order_month,
        o.order_week,
        o.order_day_of_week,
        o.order_day_name,
        o.is_weekend_order,
        o.is_completed,
        o.is_cancelled,
        o.is_returned,
        
        -- Product Details
        p.product_name,
        p.product_category,
        p.product_subcategory,
        p.brand,
        p.product_cost,
        p.is_active_product,
        
        -- Line Item Calculations (in original currency)
        oi.quantity * oi.unit_price as gross_amount,
        oi.discount_amount as discount_amount_applied,
        (oi.quantity * oi.unit_price) - coalesce(oi.discount_amount, 0) as net_amount,
        coalesce(oi.tax_amount, 0) as tax_amount_applied,
        (oi.quantity * oi.unit_price) - coalesce(oi.discount_amount, 0) + coalesce(oi.tax_amount, 0) as total_amount,
        
        -- Margin Calculations (in original currency)
        oi.quantity * p.product_cost as total_cost,
        ((oi.quantity * oi.unit_price) - coalesce(oi.discount_amount, 0)) - (oi.quantity * p.product_cost) as gross_margin,
        
        -- Exchange Rate
        coalesce(er.exchange_rate, 1) as exchange_rate_to_usd

    from order_items oi
    left join orders o
        on oi.order_id = o.order_id
    left join products p
        on oi.product_id = p.product_id
    left join exchange_rates er
        on o.order_date = er.date
        and o.currency_code = er.target_currency
),

with_usd_amounts as (
    select
        *,
        -- USD Amounts
        gross_amount * exchange_rate_to_usd as gross_amount_usd,
        discount_amount_applied * exchange_rate_to_usd as discount_amount_usd,
        net_amount * exchange_rate_to_usd as net_amount_usd,
        tax_amount_applied * exchange_rate_to_usd as tax_amount_usd,
        total_amount * exchange_rate_to_usd as total_amount_usd,
        total_cost * exchange_rate_to_usd as total_cost_usd,
        gross_margin * exchange_rate_to_usd as gross_margin_usd,
        
        -- Margin Percentages
        safe_divide(gross_margin, net_amount) as gross_margin_percentage,
        
        -- Discount Percentage
        safe_divide(discount_amount_applied, gross_amount) as discount_percentage

    from joined
)

select * from with_usd_amounts