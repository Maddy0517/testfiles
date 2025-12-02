"""
Google Cloud Function to process file uploads from GCS to BigQuery.

This function:
1. Triggers on file upload to GCS bucket
2. Extracts pattern from filename (e.g., HUM-100, HUM-200)
3. Reads file data and extracts employee_id from file content
4. Loads data to BigQuery with additional metadata columns
"""

import re
import json
from datetime import datetime
from typing import Dict, List, Any, Optional
import logging

from google.cloud import bigquery
from google.cloud import storage
import pandas as pd
from io import StringIO, BytesIO

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def extract_pattern_from_filename(filename: str) -> Optional[str]:
    """
    Extract pattern like HUM-100, HUM-200, HUM-300 from filename.
    
    Args:
        filename: The name of the uploaded file
        
    Returns:
        Extracted pattern or None if not found
    """
    # Pattern to match HUM-XXX or similar patterns
    pattern = r'(HUM-\d+)'
    match = re.search(pattern, filename, re.IGNORECASE)
    
    if match:
        return match.group(1).upper()
    
    logger.warning(f"No pattern found in filename: {filename}")
    return None


def read_file_from_gcs(bucket_name: str, blob_name: str, file_format: str = 'csv') -> pd.DataFrame:
    """
    Read file from Google Cloud Storage.
    
    Args:
        bucket_name: Name of the GCS bucket
        blob_name: Name of the blob (file) in the bucket
        file_format: Format of the file (csv, json, excel)
        
    Returns:
        DataFrame containing the file data
    """
    storage_client = storage.Client()
    bucket = storage_client.bucket(bucket_name)
    blob = bucket.blob(blob_name)
    
    # Download file content
    content = blob.download_as_bytes()
    
    # Parse based on file format
    if file_format.lower() == 'csv':
        df = pd.read_csv(BytesIO(content))
    elif file_format.lower() == 'json':
        df = pd.read_json(BytesIO(content))
    elif file_format.lower() in ['xlsx', 'excel', 'xls']:
        df = pd.read_excel(BytesIO(content))
    else:
        raise ValueError(f"Unsupported file format: {file_format}")
    
    logger.info(f"Successfully read {len(df)} rows from {blob_name}")
    return df


def prepare_data_for_bigquery(
    df: pd.DataFrame,
    filename: str,
    upload_date: datetime,
    extracted_pattern: Optional[str],
    employee_id_column: str = 'employee_id'
) -> pd.DataFrame:
    """
    Prepare data for BigQuery by adding metadata columns.
    
    Args:
        df: Original dataframe from file
        filename: Name of the uploaded file
        upload_date: Date when file was uploaded
        extracted_pattern: Pattern extracted from filename (e.g., HUM-100)
        employee_id_column: Name of the column containing employee_id
        
    Returns:
        DataFrame with additional metadata columns
    """
    # Verify employee_id column exists
    if employee_id_column not in df.columns:
        raise ValueError(f"Column '{employee_id_column}' not found in file. Available columns: {df.columns.tolist()}")
    
    # Add metadata columns
    df['file_name'] = filename
    df['upload_date'] = upload_date.isoformat()
    df['file_pattern'] = extracted_pattern
    
    # Reorder columns to put metadata first (optional)
    metadata_cols = ['employee_id', 'upload_date', 'file_name', 'file_pattern']
    other_cols = [col for col in df.columns if col not in metadata_cols]
    df = df[metadata_cols + other_cols]
    
    logger.info(f"Prepared {len(df)} rows for BigQuery")
    return df


