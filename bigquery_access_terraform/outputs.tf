# Outputs for BigQuery dataset access configuration

output "user_dataset_ids" {
  description = "IDs of created user-specific datasets"
  value = {
    for k, v in google_bigquery_dataset.user_datasets : k => v.dataset_id
  }
}

output "shared_dataset_ids" {
  description = "IDs of created shared datasets"
  value = {
    for k, v in google_bigquery_dataset.shared_datasets : k => v.dataset_id
  }
}

output "user_dataset_self_links" {
  description = "Self links of created user datasets"
  value = {
    for k, v in google_bigquery_dataset.user_datasets : k => v.self_link
  }
}

output "dataset_access_summary" {
  description = "Summary of dataset access configurations"
  value = {
    user_datasets = {
      for k, v in var.user_datasets : k => {
        user  = v.user_email
        role  = v.role
      }
    }
    shared_datasets = {
      for k, v in var.shared_datasets : k => {
        user_count           = length(v.users)
        group_count          = length(v.groups)
        service_account_count = length(v.service_accounts)
      }
    }
  }
}