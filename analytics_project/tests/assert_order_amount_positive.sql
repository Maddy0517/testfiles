-- Test to ensure all order amounts are positive
SELECT *
FROM {{ ref('stg_orders') }}
WHERE order_amount_dollars <= 0