# BigQuery Dataset Access Control Guide

## Overview

This guide provides comprehensive solutions for restricting BigQuery dataset access in Google Cloud Platform, ensuring users can only see and access their own datasets while preventing unauthorized access to other datasets.

## Problem Statement

In a single Google Cloud project with multiple BigQuery datasets, you need to:
- Restrict users from seeing datasets they don't own
- Allow users to access only their designated datasets
- Maintain proper security and isolation between different users' data

## Solutions Provided

### 1. Python Script (`bigquery_dataset_access_manager.py`)

A comprehensive Python tool for managing BigQuery dataset permissions programmatically.

#### Installation

```bash
# Install required dependencies
pip install google-cloud-bigquery

# Set up authentication
export GOOGLE_APPLICATION_CREDENTIALS="path/to/service-account-key.json"
# OR use gcloud auth
gcloud auth application-default login
```

#### Usage Examples

```bash
# Grant access to a specific user
python bigquery_dataset_access_manager.py \
    --project-id your-project-id \
    --action grant \
    --dataset-id user_dataset \
    --user-email john.doe@example.com \
    --role OWNER

# Create isolated dataset for a user
python bigquery_dataset_access_manager.py \
    --project-id your-project-id \
    --action create-user-dataset \
    --user-email jane.smith@example.com

# Remove public access from a dataset
python bigquery_dataset_access_manager.py \
    --project-id your-project-id \
    --action remove-public \
    --dataset-id shared_dataset

# Audit all dataset access
python bigquery_dataset_access_manager.py \
    --project-id your-project-id \
    --action audit

# Bulk grant access using config file
python bigquery_dataset_access_manager.py \
    --project-id your-project-id \
    --action bulk-grant \
    --config-file permissions.json
```

#### Bulk Configuration File Format

Create a `permissions.json` file:

```json
{
  "dataset_sales": {
    "users": ["sales_team@example.com", "analyst@example.com"],
    "groups": ["sales-group@example.com"],
    "role": "READER"
  },
  "dataset_marketing": {
    "users": ["marketing_lead@example.com"],
    "groups": ["marketing-group@example.com"],
    "role": "WRITER"
  }
}
```

### 2. Terraform Configuration (`bigquery_access_terraform/`)

Infrastructure-as-Code approach for managing BigQuery dataset access.

#### Setup

```bash
cd bigquery_access_terraform

# Initialize Terraform
terraform init

# Copy and update the example variables file
cp terraform.tfvars.example terraform.tfvars

# Plan the changes
terraform plan

# Apply the configuration
terraform apply
```

#### Key Features

- Automated creation of user-specific datasets
- Support for shared datasets with multiple access levels
- IAM role bindings with conditions
- Custom IAM roles for fine-grained access control
- Encryption support with Cloud KMS

### 3. Shell Script (`manage_bigquery_access.sh`)

Interactive and command-line tool for quick dataset access management.

#### Usage

```bash
# Make the script executable
chmod +x manage_bigquery_access.sh

# Set project ID
export GCP_PROJECT_ID="your-project-id"

# Interactive mode
./manage_bigquery_access.sh

# Command-line mode examples
./manage_bigquery_access.sh --project my-project --list
./manage_bigquery_access.sh --project my-project --audit
./manage_bigquery_access.sh --project my-project --grant dataset1 user@example.com READER
./manage_bigquery_access.sh --project my-project --create-user-dataset john.doe@example.com
./manage_bigquery_access.sh --project my-project --remove-public
```

## Best Practices for Dataset Access Control

### 1. Dataset Naming Conventions

Use clear naming conventions to identify dataset ownership:

```
user_<username>_<purpose>     # For individual users
team_<teamname>_<purpose>     # For team datasets
shared_<purpose>              # For shared resources
temp_<purpose>_<date>         # For temporary datasets
```

### 2. Access Control Hierarchy

Implement a clear access hierarchy:

1. **Dataset Owner**: Full control (OWNER role)
2. **Data Editor**: Can modify data (WRITER role)
3. **Data Viewer**: Read-only access (READER role)
4. **No Access**: Cannot see or access the dataset

### 3. Security Best Practices

#### Remove Default Access

By default, datasets may have broader access than needed. Always:

```python
# Remove public access
manager.remove_public_access(dataset_id)

# Remove all authenticated users access
# Check and remove special groups like "allAuthenticatedUsers"
```

#### Use Service Accounts

For automated processes, use service accounts with minimal required permissions:

```bash
# Create service account
gcloud iam service-accounts create bq-reader \
    --display-name="BigQuery Reader Service Account"

# Grant specific dataset access
python bigquery_dataset_access_manager.py \
    --project-id your-project \
    --action grant \
    --dataset-id my_dataset \
    --user-email bq-reader@your-project.iam.gserviceaccount.com \
    --role READER
```

#### Implement Audit Logging

Regular auditing helps identify security issues:

```bash
# Run regular audits
./manage_bigquery_access.sh --audit > audit_report_$(date +%Y%m%d).txt

# Check for public access
python bigquery_dataset_access_manager.py \
    --project-id your-project \
    --action audit | grep -i "public\|allUsers\|allAuthenticatedUsers"
```

### 4. User Isolation Strategies

#### Strategy 1: Separate Datasets per User

Create isolated datasets for each user:

```python
# Each user gets their own dataset
users = ["alice@example.com", "bob@example.com", "charlie@example.com"]
for user in users:
    manager.setup_user_dataset(user)
```

#### Strategy 2: Authorized Views

Create views that filter data based on user identity:

```sql
-- Create a view that shows only user's data
CREATE OR REPLACE VIEW `project.user_views.my_data` AS
SELECT * 
FROM `project.all_data.main_table`
WHERE user_email = SESSION_USER();
```

