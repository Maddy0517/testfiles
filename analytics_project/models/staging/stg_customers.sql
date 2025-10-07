{{ config(
    materialized='view',
    description='Cleaned and standardized customer data from raw source'
) }}

WITH source_data AS (
    SELECT * FROM {{ source('raw_data', 'customers') }}
),

cleaned_customers AS (
    SELECT
        -- Primary key
        customer_id,
        
        -- Personal information - clean and standardize
        LOWER(TRIM(customer_email)) AS customer_email,
        INITCAP(TRIM(first_name)) AS first_name,
        INITCAP(TRIM(last_name)) AS last_name,
        CONCAT(INITCAP(TRIM(first_name)), ' ', INITCAP(TRIM(last_name))) AS full_name,
        
        -- Dates
        CAST(registration_date AS DATE) AS registration_date,
        EXTRACT(YEAR FROM CAST(registration_date AS DATE)) AS registration_year,
        EXTRACT(MONTH FROM CAST(registration_date AS DATE)) AS registration_month,
        
        -- Segmentation
        COALESCE(UPPER(TRIM(customer_segment)), 'UNKNOWN') AS customer_segment,
        
        -- Derived fields
        DATE_DIFF(CURRENT_DATE(), CAST(registration_date AS DATE), DAY) AS days_since_registration,
        
        CASE 
            WHEN DATE_DIFF(CURRENT_DATE(), CAST(registration_date AS DATE), DAY) <= 30 THEN 'New'
            WHEN DATE_DIFF(CURRENT_DATE(), CAST(registration_date AS DATE), DAY) <= 365 THEN 'Active'
            ELSE 'Veteran'
        END AS customer_tenure_group,
        
        -- Email domain analysis
        REGEXP_EXTRACT(LOWER(TRIM(customer_email)), r'@(.+)') AS email_domain,
        
        CASE 
            WHEN REGEXP_EXTRACT(LOWER(TRIM(customer_email)), r'@(.+)') IN ('gmail.com', 'yahoo.com', 'hotmail.com', 'outlook.com') 
            THEN 'Personal'
            ELSE 'Business'
        END AS email_type,
        
        -- Audit fields
        CURRENT_TIMESTAMP() AS dbt_loaded_at,
        CURRENT_DATE() AS dbt_loaded_date
        
    FROM source_data
    
    -- Data quality filters
    WHERE 
        customer_id IS NOT NULL
        AND customer_email IS NOT NULL
        AND customer_email LIKE '%@%'  -- Basic email validation
        AND registration_date IS NOT NULL
)

SELECT * FROM cleaned_customers