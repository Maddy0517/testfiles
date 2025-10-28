import logging
import base64
from typing import Optional
from flask import Request, Response
from google.cloud import storage
from google.cloud import secretmanager
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives import padding
import os

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class FileEncryptionFunction:
    """
    Google Cloud Function for encrypting files stored in GCS buckets.
    This function reads files from a source bucket, encrypts them using AES encryption,
    and stores the encrypted files in a target bucket.
    """
    
    @staticmethod
    def access_secret(project_name: str, key_name: str) -> str:
        """
        Access a secret from Google Secret Manager.
        
        Args:
            project_name (str): The GCP project ID
            key_name (str): The name of the secret to retrieve
            
        Returns:
            str: The secret value as a string
            
        Raises:
            Exception: If unable to access the secret
        """
        try:
            client = secretmanager.SecretManagerServiceClient()
            secret_name = f"projects/{project_name}/secrets/{key_name}/versions/latest"
            
            response = client.access_secret_version(request={"name": secret_name})
            secret_value = response.payload.data.decode("UTF-8")
            
            logger.info(f"Successfully retrieved secret: {key_name}")
            return secret_value
            
        except Exception as e:
            logger.error(f"Error accessing secret {key_name}: {str(e)}")
            raise Exception(f"Failed to access secret {key_name}: {str(e)}")

    @staticmethod
    def encrypt_file_content(file_bytes: bytes, encryption_key: str) -> bytes:
        """
        Encrypt file content using AES encryption in ECB mode with PKCS7 padding.
        
        Args:
            file_bytes (bytes): The file content to encrypt
            encryption_key (str): The encryption key
            
        Returns:
            bytes: The encrypted file content
            
        Raises:
            Exception: If encryption fails
        """
        try:
            # Convert key to bytes and ensure it's 32 bytes for AES-256
            key_bytes = encryption_key.encode('utf-8')
            
            # Pad or truncate key to 32 bytes for AES-256
            if len(key_bytes) < 32:
                key_bytes = key_bytes.ljust(32, b'\0')
            elif len(key_bytes) > 32:
                key_bytes = key_bytes[:32]
            
            logger.info(f"Key bytes length: {len(key_bytes)}")
            
            # Create cipher
            cipher = Cipher(
                algorithms.AES(key_bytes),
                modes.ECB(),
                backend=default_backend()
            )
            
            # Add PKCS7 padding
            padder = padding.PKCS7(128).padder()
            padded_data = padder.update(file_bytes)
            padded_data += padder.finalize()
            
            # Encrypt
            encryptor = cipher.encryptor()
            encrypted_bytes = encryptor.update(padded_data) + encryptor.finalize()
            
            logger.info("File content encrypted successfully")
            return encrypted_bytes
            
        except Exception as e:
            logger.error(f"Error encrypting file: {str(e)}")
            raise Exception(f"Failed to encrypt file: {str(e)}")

    @staticmethod
    def process_file_encryption(request: Request) -> Response:
        """
        Main function to handle the HTTP request for file encryption.
        
        Args:
            request (Request): The HTTP request object
            
        Returns:
            Response: HTTP response indicating success or failure
        """
        try:
            # Extract parameters from query string
            src_bucket_name = request.args.get('Src_Bucket')
            tgt_bucket_name = request.args.get('Tgt_Bucket')
            src_file_name = request.args.get('Src_File')
            gcs_project_id = request.args.get('Gcs_ProjectID')
            encrypt_key = request.args.get('Encrypt_Key')
            
            # Validate required parameters
            required_params = {
                'Src_Bucket': src_bucket_name,
                'Tgt_Bucket': tgt_bucket_name,
                'Src_File': src_file_name,
                'Gcs_ProjectID': gcs_project_id,
                'Encrypt_Key': encrypt_key
            }
            
            for param_name, param_value in required_params.items():
                if not param_value:
                    error_msg = f"{param_name} is a required parameter"
                    logger.error(error_msg)
                    return Response(error_msg, status=400)
            
            logger.info(f"Processing file encryption for: {src_file_name}")
            logger.info(f"Source bucket: {src_bucket_name}")
            logger.info(f"Target bucket: {tgt_bucket_name}")
            
            # Initialize GCS client
            storage_client = storage.Client(project=gcs_project_id)
            
            # Get source and target buckets
            source_bucket = storage_client.bucket(src_bucket_name)
            target_bucket = storage_client.bucket(tgt_bucket_name)
            
            # Check if source file exists
            source_blob = source_bucket.blob(src_file_name)
            if not source_blob.exists():
                error_msg = f"Source file {src_file_name} not found in bucket {src_bucket_name}"
                logger.error(error_msg)
                return Response(error_msg, status=404)
            
            # Download file content
            file_bytes = source_blob.download_as_bytes()
            logger.info(f"Downloaded file: {src_file_name}, size: {len(file_bytes)} bytes")
            
            # Get encryption key from Secret Manager
            encryption_key = FileEncryptionFunction.access_secret(gcs_project_id, encrypt_key)
            
            # Encrypt the file content
            encrypted_bytes = FileEncryptionFunction.encrypt_file_content(file_bytes, encryption_key)
            logger.info(f"Encrypted file size: {len(encrypted_bytes)} bytes")
            
            # Store encrypted file in target bucket
            encrypted_file_name = f"{src_file_name}.encrypted"
            encrypted_blob = target_bucket.blob(encrypted_file_name)
            encrypted_blob.upload_from_string(encrypted_bytes)
            
            logger.info(f"Encrypted file stored: {encrypted_file_name}")
            
            # Delete source file
            source_blob.delete()
            logger.info(f"Source file deleted: {src_file_name}")
            
            success_msg = f"Source file encrypted and stored as {encrypted_file_name}"
            logger.info(success_msg)
            
            return Response(success_msg, status=200)
            
        except Exception as e:
            error_msg = f"Error processing file encryption: {str(e)}"
            logger.error(error_msg)
            return Response(error_msg, status=500)


def file_encryption_http_function(request: Request) -> Response:
    """
    HTTP Cloud Function entry point for file encryption.
    
    This function is called by Google Cloud Functions when an HTTP request is made.
    It expects the following query parameters:
    - Src_Bucket: Source GCS bucket name
    - Tgt_Bucket: Target GCS bucket name  
    - Src_File: Source file path in the bucket
    - Gcs_ProjectID: GCP project ID
    - Encrypt_Key: Name of the encryption key in Secret Manager
    
    Args:
        request (Request): The HTTP request object
        
    Returns:
        Response: HTTP response with success/error message
    """
    return FileEncryptionFunction.process_file_encryption(request)


# For local testing
if __name__ == "__main__":
    from flask import Flask, request
    
    app = Flask(__name__)
    
    @app.route('/', methods=['GET', 'POST'])
    def test_function():
        return file_encryption_http_function(request)
    
    app.run(debug=True, host='0.0.0.0', port=8080)