#!/bin/bash

################################################################################
# BigQuery Dataset Access Management Script
# This script provides utilities to manage dataset-level access in BigQuery
################################################################################

set -euo pipefail

# Configuration
PROJECT_ID="${GCP_PROJECT_ID:-}"
DATASET_LOCATION="${DATASET_LOCATION:-US}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    if ! command -v gcloud &> /dev/null; then
        log_error "gcloud CLI is not installed. Please install it first."
        exit 1
    fi
    
    if ! command -v bq &> /dev/null; then
        log_error "bq CLI is not installed. Please install it first."
        exit 1
    fi
    
    if [ -z "$PROJECT_ID" ]; then
        log_error "PROJECT_ID is not set. Set GCP_PROJECT_ID environment variable or use --project flag"
        exit 1
    fi
    
    # Check authentication
    if ! gcloud auth application-default print-access-token &> /dev/null; then
        log_warn "Not authenticated. Running gcloud auth login..."
        gcloud auth application-default login
    fi
    
    # Set default project
    gcloud config set project "$PROJECT_ID"
}

# List all datasets in the project
list_datasets() {
    log_info "Listing datasets in project: $PROJECT_ID"
    bq ls --project_id="$PROJECT_ID" --format=prettyjson | jq -r '.[] | .datasetReference.datasetId'
}

# Get current access for a dataset
get_dataset_access() {
    local dataset_id=$1
    log_info "Getting access list for dataset: $dataset_id"
    bq show --format=prettyjson "$PROJECT_ID:$dataset_id" | jq '.access'
}

# Grant user access to a dataset
grant_user_access() {
    local dataset_id=$1
    local user_email=$2
    local role=${3:-READER}
    
    log_info "Granting $role access to $user_email for dataset $dataset_id"
    
    # Get current access
    current_access=$(bq show --format=json "$PROJECT_ID:$dataset_id" | jq '.access')
    
    # Check if user already has access
    if echo "$current_access" | jq -e ".[] | select(.userByEmail == \"$user_email\")" > /dev/null; then
        log_warn "User $user_email already has access to $dataset_id"
        return 0
    fi
    
    # Add new access entry
    new_entry="{\"role\": \"$role\", \"userByEmail\": \"$user_email\"}"
    updated_access=$(echo "$current_access" | jq ". + [$new_entry]")
    
    # Update dataset with new access
    echo "$updated_access" > /tmp/access.json
    bq update --source /tmp/access.json "$PROJECT_ID:$dataset_id"
    rm /tmp/access.json
    
    log_info "Successfully granted access"
}

# Revoke user access from a dataset
revoke_user_access() {
    local dataset_id=$1
    local user_email=$2
    
    log_info "Revoking access from $user_email for dataset $dataset_id"
    
    # Get current access
    current_access=$(bq show --format=json "$PROJECT_ID:$dataset_id" | jq '.access')
    
    # Remove user access
    updated_access=$(echo "$current_access" | jq "map(select(.userByEmail != \"$user_email\"))")
    
    # Update dataset with new access
    echo "$updated_access" > /tmp/access.json
    bq update --source /tmp/access.json "$PROJECT_ID:$dataset_id"
    rm /tmp/access.json
    
    log_info "Successfully revoked access"
}

# Create a dataset for a specific user
create_user_dataset() {
    local user_email=$1
    local dataset_prefix=${2:-user}
    
    # Generate dataset ID from email
    local user_part=$(echo "$user_email" | cut -d@ -f1 | tr '.-' '_')
    local dataset_id="${dataset_prefix}_${user_part}"
    
    log_info "Creating dataset $dataset_id for user $user_email"
    
    # Create dataset
    bq mk --location="$DATASET_LOCATION" \
        --description="Dataset for user: $user_email" \
        --project_id="$PROJECT_ID" \
        "$dataset_id"
    
    # Set access - remove all default access and add only the user
    access_json="[
        {\"role\": \"OWNER\", \"userByEmail\": \"$user_email\"},
        {\"role\": \"OWNER\", \"specialGroup\": \"projectOwners\"}
    ]"
    
    echo "$access_json" > /tmp/access.json
    bq update --source /tmp/access.json "$PROJECT_ID:$dataset_id"
    rm /tmp/access.json
    
    log_info "Successfully created dataset $dataset_id with exclusive access for $user_email"
}

# Remove public access from all datasets
remove_all_public_access() {
    log_info "Removing public access from all datasets in project $PROJECT_ID"
    
    for dataset_id in $(list_datasets); do
        log_info "Processing dataset: $dataset_id"
        
        # Get current access
        current_access=$(bq show --format=json "$PROJECT_ID:$dataset_id" | jq '.access')
        
        # Filter out public access entries
        updated_access=$(echo "$current_access" | jq 'map(select(
            .specialGroup != "allUsers" and 
            .specialGroup != "allAuthenticatedUsers"
        ))')
        
        # Update dataset
        echo "$updated_access" > /tmp/access.json
        bq update --source /tmp/access.json "$PROJECT_ID:$dataset_id"
        
        log_info "Removed public access from $dataset_id"
    done
    
    rm -f /tmp/access.json
}

# Bulk create datasets from a file
bulk_create_user_datasets() {
    local users_file=$1
    
    if [ ! -f "$users_file" ]; then
        log_error "File $users_file not found"
        exit 1
    fi
    
    log_info "Creating datasets for users from $users_file"
    
    while IFS= read -r user_email; do
        if [ -n "$user_email" ]; then
            create_user_dataset "$user_email"
        fi
    done < "$users_file"
}

