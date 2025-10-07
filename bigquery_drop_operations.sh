#!/bin/bash

# BigQuery Drop Operations using bq Command-Line Tool
# =====================================================
#
# Prerequisites:
# - Google Cloud SDK installed (gcloud)
# - BigQuery command-line tool (bq) installed
# - Authenticated with: gcloud auth login
# - Project set: gcloud config set project PROJECT_ID

# Set your project ID
PROJECT_ID="your-project-id"
DATASET_ID="your-dataset"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored messages
print_message() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Function to confirm destructive operations
confirm_action() {
    local action=$1
    read -p "Are you sure you want to ${action}? (yes/no): " confirm
    if [[ $confirm != "yes" ]]; then
        print_message $YELLOW "Operation cancelled."
        return 1
    fi
    return 0
}

# ============================================
# DROP INDIVIDUAL TABLES
# ============================================

drop_table() {
    local dataset=$1
    local table=$2
    
    print_message $YELLOW "Dropping table: ${dataset}.${table}"
    
    # Using --force flag to skip confirmation
    bq rm -f -t "${PROJECT_ID}:${dataset}.${table}"
    
    if [ $? -eq 0 ]; then
        print_message $GREEN "Successfully dropped table: ${table}"
    else
        print_message $RED "Failed to drop table: ${table}"
    fi
}

# ============================================
# DROP VIEWS
# ============================================

drop_view() {
    local dataset=$1
    local view=$2
    
    print_message $YELLOW "Dropping view: ${dataset}.${view}"
    
    # Views are dropped the same way as tables
    bq rm -f -t "${PROJECT_ID}:${dataset}.${view}"
    
    if [ $? -eq 0 ]; then
        print_message $GREEN "Successfully dropped view: ${view}"
    else
        print_message $RED "Failed to drop view: ${view}"
    fi
}

# ============================================
# DROP MODELS
# ============================================

drop_model() {
    local dataset=$1
    local model=$2
    
    print_message $YELLOW "Dropping model: ${dataset}.${model}"
    
    bq rm -f -m "${PROJECT_ID}:${dataset}.${model}"
    
    if [ $? -eq 0 ]; then
        print_message $GREEN "Successfully dropped model: ${model}"
    else
        print_message $RED "Failed to drop model: ${model}"
    fi
}

# ============================================
# DROP ROUTINES (Functions/Procedures)
# ============================================

drop_routine() {
    local dataset=$1
    local routine=$2
    
    print_message $YELLOW "Dropping routine: ${dataset}.${routine}"
    
    bq rm -f --routine "${PROJECT_ID}:${dataset}.${routine}"
    
    if [ $? -eq 0 ]; then
        print_message $GREEN "Successfully dropped routine: ${routine}"
    else
        print_message $RED "Failed to drop routine: ${routine}"
    fi
}

# ============================================
# DROP ALL TABLES IN A DATASET
# ============================================

drop_all_tables_in_dataset() {
    local dataset=$1
    
    if ! confirm_action "drop ALL tables in dataset ${dataset}"; then
        return 1
    fi
    
    print_message $YELLOW "Fetching all tables in dataset: ${dataset}"
    
    # List all tables and iterate through them
    tables=$(bq ls -t --max_results=10000 --format=json "${PROJECT_ID}:${dataset}" | \
             jq -r '.[] | select(.type=="TABLE") | .tableReference.tableId')
    
    if [ -z "$tables" ]; then
        print_message $YELLOW "No tables found in dataset: ${dataset}"
        return 0
    fi
    
    for table in $tables; do
        drop_table "$dataset" "$table"
    done
}

# ============================================
# DROP ALL VIEWS IN A DATASET
# ============================================

drop_all_views_in_dataset() {
    local dataset=$1
    
    if ! confirm_action "drop ALL views in dataset ${dataset}"; then
        return 1
    fi
    
    print_message $YELLOW "Fetching all views in dataset: ${dataset}"
    
    # List all views and iterate through them
    views=$(bq ls -t --max_results=10000 --format=json "${PROJECT_ID}:${dataset}" | \
            jq -r '.[] | select(.type=="VIEW") | .tableReference.tableId')
    
    if [ -z "$views" ]; then
        print_message $YELLOW "No views found in dataset: ${dataset}"
        return 0
    fi
    
    for view in $views; do
        drop_view "$dataset" "$view"
    done
}

