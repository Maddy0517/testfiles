#!/usr/bin/env python3
"""
BigQuery Dataset and Content Dropping Operations using Python Client Library

Requirements:
    pip install google-cloud-bigquery
"""

from google.cloud import bigquery
from google.cloud.exceptions import NotFound
from typing import List, Optional
import logging
from datetime import datetime, timedelta

# Set up logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


class BigQueryDropManager:
    """Manages BigQuery drop operations for datasets and their contents."""
    
    def __init__(self, project_id: str):
        """
        Initialize the BigQuery client.
        
        Args:
            project_id: GCP project ID
        """
        self.client = bigquery.Client(project=project_id)
        self.project_id = project_id
    
    def drop_table(self, dataset_id: str, table_id: str, not_found_ok: bool = True) -> bool:
        """
        Drop a single table.
        
        Args:
            dataset_id: Dataset containing the table
            table_id: Table to drop
            not_found_ok: If True, don't raise error if table doesn't exist
        
        Returns:
            True if successful, False otherwise
        """
        table_ref = f"{self.project_id}.{dataset_id}.{table_id}"
        
        try:
            self.client.delete_table(table_ref, not_found_ok=not_found_ok)
            logger.info(f"Dropped table: {table_ref}")
            return True
        except Exception as e:
            logger.error(f"Failed to drop table {table_ref}: {e}")
            return False
    
    def drop_view(self, dataset_id: str, view_id: str, not_found_ok: bool = True) -> bool:
        """
        Drop a view.
        
        Args:
            dataset_id: Dataset containing the view
            view_id: View to drop
            not_found_ok: If True, don't raise error if view doesn't exist
        
        Returns:
            True if successful, False otherwise
        """
        # Views are deleted the same way as tables in the Python client
        return self.drop_table(dataset_id, view_id, not_found_ok)
    
    def drop_model(self, dataset_id: str, model_id: str, not_found_ok: bool = True) -> bool:
        """
        Drop a machine learning model.
        
        Args:
            dataset_id: Dataset containing the model
            model_id: Model to drop
            not_found_ok: If True, don't raise error if model doesn't exist
        
        Returns:
            True if successful, False otherwise
        """
        model_ref = f"{self.project_id}.{dataset_id}.{model_id}"
        
        try:
            self.client.delete_model(model_ref, not_found_ok=not_found_ok)
            logger.info(f"Dropped model: {model_ref}")
            return True
        except Exception as e:
            logger.error(f"Failed to drop model {model_ref}: {e}")
            return False
    
    def drop_routine(self, dataset_id: str, routine_id: str, not_found_ok: bool = True) -> bool:
        """
        Drop a routine (function or stored procedure).
        
        Args:
            dataset_id: Dataset containing the routine
            routine_id: Routine to drop
            not_found_ok: If True, don't raise error if routine doesn't exist
        
        Returns:
            True if successful, False otherwise
        """
        routine_ref = f"{self.project_id}.{dataset_id}.{routine_id}"
        
        try:
            self.client.delete_routine(routine_ref, not_found_ok=not_found_ok)
            logger.info(f"Dropped routine: {routine_ref}")
            return True
        except Exception as e:
            logger.error(f"Failed to drop routine {routine_ref}: {e}")
            return False
    
    def drop_all_tables_in_dataset(self, dataset_id: str, include_views: bool = True) -> int:
        """
        Drop all tables (and optionally views) in a dataset.
        
        Args:
            dataset_id: Dataset to clear
            include_views: Also drop views if True
        
        Returns:
            Number of tables/views dropped
        """
        dataset_ref = self.client.dataset(dataset_id)
        dropped_count = 0
        
        try:
            tables = self.client.list_tables(dataset_ref)
            
            for table in tables:
                if table.table_type == "VIEW" and not include_views:
                    logger.info(f"Skipping view: {table.table_id}")
                    continue
                
                if self.drop_table(dataset_id, table.table_id):
                    dropped_count += 1
            
            logger.info(f"Dropped {dropped_count} tables/views from dataset {dataset_id}")
            return dropped_count
            
        except NotFound:
            logger.error(f"Dataset {dataset_id} not found")
            return 0
        except Exception as e:
            logger.error(f"Error dropping tables in dataset {dataset_id}: {e}")
            return dropped_count
    
    def drop_tables_by_prefix(self, dataset_id: str, prefix: str) -> int:
        """
        Drop all tables with a specific prefix.
        
        Args:
            dataset_id: Dataset containing the tables
            prefix: Table name prefix to match
        
        Returns:
            Number of tables dropped
        """
        dataset_ref = self.client.dataset(dataset_id)
        dropped_count = 0
        
        try:
            tables = self.client.list_tables(dataset_ref)
            
            for table in tables:
                if table.table_id.startswith(prefix):
                    if self.drop_table(dataset_id, table.table_id):
                        dropped_count += 1
            
            logger.info(f"Dropped {dropped_count} tables with prefix '{prefix}'")
            return dropped_count
            
        except Exception as e:
            logger.error(f"Error dropping tables by prefix: {e}")
            return dropped_count
    
    def drop_old_tables(self, dataset_id: str, days_old: int) -> int:
        """
        Drop tables older than specified number of days.
        
        Args:
            dataset_id: Dataset containing the tables
            days_old: Age threshold in days
        
        Returns:
            Number of tables dropped
        """
        dataset_ref = self.client.dataset(dataset_id)
        dropped_count = 0
        cutoff_time = datetime.now() - timedelta(days=days_old)
        
        try:
            tables = self.client.list_tables(dataset_ref)
            
            for table in tables:
                table_full = self.client.get_table(f"{self.project_id}.{dataset_id}.{table.table_id}")
                
                if table_full.created and table_full.created.replace(tzinfo=None) < cutoff_time:
                    logger.info(f"Table {table.table_id} created on {table_full.created} - dropping")
                    if self.drop_table(dataset_id, table.table_id):
                        dropped_count += 1
            
            logger.info(f"Dropped {dropped_count} tables older than {days_old} days")
            return dropped_count
            
        except Exception as e:
            logger.error(f"Error dropping old tables: {e}")
            return dropped_count
    
    def drop_dataset(self, dataset_id: str, delete_contents: bool = False, 
                    not_found_ok: bool = True) -> bool:
        """
        Drop an entire dataset.
        
        Args:
            dataset_id: Dataset to drop
            delete_contents: If True, delete all tables/views first (CASCADE)
            not_found_ok: If True, don't raise error if dataset doesn't exist
        
        Returns:
            True if successful, False otherwise
        """
        dataset_ref = self.client.dataset(dataset_id)
        
        try:
            self.client.delete_dataset(
                dataset_ref,
                delete_contents=delete_contents,
                not_found_ok=not_found_ok
            )
            logger.info(f"Dropped dataset: {dataset_id}")
            return True
        except Exception as e:
            logger.error(f"Failed to drop dataset {dataset_id}: {e}")
            return False
    
    def backup_table(self, source_dataset: str, source_table: str, 
                    backup_dataset: str, backup_table: Optional[str] = None) -> bool:
        """
        Create a backup of a table before dropping.
        
        Args:
            source_dataset: Source dataset
            source_table: Source table to backup
            backup_dataset: Destination dataset for backup
            backup_table: Backup table name (defaults to source_table_backup_YYYYMMDD)
        
        Returns:
            True if successful, False otherwise
        """
        if backup_table is None:
            backup_table = f"{source_table}_backup_{datetime.now().strftime('%Y%m%d')}"
        
        source_ref = f"{self.project_id}.{source_dataset}.{source_table}"
        backup_ref = f"{self.project_id}.{backup_dataset}.{backup_table}"
        
        try:
            query = f"CREATE TABLE `{backup_ref}` AS SELECT * FROM `{source_ref}`"
            query_job = self.client.query(query)
            query_job.result()  # Wait for the query to complete
            
            logger.info(f"Created backup: {backup_ref}")
            return True
        except Exception as e:
            logger.error(f"Failed to backup table: {e}")
            return False
    
    def execute_drop_sql(self, sql: str) -> bool:
        """
        Execute a custom DROP SQL statement.
        
        Args:
            sql: SQL DROP statement to execute
        
        Returns:
            True if successful, False otherwise
        """
        try:
            query_job = self.client.query(sql)
            query_job.result()  # Wait for the query to complete
            logger.info(f"Executed SQL: {sql}")
            return True
        except Exception as e:
            logger.error(f"Failed to execute SQL: {e}")
            return False
    
    def list_dataset_contents(self, dataset_id: str) -> dict:
        """
        List all contents of a dataset.
        
        Args:
            dataset_id: Dataset to inspect
        
        Returns:
            Dictionary with lists of tables, views, models, and routines
        """
        contents = {
            "tables": [],
            "views": [],
            "models": [],
            "routines": []
        }
        
        try:
            dataset_ref = self.client.dataset(dataset_id)
            
            # List tables and views
            tables = self.client.list_tables(dataset_ref)
            for table in tables:
                if table.table_type == "VIEW":
                    contents["views"].append(table.table_id)
                else:
                    contents["tables"].append(table.table_id)
            
            # List models
            models = self.client.list_models(dataset_ref)
            for model in models:
                contents["models"].append(model.model_id)
            
            # List routines
            routines = self.client.list_routines(dataset_ref)
            for routine in routines:
                contents["routines"].append(routine.routine_id)
            
            return contents
            
        except Exception as e:
            logger.error(f"Error listing dataset contents: {e}")
            return contents


