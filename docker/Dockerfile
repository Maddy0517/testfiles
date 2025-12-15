# Dockerfile for containerized Dataflow pipeline (Flex Template)
# This allows running the pipeline as a Docker container on Dataflow

FROM gcr.io/dataflow-templates-base/java11-template-launcher-base:latest

# Set environment variables
ENV FLEX_TEMPLATE_JAVA_MAIN_CLASS="com.example.dataflow.WorkdayEmployeeDataflowPipeline"
ENV FLEX_TEMPLATE_JAVA_CLASSPATH="/template/workday-dataflow-pipeline-1.0.0.jar"

# Copy the compiled JAR
COPY target/workday-dataflow-pipeline-1.0.0.jar ${FLEX_TEMPLATE_JAVA_CLASSPATH}

# Set metadata
LABEL maintainer="your-email@company.com"
LABEL description="Workday Employee Data Ingestion Pipeline for Google Cloud Dataflow"
LABEL version="1.0.0"

# The base image handles the entrypoint
