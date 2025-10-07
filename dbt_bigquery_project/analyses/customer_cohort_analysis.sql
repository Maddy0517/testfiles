-- Customer Cohort Analysis
-- This analysis shows customer retention by monthly cohorts

with customers as (
    select * from {{ ref('dim_customers') }}
),

orders as (
    select * from {{ ref('fct_orders') }}
),

-- Create monthly cohorts based on first order date
cohorts as (
    select
        c.customer_id,
        date_trunc(c.first_order_date, month) as cohort_month,
        c.customer_value_tier
    from customers c
    where c.first_order_date is not null
),

-- Calculate months since cohort for each order
cohort_orders as (
    select
        c.customer_id,
        c.cohort_month,
        c.customer_value_tier,
        date_trunc(o.order_date, month) as order_month,
        date_diff(date_trunc(o.order_date, month), c.cohort_month, month) as months_since_cohort
    from cohorts c
    inner join orders o on c.customer_id = o.customer_id
),

-- Calculate retention metrics
retention_calc as (
    select
        cohort_month,
        months_since_cohort,
        count(distinct customer_id) as customers_active,
        count(distinct order_id) as orders_placed,
        sum(total_revenue_usd) as revenue_generated
    from cohort_orders
    group by 1, 2
),

-- Add cohort size for percentage calculations
cohort_sizes as (
    select
        cohort_month,
        count(distinct customer_id) as cohort_size
    from cohorts
    group by 1
),

-- Final retention table
retention_analysis as (
    select
        r.cohort_month,
        format_date('%Y-%m', r.cohort_month) as cohort_month_name,
        c.cohort_size,
        r.months_since_cohort,
        r.customers_active,
        round(r.customers_active * 100.0 / c.cohort_size, 2) as retention_rate,
        r.orders_placed,
        round(r.revenue_generated, 2) as revenue_generated,
        round(r.revenue_generated / nullif(r.customers_active, 0), 2) as revenue_per_customer
    from retention_calc r
    join cohort_sizes c on r.cohort_month = c.cohort_month
    where r.months_since_cohort <= 12  -- Look at first 12 months
    order by 1, 4
)

select * from retention_analysis

-- Pivot this data in your BI tool to create a cohort retention matrix