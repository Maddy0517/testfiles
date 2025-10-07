-- Test that all orders have at least one order item
-- This ensures referential integrity between orders and order items

select 
    o.order_id,
    o.order_date,
    o.customer_id
from {{ ref('fct_orders') }} o
left join {{ ref('stg_order_items') }} oi
    on o.order_id = oi.order_id
where oi.order_id is null