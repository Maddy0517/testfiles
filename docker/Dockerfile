# Multi-stage Dockerfile for Workday to BigQuery Pipeline

# Build stage
FROM maven:3.8.6-openjdk-11-slim AS builder

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM openjdk:11-jre-slim

# Install required packages
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    && rm -rf /var/lib/apt/lists/*

# Create app directory
WORKDIR /app

# Copy the built JAR
COPY --from=builder /app/target/workday-bigquery-pipeline-bundled-*.jar app.jar

# Copy configuration files
COPY src/main/resources/application.properties .
COPY src/main/resources/logback.xml .

# Create logs directory
RUN mkdir -p logs

# Set JVM options
ENV JAVA_OPTS="-Xmx3584m -Xms1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Expose port for monitoring
EXPOSE 8080

# Default command
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]