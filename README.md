# Analytics Project - dbt + BigQuery

A complete dbt (data build tool) project configured for Google BigQuery, featuring modern data warehouse patterns, comprehensive testing, and detailed documentation.

## 📁 Project Structure

```
analytics_project/
├── 📄 dbt_project.yml          # Project configuration
├── 📄 profiles.yml             # BigQuery connection settings
├── 📁 models/                  # SQL transformation models
│   ├── 📁 staging/            # Raw data cleaning & standardization
│   │   ├── stg_orders.sql
│   │   ├── stg_customers.sql
│   │   ├── stg_products.sql
│   │   └── stg_order_items.sql
│   ├── 📁 intermediate/       # Business logic & calculations
│   │   ├── int_customer_order_summary.sql
│   │   └── int_product_performance.sql
│   ├── 📁 marts/              # Analytics-ready tables
│   │   ├── dim_customers.sql
│   │   ├── dim_products.sql
│   │   └── fct_orders.sql
│   ├── sources.yml            # Source table definitions
│   └── schema.yml             # Model documentation & tests
├── 📁 macros/                 # Reusable SQL functions
│   ├── get_current_timestamp.sql
│   ├── cents_to_dollars.sql
│   └── limit_data_in_dev.sql
├── 📁 tests/                  # Custom data quality tests
│   ├── assert_order_amount_positive.sql
│   └── assert_customer_email_format.sql
├── 📁 seeds/                  # Reference data (CSV files)
│   └── product_categories.csv
└── 📄 packages.yml            # dbt package dependencies
```

## 🚀 Quick Start

1. **Follow Setup Instructions**: See [`SETUP_INSTRUCTIONS.md`](SETUP_INSTRUCTIONS.md) for step-by-step setup
2. **Read the Complete Guide**: Check [`DBT_BIGQUERY_GUIDE.md`](DBT_BIGQUERY_GUIDE.md) for comprehensive documentation
3. **Configure Your Environment**: Update `profiles.yml` with your BigQuery credentials
4. **Run Your First Models**: Execute `dbt run` to build the data warehouse

## 📊 Data Model Overview

### Staging Layer (`staging/`)
- **Purpose**: Clean and standardize raw source data
- **Materialization**: Views (for performance and cost optimization)
- **Examples**: `stg_orders`, `stg_customers`, `stg_products`, `stg_order_items`

### Intermediate Layer (`intermediate/`)
- **Purpose**: Business logic and complex calculations
- **Materialization**: Views
- **Examples**: Customer order summaries, product performance metrics

### Marts Layer (`marts/`)
- **Purpose**: Analytics-ready dimensional models
- **Materialization**: Tables (partitioned and clustered for BigQuery optimization)
- **Examples**: Customer dimension, product dimension, order facts

## 🎯 Key Features

### ✅ BigQuery Optimizations
- **Partitioning**: Tables partitioned by date for query performance
- **Clustering**: Strategic clustering on frequently filtered columns
- **Cost Control**: Development data limiting macros

### ✅ Data Quality & Testing
- **Built-in Tests**: Uniqueness, null checks, referential integrity
- **Custom Tests**: Business logic validation
- **dbt Expectations**: Advanced data quality testing

### ✅ Advanced Analytics
- **RFM Analysis**: Customer segmentation based on Recency, Frequency, Monetary
- **Product Performance**: Classification and trend analysis
- **Behavioral Insights**: Shopping patterns and seasonal analysis

### ✅ Documentation & Governance
- **Comprehensive Docs**: Every model and column documented
- **Data Lineage**: Automatic dependency tracking
- **Version Control**: Git-based collaboration

## 🛠 Common Commands

```bash
# Install dependencies
dbt deps

# Test connection
dbt debug

# Load seed data
dbt seed

# Run all models
dbt run

# Run specific model
dbt run --select dim_customers

# Run tests
dbt test

# Generate and serve documentation
dbt docs generate && dbt docs serve

# Development with limited data
dbt run --vars '{"dev_limit": true}'
```

## 📈 Business Use Cases

This project enables analytics for:

- **Customer Analytics**: Segmentation, lifetime value, churn prediction
- **Product Analytics**: Performance tracking, inventory optimization
- **Sales Analytics**: Revenue trends, seasonal patterns
- **Operational Analytics**: Order processing, fulfillment metrics

## 🔧 Customization

### Adding New Models
1. Create SQL file in appropriate folder (`staging/`, `intermediate/`, `marts/`)
2. Add model configuration and documentation to `schema.yml`
3. Add tests for data quality
4. Run `dbt run --select your_new_model`

### BigQuery-Specific Features
```sql
-- Partitioning
{{ config(
    partition_by={
        "field": "order_date",
        "data_type": "date"
    }
) }}

-- Clustering
{{ config(
    cluster_by=["customer_id", "product_category"]
) }}

-- Labels for cost tracking
{{ config(
    labels={'team': 'analytics', 'env': 'prod'}
) }}
```

## 📚 Learning Resources

- **Complete Guide**: [`DBT_BIGQUERY_GUIDE.md`](DBT_BIGQUERY_GUIDE.md) - Comprehensive dbt + BigQuery documentation
- **Setup Instructions**: [`SETUP_INSTRUCTIONS.md`](SETUP_INSTRUCTIONS.md) - Step-by-step setup guide
- **dbt Documentation**: [docs.getdbt.com](https://docs.getdbt.com/)
- **BigQuery Documentation**: [cloud.google.com/bigquery/docs](https://cloud.google.com/bigquery/docs)

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests and documentation
5. Submit a pull request

## 📄 License

This project is provided as an educational example. Adapt it to your organization's needs and policies.

---

**Ready to transform your data?** Start with the [`SETUP_INSTRUCTIONS.md`](SETUP_INSTRUCTIONS.md) and build your modern data warehouse with dbt + BigQuery! 🚀