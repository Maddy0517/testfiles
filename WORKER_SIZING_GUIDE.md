# Worker Sizing Guide - Cost-Optimized Configurations

## Quick Answer: Start Small!

**Default Configuration (Recommended for Most Users)**:
```bash
num.workers=2
max.num.workers=5
worker.machine.type=n1-standard-2
```

This handles up to **50,000 employees** efficiently and costs only **$1-3 per run**.

## Why You Don't Need 10 Workers Initially

### The Math
- Each page = 999 records max
- 10,000 employees = 11 pages
- 2 workers can process 11 pages in ~5-10 minutes
- Cost: ~$0.50-1.00

**10 workers for 10K employees = massive overkill!**

## Sizing Chart by Employee Count

### Tiny Dataset: <1,000 employees
```bash
num.workers=1
max.num.workers=2
worker.machine.type=n1-standard-2
```
- **Pages**: ~1 page
- **Runtime**: 2-3 minutes
- **Cost**: $0.20-0.50
- **Use Case**: Small companies, testing

### Small Dataset: 1,000-10,000 employees
```bash
num.workers=2
max.num.workers=3
worker.machine.type=n1-standard-2
```
- **Pages**: ~10 pages
- **Runtime**: 5-8 minutes
- **Cost**: $0.50-1.00
- **Use Case**: Most small-medium businesses

### Medium Dataset: 10,000-50,000 employees
```bash
num.workers=2
max.num.workers=5
worker.machine.type=n1-standard-2
```
- **Pages**: ~50 pages
- **Runtime**: 10-15 minutes
- **Cost**: $1.00-3.00
- **Use Case**: Medium to large enterprises (DEFAULT)

### Large Dataset: 50,000-200,000 employees
```bash
num.workers=5
max.num.workers=10
worker.machine.type=n1-standard-4
```
- **Pages**: ~200 pages
- **Runtime**: 15-25 minutes
- **Cost**: $5-10
- **Use Case**: Large corporations

### Very Large Dataset: 200,000+ employees
```bash
num.workers=10
max.num.workers=20
worker.machine.type=n1-standard-4
```
- **Pages**: ~500+ pages
- **Runtime**: 20-40 minutes
- **Cost**: $10-20
- **Use Case**: Fortune 500 companies

## Machine Type Comparison

| Type | vCPUs | Memory | Cost/hour | When to Use |
|------|-------|--------|-----------|-------------|
| **n1-standard-1** | 1 | 3.75 GB | $0.048 | Testing only |
| **n1-standard-2** | 2 | 7.5 GB | $0.095 | **Most workloads** (RECOMMENDED) |
| **n1-standard-4** | 4 | 15 GB | $0.190 | Large datasets (>50K) |
| **n1-standard-8** | 8 | 30 GB | $0.380 | Very large (>200K) |
| **n1-highmem-2** | 2 | 13 GB | $0.118 | Memory-intensive only |

**💡 Recommendation**: Start with `n1-standard-2` - it's the sweet spot for 99% of use cases.

## Cost Breakdown Examples

### Scenario 1: 5,000 Employees (Typical Small Company)
```
Configuration: 2 workers, n1-standard-2
Pages: 6 pages
Runtime: ~5 minutes
Cost: $0.95/hour × (5/60) hours × 2 workers = $0.16
```
**Cost per run: ~$0.20** ✅

### Scenario 2: 25,000 Employees (Medium Company)
```
Configuration: 2 workers, n1-standard-2
Pages: 26 pages
Runtime: ~12 minutes
Cost: $0.95/hour × (12/60) hours × 2 workers = $0.38
```
**Cost per run: ~$0.50** ✅

### Scenario 3: 100,000 Employees (Large Enterprise)
```
Configuration: 5 workers, n1-standard-4
Pages: 101 pages
Runtime: ~20 minutes
Cost: $0.19/hour × (20/60) hours × 5 workers = $0.32
```
**Cost per run: ~$5.00** ✅

### Scenario 4: OVERKILL Example (Don't do this!)
```
Configuration: 50 workers, n1-standard-8 (WASTEFUL!)
Pages: 26 pages for 25K employees
Runtime: ~3 minutes (barely faster!)
Cost: $0.38/hour × (3/60) hours × 50 workers = $0.95
```
**Cost per run: ~$20** ❌ **10x more expensive for same result!**

## How to Choose

### Step 1: Know Your Employee Count
```bash
# Check in Workday or estimate
TOTAL_EMPLOYEES=25000
```

### Step 2: Calculate Pages Needed
```bash
# Formula: ceil(employees / 999)
PAGES=$(( (TOTAL_EMPLOYEES + 998) / 999 ))
echo "Pages needed: $PAGES"
```

