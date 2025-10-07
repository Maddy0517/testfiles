"""
BigQuery Dataset Deletion Examples using Python Client Library
"""

from google.cloud import bigquery
from google.cloud.exceptions import NotFound
import logging

# Set up logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class BigQueryDatasetManager:
    def __init__(self, project_id: str):
        """Initialize BigQuery client"""
        self.client = bigquery.Client(project=project_id)
        self.project_id = project_id
    
    def drop_dataset_with_contents(self, dataset_id: str, delete_contents: bool = True):
        """
        Drop a dataset and optionally its contents
        
        Args:
            dataset_id: The ID of the dataset to delete
            delete_contents: If True, delete all tables in the dataset first
        """
        dataset_ref = self.client.dataset(dataset_id)
        
        try:
            # Check if dataset exists
            dataset = self.client.get_dataset(dataset_ref)
            logger.info(f"Found dataset: {dataset.dataset_id}")
            
            # Delete the dataset
            self.client.delete_dataset(
                dataset_ref, 
                delete_contents=delete_contents,
                not_found_ok=True
            )
            logger.info(f"Successfully deleted dataset: {dataset_id}")
            
        except NotFound:
            logger.warning(f"Dataset {dataset_id} not found")
        except Exception as e:
            logger.error(f"Error deleting dataset {dataset_id}: {str(e)}")
            raise
    
    def list_dataset_contents(self, dataset_id: str):
        """List all tables and views in a dataset"""
        dataset_ref = self.client.dataset(dataset_id)
        
        try:
            tables = list(self.client.list_tables(dataset_ref))
            logger.info(f"Dataset {dataset_id} contains {len(tables)} tables/views:")
            
            for table in tables:
                logger.info(f"  - {table.table_id} ({table.table_type})")
            
            return tables
            
        except NotFound:
            logger.warning(f"Dataset {dataset_id} not found")
            return []
    
    def drop_tables_individually(self, dataset_id: str):
        """Drop all tables in a dataset individually before dropping the dataset"""
        dataset_ref = self.client.dataset(dataset_id)
        
        try:
            # List all tables
            tables = list(self.client.list_tables(dataset_ref))
            
            # Delete each table
            for table in tables:
                table_ref = dataset_ref.table(table.table_id)
                self.client.delete_table(table_ref, not_found_ok=True)
                logger.info(f"Deleted table: {table.table_id}")
            
            # Now delete the empty dataset
            self.client.delete_dataset(dataset_ref, delete_contents=False)
            logger.info(f"Deleted empty dataset: {dataset_id}")
            
        except NotFound:
            logger.warning(f"Dataset {dataset_id} not found")
        except Exception as e:
            logger.error(f"Error in individual deletion: {str(e)}")
            raise
    
    def safe_drop_multiple_datasets(self, dataset_ids: list):
        """Safely drop multiple datasets"""
        results = {}
        
        for dataset_id in dataset_ids:
            try:
                self.drop_dataset_with_contents(dataset_id, delete_contents=True)
                results[dataset_id] = "SUCCESS"
            except Exception as e:
                results[dataset_id] = f"FAILED: {str(e)}"
                logger.error(f"Failed to delete {dataset_id}: {str(e)}")
        
        return results
    
    def create_backup_before_drop(self, source_dataset_id: str, backup_dataset_id: str):
        """Create a backup of dataset before dropping"""
        try:
            # Create backup dataset
            backup_dataset = bigquery.Dataset(self.client.dataset(backup_dataset_id))
            backup_dataset.location = "US"  # Adjust location as needed
            backup_dataset = self.client.create_dataset(backup_dataset, exists_ok=True)
            logger.info(f"Created backup dataset: {backup_dataset_id}")
            
            # List tables in source dataset
            source_dataset_ref = self.client.dataset(source_dataset_id)
            tables = list(self.client.list_tables(source_dataset_ref))
            
            # Copy each table to backup dataset
            for table in tables:
                if table.table_type == "TABLE":
                    source_table_ref = source_dataset_ref.table(table.table_id)
                    backup_table_ref = backup_dataset.dataset_id + "." + table.table_id
                    
                    # Create copy job
                    job_config = bigquery.CopyJobConfig()
                    job = self.client.copy_table(source_table_ref, backup_table_ref, job_config=job_config)
                    job.result()  # Wait for job to complete
                    
                    logger.info(f"Backed up table: {table.table_id}")
            
            logger.info(f"Backup completed for dataset: {source_dataset_id}")
            
        except Exception as e:
            logger.error(f"Backup failed: {str(e)}")
            raise


# Example usage
def main():
    """Example usage of the BigQueryDatasetManager"""
    
    # Initialize manager
    project_id = "your-project-id"  # Replace with your project ID
    manager = BigQueryDatasetManager(project_id)
    
    # Example 1: List contents before deletion
    dataset_to_delete = "test_dataset"
    print(f"\n=== Listing contents of {dataset_to_delete} ===")
    manager.list_dataset_contents(dataset_to_delete)
    
    # Example 2: Create backup before deletion
    print(f"\n=== Creating backup ===")
    try:
        manager.create_backup_before_drop(dataset_to_delete, f"{dataset_to_delete}_backup")
    except Exception as e:
        print(f"Backup failed: {e}")
    
    # Example 3: Drop dataset with all contents
    print(f"\n=== Dropping dataset with contents ===")
    manager.drop_dataset_with_contents(dataset_to_delete, delete_contents=True)
    
    # Example 4: Drop multiple datasets
    print(f"\n=== Dropping multiple datasets ===")
    datasets_to_drop = ["dataset1", "dataset2", "dataset3"]
    results = manager.safe_drop_multiple_datasets(datasets_to_drop)
    
    for dataset, result in results.items():
        print(f"{dataset}: {result}")


# SQL equivalents as comments for reference
"""
SQL Equivalents:

1. Drop dataset with contents:
   DROP SCHEMA IF EXISTS `project_id.dataset_name` CASCADE;

2. Drop empty dataset:
   DROP SCHEMA IF EXISTS `project_id.dataset_name`;

3. List dataset contents:
   SELECT table_name, table_type 
   FROM `project_id.dataset_name.INFORMATION_SCHEMA.TABLES`;

4. Drop individual table:
   DROP TABLE IF EXISTS `project_id.dataset_name.table_name`;

5. Check if dataset exists:
   SELECT schema_name 
   FROM `project_id.INFORMATION_SCHEMA.SCHEMATA` 
   WHERE schema_name = 'dataset_name';
"""

if __name__ == "__main__":
    main()