def load_to_bigquery(
    df: pd.DataFrame,
    project_id: str,
    dataset_id: str,
    table_id: str,
    write_disposition: str = 'WRITE_APPEND'
) -> None:
    """
    Load data to BigQuery table.
    
    Args:
        df: DataFrame to load
        project_id: GCP project ID
        dataset_id: BigQuery dataset ID
        table_id: BigQuery table ID
        write_disposition: Write disposition (WRITE_APPEND, WRITE_TRUNCATE, WRITE_EMPTY)
    """
    client = bigquery.Client(project=project_id)
    table_ref = f"{project_id}.{dataset_id}.{table_id}"
    
    # Configure job
    job_config = bigquery.LoadJobConfig()
    job_config.write_disposition = write_disposition
    job_config.autodetect = True  # Auto-detect schema
    
    # Load data
    job = client.load_table_from_dataframe(df, table_ref, job_config=job_config)
    job.result()  # Wait for the job to complete
    
    logger.info(f"Loaded {len(df)} rows to {table_ref}")


def get_file_format(filename: str) -> str:
    """
    Determine file format from filename extension.
    
    Args:
        filename: Name of the file
        
    Returns:
        File format (csv, json, excel)
    """
    extension = filename.lower().split('.')[-1]
    
    format_map = {
        'csv': 'csv',
        'json': 'json',
        'xlsx': 'excel',
        'xls': 'excel',
        'txt': 'csv'  # Assume txt files are CSV
    }
    
    return format_map.get(extension, 'csv')


def process_file_upload(event: Dict[str, Any], context: Any) -> None:
    """
    Cloud Function entry point - triggered by Cloud Storage event.
    
    Args:
        event: Event payload containing file information
        context: Event metadata
    """
    # Extract event information
    bucket_name = event['bucket']
    file_name = event['name']
    upload_time = event.get('timeCreated', datetime.utcnow().isoformat())
    
    logger.info(f"Processing file: {file_name} from bucket: {bucket_name}")
    
    try:
        # Configuration - These should be set as environment variables
        import os
        project_id = os.environ.get('GCP_PROJECT_ID')
        dataset_id = os.environ.get('BIGQUERY_DATASET_ID')
        table_id = os.environ.get('BIGQUERY_TABLE_ID')
        employee_id_column = os.environ.get('EMPLOYEE_ID_COLUMN', 'employee_id')
        
        if not all([project_id, dataset_id, table_id]):
            raise ValueError("Missing required environment variables: GCP_PROJECT_ID, BIGQUERY_DATASET_ID, BIGQUERY_TABLE_ID")
        
        # Step 1: Extract pattern from filename
        extracted_pattern = extract_pattern_from_filename(file_name)
        
        # Step 2: Read file from GCS
        file_format = get_file_format(file_name)
        df = read_file_from_gcs(bucket_name, file_name, file_format)
        
        # Step 3: Prepare data with metadata
        upload_date = datetime.fromisoformat(upload_time.replace('Z', '+00:00'))
        df_prepared = prepare_data_for_bigquery(
            df=df,
            filename=file_name,
            upload_date=upload_date,
            extracted_pattern=extracted_pattern,
            employee_id_column=employee_id_column
        )
        
        # Step 4: Load to BigQuery
        load_to_bigquery(
            df=df_prepared,
            project_id=project_id,
            dataset_id=dataset_id,
            table_id=table_id,
            write_disposition='WRITE_APPEND'
        )
        
        logger.info(f"Successfully processed {file_name}")
        
    except Exception as e:
        logger.error(f"Error processing file {file_name}: {str(e)}", exc_info=True)
        raise


# For local testing
def main():
    """
    Main function for local testing.
    """
    # Mock event for testing
    event = {
        'bucket': 'your-bucket-name',
        'name': 'employee_data_HUM-100.csv',
        'timeCreated': datetime.utcnow().isoformat()
    }
    
    # Set environment variables for testing
    import os
    os.environ['GCP_PROJECT_ID'] = 'your-project-id'
    os.environ['BIGQUERY_DATASET_ID'] = 'your_dataset'
    os.environ['BIGQUERY_TABLE_ID'] = 'employee_data'
    
    process_file_upload(event, None)


if __name__ == '__main__':
    main()
