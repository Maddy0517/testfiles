{% macro test_not_negative(model, column_name) %}
    {#
        Test that a numeric column contains no negative values
        Usage in schema.yml:
        tests:
          - not_negative
    #}
    
    select *
    from {{ model }}
    where {{ column_name }} < 0

{% endmacro %}


{% macro test_valid_email(model, column_name) %}
    {#
        Test that email addresses follow a valid format
    #}
    
    select *
    from {{ model }}
    where not regexp_contains({{ column_name }}, r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$')
    
{% endmacro %}


{% macro test_recent_data(model, date_column, days_back=7) %}
    {#
        Test that we have recent data (within the specified number of days)
    #}
    
    select
        case
            when count(*) = 0 then 1
            else 0
        end as test_result
    from {{ model }}
    where {{ date_column }} >= date_sub(current_date(), interval {{ days_back }} day)
    having test_result = 1

{% endmacro %}


{% macro test_column_values_in_set(model, column_name, values) %}
    {#
        Test that all values in a column are within a specified set
        Similar to accepted_values but as a custom test
    #}
    
    select *
    from {{ model }}
    where {{ column_name }} not in ({{ values | join(', ') }})
    
{% endmacro %}