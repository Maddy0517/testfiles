{% macro cents_to_dollars(column_name, precision=2) %}
    ROUND(CAST({{ column_name }} AS NUMERIC) / 100, {{ precision }})
{% endmacro %}