#!/usr/bin/env python3
"""
BigQuery Dataset Access Manager
This script helps manage dataset-level permissions in Google BigQuery,
allowing you to restrict access so users can only see their own datasets.
"""

import argparse
import json
from typing import List, Dict, Optional
from google.cloud import bigquery
from google.cloud.exceptions import GoogleCloudError
from google.api_core import exceptions


class BigQueryAccessManager:
    """Manages access control for BigQuery datasets."""
    
    def __init__(self, project_id: str):
        """
        Initialize the BigQuery Access Manager.
        
        Args:
            project_id: Google Cloud Project ID
        """
        self.project_id = project_id
        self.client = bigquery.Client(project=project_id)
    
    def list_datasets(self) -> List[str]:
        """
        List all datasets in the project.
        
        Returns:
            List of dataset IDs
        """
        datasets = list(self.client.list_datasets())
        return [dataset.dataset_id for dataset in datasets]
    
    def get_dataset_access(self, dataset_id: str) -> List[Dict]:
        """
        Get current access control list for a dataset.
        
        Args:
            dataset_id: The dataset ID
            
        Returns:
            List of access entries
        """
        dataset = self.client.get_dataset(dataset_id)
        access_entries = []
        
        for entry in dataset.access_entries:
            access_entry = {
                'role': entry.role,
                'entity_type': entry.entity_type,
                'entity_id': entry.entity_id
            }
            access_entries.append(access_entry)
        
        return access_entries
    
    def grant_dataset_access(self, dataset_id: str, user_email: str, 
                           role: str = "READER") -> None:
        """
        Grant access to a specific user for a dataset.
        
        Args:
            dataset_id: The dataset ID
            user_email: Email of the user to grant access
            role: Role to grant (READER, WRITER, OWNER)
        """
        dataset = self.client.get_dataset(dataset_id)
        
        # Create new access entry
        entry = bigquery.AccessEntry(
            role=role,
            entity_type="userByEmail",
            entity_id=user_email,
        )
        
        # Check if entry already exists
        entries = list(dataset.access_entries)
        for existing_entry in entries:
            if (existing_entry.entity_type == "userByEmail" and 
                existing_entry.entity_id == user_email):
                print(f"User {user_email} already has access to {dataset_id}")
                return
        
        # Add new entry
        entries.append(entry)
        dataset.access_entries = entries
        
        # Update dataset
        dataset = self.client.update_dataset(dataset, ["access_entries"])
        print(f"Granted {role} access to {user_email} for dataset {dataset_id}")
    
    def revoke_dataset_access(self, dataset_id: str, user_email: str) -> None:
        """
        Revoke access from a specific user for a dataset.
        
        Args:
            dataset_id: The dataset ID
            user_email: Email of the user to revoke access
        """
        dataset = self.client.get_dataset(dataset_id)
        
        # Filter out the user's access
        entries = []
        removed = False
        
        for entry in dataset.access_entries:
            if not (entry.entity_type == "userByEmail" and 
                   entry.entity_id == user_email):
                entries.append(entry)
            else:
                removed = True
        
        if removed:
            dataset.access_entries = entries
            dataset = self.client.update_dataset(dataset, ["access_entries"])
            print(f"Revoked access from {user_email} for dataset {dataset_id}")
        else:
            print(f"User {user_email} doesn't have access to {dataset_id}")
    
    def grant_group_access(self, dataset_id: str, group_email: str, 
                          role: str = "READER") -> None:
        """
        Grant access to a Google Group for a dataset.
        
        Args:
            dataset_id: The dataset ID
            group_email: Email of the group to grant access
            role: Role to grant (READER, WRITER, OWNER)
        """
        dataset = self.client.get_dataset(dataset_id)
        
        entry = bigquery.AccessEntry(
            role=role,
            entity_type="groupByEmail",
            entity_id=group_email,
        )
        
        entries = list(dataset.access_entries)
        entries.append(entry)
        dataset.access_entries = entries
        
        dataset = self.client.update_dataset(dataset, ["access_entries"])
        print(f"Granted {role} access to group {group_email} for dataset {dataset_id}")
    
    def grant_service_account_access(self, dataset_id: str, 
                                    service_account_email: str, 
                                    role: str = "READER") -> None:
        """
        Grant access to a service account for a dataset.
        
        Args:
            dataset_id: The dataset ID
            service_account_email: Email of the service account
            role: Role to grant (READER, WRITER, OWNER)
        """
        dataset = self.client.get_dataset(dataset_id)
        
        entry = bigquery.AccessEntry(
            role=role,
            entity_type="userByEmail",
            entity_id=service_account_email,
        )
        
        entries = list(dataset.access_entries)
        entries.append(entry)
        dataset.access_entries = entries
        
        dataset = self.client.update_dataset(dataset, ["access_entries"])
        print(f"Granted {role} access to service account {service_account_email} for dataset {dataset_id}")
    
    def setup_user_dataset(self, user_email: str, dataset_prefix: str = None) -> str:
        """
        Create a dataset for a specific user with restricted access.
        
        Args:
            user_email: Email of the user
            dataset_prefix: Optional prefix for dataset naming
            
        Returns:
            Created dataset ID
        """
        # Generate dataset ID from user email
        user_part = user_email.split('@')[0].replace('.', '_').replace('-', '_')
        
        if dataset_prefix:
            dataset_id = f"{dataset_prefix}_{user_part}"
        else:
            dataset_id = f"user_{user_part}"
        
        # Create dataset
        dataset = bigquery.Dataset(f"{self.project_id}.{dataset_id}")
        dataset.location = "US"  # Change as needed
        dataset.description = f"Dataset for user: {user_email}"
        
        # Set access controls - only the user and project owners
        access_entries = [
            bigquery.AccessEntry(
                role="OWNER",
                entity_type="userByEmail",
                entity_id=user_email,
            )
        ]
        dataset.access_entries = access_entries
        
        try:
            dataset = self.client.create_dataset(dataset)
            print(f"Created dataset {dataset_id} for user {user_email}")
            return dataset_id
        except exceptions.Conflict:
            print(f"Dataset {dataset_id} already exists")
            # Update access if dataset exists
            self.grant_dataset_access(dataset_id, user_email, "OWNER")
            return dataset_id
    
    def remove_public_access(self, dataset_id: str) -> None:
        """
        Remove all public and all authenticated users access from a dataset.
        
        Args:
            dataset_id: The dataset ID
        """
        dataset = self.client.get_dataset(dataset_id)
        
        # Filter out public access entries
        entries = []
        for entry in dataset.access_entries:
            if entry.entity_type not in ["specialGroup", "allUsers", 
                                        "allAuthenticatedUsers"]:
                entries.append(entry)
        
        dataset.access_entries = entries
        dataset = self.client.update_dataset(dataset, ["access_entries"])
        print(f"Removed public access from dataset {dataset_id}")
    
    def bulk_grant_access(self, permissions_config: Dict) -> None:
        """
        Bulk grant access based on a configuration dictionary.
        
        Args:
            permissions_config: Dictionary with dataset and user mappings
            
        Example config:
        {
            "dataset1": {
                "users": ["user1@example.com", "user2@example.com"],
                "groups": ["group1@example.com"],
                "role": "READER"
            }
        }
        """
        for dataset_id, config in permissions_config.items():
            role = config.get('role', 'READER')
            
            # Grant access to users
            for user in config.get('users', []):
                try:
                    self.grant_dataset_access(dataset_id, user, role)
                except Exception as e:
                    print(f"Error granting access to {user} for {dataset_id}: {e}")
            
            # Grant access to groups
            for group in config.get('groups', []):
                try:
                    self.grant_group_access(dataset_id, group, role)
                except Exception as e:
                    print(f"Error granting access to group {group} for {dataset_id}: {e}")
    
    def audit_dataset_access(self) -> Dict:
        """
        Audit all dataset access in the project.
        
        Returns:
            Dictionary with dataset access information
        """
        audit_report = {}
        
        for dataset_id in self.list_datasets():
            try:
                access_entries = self.get_dataset_access(dataset_id)
                audit_report[dataset_id] = {
                    'access_count': len(access_entries),
                    'entries': access_entries
                }
            except Exception as e:
                audit_report[dataset_id] = {'error': str(e)}
        
        return audit_report
    
    def create_isolated_datasets(self, users: List[str]) -> None:
        """
        Create isolated datasets for a list of users.
        
        Args:
            users: List of user emails
        """
        for user_email in users:
            try:
                dataset_id = self.setup_user_dataset(user_email)
                self.remove_public_access(dataset_id)
            except Exception as e:
                print(f"Error creating dataset for {user_email}: {e}")