# Audit all dataset access
audit_dataset_access() {
    log_info "Auditing dataset access for project $PROJECT_ID"
    
    echo "Dataset Access Audit Report"
    echo "==========================="
    echo ""
    
    for dataset_id in $(list_datasets); do
        echo "Dataset: $dataset_id"
        echo "-------------------"
        
        access_list=$(get_dataset_access "$dataset_id")
        
        # Count different types of access
        user_count=$(echo "$access_list" | jq '[.[] | select(.userByEmail)] | length')
        group_count=$(echo "$access_list" | jq '[.[] | select(.groupByEmail)] | length')
        special_count=$(echo "$access_list" | jq '[.[] | select(.specialGroup)] | length')
        view_count=$(echo "$access_list" | jq '[.[] | select(.view)] | length')
        
        echo "  Users: $user_count"
        echo "  Groups: $group_count"
        echo "  Special Groups: $special_count"
        echo "  Views: $view_count"
        
        # Check for public access
        if echo "$access_list" | jq -e '.[] | select(.specialGroup == "allUsers" or .specialGroup == "allAuthenticatedUsers")' > /dev/null; then
            echo "  ⚠️  WARNING: Dataset has public access!"
        fi
        
        echo ""
    done
}

# Create authorized view for controlled access
create_authorized_view() {
    local source_dataset=$1
    local source_table=$2
    local view_dataset=$3
    local view_name=$4
    local where_clause=$5
    
    log_info "Creating authorized view $view_name in dataset $view_dataset"
    
    # Create the view
    query="SELECT * FROM \`$PROJECT_ID.$source_dataset.$source_table\` WHERE $where_clause"
    
    bq mk --use_legacy_sql=false \
        --view="$query" \
        --project_id="$PROJECT_ID" \
        "$view_dataset.$view_name"
    
    log_info "Successfully created view $view_name"
}

# Apply dataset labels for organization
apply_dataset_labels() {
    local dataset_id=$1
    shift
    local labels="$@"
    
    log_info "Applying labels to dataset $dataset_id"
    
    label_args=""
    for label in $labels; do
        label_args="$label_args --set_label $label"
    done
    
    bq update $label_args "$PROJECT_ID:$dataset_id"
    
    log_info "Successfully applied labels"
}

# Main menu
show_menu() {
    echo ""
    echo "BigQuery Dataset Access Manager"
    echo "================================"
    echo "1. List all datasets"
    echo "2. Show dataset access"
    echo "3. Grant user access to dataset"
    echo "4. Revoke user access from dataset"
    echo "5. Create user-specific dataset"
    echo "6. Remove public access from all datasets"
    echo "7. Bulk create user datasets from file"
    echo "8. Audit all dataset access"
    echo "9. Exit"
    echo ""
    read -p "Select an option: " choice
}

# Process menu choice
process_choice() {
    case $choice in
        1)
            list_datasets
            ;;
        2)
            read -p "Enter dataset ID: " dataset_id
            get_dataset_access "$dataset_id"
            ;;
        3)
            read -p "Enter dataset ID: " dataset_id
            read -p "Enter user email: " user_email
            read -p "Enter role (READER/WRITER/OWNER) [READER]: " role
            role=${role:-READER}
            grant_user_access "$dataset_id" "$user_email" "$role"
            ;;
        4)
            read -p "Enter dataset ID: " dataset_id
            read -p "Enter user email: " user_email
            revoke_user_access "$dataset_id" "$user_email"
            ;;
        5)
            read -p "Enter user email: " user_email
            read -p "Enter dataset prefix [user]: " prefix
            prefix=${prefix:-user}
            create_user_dataset "$user_email" "$prefix"
            ;;
        6)
            read -p "This will remove public access from ALL datasets. Continue? (y/N): " confirm
            if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
                remove_all_public_access
            fi
            ;;
        7)
            read -p "Enter path to users file: " users_file
            bulk_create_user_datasets "$users_file"
            ;;
        8)
            audit_dataset_access
            ;;
        9)
            exit 0
            ;;
        *)
            log_error "Invalid option"
            ;;
    esac
}

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --project)
                PROJECT_ID="$2"
                shift 2
                ;;
            --location)
                DATASET_LOCATION="$2"
                shift 2
                ;;
            --list)
                check_prerequisites
                list_datasets
                exit 0
                ;;
            --audit)
                check_prerequisites
                audit_dataset_access
                exit 0
                ;;
            --grant)
                check_prerequisites
                grant_user_access "$2" "$3" "${4:-READER}"
                exit 0
                ;;
            --revoke)
                check_prerequisites
                revoke_user_access "$2" "$3"
                exit 0
                ;;
            --create-user-dataset)
                check_prerequisites
                create_user_dataset "$2" "${3:-user}"
                exit 0
                ;;
            --remove-public)
                check_prerequisites
                remove_all_public_access
                exit 0
                ;;
            --help)
                cat << EOF
Usage: $0 [OPTIONS]

Options:
    --project PROJECT_ID              Set GCP project ID
    --location LOCATION               Set dataset location (default: US)
    --list                           List all datasets
    --audit                          Audit all dataset access
    --grant DATASET USER [ROLE]      Grant user access to dataset
    --revoke DATASET USER            Revoke user access from dataset
    --create-user-dataset USER       Create dataset for specific user
    --remove-public                  Remove public access from all datasets
    --help                           Show this help message

Interactive mode:
    Run without arguments to enter interactive mode

Examples:
    $0 --project my-project --list
    $0 --project my-project --grant my_dataset user@example.com READER
    $0 --project my-project --create-user-dataset john.doe@example.com
    $0 --project my-project --audit
EOF
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                exit 1
                ;;
        esac
    done
}

# Main execution
main() {
    # If arguments provided, parse them
    if [ $# -gt 0 ]; then
        parse_args "$@"
    fi
    
    # Otherwise, run interactive mode
    check_prerequisites
    
    while true; do
        show_menu
        process_choice
    done
}

# Run main function
main "$@"