#!/bin/bash

# dbt BigQuery Project Setup Script
# This script helps set up the dbt project for BigQuery

set -e  # Exit on error

echo "========================================="
echo "dbt BigQuery Project Setup"
echo "========================================="

# Check Python version
python_version=$(python3 --version 2>&1 | grep -Po '(?<=Python )[\d.]+')
echo "✓ Python version: $python_version"

# Create virtual environment if it doesn't exist
if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
    echo "✓ Virtual environment created"
fi

# Activate virtual environment
echo "Activating virtual environment..."
source venv/bin/activate

# Install requirements
echo "Installing dbt-bigquery and dependencies..."
pip install --upgrade pip
pip install -r requirements.txt
echo "✓ Dependencies installed"

# Check dbt installation
dbt_version=$(dbt --version | head -n 1)
echo "✓ dbt installed: $dbt_version"

# Create ~/.dbt directory if it doesn't exist
if [ ! -d "$HOME/.dbt" ]; then
    mkdir -p "$HOME/.dbt"
    echo "✓ Created ~/.dbt directory"
fi

# Copy profiles.yml if it doesn't exist
if [ ! -f "$HOME/.dbt/profiles.yml" ]; then
    echo "Copying profiles.yml template..."
    cp profiles.yml.example "$HOME/.dbt/profiles.yml"
    echo "✓ Copied profiles.yml to ~/.dbt/"
    echo ""
    echo "⚠️  IMPORTANT: Edit ~/.dbt/profiles.yml with your BigQuery credentials"
    echo ""
fi

# Prompt for GCP project ID
read -p "Enter your GCP Project ID: " gcp_project
if [ ! -z "$gcp_project" ]; then
    # Update profiles.yml with project ID (macOS compatible)
    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' "s/your-gcp-project-id/$gcp_project/g" "$HOME/.dbt/profiles.yml"
    else
        sed -i "s/your-gcp-project-id/$gcp_project/g" "$HOME/.dbt/profiles.yml"
    fi
    echo "✓ Updated GCP project ID in profiles.yml"
fi

# Test connection
echo ""
echo "Testing BigQuery connection..."
echo "--------------------------------"
if dbt debug --profiles-dir ~/.dbt; then
    echo "✓ Connection successful!"
else
    echo "✗ Connection failed. Please check your credentials in ~/.dbt/profiles.yml"
    exit 1
fi

# Run initial setup commands
echo ""
echo "Running initial dbt commands..."
echo "--------------------------------"

# Install dbt packages (if packages.yml exists)
if [ -f "packages.yml" ]; then
    echo "Installing dbt packages..."
    dbt deps
    echo "✓ dbt packages installed"
fi

# Load seed data
echo "Loading seed data..."
dbt seed --profiles-dir ~/.dbt
echo "✓ Seed data loaded"

# Run models
read -p "Do you want to run all dbt models now? (y/n): " run_models
if [[ $run_models == "y" || $run_models == "Y" ]]; then
    echo "Running dbt models..."
    dbt run --profiles-dir ~/.dbt
    echo "✓ Models executed successfully"
    
    # Run tests
    echo "Running tests..."
    dbt test --profiles-dir ~/.dbt
    echo "✓ Tests completed"
fi

# Generate documentation
read -p "Do you want to generate and serve documentation? (y/n): " gen_docs
if [[ $gen_docs == "y" || $gen_docs == "Y" ]]; then
    echo "Generating documentation..."
    dbt docs generate --profiles-dir ~/.dbt
    echo "✓ Documentation generated"
    
    echo ""
    echo "========================================="
    echo "Setup Complete! 🎉"
    echo "========================================="
    echo ""
    echo "Documentation server starting on http://localhost:8080"
    echo "Press Ctrl+C to stop the server"
    echo ""
    dbt docs serve --port 8080 --profiles-dir ~/.dbt
else
    echo ""
    echo "========================================="
    echo "Setup Complete! 🎉"
    echo "========================================="
    echo ""
    echo "Next steps:"
    echo "1. Review and modify models in the 'models' directory"
    echo "2. Run: dbt run    # to execute models"
    echo "3. Run: dbt test   # to run tests"
    echo "4. Run: dbt docs generate && dbt docs serve   # for documentation"
    echo ""
    echo "For more information, check SETUP_GUIDE.md"
fi