#### Strategy 3: Row-Level Security

Use BigQuery's row-level security policies:

```sql
-- Create row access policy
CREATE ROW ACCESS POLICY user_filter
ON `project.dataset.table`
GRANT TO ("user:alice@example.com")
FILTER USING (user_column = "alice");
```

### 5. Group-Based Access Management

Use Google Groups for easier management:

```bash
# Create groups for different access levels
# In Google Admin Console, create:
# - data-owners@yourdomain.com
# - data-editors@yourdomain.com  
# - data-viewers@yourdomain.com

# Grant access to groups instead of individuals
python bigquery_dataset_access_manager.py \
    --project-id your-project \
    --action grant \
    --dataset-id shared_dataset \
    --group-email data-viewers@yourdomain.com \
    --role READER
```

### 6. Time-Based Access Control

Implement temporary access using IAM conditions:

```terraform
resource "google_bigquery_dataset_iam_binding" "temporary_access" {
  dataset_id = "temporary_dataset"
  role      = "roles/bigquery.dataViewer"
  members   = ["user:contractor@example.com"]

  condition {
    title       = "Temporary Access"
    description = "Access expires after 30 days"
    expression  = "request.time < timestamp('2024-02-01T00:00:00Z')"
  }
}
```

### 7. Monitoring and Alerting

Set up monitoring for access changes:

```bash
# Create log sink for BigQuery access logs
gcloud logging sinks create bigquery-access-sink \
    bigquery.googleapis.com/projects/your-project/datasets/audit_logs \
    --log-filter='resource.type="bigquery_dataset" AND 
                  protoPayload.methodName=~"setIamPolicy|update"'

# Set up alerting for unauthorized access attempts
gcloud alpha monitoring policies create \
    --notification-channels=CHANNEL_ID \
    --display-name="Unauthorized BigQuery Access" \
    --condition-display-name="Failed access attempts" \
    --condition-expression='resource.type="bigquery_dataset" AND 
                           severity="ERROR" AND 
                           protoPayload.status.code=7'
```

## Common Use Cases

### 1. Multi-Tenant SaaS Application

Each customer gets their own dataset:

```python
def onboard_new_customer(customer_id, admin_email):
    dataset_id = f"customer_{customer_id}"
    
    # Create isolated dataset
    manager.setup_user_dataset(admin_email, dataset_prefix=f"customer_{customer_id}")
    
    # Remove any public access
    manager.remove_public_access(dataset_id)
    
    # Grant access to customer's team
    for user in get_customer_users(customer_id):
        manager.grant_dataset_access(dataset_id, user, "READER")
```

### 2. Department-Based Access

Different departments with isolated data:

```python
departments = {
    "finance": ["cfo@company.com", "accountant@company.com"],
    "sales": ["sales_director@company.com", "sales_team@company.com"],
    "engineering": ["cto@company.com", "dev_team@company.com"]
}

for dept, users in departments.items():
    dataset_id = f"dept_{dept}"
    # Create dataset
    create_dataset(dataset_id)
    
    # Grant access to department users
    for user in users:
        role = "OWNER" if "director" in user or "cfo" in user or "cto" in user else "READER"
        manager.grant_dataset_access(dataset_id, user, role)
```

### 3. Project-Based Access

Temporary project teams with time-limited access:

```python
def setup_project_dataset(project_name, team_members, end_date):
    dataset_id = f"project_{project_name}"
    
    # Create dataset
    create_dataset(dataset_id)
    
    # Grant temporary access to team members
    for member in team_members:
        grant_temporary_access(dataset_id, member, end_date)
```

## Troubleshooting

### Common Issues and Solutions

#### 1. "Permission Denied" Errors

```bash
# Check current permissions
bq show --format=prettyjson project:dataset | jq '.access'

# Verify user authentication
gcloud auth list

# Check project-level IAM roles
gcloud projects get-iam-policy your-project
```

#### 2. Users Can Still See Other Datasets

Users need at least `bigquery.datasets.get` permission to run queries. To completely hide datasets:

1. Remove project-level BigQuery roles
2. Grant dataset-specific access only
3. Use authorized views for cross-dataset queries

```bash
# Remove project-level viewer role
gcloud projects remove-iam-policy-binding your-project \
    --member="user:user@example.com" \
    --role="roles/bigquery.dataViewer"

# Grant only specific dataset access
python bigquery_dataset_access_manager.py \
    --project-id your-project \
    --action grant \
    --dataset-id user_dataset \
    --user-email user@example.com \
    --role READER
```

#### 3. Service Account Access Issues

```bash
# Create and download service account key
gcloud iam service-accounts keys create key.json \
    --iam-account=sa@project.iam.gserviceaccount.com

# Set environment variable
export GOOGLE_APPLICATION_CREDENTIALS="path/to/key.json"

# Test access
bq ls --project_id=your-project
```

## Requirements

### Python Script Requirements

```txt
google-cloud-bigquery>=3.0.0
google-auth>=2.0.0
```

### Terraform Requirements

```hcl
terraform {
  required_version = ">= 1.0"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
}
```

### Shell Script Requirements

- `gcloud` CLI installed and configured
- `bq` command-line tool installed
- `jq` for JSON processing (optional but recommended)

## Conclusion

By implementing these access control strategies, you can effectively restrict BigQuery dataset access so that users can only see and access their own datasets. Choose the approach that best fits your organization's needs:

- Use the **Python script** for programmatic access management
- Use **Terraform** for infrastructure-as-code and version-controlled configurations
- Use the **Shell script** for quick, interactive management tasks

Remember to regularly audit access permissions and follow the security best practices outlined in this guide.