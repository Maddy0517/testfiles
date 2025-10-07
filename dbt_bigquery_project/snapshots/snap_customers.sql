{% snapshot snap_customers %}

    {{
        config(
          target_dataset='snapshots',
          unique_key='customer_id',
          strategy='timestamp',
          updated_at='customer_updated_at',
          invalidate_hard_deletes=True
        )
    }}

    select 
        customer_id,
        email,
        first_name,
        last_name,
        customer_segment,
        customer_lifetime_value_usd,
        country_code,
        state_province,
        city,
        is_high_value_customer,
        customer_created_at,
        customer_updated_at
    from {{ ref('stg_customers') }}

{% endsnapshot %}