/**
 * Terraform configuration for managing BigQuery dataset access control
 * This configuration helps create isolated datasets with user-specific permissions
 */

terraform {
  required_version = ">= 1.0"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
}

# Configure the Google Cloud Provider
provider "google" {
  project = var.project_id
  region  = var.region
}

# Variables
variable "project_id" {
  description = "Google Cloud Project ID"
  type        = string
}

variable "region" {
  description = "Default region for resources"
  type        = string
  default     = "us-central1"
}

variable "dataset_location" {
  description = "Location for BigQuery datasets"
  type        = string
  default     = "US"
}

variable "user_datasets" {
  description = "Map of users and their dataset configurations"
  type = map(object({
    user_email  = string
    role        = string
    description = string
  }))
  default = {}
}

variable "shared_datasets" {
  description = "Datasets with multiple user access"
  type = map(object({
    description = string
    users = list(object({
      email = string
      role  = string
    }))
    groups = list(object({
      email = string
      role  = string
    }))
    service_accounts = list(object({
      email = string
      role  = string
    }))
  }))
  default = {}
}

# Create user-specific datasets with restricted access
resource "google_bigquery_dataset" "user_datasets" {
  for_each = var.user_datasets

  dataset_id  = each.key
  project     = var.project_id
  location    = var.dataset_location
  description = each.value.description

  # Set default table expiration (optional)
  default_table_expiration_ms = 2592000000 # 30 days in milliseconds

  # Default encryption configuration
  default_encryption_configuration {
    kms_key_name = var.kms_key_name
  }

  # Access control - only specific user has access
  access {
    role          = each.value.role
    user_by_email = each.value.user_email
  }

  # Project owners always have access
  access {
    role           = "OWNER"
    special_group  = "projectOwners"
  }

  labels = {
    environment = "production"
    owner       = replace(each.value.user_email, "@", "_at_")
    managed_by  = "terraform"
  }
}

# Create shared datasets with multiple user access
resource "google_bigquery_dataset" "shared_datasets" {
  for_each = var.shared_datasets

  dataset_id  = each.key
  project     = var.project_id
  location    = var.dataset_location
  description = each.value.description

  # Default table expiration
  default_table_expiration_ms = 2592000000

  # Default encryption
  default_encryption_configuration {
    kms_key_name = var.kms_key_name
  }

  # Dynamic access blocks for users
  dynamic "access" {
    for_each = each.value.users
    content {
      role          = access.value.role
      user_by_email = access.value.email
    }
  }

  # Dynamic access blocks for groups
  dynamic "access" {
    for_each = each.value.groups
    content {
      role           = access.value.role
      group_by_email = access.value.email
    }
  }

  # Dynamic access blocks for service accounts
  dynamic "access" {
    for_each = each.value.service_accounts
    content {
      role          = access.value.role
      user_by_email = access.value.email
    }
  }

  # Project owners always have access
  access {
    role          = "OWNER"
    special_group = "projectOwners"
  }

  labels = {
    environment = "production"
    type        = "shared"
    managed_by  = "terraform"
  }
}

# Create authorized views for cross-dataset access
resource "google_bigquery_dataset" "authorized_views_dataset" {
  dataset_id  = "authorized_views"
  project     = var.project_id
  location    = var.dataset_location
  description = "Dataset containing authorized views for controlled data access"

  # This dataset can be accessed by all authenticated users
  # but the views inside control what data they can see
  access {
    role          = "READER"
    special_group = "allAuthenticatedUsers"
  }

  access {
    role          = "OWNER"
    special_group = "projectOwners"
  }

  labels = {
    environment = "production"
    type        = "views"
    managed_by  = "terraform"
  }
}

# IAM bindings for dataset-level permissions
resource "google_bigquery_dataset_iam_binding" "dataset_viewers" {
  for_each = var.user_datasets

  dataset_id = google_bigquery_dataset.user_datasets[each.key].dataset_id
  role       = "roles/bigquery.dataViewer"

  members = [
    "user:${each.value.user_email}"
  ]
}

resource "google_bigquery_dataset_iam_binding" "dataset_editors" {
  for_each = var.user_datasets

  dataset_id = google_bigquery_dataset.user_datasets[each.key].dataset_id
  role       = "roles/bigquery.dataEditor"

  members = [
    "user:${each.value.user_email}"
  ]

  condition {
    title       = "Restrict to business hours"
    description = "Only allow edits during business hours"
    expression  = "request.time.getHours(\"America/New_York\") >= 9 && request.time.getHours(\"America/New_York\") <= 17"
  }
}

# Custom IAM role for limited dataset access
resource "google_project_iam_custom_role" "bigquery_limited_viewer" {
  role_id     = "bigqueryLimitedViewer"
  title       = "BigQuery Limited Viewer"
  description = "Can only view specific datasets and run queries"
  
  permissions = [
    "bigquery.datasets.get",
    "bigquery.tables.get",
    "bigquery.tables.getData",
    "bigquery.tables.list",
    "bigquery.jobs.create",
  ]
}

# Variables for encryption
variable "kms_key_name" {
  description = "KMS key name for dataset encryption"
  type        = string
  default     = ""
}