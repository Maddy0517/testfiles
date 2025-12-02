"""
Local testing script for the Cloud Function.
This simulates a Cloud Storage event and tests the function locally.
"""

import os
from datetime import datetime
from main import process_file_upload

# Set environment variables for testing
os.environ['GCP_PROJECT_ID'] = 'your-project-id'  # Replace with your project ID
os.environ['BIGQUERY_DATASET_ID'] = 'your_dataset'  # Replace with your dataset
os.environ['BIGQUERY_TABLE_ID'] = 'employee_data'  # Replace with your table
os.environ['EMPLOYEE_ID_COLUMN'] = 'employee_id'

def test_function():
    """
    Test the Cloud Function with a mock event.
    
    Before running:
    1. Set correct environment variables above
    2. Authenticate: gcloud auth application-default login
    3. Upload test file to GCS bucket
    """
    
    # Mock Cloud Storage event
    event = {
        'bucket': 'your-bucket-name',  # Replace with your bucket name
        'name': 'test_file_HUM-100.csv',  # File name in the bucket
        'timeCreated': datetime.utcnow().isoformat() + 'Z'
    }
    
    print("Starting test...")
    print(f"Event: {event}")
    
    try:
        process_file_upload(event, None)
        print("\n✅ Test completed successfully!")
    except Exception as e:
        print(f"\n❌ Test failed: {str(e)}")
        raise

if __name__ == '__main__':
    test_function()
