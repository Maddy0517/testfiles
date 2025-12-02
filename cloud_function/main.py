"""
Google Cloud Function to process file uploads from GCS to BigQuery.

This function:
1. Triggers when a file is uploaded to a GCS bucket
2. Extracts information from the filename (e.g., HUM-100, HUM-200)
3. Reads employee_id from the file content
4. Loads the data into a BigQuery table

Example filename formats:
- employee_data_HUM-100_2024.csv
- report_HUM-200_monthly.csv
- HUM-300_employees.csv
"""

import os
import re
import csv
import json
import logging
from datetime import datetime
from io import StringIO

from google.cloud import storage
from google.cloud import bigquery

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Configuration - Set these as environment variables or modify directly
PROJECT_ID = os.environ.get('GCP_PROJECT_ID', 'your-project-id')
DATASET_ID = os.environ.get('BQ_DATASET_ID', 'your_dataset')
TABLE_ID = os.environ.get('BQ_TABLE_ID', 'file_uploads')

# Pattern to extract codes like HUM-100, HUM-200, HUM-300 from filename
HUM_CODE_PATTERN = r'(HUM-\d+)'


def extract_hum_code(filename: str) -> str:
    """
    Extract HUM code (e.g., HUM-100, HUM-200) from filename.
    
    Args:
        filename: The name of the uploaded file
        
    Returns:
        The extracted HUM code or 'UNKNOWN' if not found
    """
    match = re.search(HUM_CODE_PATTERN, filename, re.IGNORECASE)
    if match:
        return match.group(1).upper()
    return 'UNKNOWN'


def read_file_content(bucket_name: str, file_name: str) -> str:
    """
    Read file content from GCS bucket.
    
    Args:
        bucket_name: Name of the GCS bucket
        file_name: Name of the file to read
        
    Returns:
        File content as string
    """
    storage_client = storage.Client()
    bucket = storage_client.bucket(bucket_name)
    blob = bucket.blob(file_name)
    
    content = blob.download_as_text()
    return content


def extract_employee_ids_from_csv(content: str, employee_id_column: str = 'employee_id') -> list:
    """
    Extract employee IDs from CSV file content.
    
    Args:
        content: CSV file content as string
        employee_id_column: Name of the column containing employee IDs
        
    Returns:
        List of employee IDs
    """
    employee_ids = []
    
    try:
        # Use StringIO to treat string as file
        csv_file = StringIO(content)
        reader = csv.DictReader(csv_file)
        
        # Try to find employee_id column (case-insensitive)
        for row in reader:
            # Find the employee_id column (case-insensitive matching)
            emp_id = None
            for key in row.keys():
                if key.lower().replace(' ', '_').replace('-', '_') in [
                    'employee_id', 'employeeid', 'emp_id', 'empid', 'id'
                ]:
                    emp_id = row[key]
                    break
            
            if emp_id:
                employee_ids.append(str(emp_id).strip())
                
    except Exception as e:
        logger.error(f"Error parsing CSV: {e}")
        
    return employee_ids


def extract_employee_ids_from_json(content: str) -> list:
    """
    Extract employee IDs from JSON file content.
    
    Args:
        content: JSON file content as string
        
    Returns:
        List of employee IDs
    """
    employee_ids = []
    
    try:
        data = json.loads(content)
        
        # Handle both list and dict formats
        if isinstance(data, list):
            records = data
        elif isinstance(data, dict):
            # Try common keys for list of records
            records = data.get('employees', data.get('data', data.get('records', [data])))
        else:
            records = []
        
        for record in records:
            if isinstance(record, dict):
                # Try to find employee_id field
                for key in ['employee_id', 'employeeId', 'emp_id', 'empId', 'id']:
                    if key in record:
                        employee_ids.append(str(record[key]).strip())
                        break
                        
    except Exception as e:
        logger.error(f"Error parsing JSON: {e}")
        
    return employee_ids


def extract_employee_ids(content: str, file_name: str) -> list:
    """
    Extract employee IDs from file content based on file type.
    
    Args:
        content: File content as string
        file_name: Name of the file (to determine format)
        
    Returns:
        List of employee IDs
    """
    file_extension = file_name.lower().split('.')[-1]
    
    if file_extension == 'csv':
        return extract_employee_ids_from_csv(content)
    elif file_extension == 'json':
        return extract_employee_ids_from_json(content)
    else:
        # Try CSV first, then JSON
        employee_ids = extract_employee_ids_from_csv(content)
        if not employee_ids:
            employee_ids = extract_employee_ids_from_json(content)
        return employee_ids


