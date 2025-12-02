"""
Standalone testing script - doesn't require GCP libraries.
Tests the core logic functions independently.
"""

import re
import csv
import json
from io import StringIO

# Pattern to extract codes like HUM-100, HUM-200, HUM-300 from filename
HUM_CODE_PATTERN = r'(HUM-\d+)'


def extract_hum_code(filename: str) -> str:
    """Extract HUM code from filename."""
    match = re.search(HUM_CODE_PATTERN, filename, re.IGNORECASE)
    if match:
        return match.group(1).upper()
    return 'UNKNOWN'


def extract_employee_ids_from_csv(content: str) -> list:
    """Extract employee IDs from CSV content."""
    employee_ids = []
    
    try:
        csv_file = StringIO(content)
        reader = csv.DictReader(csv_file)
        
        for row in reader:
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
        print(f"Error parsing CSV: {e}")
        
    return employee_ids


def extract_employee_ids_from_json(content: str) -> list:
    """Extract employee IDs from JSON content."""
    employee_ids = []
    
    try:
        data = json.loads(content)
        
        if isinstance(data, list):
            records = data
        elif isinstance(data, dict):
            records = data.get('employees', data.get('data', data.get('records', [data])))
        else:
            records = []
        
        for record in records:
            if isinstance(record, dict):
                for key in ['employee_id', 'employeeId', 'emp_id', 'empId', 'id']:
                    if key in record:
                        employee_ids.append(str(record[key]).strip())
                        break
                        
    except Exception as e:
        print(f"Error parsing JSON: {e}")
        
    return employee_ids


def test_extract_hum_code():
    """Test HUM code extraction from various filename formats."""
    test_cases = [
        ("employees_HUM-100_report.csv", "HUM-100"),
        ("data_HUM-200_quarterly.json", "HUM-200"),
        ("HUM-300_employees.csv", "HUM-300"),
        ("report_hum-150_2024.csv", "HUM-150"),
        ("no_code_here.csv", "UNKNOWN"),
        ("HUM-999_test_HUM-100.csv", "HUM-999"),
        ("path/to/HUM-456_file.csv", "HUM-456"),
    ]
    
    print("\n=== Testing HUM Code Extraction ===")
    all_passed = True
    for filename, expected in test_cases:
        result = extract_hum_code(filename)
        status = "✓" if result == expected else "✗"
        if result != expected:
            all_passed = False
        print(f"{status} '{filename}' -> '{result}' (expected: '{expected}')")
    
    return all_passed


def test_extract_employee_ids_csv():
    """Test employee ID extraction from CSV content."""
    csv_content = """employee_id,name,department
EMP001,John Smith,Engineering
EMP002,Jane Doe,Marketing
EMP003,Bob Johnson,Sales"""

    print("\n=== Testing CSV Employee ID Extraction ===")
    result = extract_employee_ids_from_csv(csv_content)
    print(f"Found employee IDs: {result}")
    
    if result == ["EMP001", "EMP002", "EMP003"]:
        print("✓ CSV extraction passed")
        return True
    else:
        print(f"✗ Expected ['EMP001', 'EMP002', 'EMP003'], got {result}")
        return False


def test_extract_employee_ids_csv_different_columns():
    """Test employee ID extraction with different column names."""
    test_cases = [
        ("emp_id,name\nE1,John\nE2,Jane", ["E1", "E2"]),
        ("ID,name\n101,John\n102,Jane", ["101", "102"]),
        ("employeeId,name\nEMP-A,John\nEMP-B,Jane", ["EMP-A", "EMP-B"]),
    ]
    
    print("\n=== Testing CSV with Different Column Names ===")
    all_passed = True
    for csv_content, expected in test_cases:
        result = extract_employee_ids_from_csv(csv_content)
        status = "✓" if result == expected else "✗"
        if result != expected:
            all_passed = False
        print(f"{status} Got {result} (expected: {expected})")
    
    return all_passed


def test_extract_employee_ids_json():
    """Test employee ID extraction from JSON content."""
    print("\n=== Testing JSON Employee ID Extraction ===")
    
    # Test with list format
    json_list = '[{"employee_id": "E1"}, {"employee_id": "E2"}]'
    result1 = extract_employee_ids_from_json(json_list)
    print(f"List format: {result1}")
    
    # Test with nested format
    json_nested = '{"employees": [{"employee_id": "E3"}, {"employee_id": "E4"}]}'
    result2 = extract_employee_ids_from_json(json_nested)
    print(f"Nested format: {result2}")
    
    if result1 == ["E1", "E2"] and result2 == ["E3", "E4"]:
        print("✓ JSON extraction passed")
        return True
    else:
        print("✗ JSON extraction failed")
        return False


def test_sample_files():
    """Test with sample data files."""
    print("\n=== Testing Sample Files ===")
    
    # Read CSV sample
    try:
        with open('sample_data/employees_HUM-100_report.csv', 'r') as f:
            csv_content = f.read()
        
        filename = 'employees_HUM-100_report.csv'
        hum_code = extract_hum_code(filename)
        emp_ids = extract_employee_ids_from_csv(csv_content)
        
        print(f"CSV File: {filename}")
        print(f"  HUM Code: {hum_code}")
        print(f"  Employee IDs: {emp_ids}")
        print()
    except FileNotFoundError:
        print("CSV sample file not found (OK if running from different directory)")
    
    # Read JSON sample
    try:
        with open('sample_data/data_HUM-200_quarterly.json', 'r') as f:
            json_content = f.read()
        
        filename = 'data_HUM-200_quarterly.json'
        hum_code = extract_hum_code(filename)
        emp_ids = extract_employee_ids_from_json(json_content)
        
        print(f"JSON File: {filename}")
        print(f"  HUM Code: {hum_code}")
        print(f"  Employee IDs: {emp_ids}")
    except FileNotFoundError:
        print("JSON sample file not found (OK if running from different directory)")
    
    return True


def run_all_tests():
    """Run all tests."""
    print("\n" + "=" * 60)
    print("Running Standalone Tests for GCS to BigQuery Cloud Function")
    print("=" * 60)
    
    results = []
    results.append(("HUM Code Extraction", test_extract_hum_code()))
    results.append(("CSV Extraction", test_extract_employee_ids_csv()))
    results.append(("CSV Different Columns", test_extract_employee_ids_csv_different_columns()))
    results.append(("JSON Extraction", test_extract_employee_ids_json()))
    results.append(("Sample Files", test_sample_files()))
    
    print("\n" + "=" * 60)
    print("Test Summary:")
    print("=" * 60)
    
    all_passed = True
    for name, passed in results:
        status = "✓ PASS" if passed else "✗ FAIL"
        if not passed:
            all_passed = False
        print(f"  {status}: {name}")
    
    print("=" * 60)
    if all_passed:
        print("All tests passed! ✓")
    else:
        print("Some tests failed! ✗")
    print("=" * 60)
    
    return all_passed


if __name__ == "__main__":
    success = run_all_tests()
    exit(0 if success else 1)