def main():
    """Example usage of the BigQueryDropManager class."""
    
    # Initialize the manager
    project_id = "your-project-id"
    manager = BigQueryDropManager(project_id)
    
    # Example 1: Drop a single table
    manager.drop_table("analytics_dataset", "temp_table")
    
    # Example 2: Drop all tables with a prefix
    manager.drop_tables_by_prefix("analytics_dataset", "temp_")
    
    # Example 3: Drop tables older than 30 days
    manager.drop_old_tables("analytics_dataset", days_old=30)
    
    # Example 4: Backup and drop a table
    manager.backup_table(
        source_dataset="production",
        source_table="important_data",
        backup_dataset="backups"
    )
    manager.drop_table("production", "important_data")
    
    # Example 5: List dataset contents before dropping
    contents = manager.list_dataset_contents("test_dataset")
    print(f"Dataset contents: {contents}")
    
    # Example 6: Drop all contents in a dataset
    manager.drop_all_tables_in_dataset("test_dataset")
    
    # Example 7: Drop entire dataset with all contents (CASCADE)
    manager.drop_dataset("test_dataset", delete_contents=True)
    
    # Example 8: Execute custom DROP SQL
    manager.execute_drop_sql(
        "DROP TABLE IF EXISTS `your-project-id.dataset_name.table_name`"
    )


if __name__ == "__main__":
    main()