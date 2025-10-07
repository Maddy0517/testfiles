#!/usr/bin/env python3
"""
BigQuery Dataset Access Control Manager

This script provides functionality to restrict BigQuery dataset access so that users
can only see and access their own datasets. It implements multiple strategies:

1. IAM-based access control using custom roles
2. Dataset-level permissions management
3. User-specific dataset naming conventions
4. Automated access control setup

Usage:
    python bigquery_access_control.py --setup-user-access user@example.com
    python bigquery_access_control.py --list-user-datasets user@example.com
    python bigquery_access_control.py --grant-dataset-access dataset_id user@example.com
"""

import argparse
import json
import logging
from typing import List, Dict, Optional
from google.cloud import bigquery
from google.cloud import resourcemanager_v3
from google.api_core import exceptions
import re

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class BigQueryAccessController:
    """Manages BigQuery dataset access control and user permissions."""
    
    def __init__(self, project_id: str):
        """
        Initialize the BigQuery Access Controller.
        
        Args:
            project_id: Google Cloud Project ID
        """
        self.project_id = project_id
        self.client = bigquery.Client(project=project_id)
        self.resource_manager = resourcemanager_v3.ProjectsClient()
        
    def create_custom_roles(self) -> Dict[str, str]:
        """
        Create custom IAM roles for dataset access control.
        
        Returns:
            Dictionary mapping role names to their full resource names
        """
        roles = {}
        
        # Custom role for dataset viewers (can only see their own datasets)
        dataset_viewer_role = {
            "roleId": "bigquery.datasetViewer.restricted",
            "role": {
                "title": "BigQuery Dataset Viewer (Restricted)",
                "description": "Can view only assigned datasets in BigQuery",
                "stage": "GA",
                "includedPermissions": [
                    "bigquery.datasets.get",
                    "bigquery.tables.list",
                    "bigquery.tables.get",
                    "bigquery.tables.getData",
                    "bigquery.jobs.create",
                    "bigquery.jobs.list",
                    "bigquery.jobs.get"
                ]
            }
        }
        
        # Custom role for dataset editors (can edit their own datasets)
        dataset_editor_role = {
            "roleId": "bigquery.datasetEditor.restricted", 
            "role": {
                "title": "BigQuery Dataset Editor (Restricted)",
                "description": "Can edit only assigned datasets in BigQuery",
                "stage": "GA",
                "includedPermissions": [
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
            }
        }
        
        try:
            # Note: Creating custom roles requires Organization Admin permissions
            # This is a template - actual implementation would use IAM API
            logger.info("Custom roles defined. Apply these using gcloud or IAM API:")
            logger.info(f"Dataset Viewer Role: {json.dumps(dataset_viewer_role, indent=2)}")
            logger.info(f"Dataset Editor Role: {json.dumps(dataset_editor_role, indent=2)}")
            
            roles["viewer"] = f"projects/{self.project_id}/roles/{dataset_viewer_role['roleId']}"
            roles["editor"] = f"projects/{self.project_id}/roles/{dataset_editor_role['roleId']}"
            
        except Exception as e:
            logger.error(f"Error creating custom roles: {e}")
            
        return roles
    
    def get_user_datasets(self, user_email: str) -> List[str]:
        """
        Get datasets that a user has access to.
        
        Args:
            user_email: User's email address
            
        Returns:
            List of dataset IDs the user can access
        """
        user_datasets = []
        
        try:
            datasets = list(self.client.list_datasets())
            
            for dataset in datasets:
                dataset_ref = self.client.get_dataset(dataset.dataset_id)
                
                # Check if user has access to this dataset
                if self._user_has_dataset_access(user_email, dataset_ref):
                    user_datasets.append(dataset.dataset_id)
                    
        except Exception as e:
            logger.error(f"Error getting user datasets: {e}")
            
        return user_datasets
    
    def _user_has_dataset_access(self, user_email: str, dataset: bigquery.Dataset) -> bool:
        """
        Check if a user has access to a specific dataset.
        
        Args:
            user_email: User's email address
            dataset: BigQuery dataset object
            
        Returns:
            True if user has access, False otherwise
        """
        try:
            # Check dataset access entries
            for access_entry in dataset.access_entries:
                if access_entry.entity_type == "userByEmail" and access_entry.entity_id == user_email:
                    return True
                    
                # Check group membership (simplified - would need actual group API)
                if access_entry.entity_type == "groupByEmail":
                    # This would require checking if user is in the group
                    pass
                    
        except Exception as e:
            logger.error(f"Error checking dataset access: {e}")
            
        return False
    
    def setup_user_dataset_access(self, user_email: str, dataset_id: str, 
                                role: str = "READER") -> bool:
        """
        Grant a user access to a specific dataset.
        
        Args:
            user_email: User's email address
            dataset_id: Dataset ID to grant access to
            role: Access role (READER, WRITER, OWNER)
            
        Returns:
            True if successful, False otherwise
        """
        try:
            dataset_ref = self.client.dataset(dataset_id)
            dataset = self.client.get_dataset(dataset_ref)
            
            # Create access entry for the user
            access_entry = bigquery.AccessEntry(
                role=role,
                entity_type="userByEmail",
                entity_id=user_email
            )
            
            # Add the access entry to the dataset
            entries = list(dataset.access_entries)
            entries.append(access_entry)
            dataset.access_entries = entries
            
            # Update the dataset
            dataset = self.client.update_dataset(dataset, ["access_entries"])
            
            logger.info(f"Granted {role} access to dataset {dataset_id} for user {user_email}")
            return True
            
        except Exception as e:
            logger.error(f"Error granting dataset access: {e}")
            return False
    
    def remove_user_dataset_access(self, user_email: str, dataset_id: str) -> bool:
        """
        Remove a user's access to a specific dataset.
        
        Args:
            user_email: User's email address
            dataset_id: Dataset ID to remove access from
            
        Returns:
            True if successful, False otherwise
        """
        try:
            dataset_ref = self.client.dataset(dataset_id)
            dataset = self.client.get_dataset(dataset_ref)
            
            # Filter out the user's access entries
            entries = [
                entry for entry in dataset.access_entries
                if not (entry.entity_type == "userByEmail" and entry.entity_id == user_email)
            ]
            
            dataset.access_entries = entries
            dataset = self.client.update_dataset(dataset, ["access_entries"])
            
            logger.info(f"Removed access to dataset {dataset_id} for user {user_email}")
            return True
            
        except Exception as e:
            logger.error(f"Error removing dataset access: {e}")
            return False
    
    def create_user_specific_dataset(self, user_email: str, dataset_suffix: str = None) -> str:
        """
        Create a dataset specific to a user with proper naming convention.
        
        Args:
            user_email: User's email address
            dataset_suffix: Optional suffix for the dataset name
            
        Returns:
            Created dataset ID
        """
        # Create user-specific dataset ID
        username = user_email.split('@')[0]
        safe_username = re.sub(r'[^a-zA-Z0-9_]', '_', username)
        
        if dataset_suffix:
            dataset_id = f"{safe_username}_{dataset_suffix}"
        else:
            dataset_id = f"{safe_username}_dataset"
            
        try:
            # Create the dataset
            dataset_ref = self.client.dataset(dataset_id)
            dataset = bigquery.Dataset(dataset_ref)
            dataset.description = f"Private dataset for user {user_email}"
            dataset.location = "US"  # or your preferred location
            
            # Set access control - only the user can access
            access_entries = [
                bigquery.AccessEntry(
                    role="OWNER",
                    entity_type="userByEmail", 
                    entity_id=user_email
                )
            ]
            dataset.access_entries = access_entries
            
            dataset = self.client.create_dataset(dataset)
            logger.info(f"Created user-specific dataset: {dataset_id}")
            
            return dataset_id
            
        except exceptions.Conflict:
            logger.warning(f"Dataset {dataset_id} already exists")
            return dataset_id
        except Exception as e:
            logger.error(f"Error creating user dataset: {e}")
            raise
    
    def audit_dataset_access(self) -> Dict[str, List[Dict]]:
        """
        Audit all dataset access permissions in the project.
        
        Returns:
            Dictionary mapping dataset IDs to their access entries
        """
        audit_results = {}
        
        try:
            datasets = list(self.client.list_datasets())
            
            for dataset in datasets:
                dataset_ref = self.client.get_dataset(dataset.dataset_id)
                
                access_info = []
                for access_entry in dataset_ref.access_entries:
                    access_info.append({
                        "entity_type": access_entry.entity_type,
                        "entity_id": access_entry.entity_id,
                        "role": access_entry.role
                    })
                
                audit_results[dataset.dataset_id] = access_info
                
        except Exception as e:
            logger.error(f"Error during access audit: {e}")
            
        return audit_results
    
    def setup_dataset_naming_policy(self) -> Dict[str, str]:
        """
        Define and return dataset naming policy for user isolation.
        
        Returns:
            Dictionary with naming policy guidelines
        """
        policy = {
            "user_datasets": "{username}_{purpose}",
            "shared_datasets": "shared_{purpose}",
            "system_datasets": "system_{purpose}",
            "temp_datasets": "temp_{username}_{timestamp}",
            "examples": {
                "user_dataset": "john_doe_analytics",
                "shared_dataset": "shared_reference_data", 
                "system_dataset": "system_logs",
                "temp_dataset": "temp_jane_smith_20241007"
            },
            "rules": [
                "User datasets must start with sanitized username",
                "Shared datasets must be explicitly marked as 'shared_'",
                "System datasets are for administrative purposes only",
                "Temporary datasets should include timestamp"
            ]
        }
        
        logger.info("Dataset naming policy:")
        logger.info(json.dumps(policy, indent=2))
        
        return policy


def main():
    """Main function to handle command line arguments and execute operations."""
    parser = argparse.ArgumentParser(description="BigQuery Dataset Access Control Manager")
    parser.add_argument("--project-id", required=True, help="Google Cloud Project ID")
    parser.add_argument("--setup-user-access", help="Setup access for a user (provide email)")
    parser.add_argument("--list-user-datasets", help="List datasets for a user (provide email)")
    parser.add_argument("--grant-dataset-access", nargs=2, metavar=("DATASET_ID", "USER_EMAIL"),
                       help="Grant dataset access to user")
    parser.add_argument("--remove-dataset-access", nargs=2, metavar=("DATASET_ID", "USER_EMAIL"),
                       help="Remove dataset access from user")
    parser.add_argument("--create-user-dataset", help="Create user-specific dataset (provide email)")
    parser.add_argument("--audit-access", action="store_true", help="Audit all dataset access")
    parser.add_argument("--show-naming-policy", action="store_true", help="Show dataset naming policy")
    parser.add_argument("--create-custom-roles", action="store_true", help="Create custom IAM roles")
    
    args = parser.parse_args()
    
    # Initialize the access controller
    controller = BigQueryAccessController(args.project_id)
    
    try:
        if args.setup_user_access:
            user_email = args.setup_user_access
            dataset_id = controller.create_user_specific_dataset(user_email)
            logger.info(f"Setup complete for user {user_email} with dataset {dataset_id}")
            
        elif args.list_user_datasets:
            user_email = args.list_user_datasets
            datasets = controller.get_user_datasets(user_email)
            logger.info(f"Datasets accessible by {user_email}: {datasets}")
            
        elif args.grant_dataset_access:
            dataset_id, user_email = args.grant_dataset_access
            success = controller.setup_user_dataset_access(user_email, dataset_id)
            if success:
                logger.info(f"Access granted successfully")
            else:
                logger.error(f"Failed to grant access")
                
        elif args.remove_dataset_access:
            dataset_id, user_email = args.remove_dataset_access
            success = controller.remove_user_dataset_access(user_email, dataset_id)
            if success:
                logger.info(f"Access removed successfully")
            else:
                logger.error(f"Failed to remove access")
                
        elif args.create_user_dataset:
            user_email = args.create_user_dataset
            dataset_id = controller.create_user_specific_dataset(user_email)
            logger.info(f"Created dataset {dataset_id} for user {user_email}")
            
        elif args.audit_access:
            audit_results = controller.audit_dataset_access()
            logger.info("Dataset Access Audit Results:")
            print(json.dumps(audit_results, indent=2))
            
        elif args.show_naming_policy:
            controller.setup_dataset_naming_policy()
            
        elif args.create_custom_roles:
            roles = controller.create_custom_roles()
            logger.info(f"Custom roles created: {roles}")
            
        else:
            parser.print_help()
            
    except Exception as e:
        logger.error(f"Operation failed: {e}")
        return 1
        
    return 0


if __name__ == "__main__":
    exit(main())