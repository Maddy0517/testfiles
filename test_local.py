"""
Local testing script for the Cloud Function.
This simulates a GCS event and tests the function locally.
"""

import json
import os
from main import process_file

# Mock context object
class MockContext:
    def __init__(self):
        self.event_id = "test-event-id"
        self.timestamp = "2024-01-01T00:00:00Z"
        self.event_type = "google.storage.object.finalize"
        self.resource = "projects/_/buckets/test-bucket/objects/test-file.csv"


def create_test_event(bucket_name: str, file_name: str) -> dict:
    """Create a mock GCS event."""
    return {
        "bucket": bucket_name,
        "name": file_name,
        "contentType": "text/csv",
        "timeCreated": "2024-01-01T00:00:00Z",
        "updated": "2024-01-01T00:00:00Z"
    }


def test_with_local_file():
    """Test the function with a local file."""
    print("=" * 60)
    print("Local Testing for Cloud Function")
    print("=" * 60)
    
    # Configuration
    bucket_name = input("Enter bucket name (or press Enter for 'test-bucket'): ").strip() or "test-bucket"
    file_name = input("Enter file name (e.g., 'data_HUM-100.csv'): ").strip()
    
    if not file_name:
        print("Error: File name is required")
        return
    
    # Check if file exists locally
    if os.path.exists(file_name):
        print(f"\n✓ Found local file: {file_name}")
        print("Note: This test requires the file to be uploaded to GCS first.")
        print("The function reads from GCS, not local filesystem.")
    else:
        print(f"\n⚠ Local file not found: {file_name}")
        print("Make sure the file exists in your GCS bucket.")
    
    # Create mock event
    event = create_test_event(bucket_name, file_name)
    
    print(f"\nEvent details:")
    print(f"  Bucket: {event['bucket']}")
    print(f"  File: {event['name']}")
    
    # Set environment variables if not set
    if not os.environ.get('PROJECT_ID'):
        project_id = input("\nEnter PROJECT_ID (or press Enter to skip): ").strip()
        if project_id:
            os.environ['PROJECT_ID'] = project_id
    
    if not os.environ.get('DATASET_ID'):
        dataset_id = input("Enter DATASET_ID (or press Enter to skip): ").strip()
        if dataset_id:
            os.environ['DATASET_ID'] = dataset_id
    
    if not os.environ.get('TABLE_ID'):
        os.environ['TABLE_ID'] = 'employee_data'
    
    print("\n" + "=" * 60)
    print("Processing event...")
    print("=" * 60)
    
    try:
        context = MockContext()
        process_file(event, context)
        print("\n✓ Function executed successfully!")
        print("\nCheck your BigQuery table to verify the data was inserted.")
    except Exception as e:
        print(f"\n✗ Error: {str(e)}")
        import traceback
        traceback.print_exc()


def test_pattern_extraction():
    """Test filename pattern extraction."""
    from main import extract_code_from_filename
    
    print("\n" + "=" * 60)
    print("Testing Filename Pattern Extraction")
    print("=" * 60)
    
    test_cases = [
        "employee_data_HUM-100.csv",
        "data_HUM-200_2024.csv",
        "HUM-300_report.csv",
        "test_file.csv",  # No pattern
        "hum-150_data.csv",  # Lowercase
        "report_HUM-999_final.csv"
    ]
    
    for filename in test_cases:
        code = extract_code_from_filename(filename)
        status = "✓" if code else "✗"
        print(f"{status} {filename:40} -> {code or 'No match'}")


if __name__ == "__main__":
    import sys
    
    if len(sys.argv) > 1 and sys.argv[1] == "--test-pattern":
        test_pattern_extraction()
    else:
        test_with_local_file()
        print("\n" + "=" * 60)
        print("Run with --test-pattern to test pattern extraction only")
        print("=" * 60)
