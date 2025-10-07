{{
  config(
    materialized = 'view',
    tags = ['staging', 'orders']
  )
}}

with source as (
    select * from {{ source('ecommerce_raw', 'orders') }}
),

renamed as (
    select
        -- Primary and Foreign Keys
        order_id,
        customer_id,
        
        -- Order Details
        cast(order_date as date) as order_date,
        upper(status) as order_status,
        upper(currency) as currency_code,
        lower(payment_method) as payment_method,
        
        -- Amounts (convert from cents to dollars)
        cast(total_amount as numeric) / 100 as order_amount_base_currency,
        
        -- Shipping Information
        json_extract_scalar(shipping_address, '$.country') as shipping_country,
        json_extract_scalar(shipping_address, '$.state') as shipping_state,
        json_extract_scalar(shipping_address, '$.city') as shipping_city,
        json_extract_scalar(shipping_address, '$.postal_code') as shipping_postal_code,
        
        -- Timestamps
        cast(created_at as timestamp) as order_created_at,
        cast(updated_at as timestamp) as order_updated_at,
        
        -- Calculated Fields
        date_diff(current_date(), cast(order_date as date), day) as days_since_order,
        extract(year from order_date) as order_year,
        extract(quarter from order_date) as order_quarter,
        extract(month from order_date) as order_month,
        extract(week from order_date) as order_week,
        extract(dayofweek from order_date) as order_day_of_week,
        format_date('%A', cast(order_date as date)) as order_day_name,
        
        -- Status Flags
        case when status in ('delivered', 'shipped') then true else false end as is_completed,
        case when status = 'cancelled' then true else false end as is_cancelled,
        case when status = 'returned' then true else false end as is_returned

    from source
    where order_id is not null
      and customer_id is not null
      {% if target.name == 'dev' %}
      -- In dev, limit to recent data
      and order_date >= date_sub(current_date(), interval {{ var('max_days_back') }} day)
      {% endif %}
),

add_order_attributes as (
    select
        *,
        -- Order value buckets
        case
            when order_amount_base_currency < 50 then '< $50'
            when order_amount_base_currency < 100 then '$50 - $100'
            when order_amount_base_currency < 250 then '$100 - $250'
            when order_amount_base_currency < 500 then '$250 - $500'
            when order_amount_base_currency < 1000 then '$500 - $1000'
            else '$1000+'
        end as order_value_bucket,
        
        -- Payment method grouping
        case
            when payment_method in ('credit_card', 'debit_card') then 'Card'
            when payment_method in ('paypal', 'stripe') then 'Digital Wallet'
            when payment_method = 'bank_transfer' then 'Bank Transfer'
            else 'Other'
        end as payment_method_group,
        
        -- Weekend flag
        case
            when extract(dayofweek from order_date) in (1, 7) then true
            else false
        end as is_weekend_order

    from renamed
)

select * from add_order_attributes