### Step 3: Choose Workers
```bash
# Rule of thumb: 1 worker per 10-20 pages
WORKERS=$(( PAGES / 10 ))
# But minimum 1, maximum start at 5
if [ $WORKERS -lt 1 ]; then WORKERS=1; fi
if [ $WORKERS -gt 5 ]; then WORKERS=5; fi
```

## Performance vs Cost Trade-off

| Workers | Pages/Worker | Speed | Cost | When to Use |
|---------|--------------|-------|------|-------------|
| 1 | All pages | Slow | Cheapest | Testing, <1K employees |
| 2 | ~25 pages | Good | **Best value** | Most use cases |
| 5 | ~10 pages | Fast | Reasonable | >50K employees |
| 10 | ~5 pages | Very Fast | Expensive | >200K employees |
| 20+ | ~2 pages | Barely faster | Very expensive | Almost never needed |

**💡 Sweet Spot**: 2 workers handles most real-world scenarios efficiently.

## Autoscaling Benefits

Dataflow automatically scales workers based on workload:

```bash
--numWorkers=2         # Start with 2
--maxNumWorkers=5      # Scale up to 5 if needed
```

**Benefits**:
- Start small (cheap)
- Scale only if needed
- Pay only for what you use
- Best of both worlds

## Cost Savings Tips

### 1. Use Appropriate Worker Count
```bash
# ❌ Bad: Overkill for small dataset
--numWorkers=20 --maxNumWorkers=50

# ✅ Good: Right-sized
--numWorkers=2 --maxNumWorkers=5
```
**Savings**: 80-90%

### 2. Use Smaller Machines
```bash
# ❌ Expensive: n1-standard-8 ($0.38/hr)
--workerMachineType=n1-standard-8

# ✅ Cheaper: n1-standard-2 ($0.095/hr)
--workerMachineType=n1-standard-2
```
**Savings**: 75%

### 3. Use Preemptible Workers (Advanced)
```bash
--usePublicIps=false \
--numWorkers=2 \
--workerDiskType=pd-standard
```
**Savings**: Additional 20-30%

### 4. Schedule During Off-Peak Hours
```bash
# Run at 2 AM when Workday API is less busy
# Faster response times = shorter runtime = lower cost
```

## Monitoring and Optimization

### After Your First Run

1. **Check Actual Runtime**
   ```bash
   gcloud dataflow jobs list --region=us-central1
   ```

2. **Review Worker Utilization**
   - Go to Dataflow Console
   - Check CPU usage
   - If <50% utilized → reduce workers
   - If >90% utilized → increase workers

3. **Adjust Configuration**
   ```bash
   # If run was too slow
   num.workers=3
   max.num.workers=7
   
   # If workers were underutilized
   num.workers=1
   max.num.workers=3
   ```

## Real-World Recommendations

### Recommendation #1: Start Conservative
```bash
# First run: Use minimal config
num.workers=1
max.num.workers=3
worker.machine.type=n1-standard-2
```
- Measure actual performance
- Adjust based on results
- Scale up only if needed

### Recommendation #2: For Most Companies
```bash
# Best balance for 10K-50K employees
num.workers=2
max.num.workers=5
worker.machine.type=n1-standard-2
```
- Handles 90% of use cases
- ~10-15 min runtime
- Cost: $1-3 per run
- ~$30-50/month for daily runs

### Recommendation #3: For Enterprises Only
```bash
# Only if you have >100K employees
num.workers=5
max.num.workers=10
worker.machine.type=n1-standard-4
```
- For very large datasets
- Faster processing (15-20 min)
- Cost: $5-10 per run
- Worth it for Fortune 500

## Updated Default Configuration

We've changed the defaults to be more cost-effective:

### Old (Expensive)
```properties
num.workers=10
max.num.workers=50
worker.machine.type=n1-standard-4
```
**Cost**: $10-20 per run (OVERKILL)

### New (Cost-Optimized)
```properties
num.workers=2
max.num.workers=5
worker.machine.type=n1-standard-2
```
**Cost**: $1-3 per run ✅

## Summary

### Do You Really Need 10 Workers?

**NO** - unless you have **>100,000 employees**

### What Should You Use?

| Your Employee Count | Configuration |
|-------------------|---------------|
| <10,000 | 1-2 workers, n1-standard-2 |
| 10,000-50,000 | 2 workers, n1-standard-2 (DEFAULT) |
| 50,000-200,000 | 5 workers, n1-standard-4 |
| >200,000 | 10 workers, n1-standard-4 |

### Bottom Line

- **Start with 2 workers** (our new default)
- **Monitor first run** performance
- **Scale up only if needed**
- **Save 80-90%** on costs

**The old defaults (10 workers) were examples for very large enterprises. Most users should start with 2 workers!**