# ============================================
# DROP TABLES BY PREFIX
# ============================================

drop_tables_by_prefix() {
    local dataset=$1
    local prefix=$2
    
    print_message $YELLOW "Dropping tables with prefix '${prefix}' in dataset: ${dataset}"
    
    # List tables with specific prefix
    tables=$(bq ls -t --max_results=10000 --format=json "${PROJECT_ID}:${dataset}" | \
             jq -r --arg prefix "$prefix" '.[] | select(.tableReference.tableId | startswith($prefix)) | .tableReference.tableId')
    
    if [ -z "$tables" ]; then
        print_message $YELLOW "No tables found with prefix: ${prefix}"
        return 0
    fi
    
    for table in $tables; do
        drop_table "$dataset" "$table"
    done
}

# ============================================
# DROP ENTIRE DATASET
# ============================================

drop_dataset() {
    local dataset=$1
    local recursive=$2  # true or false
    
    if ! confirm_action "drop entire dataset ${dataset}"; then
        return 1
    fi
    
    print_message $YELLOW "Dropping dataset: ${dataset}"
    
    if [ "$recursive" == "true" ]; then
        # Drop dataset and all its contents
        bq rm -r -f -d "${PROJECT_ID}:${dataset}"
    else
        # Drop dataset only if empty
        bq rm -f -d "${PROJECT_ID}:${dataset}"
    fi
    
    if [ $? -eq 0 ]; then
        print_message $GREEN "Successfully dropped dataset: ${dataset}"
    else
        print_message $RED "Failed to drop dataset: ${dataset}"
    fi
}

# ============================================
# BACKUP TABLE BEFORE DROPPING
# ============================================

backup_and_drop_table() {
    local source_dataset=$1
    local source_table=$2
    local backup_dataset=$3
    
    local backup_table="${source_table}_backup_$(date +%Y%m%d_%H%M%S)"
    
    print_message $YELLOW "Creating backup of table: ${source_table}"
    
    # Create backup using bq cp
    bq cp -f "${PROJECT_ID}:${source_dataset}.${source_table}" \
             "${PROJECT_ID}:${backup_dataset}.${backup_table}"
    
    if [ $? -eq 0 ]; then
        print_message $GREEN "Backup created: ${backup_dataset}.${backup_table}"
        
        # Now drop the original table
        drop_table "$source_dataset" "$source_table"
    else
        print_message $RED "Failed to create backup. Original table not dropped."
    fi
}

# ============================================
# DROP OLD TABLES (Older than N days)
# ============================================

drop_old_tables() {
    local dataset=$1
    local days_old=$2
    
    if ! confirm_action "drop tables older than ${days_old} days in dataset ${dataset}"; then
        return 1
    fi
    
    print_message $YELLOW "Finding tables older than ${days_old} days..."
    
    # Calculate timestamp in milliseconds for comparison
    local cutoff_timestamp=$(($(date -d "${days_old} days ago" +%s) * 1000))
    
    # Get all tables with their creation time
    tables_json=$(bq ls -t --max_results=10000 --format=json "${PROJECT_ID}:${dataset}")
    
    # Parse and filter old tables
    old_tables=$(echo "$tables_json" | jq -r --arg cutoff "$cutoff_timestamp" \
                 '.[] | select(.creationTime < ($cutoff | tonumber)) | .tableReference.tableId')
    
    if [ -z "$old_tables" ]; then
        print_message $YELLOW "No tables older than ${days_old} days found"
        return 0
    fi
    
    for table in $old_tables; do
        drop_table "$dataset" "$table"
    done
}

# ============================================
# LIST DATASET CONTENTS
# ============================================

list_dataset_contents() {
    local dataset=$1
    
    print_message $GREEN "Contents of dataset: ${dataset}"
    
    echo -e "\n${YELLOW}Tables:${NC}"
    bq ls -t --max_results=10000 "${PROJECT_ID}:${dataset}" | grep TABLE || echo "No tables found"
    
    echo -e "\n${YELLOW}Views:${NC}"
    bq ls -t --max_results=10000 "${PROJECT_ID}:${dataset}" | grep VIEW || echo "No views found"
    
    echo -e "\n${YELLOW}Models:${NC}"
    bq ls -m --max_results=10000 "${PROJECT_ID}:${dataset}" 2>/dev/null || echo "No models found"
    
    echo -e "\n${YELLOW}Routines:${NC}"
    bq ls --routines --max_results=10000 "${PROJECT_ID}:${dataset}" 2>/dev/null || echo "No routines found"
}

