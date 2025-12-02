"""
Google Cloud Function to process files from Cloud Storage and load to BigQuery.

This function:
1. Triggers on file uploads to Cloud Storage bucket
2. Extracts pattern (e.g., HUM-100, HUM-200) from filename
3. Reads file content and extracts employee_id
4. Loads data to BigQuery table with employee_id, upload_date, file_name, extracted_code
"""

import re
import json
import os
import logging
from datetime import datetime
from typing import Dict, Any, List, Optional

from google.cloud import storage
from google.cloud import bigquery
from google.cloud.exceptions import NotFound

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Configuration - Use environment variables with fallback defaults
PROJECT_ID = os.environ.get('PROJECT_ID', 'your-project-id')
DATASET_ID = os.environ.get('DATASET_ID', 'your_dataset')
TABLE_ID = os.environ.get('TABLE_ID', 'employee_data')
EMPLOYEE_ID_COLUMN = os.environ.get('EMPLOYEE_ID_COLUMN', 'employee_id')  # Column name for employee ID

# Pattern to extract codes like HUM-100, HUM-200, HUM-300 from filename
CODE_PATTERN = re.compile(r'(HUM-\d{3})', re.IGNORECASE)


def extract_code_from_filename(filename: str) -> Optional[str]:
    """
    Extract code pattern (e.g., HUM-100, HUM-200) from filename.
    
    Args:
        filename: The name of the uploaded file
        
    Returns:
        Extracted code string or None if not found
    """
    match = CODE_PATTERN.search(filename)
    if match:
        return match.group(1).upper()  # Return uppercase for consistency
    logger.warning(f"No code pattern found in filename: {filename}")
    return None


def read_file_from_gcs(bucket_name: str, file_name: str) -> str:
    """
    Read file content from Google Cloud Storage.
    
    Args:
        bucket_name: Name of the GCS bucket
        file_name: Name of the file in the bucket
        
    Returns:
        File content as string
    """
    try:
        storage_client = storage.Client()
        bucket = storage_client.bucket(bucket_name)
        blob = bucket.blob(file_name)
        content = blob.download_as_text()
        logger.info(f"Successfully read file: {file_name} from bucket: {bucket_name}")
        return content
    except Exception as e:
        logger.error(f"Error reading file {file_name} from bucket {bucket_name}: {str(e)}")
        raise


def parse_file_content(file_content: str, file_name: str) -> List[Dict[str, Any]]:
    """
    Parse file content and extract employee_id.
    Assumes CSV format with header row. Adjust based on your file format.
    
    Args:
        file_content: Content of the file as string
        file_name: Name of the file (for logging)
        
    Returns:
        List of dictionaries with employee_id and other parsed data
    """
    rows = []
    lines = file_content.strip().split('\n')
    
    if not lines:
        logger.warning(f"File {file_name} is empty")
        return rows
    
    # Assume first line is header
    headers = [h.strip() for h in lines[0].split(',')]
    
    # Find employee_id column index (using configurable column name)
    try:
        employee_id_index = headers.index(EMPLOYEE_ID_COLUMN)
    except ValueError:
        # Try case-insensitive search
        employee_id_index = next(
            (i for i, h in enumerate(headers) if h.lower() == EMPLOYEE_ID_COLUMN.lower()),
            None
        )
        if employee_id_index is None:
            logger.error(f"{EMPLOYEE_ID_COLUMN} column not found in file {file_name}. Headers: {headers}")
            raise ValueError(f"{EMPLOYEE_ID_COLUMN} column not found in headers: {headers}")
    
    # Parse data rows
    for line_num, line in enumerate(lines[1:], start=2):
        if not line.strip():
            continue
        
        values = [v.strip() for v in line.split(',')]
        if len(values) <= employee_id_index:
            logger.warning(f"Skipping line {line_num} in {file_name}: insufficient columns")
            continue
        
        employee_id = values[employee_id_index]
        if employee_id:  # Only add non-empty employee_ids
            rows.append({
                'employee_id': employee_id,
                'raw_data': line  # Store raw line for reference
            })
    
    logger.info(f"Parsed {len(rows)} rows from file {file_name}")
    return rows


