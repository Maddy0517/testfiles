"""
Example configuration file for the Cloud Function.
Copy this to config.py and update with your values, or use environment variables.
"""

# GCP Configuration
PROJECT_ID = "your-project-id"
DATASET_ID = "your_dataset"
TABLE_ID = "employee_data"

# File Processing Configuration
EMPLOYEE_ID_COLUMN = "employee_id"  # Column name in CSV files
CODE_PATTERN_REGEX = r'(HUM-\d{3})'  # Regex pattern to extract from filename

# BigQuery Configuration
BIGQUERY_WRITE_DISPOSITION = "WRITE_APPEND"  # Options: WRITE_APPEND, WRITE_TRUNCATE, WRITE_EMPTY
