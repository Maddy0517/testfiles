# dbt + BigQuery Setup Instructions

## Quick Start Guide

Follow these steps to get your dbt project running with BigQuery:

### 1. Prerequisites

- **Google Cloud Platform Account** with BigQuery enabled
- **Python 3.7+** installed
- **Service Account** with appropriate permissions

### 2. Install dbt-bigquery

```bash
pip install dbt-bigquery
```

### 3. Set Up BigQuery Authentication

#### Option A: Service Account (Recommended)

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Navigate to IAM & Admin > Service Accounts
3. Create a new service account or use existing one
4. Grant these roles:
   - BigQuery Data Editor
   - BigQuery Job User
   - BigQuery User
5. Download the JSON key file
6. Store it securely (e.g., `~/.gcp/service-account-key.json`)

#### Option B: OAuth (Development)

```bash
gcloud auth application-default login
```

### 4. Configure Your Profile

1. Copy `profiles.yml` to `~/.dbt/profiles.yml` or keep it in project root
2. Update the following values:
   ```yaml
   project: your-gcp-project-id  # Your GCP project ID
   dataset: dbt_dev              # Your BigQuery dataset name
   keyfile: /path/to/your/service-account-key.json  # Path to your key file
   ```

### 5. Create BigQuery Datasets

Create the required datasets in BigQuery:

```sql
-- In BigQuery Console, run these commands:
CREATE SCHEMA IF NOT EXISTS `your-project-id.dbt_dev`;
CREATE SCHEMA IF NOT EXISTS `your-project-id.dbt_dev_staging`;
CREATE SCHEMA IF NOT EXISTS `your-project-id.dbt_dev_marts`;
CREATE SCHEMA IF NOT EXISTS `your-project-id.dbt_dev_intermediate`;
CREATE SCHEMA IF NOT EXISTS `your-project-id.dbt_dev_seeds`;
```

### 6. Set Up Source Data

For this example project, you'll need source tables. Create them with sample data:

```sql
-- Create raw_data dataset
CREATE SCHEMA IF NOT EXISTS `your-project-id.raw_data`;

-- Create sample orders table
CREATE TABLE `your-project-id.raw_data.orders` (
  order_id STRING,
  customer_id STRING,
  order_date DATE,
  order_amount INT64,
  order_status STRING
);

-- Insert sample data
INSERT INTO `your-project-id.raw_data.orders` VALUES
('ORD001', 'CUST001', '2023-01-15', 2500, 'delivered'),
('ORD002', 'CUST002', '2023-01-16', 1200, 'shipped'),
('ORD003', 'CUST001', '2023-01-17', 800, 'processing');

-- Create sample customers table
CREATE TABLE `your-project-id.raw_data.customers` (
  customer_id STRING,
  customer_email STRING,
  first_name STRING,
  last_name STRING,
  registration_date DATE,
  customer_segment STRING
);

-- Insert sample data
INSERT INTO `your-project-id.raw_data.customers` VALUES
('CUST001', 'john.doe@email.com', 'John', 'Doe', '2022-06-15', 'Premium'),
('CUST002', 'jane.smith@email.com', 'Jane', 'Smith', '2022-08-20', 'Standard');

-- Create sample products table
CREATE TABLE `your-project-id.raw_data.products` (
  product_id STRING,
  product_name STRING,
  category STRING,
  price INT64,
  created_date DATE
);

-- Insert sample data
INSERT INTO `your-project-id.raw_data.products` VALUES
('PROD001', 'Laptop Computer', 'Electronics', 89999, '2022-01-01'),
('PROD002', 'Running Shoes', 'Clothing', 12999, '2022-02-01');

-- Create sample order_items table
CREATE TABLE `your-project-id.raw_data.order_items` (
  order_item_id STRING,
  order_id STRING,
  product_id STRING,
  quantity INT64,
  unit_price INT64
);

-- Insert sample data
INSERT INTO `your-project-id.raw_data.order_items` VALUES
('OI001', 'ORD001', 'PROD001', 1, 89999),
('OI002', 'ORD002', 'PROD002', 2, 12999),
('OI003', 'ORD003', 'PROD002', 1, 12999);
```

### 7. Test Your Connection

```bash
cd analytics_project
dbt debug
```

You should see:
```
All checks passed!
```

### 8. Run Your First Models

```bash
# Load seed data
dbt seed

# Run all models
dbt run

# Run tests
dbt test

# Generate documentation
dbt docs generate
dbt docs serve
```

### 9. Verify Results

Check your BigQuery console. You should see new tables in your datasets:
- `dbt_dev_staging.stg_orders`
- `dbt_dev_staging.stg_customers`
- `dbt_dev_marts.dim_customers`
- `dbt_dev_marts.fct_orders`

## Common Issues and Solutions

### Authentication Errors

**Error**: `403 Forbidden`
**Solution**: Check service account permissions and key file path

**Error**: `404 Not Found`
**Solution**: Verify project ID and dataset names exist

### Permission Errors

Ensure your service account has these roles:
- BigQuery Data Editor
- BigQuery Job User
- BigQuery User

### Dataset Not Found

Create datasets manually in BigQuery Console or run:
```sql
CREATE SCHEMA IF NOT EXISTS `your-project-id.dataset_name`;
```

## Development Workflow

### Daily Development

```bash
# Check what changed
dbt run --select state:modified+

# Run specific model and downstream
dbt run --select +dim_customers+

# Test specific model
dbt test --select dim_customers

# Run in development with limited data
dbt run --vars '{"dev_limit": true}'
```

### Production Deployment

```bash
# Run with production profile
dbt run --target prod

# Run with full refresh
dbt run --full-refresh --target prod

# Run tests in production
dbt test --target prod
```

## Next Steps

1. **Customize Source Configuration**: Update `models/sources.yml` with your actual source tables
2. **Add Your Business Logic**: Modify models to match your business requirements
3. **Set Up CI/CD**: Integrate with your deployment pipeline
4. **Add More Tests**: Create custom tests for your specific data quality needs
5. **Optimize Performance**: Add partitioning and clustering for large tables
6. **Schedule Runs**: Set up automated runs using Cloud Composer, Airflow, or GitHub Actions

## Resources

- [dbt Documentation](https://docs.getdbt.com/)
- [BigQuery dbt Adapter](https://docs.getdbt.com/reference/warehouse-profiles/bigquery-profile)
- [dbt Best Practices](https://docs.getdbt.com/guides/best-practices)
- [BigQuery Best Practices](https://cloud.google.com/bigquery/docs/best-practices-performance-overview)

## Support

If you encounter issues:
1. Check the `DBT_BIGQUERY_GUIDE.md` for detailed explanations
2. Review dbt logs in `logs/dbt.log`
3. Use `dbt debug` to diagnose connection issues
4. Check BigQuery job history for query errors