def ensure_bigquery_table_exists():
    """
    Ensure BigQuery table exists with required schema.
    Creates table if it doesn't exist.
    """
    client = bigquery.Client(project=PROJECT_ID)
    table_ref = client.dataset(DATASET_ID).table(TABLE_ID)
    
    try:
        client.get_table(table_ref)
        logger.info(f"Table {DATASET_ID}.{TABLE_ID} already exists")
    except NotFound:
        logger.info(f"Table {DATASET_ID}.{TABLE_ID} not found. Creating...")
        
        schema = [
            bigquery.SchemaField("employee_id", "STRING", mode="REQUIRED"),
            bigquery.SchemaField("upload_date", "TIMESTAMP", mode="REQUIRED"),
            bigquery.SchemaField("file_name", "STRING", mode="REQUIRED"),
            bigquery.SchemaField("extracted_code", "STRING", mode="NULLABLE"),
        ]
        
        table = bigquery.Table(table_ref, schema=schema)
        table = client.create_table(table)
        logger.info(f"Created table {DATASET_ID}.{TABLE_ID}")


def load_to_bigquery(rows: List[Dict[str, Any]], file_name: str, extracted_code: Optional[str]):
    """
    Load data to BigQuery table.
    
    Args:
        rows: List of dictionaries with employee_id and other data
        file_name: Name of the uploaded file
        extracted_code: Code extracted from filename (e.g., HUM-100)
    """
    client = bigquery.Client(project=PROJECT_ID)
    table_ref = client.dataset(DATASET_ID).table(TABLE_ID)
    
    # Prepare rows for BigQuery insertion
    upload_timestamp = datetime.utcnow()
    bigquery_rows = []
    
    for row in rows:
        bigquery_rows.append({
            'employee_id': row['employee_id'],
            'upload_date': upload_timestamp.isoformat(),
            'file_name': file_name,
            'extracted_code': extracted_code
        })
    
    # Insert rows
    errors = client.insert_rows_json(table_ref, bigquery_rows)
    
    if errors:
        logger.error(f"Errors inserting rows to BigQuery: {errors}")
        raise Exception(f"BigQuery insertion errors: {errors}")
    
    logger.info(f"Successfully inserted {len(bigquery_rows)} rows to BigQuery")


def process_file(event: Dict[str, Any], context: Any) -> None:
    """
    Main Cloud Function entry point.
    Triggered by Cloud Storage file upload events.
    
    Args:
        event: Event payload containing file information
        context: Context object with metadata about the event
    """
    try:
        # Extract file information from event
        bucket_name = event['bucket']
        file_name = event['name']
        
        logger.info(f"Processing file: {file_name} from bucket: {bucket_name}")
        
        # Step 1: Extract code from filename
        extracted_code = extract_code_from_filename(file_name)
        if not extracted_code:
            logger.warning(f"No code pattern found in filename: {file_name}. Processing anyway...")
        
        # Step 2: Read file content from GCS
        file_content = read_file_from_gcs(bucket_name, file_name)
        
        # Step 3: Parse file content and extract employee_id
        parsed_rows = parse_file_content(file_content, file_name)
        
        if not parsed_rows:
            logger.warning(f"No valid rows found in file: {file_name}")
            return
        
        # Step 4: Ensure BigQuery table exists
        ensure_bigquery_table_exists()
        
        # Step 5: Load data to BigQuery
        load_to_bigquery(parsed_rows, file_name, extracted_code)
        
        logger.info(f"Successfully processed file: {file_name}")
        
    except Exception as e:
        logger.error(f"Error processing file: {str(e)}", exc_info=True)
        raise  # Re-raise to mark function as failed


# Alternative entry point for HTTP-triggered functions (if needed)
def process_file_http(request):
    """
    HTTP-triggered version of the function.
    Can be used if you prefer HTTP triggers over GCS triggers.
    """
    try:
        request_json = request.get_json(silent=True)
        
        if not request_json:
            return {'error': 'No JSON payload provided'}, 400
        
        bucket_name = request_json.get('bucket')
        file_name = request_json.get('name')
        
        if not bucket_name or not file_name:
            return {'error': 'Missing bucket or name in payload'}, 400
        
        # Create event-like dictionary
        event = {
            'bucket': bucket_name,
            'name': file_name
        }
        
        # Process using main function
        process_file(event, None)
        
        return {'status': 'success', 'message': f'Processed {file_name}'}, 200
        
    except Exception as e:
        logger.error(f"Error in HTTP function: {str(e)}", exc_info=True)
        return {'error': str(e)}, 500
