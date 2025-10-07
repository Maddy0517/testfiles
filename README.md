# BigQuery Dataset Access Control

This repository provides a comprehensive solution for restricting BigQuery dataset access so that users can only see and access their own datasets, while maintaining the ability to share specific datasets when needed.

## Problem Statement

In a Google Cloud project with multiple BigQuery datasets, you want to:
- Prevent users from seeing datasets they shouldn't have access to
- Allow users to access only their own datasets by default
- Enable controlled sharing of specific datasets when needed
- Maintain administrative oversight and audit capabilities

## Solution Overview

This solution implements multiple strategies for dataset access control:

1. **Dataset-Level IAM Controls** - Use BigQuery's native dataset-level permissions
2. **Custom IAM Roles** - Create restricted roles with minimal necessary permissions
3. **Naming Conventions** - Organize datasets with clear ownership patterns
4. **Automated Management** - Tools for setting up and maintaining access controls

## Files in This Solution

### Core Implementation
- `bigquery_access_control.py` - Main Python script for managing dataset access
- `bigquery_access_setup.sh` - Shell script for initial setup and configuration
- `requirements.txt` - Python dependencies

### Infrastructure as Code
- `terraform_bigquery_access.tf` - Terraform configuration for infrastructure setup
- `terraform.tfvars.example` - Example variables file for Terraform

### Configuration and Policies
- `bigquery_access_policies.json` - Detailed access control policies and strategies
- `bigquery_access_config.json` - Generated configuration file (created by setup script)

### Documentation
- `README.md` - This documentation file
- `example_usage.sh` - Generated examples (created by setup script)

## Quick Start

### 1. Prerequisites

- Google Cloud Project with BigQuery API enabled
- Python 3.7+ installed
- `gcloud` CLI installed and authenticated
- Appropriate IAM permissions (Project Owner or custom permissions)

### 2. Initial Setup

Run the setup script to configure your environment:

```bash
chmod +x bigquery_access_setup.sh
./bigquery_access_setup.sh --project-id YOUR_PROJECT_ID
```

This script will:
- Enable required Google Cloud APIs
- Install Python dependencies
- Create custom IAM roles (if permissions allow)
- Generate configuration files
- Create example usage scripts

### 3. Basic Usage

#### Create a user-specific dataset:
```bash
python3 bigquery_access_control.py \
    --project-id YOUR_PROJECT_ID \
    --setup-user-access user@company.com
```

#### List datasets accessible by a user:
```bash
python3 bigquery_access_control.py \
    --project-id YOUR_PROJECT_ID \
    --list-user-datasets user@company.com
```

#### Grant access to a shared dataset:
```bash
python3 bigquery_access_control.py \
    --project-id YOUR_PROJECT_ID \
    --grant-dataset-access shared_data user@company.com
```

#### Audit all dataset access:
```bash
python3 bigquery_access_control.py \
    --project-id YOUR_PROJECT_ID \
    --audit-access
```

## Implementation Strategies

### 1. Dataset-Level Permissions (Recommended)

This approach uses BigQuery's native dataset-level IAM to control access:

**Advantages:**
- Granular control per dataset
- Native BigQuery feature
- Easy to audit and manage
- Works immediately without custom roles

**Implementation:**
- Each dataset has explicit access entries
- Users are granted access only to specific datasets
- No broad project-level BigQuery permissions

### 2. Custom IAM Roles

Create restricted IAM roles with minimal necessary permissions:

**Advantages:**
- Centralized role management
- Consistent permissions across datasets
- Easier to scale with many users

**Requirements:**
- Organization Admin or Project Owner permissions
- Custom role creation capabilities

### 3. Naming Conventions

Organize datasets with clear ownership patterns:

**User Datasets:** `{username}_{purpose}`
- Example: `john_doe_analytics`, `jane_smith_experiments`

**Shared Datasets:** `shared_{purpose}`
- Example: `shared_reference_data`, `shared_lookup_tables`

