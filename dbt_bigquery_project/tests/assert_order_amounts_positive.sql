-- Test that all order amounts are positive
-- This test will fail if any orders have negative or zero amounts

select 
    order_id,
    total_revenue_usd,
    order_date,
    order_status
from {{ ref('fct_orders') }}
where total_revenue_usd <= 0
  and order_status not in ('CANCELLED', 'RETURNED')  -- Cancelled/returned orders might have adjustments