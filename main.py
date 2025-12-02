"""
Google Cloud Function to process file uploads from GCS to BigQuery.

This function:
1. Triggers when a file is uploaded to a GCS bucket
2. Extracts pattern from filename (e.g., HUM-100, HUM-200)
3. Reads employee_id from the file data
4. Inserts records into BigQuery with employee_id, upload_date, file_name, and extracted_pattern
"""

import json
import re
import logging
from datetime import datetime
from typing import Dict, Any

from google.cloud import bigquery
from google.cloud import storage
from google.cloud.functions_v1.context import Context

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Initialize BigQuery client
bq_client = bigquery.Client()

# Configuration - Update these values
PROJECT_ID = "your-project-id"  # Update with your GCP project ID
DATASET_ID = "your_dataset"  # Update with your BigQuery dataset ID
TABLE_ID = "employee_uploads"  # Update with your BigQuery table ID
EMPLOYEE_ID_COLUMN = "employee_id"  # Column name in the file that contains employee_id


def extract_pattern_from_filename(filename: str) -> str:
    """
    Extract pattern from filename (e.g., HUM-100, HUM-200, HUM-300).
    
    Args:
        filename: The name of the uploaded file
        
    Returns:
        Extracted pattern or None if not found
    """
    # Pattern to match HUM-XXX format (case insensitive)
    pattern = r'(HUM-\d+)'
    match = re.search(pattern, filename, re.IGNORECASE)
    
    if match:
        return match.group(1).upper()  # Return uppercase version
    
    # If no match found, you can add more patterns here
    # Example: pattern = r'([A-Z]{3}-\d+)'  # Matches any 3 letters followed by dash and numbers
    
    logger.warning(f"No pattern found in filename: {filename}")
    return None


def read_file_from_gcs(bucket_name: str, file_name: str) -> str:
    """
    Read file content from GCS bucket.
    
    Args:
        bucket_name: Name of the GCS bucket
        file_name: Name of the file in the bucket
        
    Returns:
        File content as string
    """
    storage_client = storage.Client()
    bucket = storage_client.bucket(bucket_name)
    blob = bucket.blob(file_name)
    
    return blob.download_as_text()


def parse_file_content(file_content: str, file_name: str) -> list:
    """
    Parse file content and extract employee_id values.
    
    Supports CSV and text files. Assumes employee_id is in a column.
    For CSV: expects header row with employee_id column
    For text: assumes first column or space-separated values
    
    Args:
        file_content: Content of the file as string
        file_name: Name of the file (to determine format)
        
    Returns:
        List of employee_id values
    """
    employee_ids = []
    lines = file_content.strip().split('\n')
    
    if not lines:
        logger.warning(f"File {file_name} is empty")
        return employee_ids
    
    # Check if CSV format (has header)
    first_line = lines[0].lower()
    is_csv = EMPLOYEE_ID_COLUMN.lower() in first_line
    
    if is_csv:
        # CSV format - find column index
        headers = [h.strip() for h in lines[0].split(',')]
        try:
            emp_id_index = headers.index(EMPLOYEE_ID_COLUMN.lower())
        except ValueError:
            # Try case-sensitive match
            headers = [h.strip() for h in lines[0].split(',')]
            try:
                emp_id_index = headers.index(EMPLOYEE_ID_COLUMN)
            except ValueError:
                logger.error(f"Column '{EMPLOYEE_ID_COLUMN}' not found in file {file_name}")
                return employee_ids
        
        # Extract employee_ids from data rows
        for line in lines[1:]:
            if line.strip():
                values = [v.strip() for v in line.split(',')]
                if len(values) > emp_id_index:
                    emp_id = values[emp_id_index]
                    if emp_id:  # Skip empty values
                        employee_ids.append(emp_id)
    else:
        # Text format - assume first column or space-separated
        for line in lines:
            if line.strip():
                # Try space-separated first
                parts = line.strip().split()
                if parts:
                    employee_ids.append(parts[0])
    
    logger.info(f"Extracted {len(employee_ids)} employee IDs from file {file_name}")
    return employee_ids


