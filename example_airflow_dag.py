"""
Example Airflow DAG for calling the PGP Encryption Cloud Function

This DAG demonstrates how to trigger the PGP encryption Cloud Function
from Google Cloud Composer / Apache Airflow.
"""

from datetime import datetime, timedelta
from airflow import DAG
from airflow.providers.google.cloud.operators.functions import CloudFunctionInvokeFunctionOperator
from airflow.operators.python import PythonOperator
from airflow.utils.dates import days_ago

# Default arguments for the DAG
default_args = {
    'owner': 'data-engineering',
    'depends_on_past': False,
    'email_on_failure': True,
    'email_on_retry': False,
    'retries': 2,
    'retry_delay': timedelta(minutes=5),
    'start_date': days_ago(1),
}

# DAG definition
dag = DAG(
    'pgp_file_encryption_workflow',
    default_args=default_args,
    description='Encrypt files in GCS using PGP encryption',
    schedule_interval=None,  # Triggered manually or by external event
    catchup=False,
    tags=['encryption', 'pgp', 'gcs', 'security'],
)

# Configuration - Update these values for your environment
GCP_PROJECT_ID = 'your-project-id'
CLOUD_FUNCTION_NAME = 'pgp-encryption-function'
CLOUD_FUNCTION_REGION = 'us-central1'
SOURCE_BUCKET = 'your-source-bucket'
TARGET_BUCKET = 'your-target-bucket'
SOURCE_FILE_PATH = 'path/to/decrypted_file.csv'
PGP_KEY_PASSPHRASE = 'your-passphrase'
SECRET_MANAGER_KEY_NAME = 'PULSE_BYOD_FILE_ENCRYPTION_KEY'


def validate_parameters(**context):
    """
    Validate that all required parameters are set.
    This is a good practice to catch configuration errors early.
    """
    required_params = {
        'GCP_PROJECT_ID': GCP_PROJECT_ID,
        'CLOUD_FUNCTION_NAME': CLOUD_FUNCTION_NAME,
        'SOURCE_BUCKET': SOURCE_BUCKET,
        'TARGET_BUCKET': TARGET_BUCKET,
        'SOURCE_FILE_PATH': SOURCE_FILE_PATH,
    }
    
    missing_params = [k for k, v in required_params.items() if not v or v.startswith('your-')]
    
    if missing_params:
        raise ValueError(f"Missing or invalid configuration parameters: {', '.join(missing_params)}")
    
    print("All parameters validated successfully")
    return True


def log_encryption_start(**context):
    """Log the start of encryption process"""
    print(f"Starting PGP encryption for file: {SOURCE_FILE_PATH}")
    print(f"Source bucket: {SOURCE_BUCKET}")
    print(f"Target bucket: {TARGET_BUCKET}")


def log_encryption_complete(**context):
    """Log the completion of encryption process"""
    ti = context['ti']
    result = ti.xcom_pull(task_ids='encrypt_file_task')
    print(f"PGP encryption completed successfully")
    print(f"Result: {result}")


# Task 1: Validate parameters
validate_params_task = PythonOperator(
    task_id='validate_parameters',
    python_callable=validate_parameters,
    dag=dag,
)

# Task 2: Log start
log_start_task = PythonOperator(
    task_id='log_encryption_start',
    python_callable=log_encryption_start,
    dag=dag,
)

# Task 3: Call Cloud Function to encrypt file
encrypt_file_task = CloudFunctionInvokeFunctionOperator(
    task_id='encrypt_file_task',
    function_name=CLOUD_FUNCTION_NAME,
    location=CLOUD_FUNCTION_REGION,
    project_id=GCP_PROJECT_ID,
    input_data={
        'Src_Bucket': SOURCE_BUCKET,
        'Tgt_Bucket': TARGET_BUCKET,
        'Src_File': SOURCE_FILE_PATH,
        'Gcs_ProjectID': GCP_PROJECT_ID,
        'passphrase': PGP_KEY_PASSPHRASE,
        'Private_encrypt_Key': SECRET_MANAGER_KEY_NAME,
    },
    dag=dag,
)

# Task 4: Log completion
log_complete_task = PythonOperator(
    task_id='log_encryption_complete',
    python_callable=log_encryption_complete,
    dag=dag,
)

# Define task dependencies
validate_params_task >> log_start_task >> encrypt_file_task >> log_complete_task


# Alternative: Dynamic DAG for multiple files
def create_encryption_dag_for_files(dag_id, schedule, file_list):
    """
    Create a DAG that processes multiple files in parallel.
    
    Args:
        dag_id: Unique identifier for the DAG
        schedule: Cron schedule or None for manual trigger
        file_list: List of dictionaries with file information
    """
    
    dag = DAG(
        dag_id,
        default_args=default_args,
        description='Encrypt multiple files in parallel',
        schedule_interval=schedule,
        catchup=False,
        tags=['encryption', 'pgp', 'batch'],
    )
    
    with dag:
        start = PythonOperator(
            task_id='start',
            python_callable=lambda: print("Starting batch encryption"),
        )
        
        end = PythonOperator(
            task_id='end',
            python_callable=lambda: print("Batch encryption complete"),
        )
        
        # Create a task for each file
        encrypt_tasks = []
        for idx, file_info in enumerate(file_list):
            task = CloudFunctionInvokeFunctionOperator(
                task_id=f'encrypt_file_{idx}',
                function_name=CLOUD_FUNCTION_NAME,
                location=CLOUD_FUNCTION_REGION,
                project_id=GCP_PROJECT_ID,
                input_data={
                    'Src_Bucket': file_info.get('source_bucket', SOURCE_BUCKET),
                    'Tgt_Bucket': file_info.get('target_bucket', TARGET_BUCKET),
                    'Src_File': file_info['file_path'],
                    'Gcs_ProjectID': GCP_PROJECT_ID,
                    'passphrase': PGP_KEY_PASSPHRASE,
                    'Private_encrypt_Key': SECRET_MANAGER_KEY_NAME,
                },
            )
            encrypt_tasks.append(task)
        
        # All files processed in parallel
        start >> encrypt_tasks >> end
    
    return dag


# Example: Create a batch DAG for multiple files
batch_files = [
    {'file_path': 'data/file1.csv'},
    {'file_path': 'data/file2.csv'},
    {'file_path': 'data/file3.csv'},
]

# Uncomment to enable batch processing DAG
# batch_dag = create_encryption_dag_for_files(
#     dag_id='pgp_batch_encryption',
#     schedule=None,
#     file_list=batch_files
# )


# Alternative: Using Airflow variables for configuration
# This is more flexible for production environments
"""
from airflow.models import Variable

# Store configuration in Airflow Variables
config = Variable.get('pgp_encryption_config', deserialize_json=True)

encrypt_file_from_vars = CloudFunctionInvokeFunctionOperator(
    task_id='encrypt_file_from_variables',
    function_name=config['function_name'],
    location=config['region'],
    project_id=config['project_id'],
    input_data={
        'Src_Bucket': config['source_bucket'],
        'Tgt_Bucket': config['target_bucket'],
        'Src_File': '{{ dag_run.conf.file_path }}',  # From trigger config
        'Gcs_ProjectID': config['project_id'],
        'passphrase': config['passphrase'],
        'Private_encrypt_Key': config['secret_name'],
    },
    dag=dag,
)
"""
