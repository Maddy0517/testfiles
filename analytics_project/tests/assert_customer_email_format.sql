-- Test to ensure all customer emails have valid format
SELECT *
FROM {{ ref('stg_customers') }}
WHERE customer_email IS NULL 
   OR customer_email = ''
   OR NOT REGEXP_CONTAINS(customer_email, r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$')