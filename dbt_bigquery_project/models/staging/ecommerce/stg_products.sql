{{
  config(
    materialized = 'view',
    tags = ['staging', 'products']
  )
}}

with source as (
    select * from {{ source('ecommerce_raw', 'products') }}
),

renamed as (
    select
        -- Primary Key
        product_id,
        
        -- Product Identifiers
        upper(trim(sku)) as sku,
        trim(product_name) as product_name,
        
        -- Category Hierarchy
        initcap(trim(category)) as product_category,
        initcap(trim(subcategory)) as product_subcategory,
        initcap(trim(brand)) as brand,
        
        -- Pricing (convert from cents to dollars)
        cast(price as numeric) / 100 as product_price,
        cast(cost as numeric) / 100 as product_cost,
        
        -- Product Attributes
        cast(weight as numeric) as weight_grams,
        cast(weight as numeric) / 1000 as weight_kg,
        
        -- Status
        case 
            when lower(is_active) in ('true', 't', 'yes', 'y', '1') then true
            else false
        end as is_active_product,
        
        -- Timestamps
        cast(created_at as timestamp) as product_created_at,
        cast(updated_at as timestamp) as product_updated_at,
        
        -- Calculated Fields
        (cast(price as numeric) - cast(cost as numeric)) / 100 as unit_margin,
        safe_divide(cast(price as numeric) - cast(cost as numeric), cast(price as numeric)) as margin_percentage

    from source
    where product_id is not null
      and sku is not null
)

select * from renamed