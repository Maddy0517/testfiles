# Terraform configuration for BigQuery dataset access control
# This file defines infrastructure for restricting BigQuery dataset access

terraform {
  required_version = ">= 1.0"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 4.0"
    }
  }
}

# Variables
variable "project_id" {
  description = "Google Cloud Project ID"
  type        = string
}

variable "organization_id" {
  description = "Google Cloud Organization ID (optional)"
  type        = string
  default     = ""
}

variable "region" {
  description = "Default region for resources"
  type        = string
  default     = "us-central1"
}

variable "users" {
  description = "List of users who need dataset access"
  type = list(object({
    email       = string
    role        = string # "viewer" or "editor"
    datasets    = list(string)
  }))
  default = []
}

# Provider configuration
provider "google" {
  project = var.project_id
  region  = var.region
}

# Enable required APIs
resource "google_project_service" "required_apis" {
  for_each = toset([
    "bigquery.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "iam.googleapis.com"
  ])

  project = var.project_id
  service = each.value

  disable_dependent_services = false
  disable_on_destroy        = false
}

# Custom IAM role for restricted dataset viewer
resource "google_project_iam_custom_role" "bigquery_dataset_viewer_restricted" {
  role_id     = "bigquery.datasetViewer.restricted"
  title       = "BigQuery Dataset Viewer (Restricted)"
  description = "Can view only assigned datasets in BigQuery"
  stage       = "GA"

  permissions = [
    "bigquery.datasets.get",
    "bigquery.tables.list",
    "bigquery.tables.get",
    "bigquery.tables.getData",
    "bigquery.jobs.create",
    "bigquery.jobs.list",
    "bigquery.jobs.get"
  ]

  depends_on = [google_project_service.required_apis]
}

# Custom IAM role for restricted dataset editor
resource "google_project_iam_custom_role" "bigquery_dataset_editor_restricted" {
  role_id     = "bigquery.datasetEditor.restricted"
  title       = "BigQuery Dataset Editor (Restricted)"
  description = "Can edit only assigned datasets in BigQuery"
  stage       = "GA"

  permissions = [
    "bigquery.datasets.get",
    "bigquery.datasets.update",
    "bigquery.tables.list",
    "bigquery.tables.get",
    "bigquery.tables.create",
    "bigquery.tables.update",
    "bigquery.tables.delete",
    "bigquery.tables.getData",
    "bigquery.tables.updateData",
    "bigquery.jobs.create",
    "bigquery.jobs.list",
    "bigquery.jobs.get"
  ]

  depends_on = [google_project_service.required_apis]
}

# Create user-specific datasets
resource "google_bigquery_dataset" "user_datasets" {
  for_each = {
    for user in var.users : user.email => user
  }

  dataset_id  = replace(replace(split("@", each.value.email)[0], ".", "_"), "-", "_")
  description = "Private dataset for user ${each.value.email}"
  location    = "US"

  # Set access control - only the user can access their dataset
  access {
    role          = "OWNER"
    user_by_email = each.value.email
  }

  # Optional: Add project owners as dataset owners for administrative access
  access {
    role         = "OWNER"
    special_group = "projectOwners"
  }

  depends_on = [google_project_service.required_apis]
}

# Shared datasets (accessible by multiple users)
resource "google_bigquery_dataset" "shared_datasets" {
  for_each = toset([
    "shared_reference_data",
    "shared_lookup_tables",
    "shared_reporting"
  ])

  dataset_id  = each.value
  description = "Shared dataset: ${each.value}"
  location    = "US"

  # Default access for project owners
  access {
    role         = "OWNER"
    special_group = "projectOwners"
  }

  # Add specific user access based on configuration
  dynamic "access" {
    for_each = {
      for user in var.users : user.email => user
      if contains(user.datasets, each.value)
    }
    content {
      role          = access.value.role == "editor" ? "WRITER" : "READER"
      user_by_email = access.value.email
    }
  }

  depends_on = [google_project_service.required_apis]
}

# System datasets (for administrative purposes)
resource "google_bigquery_dataset" "system_datasets" {
  for_each = toset([
    "system_audit_logs",
    "system_monitoring",
    "system_metadata"
  ])

  dataset_id  = each.value
  description = "System dataset: ${each.value}"
  location    = "US"

  # Only project owners and service accounts can access system datasets
  access {
    role         = "OWNER"
    special_group = "projectOwners"
  }

  depends_on = [google_project_service.required_apis]
}

# Data source to get current project information
data "google_project" "current" {
  project_id = var.project_id
}

# Output information
output "project_info" {
  description = "Project information"
  value = {
    project_id     = var.project_id
    project_number = data.google_project.current.number
    organization_id = var.organization_id
  }
}

output "custom_roles" {
  description = "Created custom IAM roles"
  value = {
    viewer_role = google_project_iam_custom_role.bigquery_dataset_viewer_restricted.name
    editor_role = google_project_iam_custom_role.bigquery_dataset_editor_restricted.name
  }
}

output "created_datasets" {
  description = "Created datasets"
  value = {
    user_datasets   = keys(google_bigquery_dataset.user_datasets)
    shared_datasets = keys(google_bigquery_dataset.shared_datasets)
    system_datasets = keys(google_bigquery_dataset.system_datasets)
  }
}

output "access_control_summary" {
  description = "Summary of access control setup"
  value = {
    total_users = length(var.users)
    user_datasets_created = length(google_bigquery_dataset.user_datasets)
    shared_datasets_created = length(google_bigquery_dataset.shared_datasets)
    system_datasets_created = length(google_bigquery_dataset.system_datasets)
  }
}