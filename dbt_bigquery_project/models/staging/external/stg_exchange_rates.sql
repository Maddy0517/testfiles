{{
  config(
    materialized = 'view',
    tags = ['staging', 'external']
  )
}}

with source as (
    select * from {{ source('external_apis', 'exchange_rates') }}
),

renamed as (
    select
        cast(date as date) as date,
        upper(base_currency) as base_currency,
        upper(target_currency) as target_currency,
        cast(exchange_rate as numeric) as exchange_rate,
        cast(loaded_at as timestamp) as loaded_at,
        
        -- Add inverse rate for convenience
        1 / nullif(cast(exchange_rate as numeric), 0) as inverse_rate

    from source
    where date is not null
      and base_currency = 'USD'  -- We standardize on USD as base
      and exchange_rate > 0
),

-- Add missing USD to USD rate
with_usd_rate as (
    select * from renamed
    
    union all
    
    select distinct
        date,
        'USD' as base_currency,
        'USD' as target_currency,
        1.0 as exchange_rate,
        loaded_at,
        1.0 as inverse_rate
    from renamed
)

select * from with_usd_rate