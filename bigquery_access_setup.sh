#!/bin/bash

# BigQuery Dataset Access Control Setup Script
# This script helps set up restricted access to BigQuery datasets

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_ID=""
ORGANIZATION_ID=""

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo -e "${BLUE}[SETUP]${NC} $1"
}

# Function to check prerequisites
check_prerequisites() {
    print_header "Checking prerequisites..."
    
    # Check if gcloud is installed
    if ! command -v gcloud &> /dev/null; then
        print_error "gcloud CLI is not installed. Please install it first."
        exit 1
    fi
    
    # Check if user is authenticated
    if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" | head -n1 > /dev/null; then
        print_error "No active gcloud authentication found. Please run 'gcloud auth login'"
        exit 1
    fi
    
    # Check if Python is available
    if ! command -v python3 &> /dev/null; then
        print_error "Python 3 is not installed. Please install it first."
        exit 1
    fi
    
    print_status "Prerequisites check passed"
}

# Function to get project configuration
get_project_config() {
    print_header "Getting project configuration..."
    
    if [ -z "$PROJECT_ID" ]; then
        PROJECT_ID=$(gcloud config get-value project 2>/dev/null)
        if [ -z "$PROJECT_ID" ]; then
            print_error "No project ID found. Please set it with 'gcloud config set project PROJECT_ID'"
            exit 1
        fi
    fi
    
    print_status "Using project: $PROJECT_ID"
    
    # Try to get organization ID
    ORGANIZATION_ID=$(gcloud projects describe $PROJECT_ID --format="value(parent.id)" 2>/dev/null || echo "")
    if [ -n "$ORGANIZATION_ID" ]; then
        print_status "Organization ID: $ORGANIZATION_ID"
    else
        print_warning "Could not determine organization ID. Some features may be limited."
    fi
}

# Function to enable required APIs
enable_apis() {
    print_header "Enabling required APIs..."
    
    apis=(
        "bigquery.googleapis.com"
        "cloudresourcemanager.googleapis.com"
        "iam.googleapis.com"
    )
    
    for api in "${apis[@]}"; do
        print_status "Enabling $api..."
        gcloud services enable $api --project=$PROJECT_ID
    done
    
    print_status "APIs enabled successfully"
}

# Function to create custom IAM roles
create_custom_roles() {
    print_header "Creating custom IAM roles..."
    
    # Create restricted dataset viewer role
    cat > /tmp/dataset_viewer_role.yaml << EOF
title: "BigQuery Dataset Viewer (Restricted)"
description: "Can view only assigned datasets in BigQuery"
stage: "GA"
includedPermissions:
- bigquery.datasets.get
- bigquery.tables.list
- bigquery.tables.get
- bigquery.tables.getData
- bigquery.jobs.create
- bigquery.jobs.list
- bigquery.jobs.get
EOF

    # Create restricted dataset editor role
    cat > /tmp/dataset_editor_role.yaml << EOF
title: "BigQuery Dataset Editor (Restricted)"
description: "Can edit only assigned datasets in BigQuery"
stage: "GA"
includedPermissions:
- bigquery.datasets.get
- bigquery.datasets.update
- bigquery.tables.list
- bigquery.tables.get
- bigquery.tables.create
- bigquery.tables.update
- bigquery.tables.delete
- bigquery.tables.getData
- bigquery.tables.updateData
- bigquery.jobs.create
- bigquery.jobs.list
- bigquery.jobs.get
EOF

    # Try to create the roles (requires appropriate permissions)
    if gcloud iam roles create bigquery.datasetViewer.restricted \
        --project=$PROJECT_ID \
        --file=/tmp/dataset_viewer_role.yaml 2>/dev/null; then
        print_status "Created dataset viewer role"
    else
        print_warning "Could not create dataset viewer role (may already exist or insufficient permissions)"
    fi
    
    if gcloud iam roles create bigquery.datasetEditor.restricted \
        --project=$PROJECT_ID \
        --file=/tmp/dataset_editor_role.yaml 2>/dev/null; then
        print_status "Created dataset editor role"
    else
        print_warning "Could not create dataset editor role (may already exist or insufficient permissions)"
    fi
    
    # Clean up temporary files
    rm -f /tmp/dataset_viewer_role.yaml /tmp/dataset_editor_role.yaml
}

# Function to install Python dependencies
install_python_deps() {
    print_header "Installing Python dependencies..."
    
    cat > requirements.txt << EOF
google-cloud-bigquery>=3.0.0
google-cloud-resource-manager>=1.0.0
google-api-core>=2.0.0
EOF

    if command -v pip3 &> /dev/null; then
        pip3 install -r requirements.txt
    elif command -v pip &> /dev/null; then
        pip install -r requirements.txt
    else
        print_error "pip is not available. Please install Python dependencies manually."
        exit 1
    fi
    
    print_status "Python dependencies installed"
}