**System Datasets:** `system_{purpose}`
- Example: `system_audit_logs`, `system_monitoring`

**Temporary Datasets:** `temp_{username}_{timestamp}`
- Example: `temp_john_doe_20241007`

## Using Terraform (Infrastructure as Code)

### 1. Setup Terraform Configuration

Copy the example variables file:
```bash
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` with your project details and user list.

### 2. Deploy Infrastructure

```bash
terraform init
terraform plan
terraform apply
```

This will create:
- Custom IAM roles
- User-specific datasets
- Shared datasets with proper access controls
- System datasets for administrative use

### 3. Manage Changes

Update the `users` variable in `terraform.tfvars` and run:
```bash
terraform plan
terraform apply
```

## Access Control Patterns

### User Private Datasets
- **Pattern:** `{username}_{purpose}`
- **Access:** OWNER for user, no other access
- **Use Case:** Personal analytics, experiments, staging data

### Shared Datasets
- **Pattern:** `shared_{purpose}`
- **Access:** Explicit user grants with READER/WRITER roles
- **Use Case:** Reference data, lookup tables, shared reporting

### System Datasets
- **Pattern:** `system_{purpose}`
- **Access:** Only project owners and service accounts
- **Use Case:** Audit logs, monitoring data, system metadata

### Temporary Datasets
- **Pattern:** `temp_{username}_{timestamp}`
- **Access:** OWNER for user, automatic cleanup
- **Use Case:** Temporary analysis, data processing, testing

## Security Best Practices

### 1. Principle of Least Privilege
- Grant minimum necessary access
- Use READER role by default, WRITER only when needed
- Avoid project-level BigQuery roles

### 2. Regular Audits
- Review access permissions quarterly
- Use the audit functionality to identify unused access
- Remove access for departed team members

### 3. Access Logging
- Enable BigQuery audit logs
- Monitor for unusual access patterns
- Set up alerts for sensitive dataset access

### 4. Data Classification
- Classify datasets by sensitivity level
- Apply stricter controls to sensitive data
- Document data ownership and access requirements

### 5. Emergency Access
- Maintain break-glass procedures for emergencies
- Document emergency access processes
- Regular test emergency procedures

## Troubleshooting

### Common Issues

#### "Insufficient permissions to create custom roles"
- Custom roles require Organization Admin permissions
- Use dataset-level permissions instead
- Contact your organization administrator

#### "Dataset already exists"
- Check existing datasets with `bq ls`
- Use different naming or update existing dataset permissions
- Consider using dataset suffixes for uniqueness

#### "User cannot see their dataset"
- Verify user email is correct
- Check dataset access entries
- Ensure user has basic BigQuery permissions

#### "Access changes not taking effect"
- BigQuery access changes can take a few minutes to propagate
- Clear browser cache and retry
- Check for conflicting project-level permissions

### Getting Help

1. Check the audit output for current permissions
2. Review the generated configuration files
3. Test with a single user first before scaling
4. Use the example scripts to verify functionality

## Advanced Configuration

### Custom Access Patterns

You can extend the solution with custom access patterns:

```python
# Example: Department-based access
def setup_department_access(department, users):
    dataset_id = f"dept_{department}_data"
    for user in users:
        controller.setup_user_dataset_access(user, dataset_id, "READER")
```

### Integration with External Systems

The solution can be integrated with:
- LDAP/Active Directory for user management
- CI/CD pipelines for automated dataset creation
- Monitoring systems for access tracking
- Data governance tools for policy enforcement

### Automation

Set up automated processes for:
- New user onboarding
- Dataset cleanup
- Access reviews
- Policy compliance checking

## Contributing

To contribute to this solution:
1. Test changes in a development environment
2. Update documentation for any new features
3. Ensure backward compatibility
4. Add appropriate error handling

## License

This solution is provided as-is for educational and implementation purposes. Adapt as needed for your specific requirements and security policies.