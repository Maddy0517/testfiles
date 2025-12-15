# Performance Tuning Guide - Workday Dataflow Pipeline

This guide provides detailed strategies for optimizing the Workday employee data ingestion pipeline performance.

## Table of Contents
1. [Performance Architecture](#performance-architecture)
2. [Sizing Guidelines](#sizing-guidelines)
3. [Optimization Strategies](#optimization-strategies)
4. [Monitoring & Metrics](#monitoring--metrics)
5. [Troubleshooting Performance Issues](#troubleshooting-performance-issues)

## Performance Architecture

### Parallel Processing Model

```
Workday API (Max 999 records/page)
         │
         ▼
┌────────────────────────────────────┐
│  Initial Request                   │
│  └─ Get Total Count                │
└───────────┬────────────────────────┘
            │
            ▼
┌────────────────────────────────────┐
│  Page Request Generation           │
│  └─ Create N page requests         │
│     (Total Count / 999)            │
└───────────┬────────────────────────┘
            │
            ▼
┌────────────────────────────────────┐
│  Parallel Page Fetching            │
│  ┌─────────────────────────────┐  │
│  │ Worker 1: Pages 1-10        │  │
│  │ Worker 2: Pages 11-20       │  │
│  │ Worker 3: Pages 21-30       │  │
│  │ ...                         │  │
│  │ Worker N: Pages X-Y         │  │
│  └─────────────────────────────┘  │
└───────────┬────────────────────────┘
            │
            ▼
┌────────────────────────────────────┐
│  BigQuery Batch Write              │
│  └─ FILE_LOADS method              │
└────────────────────────────────────┘
```

## Sizing Guidelines

### Worker Count Calculation

**Formula**: `numWorkers = ceil(totalPages / pagesPerWorker)`

Where:
- `totalPages = ceil(totalEmployees / 999)`
- `pagesPerWorker` = 5-10 (depending on API response time)

### Recommended Configurations

#### Small Dataset (< 10,000 employees)
```bash
--numWorkers=2 \
--maxNumWorkers=5 \
--workerMachineType=n1-standard-2 \
--maxRetries=3
```
- **Total Pages**: ~10
- **Estimated Runtime**: 5-10 minutes
- **Estimated Cost**: $0.50-$1.00

#### Medium Dataset (10,000 - 50,000 employees)
```bash
--numWorkers=5 \
--maxNumWorkers=15 \
--workerMachineType=n1-standard-4 \
--maxRetries=3
```
- **Total Pages**: ~50
- **Estimated Runtime**: 10-20 minutes
- **Estimated Cost**: $2-$5

#### Large Dataset (50,000 - 200,000 employees)
```bash
--numWorkers=10 \
--maxNumWorkers=30 \
--workerMachineType=n1-standard-4 \
--maxRetries=5
```
- **Total Pages**: ~200
- **Estimated Runtime**: 20-40 minutes
- **Estimated Cost**: $5-$15

#### Very Large Dataset (> 200,000 employees)
```bash
--numWorkers=20 \
--maxNumWorkers=50 \
--workerMachineType=n1-standard-8 \
--workerDiskType=compute.googleapis.com/projects//zones//diskTypes/pd-ssd \
--maxRetries=5 \
--numberOfWorkerHarnessThreads=16
```
- **Total Pages**: ~500+
- **Estimated Runtime**: 30-60 minutes
- **Estimated Cost**: $15-$40

## Optimization Strategies

### 1. Network Optimization

#### Use VPC Service Controls
```bash
--network=projects/PROJECT_ID/global/networks/NETWORK_NAME \
--subnetwork=regions/REGION/subnetworks/SUBNET_NAME \
--usePublicIps=false
```
**Benefits**:
- Lower latency
- Better security
- Reduced NAT costs

#### Enable Streaming Engine
```bash
--enableStreamingEngine
```
**Benefits**:
- Reduces worker CPU usage
- Better autoscaling
- Lower cost for batch jobs

### 2. Worker Optimization

#### Increase Worker Threads
```bash
--numberOfWorkerHarnessThreads=16
```
**When to Use**: API calls have high latency (>2 seconds)

#### Use Faster Disk
```bash
--workerDiskType=compute.googleapis.com/projects//zones//diskTypes/pd-ssd
```
**When to Use**: Large datasets with significant shuffle operations

#### Optimize Autoscaling
```bash
--autoscalingAlgorithm=THROUGHPUT_BASED \
--maxNumWorkers=50 \
--numWorkers=10
```

### 3. BigQuery Write Optimization

#### Use FILE_LOADS for Large Batches
```java
BigQueryIO.writeTableRows()
    .withMethod(BigQueryIO.Write.Method.FILE_LOADS)
    .withTriggeringFrequency(Duration.standardMinutes(5))
```

**Benefits**:
- Better for >10K records
- Lower cost
- Better throughput

#### Use STREAMING_INSERTS for Real-time
```java
BigQueryIO.writeTableRows()
    .withMethod(BigQueryIO.Write.Method.STREAMING_INSERTS)
```

**Use Case**: Real-time updates, small batches

### 4. API Call Optimization

#### Connection Pooling
Already implemented in `WorkdaySoapClient`:
- Reuses SOAP connections within workers
- Reduces connection overhead

#### Adjust Timeouts
```java
workdayConfig.setConnectionTimeoutSeconds(60);
workdayConfig.setReadTimeoutSeconds(120);
```

#### Increase Retry Attempts for Unreliable Networks
```bash
--maxRetries=5
```

### 5. Parallelism Tuning

#### Bundle Size Control
```java
// In FetchEmployeePageFn
@ProcessElement
public void processElement(
    @Element PageRequest pageRequest,
    OutputReceiver<Employee> out,
    BundleFinalizer bundleFinalizer) {
    // Process with custom bundling
}
```

#### Fusion Break
```java
pageRequests
    .apply("BreakFusion", Reshuffle.viaRandomKey())
    .apply("FetchPages", ParDo.of(new FetchEmployeePageFn(config)));
```
**When to Use**: Force parallelism when Beam optimizes too aggressively

## Monitoring & Metrics

### Key Performance Metrics

#### 1. Throughput
```
Target: 500-1000 employees/second
Measure: Total employees / Total runtime
```

#### 2. API Call Latency
```
Target: < 3 seconds per page
Monitor: Logs from FetchEmployeePageFn
```

#### 3. Worker Utilization
```
Target: 70-90% CPU utilization
Monitor: Dataflow Console > Worker metrics
```

#### 4. BigQuery Write Rate
```
Target: > 1000 rows/second
Monitor: BigQuery streaming inserts or load jobs
```

### Monitoring Commands

#### View Job Metrics
```bash
gcloud dataflow jobs describe JOB_ID \
    --region=REGION \
    --full
```

#### View Worker Metrics
```bash
gcloud monitoring time-series list \
    --filter='resource.type="dataflow_job" AND metric.type="dataflow.googleapis.com/job/element_count"' \
    --project=PROJECT_ID
```

#### View Logs with Performance Data
```bash
gcloud logging read \
    "resource.type=dataflow_step AND jsonPayload.message=~\"Fetched.*employees\"" \
    --limit=50 \
    --format=json \
    | jq '.[] | {time: .timestamp, message: .jsonPayload.message}'
```

### Custom Performance Logging

Add to `FetchEmployeePageFn`:

```java
@ProcessElement
public void processElement(@Element PageRequest pageRequest, OutputReceiver<Employee> out) {
    long startTime = System.currentTimeMillis();
    List<Employee> employees = client.fetchEmployeePage(...);
    long duration = System.currentTimeMillis() - startTime;
    
    // Log performance metrics
    LOG.info("PERF: Page={}, Records={}, Duration={}ms, Throughput={}rec/s",
        pageRequest.getPageNumber(),
        employees.size(),
        duration,
        (employees.size() * 1000.0 / duration)
    );
}
```

## Troubleshooting Performance Issues

### Issue 1: Slow API Response Time

**Symptoms**:
- High latency per page (> 5 seconds)
- Workers idle waiting for responses

**Solutions**:
1. Increase worker threads:
   ```bash
   --numberOfWorkerHarnessThreads=32
   ```

2. Add connection pooling timeout:
   ```java
   workdayConfig.setReadTimeoutSeconds(180);
   ```

3. Check Workday API limits and quotas

### Issue 2: Workers Underutilized

**Symptoms**:
- Low CPU usage (< 40%)
- Few workers processing data

**Solutions**:
1. Increase initial workers:
   ```bash
   --numWorkers=20
   ```

2. Force fusion break:
   ```java
   .apply(Reshuffle.viaRandomKey())
   ```

3. Reduce bundle size:
   ```bash
   --maxBundleSize=100
   ```

### Issue 3: BigQuery Write Bottleneck

**Symptoms**:
- Workers waiting on BigQuery writes
- High write latency

**Solutions**:
1. Switch to FILE_LOADS:
   ```java
   .withMethod(BigQueryIO.Write.Method.FILE_LOADS)
   ```

2. Increase temp storage:
   ```bash
   --workerDiskSizeGb=200
   ```

3. Use partitioned tables:
   ```sql
   PARTITION BY DATE(ingestion_timestamp)
   ```

### Issue 4: Memory Issues

**Symptoms**:
- Out of memory errors
- Workers crashing

**Solutions**:
1. Use memory-optimized machines:
   ```bash
   --workerMachineType=n1-highmem-4
   ```

2. Reduce page size (if possible):
   ```java
   pageRequest.setPageSize(500);
   ```

3. Increase worker heap:
   ```bash
   --maxWorkerMemoryMb=8192
   ```

### Issue 5: Rate Limiting by Workday

**Symptoms**:
- 429 errors in logs
- Frequent retries

**Solutions**:
1. Add exponential backoff:
   ```java
   workdayConfig.setRetryDelaySeconds(10);
   ```

2. Reduce parallelism:
   ```bash
   --maxNumWorkers=10
   ```

3. Contact Workday to increase rate limits

## Best Practices Summary

1. **Start Conservative**: Begin with lower worker counts and scale up
2. **Monitor First Run**: Analyze metrics before production deployment
3. **Use Preemptible Workers**: 70% cost savings for non-critical jobs
4. **Schedule Off-Peak**: Better performance and pricing
5. **Partition BigQuery Tables**: Improve query performance
6. **Enable Stackdriver**: Comprehensive monitoring
7. **Test Locally First**: Use DirectRunner for development
8. **Implement Circuit Breakers**: Protect against API failures
9. **Use Quotas**: Prevent runaway costs
10. **Document Performance**: Track metrics over time

## Performance Benchmarks

| Dataset Size | Workers | Machine Type | Runtime | Cost | Throughput |
|-------------|---------|--------------|---------|------|------------|
| 5K          | 2       | n1-standard-2| 5 min   | $0.50| 1000/sec   |
| 25K         | 5       | n1-standard-4| 15 min  | $3.00| 1666/sec   |
| 100K        | 15      | n1-standard-4| 30 min  | $10  | 3333/sec   |
| 500K        | 30      | n1-standard-8| 45 min  | $30  | 11111/sec  |

*Actual performance may vary based on API response times and network conditions*

## Additional Resources

- [Dataflow Performance Tips](https://cloud.google.com/dataflow/docs/guides/deploying-a-pipeline#performance)
- [BigQuery Write Optimization](https://cloud.google.com/bigquery/docs/loading-data)
- [Workday API Documentation](https://community.workday.com/sites/default/files/file-hosting/productionapi/index.html)
