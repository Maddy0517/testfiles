{{
  config(
    materialized = 'view',
    tags = ['staging', 'orders']
  )
}}

with source as (
    select * from {{ source('ecommerce_raw', 'order_items') }}
),

renamed as (
    select
        -- Primary and Foreign Keys
        order_item_id,
        order_id,
        product_id,
        
        -- Item Details
        quantity,
        cast(unit_price as numeric) / 100 as unit_price,
        cast(discount_amount as numeric) / 100 as discount_amount,
        cast(tax_amount as numeric) / 100 as tax_amount,
        
        -- Calculated Fields
        quantity * cast(unit_price as numeric) / 100 as line_total,
        cast(discount_amount as numeric) / (nullif(cast(unit_price as numeric) * quantity, 0)) as discount_rate

    from source
    where order_item_id is not null
      and order_id is not null
      and product_id is not null
      and quantity > 0
)

select * from renamed