# Function to create sample configuration
create_sample_config() {
    print_header "Creating sample configuration..."
    
    cat > bigquery_access_config.json << EOF
{
    "project_id": "$PROJECT_ID",
    "organization_id": "$ORGANIZATION_ID",
    "dataset_naming_policy": {
        "user_datasets": "{username}_{purpose}",
        "shared_datasets": "shared_{purpose}",
        "system_datasets": "system_{purpose}",
        "temp_datasets": "temp_{username}_{timestamp}"
    },
    "default_roles": {
        "viewer": "projects/$PROJECT_ID/roles/bigquery.datasetViewer.restricted",
        "editor": "projects/$PROJECT_ID/roles/bigquery.datasetEditor.restricted"
    },
    "access_control_settings": {
        "enforce_naming_policy": true,
        "auto_create_user_datasets": true,
        "audit_access_changes": true
    }
}
EOF

    print_status "Sample configuration created: bigquery_access_config.json"
}

# Function to create example usage script
create_example_usage() {
    print_header "Creating example usage scripts..."
    
    cat > example_usage.sh << 'EOF'
#!/bin/bash

# Example usage of BigQuery Access Control

PROJECT_ID="your-project-id"

echo "=== BigQuery Dataset Access Control Examples ==="

# 1. Setup access for a new user
echo "1. Setting up access for user john.doe@company.com..."
python3 bigquery_access_control.py \
    --project-id $PROJECT_ID \
    --setup-user-access john.doe@company.com

# 2. List datasets accessible by a user
echo "2. Listing datasets for user john.doe@company.com..."
python3 bigquery_access_control.py \
    --project-id $PROJECT_ID \
    --list-user-datasets john.doe@company.com

# 3. Grant access to a specific dataset
echo "3. Granting access to shared dataset..."
python3 bigquery_access_control.py \
    --project-id $PROJECT_ID \
    --grant-dataset-access shared_reference_data john.doe@company.com

# 4. Audit all dataset access
echo "4. Auditing all dataset access..."
python3 bigquery_access_control.py \
    --project-id $PROJECT_ID \
    --audit-access

# 5. Show naming policy
echo "5. Showing dataset naming policy..."
python3 bigquery_access_control.py \
    --project-id $PROJECT_ID \
    --show-naming-policy

echo "=== Examples completed ==="
EOF

    chmod +x example_usage.sh
    print_status "Example usage script created: example_usage.sh"
}

# Function to run basic tests
run_tests() {
    print_header "Running basic tests..."
    
    # Test Python script syntax
    if python3 -m py_compile bigquery_access_control.py; then
        print_status "Python script syntax is valid"
    else
        print_error "Python script has syntax errors"
        exit 1
    fi
    
    # Test gcloud access
    if gcloud projects describe $PROJECT_ID > /dev/null 2>&1; then
        print_status "Project access verified"
    else
        print_error "Cannot access project $PROJECT_ID"
        exit 1
    fi
    
    print_status "Basic tests passed"
}

# Function to show next steps
show_next_steps() {
    print_header "Setup completed successfully!"
    
    echo ""
    echo "Next steps:"
    echo "1. Review the configuration in bigquery_access_config.json"
    echo "2. Test the access control with: ./example_usage.sh"
    echo "3. Create user-specific datasets:"
    echo "   python3 bigquery_access_control.py --project-id $PROJECT_ID --setup-user-access user@company.com"
    echo "4. Audit existing access:"
    echo "   python3 bigquery_access_control.py --project-id $PROJECT_ID --audit-access"
    echo ""
    echo "Important notes:"
    echo "- Custom IAM roles require Organization Admin or Project Owner permissions"
    echo "- Dataset access changes may take a few minutes to propagate"
    echo "- Always test access control changes in a development environment first"
    echo ""
}

# Main execution
main() {
    echo "BigQuery Dataset Access Control Setup"
    echo "====================================="
    echo ""
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --project-id)
                PROJECT_ID="$2"
                shift 2
                ;;
            --help)
                echo "Usage: $0 [--project-id PROJECT_ID]"
                echo ""
                echo "Options:"
                echo "  --project-id    Google Cloud Project ID (optional, will use gcloud default)"
                echo "  --help          Show this help message"
                exit 0
                ;;
            *)
                print_error "Unknown option: $1"
                echo "Use --help for usage information"
                exit 1
                ;;
        esac
    done
    
    # Run setup steps
    check_prerequisites
    get_project_config
    enable_apis
    install_python_deps
    create_custom_roles
    create_sample_config
    create_example_usage
    run_tests
    show_next_steps
}

# Run main function
main "$@"