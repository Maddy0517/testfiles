"""
Google Cloud Function for File Encryption

This module provides the main entry point for the file encryption cloud function.
It can be deployed to Google Cloud Functions and called from Airflow/Cloud Composer DAGs.

Usage:
    Deploy this function to Google Cloud Functions and call it with the following parameters:
    - Src_Bucket: Source GCS bucket name
    - Tgt_Bucket: Target GCS bucket name  
    - Src_File: Source file path in the bucket
    - Gcs_ProjectID: GCP project ID
    - Encrypt_Key: Name of the encryption key in Secret Manager

Example URL:
    https://YOUR_REGION-YOUR_PROJECT.cloudfunctions.net/file-encryption?Src_Bucket=source-bucket&Tgt_Bucket=target-bucket&Src_File=data.csv&Gcs_ProjectID=your-project&Encrypt_Key=encryption-key-name
"""

from file_encryption_function import file_encryption_http_function

def encrypt_file(request):
    """
    Main entry point for Google Cloud Function.
    
    This function will be called when the Cloud Function is triggered via HTTP.
    
    Args:
        request: The HTTP request object containing query parameters
        
    Returns:
        HTTP response with success/error message
    """
    return file_encryption_http_function(request)