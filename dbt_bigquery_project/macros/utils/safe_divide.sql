{% macro safe_divide(numerator, denominator) %}
    {#
        Safely divide two numbers, returning null if denominator is zero or null
        Usage: {{ safe_divide('revenue', 'quantity') }}
    #}
    safe_divide({{ numerator }}, {{ denominator }})
{% endmacro %}


{% macro calculate_percentage(numerator, denominator, decimal_places=2) %}
    {#
        Calculate percentage with safe division
        Usage: {{ calculate_percentage('completed_orders', 'total_orders') }}
    #}
    round({{ safe_divide(numerator, denominator) }} * 100, {{ decimal_places }})
{% endmacro %}


{% macro cents_to_dollars(column_name, decimal_places=2) %}
    {#
        Convert cents to dollars
        Usage: {{ cents_to_dollars('price_cents') }}
    #}
    round(cast({{ column_name }} as numeric) / 100, {{ decimal_places }})
{% endmacro %}