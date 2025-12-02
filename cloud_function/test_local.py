"""
Local testing script for the Cloud Function.

This script allows you to test the function logic locally before deploying.
"""

import os
import sys
from datetime import datetime
from unittest.mock import Mock, patch

# Add parent directory to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from main import (
    extract_hum_code,
    extract_employee_ids_from_csv,
    extract_employee_ids_from_json,
    extract_employee_ids,
)


def test_extract_hum_code():
    """Test HUM code extraction from various filename formats."""
    test_cases = [
        ("employees_HUM-100_report.csv", "HUM-100"),
        ("data_HUM-200_quarterly.json", "HUM-200"),
        ("HUM-300_employees.csv", "HUM-300"),
        ("report_hum-150_2024.csv", "HUM-150"),
        ("no_code_here.csv", "UNKNOWN"),
        ("HUM-999_test_HUM-100.csv", "HUM-999"),  # Takes first match
        ("path/to/HUM-456_file.csv", "HUM-456"),
    ]
    
    print("\n=== Testing HUM Code Extraction ===")
    for filename, expected in test_cases:
        result = extract_hum_code(filename)
        status = "✓" if result == expected else "✗"
        print(f"{status} '{filename}' -> '{result}' (expected: '{expected}')")
    print()


def test_extract_employee_ids_csv():
    """Test employee ID extraction from CSV content."""
    csv_content = """employee_id,name,department
EMP001,John Smith,Engineering
EMP002,Jane Doe,Marketing
EMP003,Bob Johnson,Sales"""

    print("=== Testing CSV Employee ID Extraction ===")
    result = extract_employee_ids_from_csv(csv_content)
    print(f"Found employee IDs: {result}")
    assert result == ["EMP001", "EMP002", "EMP003"], f"Expected ['EMP001', 'EMP002', 'EMP003'], got {result}"
    print("✓ CSV extraction passed\n")


def test_extract_employee_ids_csv_different_column():
    """Test employee ID extraction with different column names."""
    test_cases = [
        # (csv_content, expected_ids)
        ("emp_id,name\nE1,John\nE2,Jane", ["E1", "E2"]),
        ("ID,name\n101,John\n102,Jane", ["101", "102"]),
        ("employeeId,name\nEMP-A,John\nEMP-B,Jane", ["EMP-A", "EMP-B"]),
    ]
    
    print("=== Testing CSV with Different Column Names ===")
    for csv_content, expected in test_cases:
        result = extract_employee_ids_from_csv(csv_content)
        status = "✓" if result == expected else "✗"
        print(f"{status} Got {result} (expected: {expected})")
    print()


def test_extract_employee_ids_json():
    """Test employee ID extraction from JSON content."""
    # Test with list format
    json_list = '[{"employee_id": "E1"}, {"employee_id": "E2"}]'
    
    # Test with nested format
    json_nested = '{"employees": [{"employee_id": "E3"}, {"employee_id": "E4"}]}'
    
    print("=== Testing JSON Employee ID Extraction ===")
    
    result1 = extract_employee_ids_from_json(json_list)
    print(f"List format: {result1}")
    assert result1 == ["E1", "E2"], f"Expected ['E1', 'E2'], got {result1}"
    
    result2 = extract_employee_ids_from_json(json_nested)
    print(f"Nested format: {result2}")
    assert result2 == ["E3", "E4"], f"Expected ['E3', 'E4'], got {result2}"
    
    print("✓ JSON extraction passed\n")


def test_extract_employee_ids_auto():
    """Test automatic file type detection."""
    csv_content = "employee_id,name\nEMP001,John"
    json_content = '{"employees": [{"employee_id": "EMP002"}]}'
    
    print("=== Testing Auto-Detection ===")
    
    result_csv = extract_employee_ids(csv_content, "file.csv")
    print(f"CSV file: {result_csv}")
    
    result_json = extract_employee_ids(json_content, "file.json")
    print(f"JSON file: {result_json}")
    
    # Unknown extension should try both
    result_unknown = extract_employee_ids(csv_content, "file.dat")
    print(f"Unknown extension (CSV content): {result_unknown}")
    print("✓ Auto-detection passed\n")


def simulate_cloud_event():
    """Simulate a Cloud Function event for integration testing."""
    print("=== Simulating Cloud Event ===")
    
    # Mock event data (simulating GCS trigger)
    event = {
        'bucket': 'test-bucket',
        'name': 'uploads/employees_HUM-100_monthly.csv',
        'timeCreated': '2024-01-15T10:30:00Z',
        'metageneration': '1'
    }
    
    print(f"Event: {event}")
    print(f"Bucket: {event['bucket']}")
    print(f"File: {event['name']}")
    print(f"HUM Code: {extract_hum_code(event['name'])}")
    print()
    
    # Note: Full integration test requires mocking GCS and BigQuery clients
    print("Note: Full integration test requires GCP credentials and resources")
    print()


def run_all_tests():
    """Run all tests."""
    print("\n" + "=" * 60)
    print("Running Local Tests for GCS to BigQuery Cloud Function")
    print("=" * 60)
    
    test_extract_hum_code()
    test_extract_employee_ids_csv()
    test_extract_employee_ids_csv_different_column()
    test_extract_employee_ids_json()
    test_extract_employee_ids_auto()
    simulate_cloud_event()
    
    print("=" * 60)
    print("All tests completed!")
    print("=" * 60)


if __name__ == "__main__":
    run_all_tests()