def insert_to_bigquery(rows: list) -> int:
    """
    Insert rows into BigQuery table.
    
    Args:
        rows: List of dictionaries containing row data
        
    Returns:
        Number of rows inserted
    """
    if not rows:
        logger.warning("No rows to insert")
        return 0
    
    client = bigquery.Client(project=PROJECT_ID)
    table_ref = f"{PROJECT_ID}.{DATASET_ID}.{TABLE_ID}"
    
    # Insert rows
    errors = client.insert_rows_json(table_ref, rows)
    
    if errors:
        logger.error(f"Errors inserting rows: {errors}")
        raise Exception(f"Failed to insert rows: {errors}")
    
    logger.info(f"Successfully inserted {len(rows)} rows to {table_ref}")
    return len(rows)


def process_gcs_event(event: dict, context) -> dict:
    """
    Main Cloud Function entry point - triggered by GCS file upload.
    
    This function is triggered when a new file is uploaded to the configured
    GCS bucket. It extracts relevant information and loads it to BigQuery.
    
    Args:
        event: The Cloud Functions event payload containing:
            - bucket: The GCS bucket name
            - name: The file name/path
            - timeCreated: Upload timestamp
            - metageneration: Object metadata generation
        context: The Cloud Functions event context
        
    Returns:
        Dictionary with processing results
    """
    bucket_name = event['bucket']
    file_name = event['name']
    time_created = event.get('timeCreated', datetime.utcnow().isoformat())
    
    logger.info(f"Processing file: gs://{bucket_name}/{file_name}")
    
    # Skip if not a data file (optional - adjust extensions as needed)
    valid_extensions = ['.csv', '.json', '.txt']
    if not any(file_name.lower().endswith(ext) for ext in valid_extensions):
        logger.info(f"Skipping file {file_name} - not a supported data file")
        return {'status': 'skipped', 'reason': 'unsupported file type'}
    
    # Extract HUM code from filename
    hum_code = extract_hum_code(file_name)
    logger.info(f"Extracted HUM code: {hum_code}")
    
    # Parse upload date
    try:
        upload_date = datetime.fromisoformat(time_created.replace('Z', '+00:00'))
        upload_date_str = upload_date.strftime('%Y-%m-%d %H:%M:%S')
    except Exception:
        upload_date_str = datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S')
    
    # Read file content
    try:
        content = read_file_content(bucket_name, file_name)
    except Exception as e:
        logger.error(f"Error reading file: {e}")
        return {'status': 'error', 'message': str(e)}
    
    # Extract employee IDs from file
    employee_ids = extract_employee_ids(content, file_name)
    logger.info(f"Found {len(employee_ids)} employee IDs")
    
    if not employee_ids:
        # If no employee IDs found, still create a record for tracking
        employee_ids = ['NONE']
    
    # Prepare rows for BigQuery
    rows = []
    for emp_id in employee_ids:
        row = {
            'employee_id': emp_id,
            'upload_date': upload_date_str,
            'file_name': file_name,
            'hum_code': hum_code,
            'bucket_name': bucket_name,
            'processed_at': datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S')
        }
        rows.append(row)
    
    # Insert to BigQuery
    try:
        inserted_count = insert_to_bigquery(rows)
        return {
            'status': 'success',
            'file_name': file_name,
            'hum_code': hum_code,
            'employee_count': len(employee_ids),
            'rows_inserted': inserted_count
        }
    except Exception as e:
        logger.error(f"Error inserting to BigQuery: {e}")
        return {'status': 'error', 'message': str(e)}


# Alternative entry point for HTTP trigger (useful for testing)
def process_file_http(request):
    """
    HTTP-triggered version of the function for testing.
    
    Expected JSON body:
    {
        "bucket": "your-bucket-name",
        "name": "path/to/file.csv"
    }
    """
    request_json = request.get_json(silent=True)
    
    if not request_json:
        return {'error': 'No JSON body provided'}, 400
    
    if 'bucket' not in request_json or 'name' not in request_json:
        return {'error': 'Missing required fields: bucket, name'}, 400
    
    # Add timeCreated if not provided
    if 'timeCreated' not in request_json:
        request_json['timeCreated'] = datetime.utcnow().isoformat()
    
    result = process_gcs_event(request_json, None)
    return result, 200


# Cloud Function entry point for GCS trigger
def main(event, context):
    """
    Cloud Function entry point for GCS trigger.
    
    This is the function that should be specified as the entry point
    when deploying the Cloud Function with a GCS trigger.
    """
    return process_gcs_event(event, context)