def insert_to_bigquery(
    employee_ids: list,
    file_name: str,
    upload_date: str,
    extracted_pattern: str,
    dataset_id: str,
    table_id: str
) -> None:
    """
    Insert records into BigQuery table.
    
    Args:
        employee_ids: List of employee_id values from the file
        file_name: Name of the uploaded file
        upload_date: Date when file was uploaded (ISO format)
        extracted_pattern: Pattern extracted from filename (e.g., HUM-100)
        dataset_id: BigQuery dataset ID
        table_id: BigQuery table ID
    """
    if not employee_ids:
        logger.warning(f"No employee IDs to insert for file {file_name}")
        return
    
    # Prepare rows for insertion
    rows_to_insert = []
    for emp_id in employee_ids:
        rows_to_insert.append({
            'employee_id': emp_id,
            'upload_date': upload_date,
            'file_name': file_name,
            'extracted_pattern': extracted_pattern
        })
    
    # Get table reference
    table_ref = bq_client.dataset(dataset_id).table(table_id)
    table = bq_client.get_table(table_ref)
    
    # Insert rows
    errors = bq_client.insert_rows_json(table, rows_to_insert)
    
    if errors:
        logger.error(f"Errors inserting rows: {errors}")
        raise Exception(f"Failed to insert rows: {errors}")
    else:
        logger.info(f"Successfully inserted {len(rows_to_insert)} rows into BigQuery")


def create_table_if_not_exists(dataset_id: str, table_id: str) -> None:
    """
    Create BigQuery table if it doesn't exist.
    
    Args:
        dataset_id: BigQuery dataset ID
        table_id: BigQuery table ID
    """
    dataset_ref = bq_client.dataset(dataset_id)
    table_ref = dataset_ref.table(table_id)
    
    try:
        bq_client.get_table(table_ref)
        logger.info(f"Table {dataset_id}.{table_id} already exists")
    except Exception:
        # Table doesn't exist, create it
        schema = [
            bigquery.SchemaField("employee_id", "STRING", mode="REQUIRED"),
            bigquery.SchemaField("upload_date", "DATE", mode="REQUIRED"),
            bigquery.SchemaField("file_name", "STRING", mode="REQUIRED"),
            bigquery.SchemaField("extracted_pattern", "STRING", mode="NULLABLE"),
        ]
        
        table = bigquery.Table(table_ref, schema=schema)
        table = bq_client.create_table(table)
        logger.info(f"Created table {dataset_id}.{table_id}")


def process_file_upload(event: Dict[str, Any], context: Context) -> None:
    """
    Cloud Function entry point triggered by GCS file upload.
    
    Args:
        event: Event payload containing GCS file information
        context: Cloud Function context
    """
    try:
        # Extract file information from event
        bucket_name = event.get('bucket')
        file_name = event.get('name')
        
        if not bucket_name or not file_name:
            logger.error("Missing bucket or file name in event")
            return
        
        logger.info(f"Processing file: gs://{bucket_name}/{file_name}")
        
        # Extract pattern from filename
        extracted_pattern = extract_pattern_from_filename(file_name)
        
        # Get upload date (current date)
        upload_date = datetime.now().date().isoformat()
        
        # Read file from GCS
        file_content = read_file_from_gcs(bucket_name, file_name)
        
        # Parse file and extract employee_ids
        employee_ids = parse_file_content(file_content, file_name)
        
        if not employee_ids:
            logger.warning(f"No employee IDs found in file {file_name}")
            return
        
        # Ensure table exists
        create_table_if_not_exists(DATASET_ID, TABLE_ID)
        
        # Insert data into BigQuery
        insert_to_bigquery(
            employee_ids=employee_ids,
            file_name=file_name,
            upload_date=upload_date,
            extracted_pattern=extracted_pattern,
            dataset_id=DATASET_ID,
            table_id=TABLE_ID
        )
        
        logger.info(f"Successfully processed file {file_name}")
        
    except Exception as e:
        logger.error(f"Error processing file upload: {str(e)}", exc_info=True)
        raise


# For local testing
if __name__ == "__main__":
    # Example test event
    test_event = {
        'bucket': 'your-bucket-name',
        'name': 'data_HUM-100_20240101.csv'
    }
    
    class TestContext:
        event_id = "test-event-id"
        timestamp = datetime.now().isoformat()
        event_type = "google.storage.object.finalize"
        resource = {
            'name': test_event['name'],
            'service': 'storage.googleapis.com'
        }
    
    process_file_upload(test_event, TestContext())