def main():
    """Main function to handle command-line arguments."""
    parser = argparse.ArgumentParser(
        description='Manage BigQuery dataset access control'
    )
    parser.add_argument('--project-id', required=True, 
                       help='Google Cloud Project ID')
    parser.add_argument('--action', required=True,
                       choices=['grant', 'revoke', 'list', 'audit', 
                               'create-user-dataset', 'remove-public',
                               'bulk-grant'],
                       help='Action to perform')
    parser.add_argument('--dataset-id', help='Dataset ID')
    parser.add_argument('--user-email', help='User email address')
    parser.add_argument('--group-email', help='Group email address')
    parser.add_argument('--role', default='READER',
                       choices=['READER', 'WRITER', 'OWNER'],
                       help='Role to grant')
    parser.add_argument('--config-file', help='JSON config file for bulk operations')
    parser.add_argument('--users-file', help='File with list of user emails')
    
    args = parser.parse_args()
    
    # Initialize manager
    manager = BigQueryAccessManager(args.project_id)
    
    # Perform action
    if args.action == 'grant':
        if args.user_email and args.dataset_id:
            manager.grant_dataset_access(args.dataset_id, args.user_email, 
                                        args.role)
        elif args.group_email and args.dataset_id:
            manager.grant_group_access(args.dataset_id, args.group_email, 
                                      args.role)
        else:
            print("Error: Specify --dataset-id and either --user-email or --group-email")
    
    elif args.action == 'revoke':
        if args.user_email and args.dataset_id:
            manager.revoke_dataset_access(args.dataset_id, args.user_email)
        else:
            print("Error: Specify --dataset-id and --user-email")
    
    elif args.action == 'list':
        if args.dataset_id:
            access_list = manager.get_dataset_access(args.dataset_id)
            print(json.dumps(access_list, indent=2))
        else:
            datasets = manager.list_datasets()
            print("Datasets in project:")
            for dataset in datasets:
                print(f"  - {dataset}")
    
    elif args.action == 'audit':
        audit_report = manager.audit_dataset_access()
        print(json.dumps(audit_report, indent=2))
    
    elif args.action == 'create-user-dataset':
        if args.user_email:
            manager.setup_user_dataset(args.user_email)
        elif args.users_file:
            with open(args.users_file, 'r') as f:
                users = [line.strip() for line in f if line.strip()]
            manager.create_isolated_datasets(users)
        else:
            print("Error: Specify --user-email or --users-file")
    
    elif args.action == 'remove-public':
        if args.dataset_id:
            manager.remove_public_access(args.dataset_id)
        else:
            # Remove public access from all datasets
            for dataset_id in manager.list_datasets():
                try:
                    manager.remove_public_access(dataset_id)
                except Exception as e:
                    print(f"Error removing public access from {dataset_id}: {e}")
    
    elif args.action == 'bulk-grant':
        if args.config_file:
            with open(args.config_file, 'r') as f:
                config = json.load(f)
            manager.bulk_grant_access(config)
        else:
            print("Error: Specify --config-file for bulk operations")


if __name__ == "__main__":
    main()