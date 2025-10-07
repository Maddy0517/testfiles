{{
  config(
    materialized = 'view',
    tags = ['staging', 'customers']
  )
}}

with source as (
    select * from {{ source('ecommerce_raw', 'customers') }}
),

renamed as (
    select
        -- Primary Key
        customer_id,
        
        -- Customer Details
        lower(trim(email)) as email,
        initcap(trim(first_name)) as first_name,
        initcap(trim(last_name)) as last_name,
        concat(initcap(trim(first_name)), ' ', initcap(trim(last_name))) as full_name,
        
        -- Segmentation
        upper(customer_segment) as customer_segment,
        cast(customer_lifetime_value as numeric) / 100 as customer_lifetime_value_usd,
        
        -- Location
        upper(country) as country_code,
        initcap(state) as state_province,
        initcap(city) as city,
        
        -- Timestamps
        cast(created_at as timestamp) as customer_created_at,
        cast(updated_at as timestamp) as customer_updated_at,
        
        -- Calculated Fields
        date_diff(current_date(), date(created_at), day) as days_since_signup,
        case 
            when customer_lifetime_value > {{ var('high_value_customer_threshold') }} * 100 then true
            else false
        end as is_high_value_customer

    from source
    where customer_id is not null
      and email is not null
      -- In dev, optionally sample data for faster iteration
      {% if target.name == 'dev' %}
      and date(created_at) >= date_sub(current_date(), interval {{ var('max_days_back') }} day)
      {% endif %}
),

add_customer_attributes as (
    select
        *,
        -- Customer tenure buckets
        case
            when days_since_signup < 30 then '0-30 days'
            when days_since_signup < 90 then '31-90 days'
            when days_since_signup < 180 then '91-180 days'
            when days_since_signup < 365 then '181-365 days'
            else '365+ days'
        end as customer_tenure_bucket,
        
        -- Email domain for B2B analysis
        split(email, '@')[safe_offset(1)] as email_domain,
        
        -- Check if business email (simple heuristic)
        case
            when email like '%gmail.com' 
              or email like '%yahoo.com'
              or email like '%hotmail.com'
              or email like '%outlook.com'
            then false
            else true
        end as is_business_email

    from renamed
)

select * from add_customer_attributes