# dbt BigQuery Project Setup Guide

## Prerequisites

1. **Google Cloud Platform Setup**
   - Create a GCP Project
   - Enable BigQuery API
   - Create a service account with BigQuery Admin role
   - Download the service account JSON key

2. **Local Environment Setup**
   ```bash
   # Install Python 3.7+
   python --version
   
   # Create virtual environment
   python -m venv dbt_env
   source dbt_env/bin/activate  # On Windows: dbt_env\Scripts\activate
   
   # Install dbt-bigquery
   pip install dbt-bigquery
   ```

## Step 1: Configure BigQuery Connection

1. Copy the example profiles file:
   ```bash
   cp profiles.yml.example ~/.dbt/profiles.yml
   ```

2. Edit `~/.dbt/profiles.yml` and update:
   - `project`: Your GCP project ID
   - `dataset`: Your BigQuery dataset name
   - `keyfile`: Path to your service account JSON (for service-account method)
   - `location`: Your BigQuery data location

3. Test the connection:
   ```bash
   dbt debug
   ```

## Step 2: Set Up Source Data

1. **Create BigQuery Datasets**:
   ```sql
   -- In BigQuery Console or using bq CLI
   CREATE SCHEMA IF NOT EXISTS `your-project.raw_ecommerce`;
   CREATE SCHEMA IF NOT EXISTS `your-project.external_data`;
   ```

2. **Load Sample Data** (optional):
   ```bash
   # Using bq CLI to load sample data
   bq load --source_format=CSV \
     raw_ecommerce.customers \
     gs://your-bucket/customers.csv \
     customer_id:STRING,email:STRING,first_name:STRING,last_name:STRING
   ```

## Step 3: Initialize dbt Project

1. **Install dependencies**:
   ```bash
   # Install dbt packages (if using dbt-utils)
   dbt deps
   ```

2. **Load seed data**:
   ```bash
   dbt seed
   ```

3. **Run models**:
   ```bash
   # Run all models
   dbt run
   
   # Run specific models
   dbt run --select staging
   dbt run --select +fct_orders  # Run fct_orders and its dependencies
   ```

## Step 4: Test Your Models

```bash
# Run all tests
dbt test

# Run tests for specific models
dbt test --select fct_orders

# Run and test together
dbt build
```

## Step 5: Generate Documentation

```bash
# Generate documentation
dbt docs generate

# Serve documentation locally
dbt docs serve --port 8080
```

## Common Development Workflows

### 1. Developing a New Model

```bash
# 1. Create your SQL file in the appropriate folder
# 2. Run just your model
dbt run --select my_new_model

# 3. Add tests to schema.yml
# 4. Run tests
dbt test --select my_new_model

# 5. Generate docs
dbt docs generate
```

### 2. Debugging a Model

```bash
# Compile to see the actual SQL
dbt compile --select problematic_model

# Show the compiled SQL
dbt show --select problematic_model

# Run with more verbose logging
dbt --debug run --select problematic_model
```

### 3. Working with Incremental Models

```bash
# Normal incremental run
dbt run --select my_incremental_model

# Full refresh (rebuild from scratch)
dbt run --full-refresh --select my_incremental_model
```

### 4. Using Different Environments

```bash
# Run in development (default)
dbt run

# Run in production
dbt run --target prod

# Run in CI environment
dbt run --target ci
```

## Project Structure

```
.
├── analyses/           # Ad-hoc analytical queries
├── data/              # CSV seed files
├── dbt_project.yml    # Project configuration
├── macros/            # Reusable SQL functions
├── models/            # Your dbt models
│   ├── staging/       # Raw data cleaning
│   ├── intermediate/  # Business logic
│   └── marts/         # Final analytical tables
├── profiles.yml.example  # Connection config template
├── snapshots/         # SCD Type 2 history tracking
└── tests/             # Custom data tests
```

## BigQuery-Specific Tips

1. **Cost Optimization**:
   - Use partitioning for large tables
   - Set `maximum_bytes_billed` in dev profile
   - Use incremental models for large datasets
   - Sample data in development using Jinja

2. **Performance**:
   - Use clustering on frequently filtered columns
   - Leverage BigQuery's native functions
   - Avoid SELECT * in production models

3. **Permissions**:
   - Development: BigQuery Data Editor + Job User
   - Production: Consider using separate service accounts
   - Use dataset-level permissions for access control

## Troubleshooting

### Authentication Issues
```bash
# For OAuth
gcloud auth application-default login

# For service account
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/key.json"
```

### Permission Errors
Ensure your service account has:
- `roles/bigquery.dataEditor`
- `roles/bigquery.jobUser`
- `roles/bigquery.user`

### Query Exceeded Limits
- Add partitioning to your models
- Use incremental strategies
- Set `maximum_bytes_billed` in profiles.yml

## Next Steps

1. Customize the models for your data structure
2. Add your own business logic
3. Set up orchestration (e.g., Airflow, Prefect)
4. Configure CI/CD pipeline
5. Set up monitoring and alerting

## Resources

- [dbt Documentation](https://docs.getdbt.com/)
- [dbt BigQuery Setup](https://docs.getdbt.com/docs/core/connect-data-platform/bigquery-setup)
- [BigQuery Best Practices](https://cloud.google.com/bigquery/docs/best-practices-performance-overview)
- [dbt Discourse](https://discourse.getdbt.com/)
- [dbt Slack](https://www.getdbt.com/community/)