# ============================================
# EXECUTE DROP SQL
# ============================================

execute_drop_sql() {
    local sql=$1
    
    print_message $YELLOW "Executing SQL: ${sql}"
    
    bq query --use_legacy_sql=false "$sql"
    
    if [ $? -eq 0 ]; then
        print_message $GREEN "SQL executed successfully"
    else
        print_message $RED "SQL execution failed"
    fi
}

# ============================================
# INTERACTIVE MENU
# ============================================

show_menu() {
    echo -e "\n${GREEN}=== BigQuery Drop Operations Menu ===${NC}"
    echo "1. Drop a single table"
    echo "2. Drop a single view"
    echo "3. Drop a model"
    echo "4. Drop a routine"
    echo "5. Drop all tables in a dataset"
    echo "6. Drop all views in a dataset"
    echo "7. Drop tables by prefix"
    echo "8. Drop entire dataset (empty)"
    echo "9. Drop entire dataset (with contents)"
    echo "10. Backup and drop table"
    echo "11. Drop old tables"
    echo "12. List dataset contents"
    echo "13. Execute custom DROP SQL"
    echo "14. Exit"
}

# ============================================
# MAIN SCRIPT
# ============================================

main() {
    # Check if bq command is available
    if ! command -v bq &> /dev/null; then
        print_message $RED "Error: bq command not found. Please install Google Cloud SDK."
        exit 1
    fi
    
    # Check if jq is available (for JSON parsing)
    if ! command -v jq &> /dev/null; then
        print_message $YELLOW "Warning: jq not found. Some features may not work properly."
        print_message $YELLOW "Install with: sudo apt-get install jq (or appropriate package manager)"
    fi
    
    while true; do
        show_menu
        read -p "Select an option (1-14): " choice
        
        case $choice in
            1)
                read -p "Enter dataset name: " dataset
                read -p "Enter table name: " table
                drop_table "$dataset" "$table"
                ;;
            2)
                read -p "Enter dataset name: " dataset
                read -p "Enter view name: " view
                drop_view "$dataset" "$view"
                ;;
            3)
                read -p "Enter dataset name: " dataset
                read -p "Enter model name: " model
                drop_model "$dataset" "$model"
                ;;
            4)
                read -p "Enter dataset name: " dataset
                read -p "Enter routine name: " routine
                drop_routine "$dataset" "$routine"
                ;;
            5)
                read -p "Enter dataset name: " dataset
                drop_all_tables_in_dataset "$dataset"
                ;;
            6)
                read -p "Enter dataset name: " dataset
                drop_all_views_in_dataset "$dataset"
                ;;
            7)
                read -p "Enter dataset name: " dataset
                read -p "Enter table prefix: " prefix
                drop_tables_by_prefix "$dataset" "$prefix"
                ;;
            8)
                read -p "Enter dataset name: " dataset
                drop_dataset "$dataset" "false"
                ;;
            9)
                read -p "Enter dataset name: " dataset
                drop_dataset "$dataset" "true"
                ;;
            10)
                read -p "Enter source dataset: " source_dataset
                read -p "Enter source table: " source_table
                read -p "Enter backup dataset: " backup_dataset
                backup_and_drop_table "$source_dataset" "$source_table" "$backup_dataset"
                ;;
            11)
                read -p "Enter dataset name: " dataset
                read -p "Enter age threshold (days): " days
                drop_old_tables "$dataset" "$days"
                ;;
            12)
                read -p "Enter dataset name: " dataset
                list_dataset_contents "$dataset"
                ;;
            13)
                read -p "Enter DROP SQL statement: " sql
                execute_drop_sql "$sql"
                ;;
            14)
                print_message $GREEN "Exiting..."
                exit 0
                ;;
            *)
                print_message $RED "Invalid option. Please try again."
                ;;
        esac
    done
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi