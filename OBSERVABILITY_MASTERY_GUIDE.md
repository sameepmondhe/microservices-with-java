# 🎓 **Complete Observability Mastery: From Zero to Hero**

*A comprehensive step-by-step walkthrough of modern observability - transforming raw application data into actionable business intelligence*

---

## 🎯 **Learning Objectives**

By the end of this deep-dive, you'll be an **observability expert** who can:
- ✅ Understand **exactly how** raw application output becomes dashboard insights
- ✅ Configure **every component** in the observability pipeline
- ✅ **Trace data transformation** through each processing stage
- ✅ **Debug issues** at any layer of the stack
- ✅ **Optimize performance** and costs
- ✅ **Design custom** observability solutions

**⏱️ Total Learning Time: 2-3 hours** (hands-on with real examples)

---

## 📚 **What We'll Build Together**

```
🎯 COMPLETE DATA TRANSFORMATION JOURNEY
───────────────────────────────────────────────────────────────────

📱 MICROSERVICE
   ↓ (Raw application output)
🤖 GRAFANA ALLOY  
   ↓ (Structured, enriched data)
🗄️ STORAGE BACKENDS
   ↓ (Optimized for queries)
📊 GRAFANA DASHBOARDS
   ↓ (Business intelligence)
🚨 INTELLIGENT ALERTS
   ↓ (Proactive issue detection)
👨‍💼 BUSINESS VALUE
```

---

# 📖 **Chapter 1: Understanding the Raw Materials**

## **Step 1: What Your Applications Actually Output**

Let's start with a **real banking microservice** and see exactly what it produces:

### **🏦 Accounts Service - Raw Output**

When a customer transfers $5,000, here's what happens under the hood:

**1.1 Application Logs (Raw Text)**
```bash
# This is what gets written to stdout/stderr
2025-09-24T10:15:23.456Z INFO  c.b.AccountController - Processing transfer request
2025-09-24T10:15:23.458Z DEBUG c.b.ValidationService - Validating customer CUST_12345 
2025-09-24T10:15:23.462Z INFO  c.b.AccountService - {"timestamp":"2025-09-24T10:15:23.462Z","level":"INFO","thread":"http-nio-8080-exec-1","logger":"com.banking.AccountService","message":"Transfer initiated","businessContext":{"customerId":"CUST_12345","customerTier":"PREMIUM","transactionType":"TRANSFER","amount":5000.00,"fromAccount":"ACC-001","toAccount":"ACC-002","complianceRequired":true},"traceId":"abc123xyz789","spanId":"span-001"}
2025-09-24T10:15:23.485Z INFO  c.b.ComplianceService - High-value transaction detected: $5000.00
2025-09-24T10:15:23.520Z INFO  c.b.AccountController - Transfer completed successfully
```

**❓ Questions for You:**
- Can you easily find all transfers for customer CUST_12345?
- How would you calculate average transfer amounts?
- What if you needed to alert on high-value transactions?

**Answer: You can't! This is unstructured text. Let's fix that.**

### **1.2 Application Metrics (JVM/Business)**

Your Spring Boot application also exposes metrics on `/actuator/metrics`:

```bash
# JVM Metrics (automatic)
curl localhost:8080/actuator/prometheus

# Raw Prometheus format output:
jvm_memory_used_bytes{area="heap",id="PS Eden Space"} 1.67772160E8
jvm_memory_used_bytes{area="heap",id="PS Old Gen"} 2.4567808E7
http_server_requests_seconds_count{exception="None",method="POST",outcome="SUCCESS",status="200",uri="/api/v1/accounts/transfer"} 1247.0
http_server_requests_seconds_sum{exception="None",method="POST",outcome="SUCCESS",status="200",uri="/api/v1/accounts/transfer"} 23.456

# Business Metrics (custom)
banking_transfers_total{customer_tier="PREMIUM",transaction_type="TRANSFER"} 156.0
banking_transfer_amount_sum{customer_tier="PREMIUM"} 785000.0
```

**❓ Questions for You:**
- How do you correlate metrics with specific customer transactions?
- What's the business impact of that 23.456 seconds total response time?

### **1.3 Distributed Traces (OpenTelemetry)**

Your application also generates trace spans:

```json
{
  "traceID": "abc123xyz789",
  "spanID": "span-accounts-001", 
  "parentSpanID": "span-gateway-001",
  "operationName": "POST /api/v1/accounts/transfer",
  "startTime": 1727172923456000000,
  "duration": 120000000,
  "tags": {
    "service.name": "accounts-service",
    "service.version": "1.2.3",
    "http.method": "POST",
    "http.url": "/api/v1/accounts/transfer", 
    "http.status_code": 200,
    "customer.id": "CUST_12345",
    "customer.tier": "PREMIUM",
    "transaction.amount": 5000.00,
    "business.compliance_required": true,
    "db.statement": "UPDATE accounts SET balance = balance - ? WHERE account_id = ?",
    "db.rows_affected": 1
  },
  "process": {
    "serviceName": "accounts-service",
    "tags": {
      "hostname": "accounts-pod-789",
      "ip": "10.244.1.15"
    }
  }
}
```

**❓ Questions for You:**
- How do you find all traces for a specific customer?  
- How do you calculate the 95th percentile response time?
- How do you identify slow database queries?

**The Problem: Three separate data streams with no easy correlation!**

---

# 🤖 **Chapter 2: The Grafana Alloy Transformation Engine**

## **Step 2: Understanding Grafana Alloy's Role**

Grafana Alloy is your **unified data transformation pipeline**. Think of it as a smart factory that:
- 📥 **Ingests** raw data from all sources
- 🔄 **Transforms** it into structured, correlated data  
- 📤 **Routes** it to appropriate storage backends
- 🏷️ **Enriches** it with business context

### **2.1 Modern Alloy Web UI and Monitoring**

**🎯 Important Update**: Modern Grafana Alloy (v1.10+) uses a web-based UI instead of REST API endpoints for debugging. Here's how to access it:

```bash
# Access Alloy Web UI
open http://localhost:12345    # Opens the modern Alloy dashboard

# Alternative: Use metrics for programmatic monitoring
curl -s http://localhost:12345/metrics | grep "alloy_build_info"
```

**What you'll see in the Web UI:**
- 📊 **Component Graph**: Visual representation of your data pipeline
- 🎯 **Component Details**: Real-time status of each Alloy component  
- 📈 **Metrics Dashboard**: Built-in monitoring of processing rates
- 🔍 **Target Discovery**: Live view of discovered containers and services

### **2.2 Alloy Configuration Architecture**

Let's examine our complete Alloy configuration step by step:

**`alloy/config.alloy` - The Master Configuration**

```hcl
// ====================================================================
// STEP 2.1: SERVICE DISCOVERY - Finding Your Microservices
// ====================================================================

// Discover all Docker containers running microservices
discovery.docker "microservices" {
    host = "unix:///var/run/docker.sock"
    
    // Only monitor containers with our special label
    filter {
        name   = "label"
        values = ["logging=alloy"]
    }
    
    // Extract metadata for enrichment
    relabel_configs = [
        {
            source_labels = ["__meta_docker_container_label_service"]
            target_label  = "service"
        },
        {
            source_labels = ["__meta_docker_container_label_environment"] 
            target_label  = "environment"
        },
        {
            source_labels = ["__meta_docker_container_name"]
            target_label  = "container_name"
        }
    ]
}
```

**🎓 Learning Checkpoint:**
Run these commands to see what Alloy discovers:
```bash
# See containers with proper labels
docker ps --filter "label=logging=alloy" --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"

# Check Docker service discovery is working (should be 0 failures)
curl -s http://localhost:12345/metrics | grep "prometheus_sd_refresh_failures_total"

# See how many discovery refresh cycles have run
curl -s http://localhost:12345/metrics | grep "prometheus_sd_refresh_duration_seconds_count"

# Find actual number of targets being scraped
curl -s http://localhost:12345/metrics | grep "prometheus_scrape_targets_gauge"
```

You should see your microservices with labels and successful metrics:
```
NAMES                IMAGE                      STATUS  
accounts-service     banking/accounts:latest    Up 10 minutes
cards-service        banking/cards:latest       Up 10 minutes

# Metrics interpretation:
prometheus_sd_refresh_failures_total{...docker...} 0          # ✅ No discovery failures
prometheus_sd_refresh_duration_seconds_count{...docker...} 10  # ✅ 10 discovery cycles completed  
prometheus_scrape_targets_gauge{...self_monitoring...} 1       # ✅ Self-monitoring active
prometheus_scrape_targets_gauge{...microservices...} X         # ✅ X = number of microservices
```

**🎯 Key Understanding:** 
- `refresh_duration_seconds_count = 10` means **10 discovery cycles**, not 10 containers
- `scrape_targets_gauge` shows the **actual number of targets** being monitored

### **2.2 Log Collection and Processing Pipeline**

```hcl
// ====================================================================
// STEP 2.2: LOG COLLECTION - Capturing Raw Application Output
// ====================================================================

// Collect logs from discovered containers
loki.source.docker "microservices_logs" {
    host       = "unix:///var/run/docker.sock" 
    targets    = discovery.docker.microservices.targets
    forward_to = [loki.process.banking_intelligence.receiver]
    
    // Add basic labels from discovery
    relabel_configs = [
        {
            source_labels = ["__meta_docker_container_label_service"]
            target_label  = "service"
        }
    ]
}
```

**🎓 What's Happening Here:**
1. Alloy connects to Docker socket
2. Reads log streams from each container
3. Adds service labels from container metadata
4. Forwards raw logs to processing pipeline

**Test This Step:**
```bash
# See raw logs flowing
docker logs accounts-service | head -5

# Verify Alloy is collecting logs (modern Alloy metrics)
curl -s http://localhost:12345/metrics | grep "loki_source_docker_target_entries_total"

# Check for any collection errors
curl -s http://localhost:12345/metrics | grep "loki_source_docker_target_parsing_errors_total"
```

### **2.3 The Intelligence Layer - Multi-Stage Processing**

Now comes the **magic** - transforming raw text into structured data:

```hcl
// ====================================================================
// STEP 2.3: INTELLIGENT PROCESSING - Raw Text → Structured Data
// ====================================================================

loki.process "banking_intelligence" {
    forward_to = [loki.write.default.receiver]
    
    // STAGE 1: Basic Log Level Extraction
    stage.regex {
        expression = `(?P<timestamp>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z)\s+(?P<level>TRACE|DEBUG|INFO|WARN|ERROR|FATAL)`
    }
    
    // STAGE 2: JSON Structure Detection and Parsing
    stage.match {
        selector = `{service=~"accounts|cards|loans|customers"}`
        
        // Look for JSON structures in log messages
        stage.regex {
            expression = `"businessContext":\{(?P<business_json>[^}]+)\}`
        }
        
        // Parse the JSON business context
        stage.json {
            expressions = {
                customer_id = "businessContext.customerId",
                customer_tier = "businessContext.customerTier", 
                transaction_type = "businessContext.transactionType",
                transaction_amount = "businessContext.amount",
                trace_id = "traceId",
                span_id = "spanId",
                compliance_required = "businessContext.complianceRequired"
            }
        }
    }
    
    // STAGE 3: Business Intelligence Enrichment
    stage.template {
        source = "priority_level"
        template = `{{ if eq .customer_tier "PREMIUM" }}high{{ else if eq .customer_tier "GOLD" }}medium{{ else }}low{{ end }}`
    }
    
    // STAGE 4: Compliance and Risk Assessment  
    stage.template {
        source = "risk_category"
        template = `{{ if and .compliance_required (gt .transaction_amount 10000) }}high_risk{{ else if .compliance_required }}medium_risk{{ else }}low_risk{{ end }}`
    }
    
    // STAGE 5: Dynamic Label Assignment
    stage.labels {
        values = {
            level = "",
            service = "",
            customer_tier = "",
            priority_level = "", 
            risk_category = "",
            environment = "production"
        }
    }
    
    // STAGE 6: Metrics Generation from Logs
    stage.metrics {
        // Business transaction counter
        banking_transactions_total = {
            type = "Counter"
            description = "Total banking transactions by service and tier"
            source = "transaction_amount"
            config = {
                action = "inc"
            }
        }
        
        // High-value transaction tracking
        banking_high_value_transactions_total = {
            type = "Counter" 
            description = "High-value transactions requiring compliance review"
            source = "transaction_amount"
            config = {
                action = "inc"
                match = "> 10000"
            }
        }
        
        // Transaction amount histogram
        banking_transaction_amount = {
            type = "Histogram"
            description = "Distribution of transaction amounts"
            source = "transaction_amount"
            config = {
                buckets = [100, 500, 1000, 5000, 10000, 50000, 100000]
            }
        }
    }
    
    // STAGE 7: Trace Correlation Enhancement  
    stage.template {
        source = "correlation_id"
        template = `{{ .trace_id }}-{{ .customer_id }}`
    }
}
```

**🎓 Deep Dive Exercise - Log Transformation:**

Let's trace a **single log line** through each stage:

**Input (Raw Log):**
```
2025-09-24T10:15:23.462Z INFO c.b.AccountService - {"businessContext":{"customerId":"CUST_12345","customerTier":"PREMIUM","amount":5000.00},"traceId":"abc123xyz789"}
```

**After Stage 1 (Regex Extraction):**
```
timestamp = "2025-09-24T10:15:23.462Z"
level = "INFO"
```

**After Stage 2 (JSON Parsing):**
```  
customer_id = "CUST_12345"
customer_tier = "PREMIUM"
transaction_amount = "5000.00"
trace_id = "abc123xyz789"
```

**After Stage 3 (Business Enrichment):**
```
priority_level = "high"  // Because customer_tier == "PREMIUM"
```

**After Stage 4 (Risk Assessment):**
```
risk_category = "medium_risk"  // compliance_required=true, amount < 10000
```

**After Stage 5 (Label Assignment):**
```
Labels: {
    service="accounts",
    level="INFO", 
    customer_tier="PREMIUM",
    priority_level="high",
    risk_category="medium_risk"
}
```

**After Stage 6 (Metrics Generation):**
```
banking_transactions_total{service="accounts",customer_tier="PREMIUM"} += 1
banking_transaction_amount_bucket{service="accounts",le="5000"} += 1
banking_transaction_amount_sum{service="accounts"} += 5000
```

**After Stage 7 (Correlation):**
```
correlation_id = "abc123xyz789-CUST_12345"
```

**🔬 Test the Transformation:**
```bash
# Verify logs are being processed and sent to Loki
curl -s http://localhost:12345/metrics | grep "loki_source_docker_target_entries_total"

# See the transformed logs in Loki
curl -G 'http://localhost:3100/loki/api/v1/query' \
  --data-urlencode 'query={service="accounts",customer_tier="PREMIUM"}' \
  --data-urlencode 'limit=5' | jq '.data.result'

# Check processing pipeline health
curl -s http://localhost:12345/metrics | grep "loki_process.*errors_total"
```

### **2.4 Metrics Collection - Direct Prometheus Architecture** 

**🎯 Important Architecture Clarification**: In our production setup, **Prometheus scrapes metrics directly** from microservices, not through Alloy. This is actually a **superior architecture** for performance and simplicity.

**Our Actual Working Configuration:**

**Prometheus Configuration (`prometheus/prometheus.yml`):**
```yaml
scrape_configs:
  # Direct scraping from each microservice
  - job_name: 'accounts-service'
    static_configs:
      - targets: ['host.docker.internal:8081']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 5s

  - job_name: 'cards-service'
    static_configs:
      - targets: ['host.docker.internal:8082']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 5s

  - job_name: 'loans-service'
    static_configs:
      - targets: ['host.docker.internal:8083']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 5s

  # ... similar for customers, gateway, eureka services
```

**🏗️ Actual Data Flow:**
```
📊 METRICS PIPELINE (Direct Scraping):
┌─────────────────────────────────────────────────┐
│             PROMETHEUS                          │
│         (Direct Scraping)                       │
└─────────────────┬───────────────────────────────┘
                  │ Every 5 seconds
                  ▼
    ┌─────────────────────────────────────┐
    │        MICROSERVICES                │
    │  /actuator/prometheus endpoints     │
    │  • accounts-service:8081            │
    │  • cards-service:8082               │
    │  • loans-service:8083               │
    │  • customers-service:8084           │
    │  • gateway-service:8072             │
    │  • eureka-service:8761              │
    └─────────────────────────────────────┘

📝 LOGS PIPELINE (Alloy Processing):
┌─────────────────────────────────────────────────┐
│              ALLOY                              │
│        (Log Processing Only)                    │
└─────────────────┬───────────────────────────────┘
                  │ Docker log streams
                  ▼
    ┌─────────────────────────────────────┐
    │        MICROSERVICES                │
    │      Docker containers              │
    └─────────────────────────────────────┘
```

**🎓 Test the Actual Metrics Architecture:**
```bash
# Verify Prometheus is scraping all 7 targets (6 microservices + prometheus)
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets | length'

# Check health of all scraping targets
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {job: .labels.job, health: .health}'

# Test direct microservice metrics access
curl http://localhost:8081/actuator/prometheus | grep banking_ | head -3

# Query aggregated metrics in Prometheus
curl 'http://localhost:9090/api/v1/query?query=banking_transactions_total'

# See them in Prometheus  
curl 'http://localhost:9090/api/v1/query?query=banking_transactions_total'
```
```

### **2.5 Distributed Tracing Pipeline**

```hcl
// ====================================================================  
// STEP 2.5: TRACE COLLECTION - Distributed Request Journey
// ====================================================================

// Receive traces from microservices
otelcol.receiver.otlp "microservices_traces" {
    grpc {
        endpoint = "0.0.0.0:4317"
    }
    http {
        endpoint = "0.0.0.0:4318"  
    }
    
    output {
        traces = [otelcol.processor.batch.default.input]
    }
}

// Batch processing for efficiency
otelcol.processor.batch "default" {
    output {
        traces = [otelcol.processor.attributes.business_enrichment.input]
    }
}

// Add business context to traces
otelcol.processor.attributes "business_enrichment" {
    action {
        key = "business.priority"
        action = "insert"
        from_attribute = "customer.tier"
        // Convert PREMIUM -> high, GOLD -> medium, STANDARD -> low
    }
    
    action {
        key = "business.revenue_impact" 
        action = "insert"
        value = "high"
        # Apply only to spans with customer.tier = "PREMIUM"
    }
    
    output {
        traces = [otelcol.exporter.otlp.tempo.input]
    }
}

// Send to Tempo for storage
otelcol.exporter.otlp "tempo" {
    client {
        endpoint = "http://tempo:3200"
        tls {
            insecure = true
        }
    }
}
```

**🎓 Understanding Trace Flow:**

1. **Microservice** generates span: `trace_id=abc123, span_id=span-001, customer.id=CUST_12345`
2. **OTLP Receiver** ingests the span over HTTP/gRPC
3. **Batch Processor** groups spans for efficient processing
4. **Attributes Processor** adds business context: `business.priority=high`
5. **Tempo Exporter** stores the enriched span

**Test Trace Collection:**
```bash
# Check OTLP receiver metrics
curl -s http://localhost:12345/metrics | grep "otelcol_receiver_accepted_spans"

# Verify trace processing
curl -s http://localhost:12345/metrics | grep "otelcol_exporter_sent_spans"

# Query traces in Tempo
curl 'http://localhost:3200/api/search?tags=customer.id=CUST_12345'
```

---

# 🗄️ **Chapter 3: Storage Backends - Where Data Lives**

## **Step 3: Understanding How Each Storage System Optimizes Data**

### **3.1 Loki - Log Storage and Indexing**

**How Loki Stores Your Processed Logs:**

```yaml
# Loki Configuration (docker-compose.yml)
loki:
  image: grafana/loki:latest
  command: -config.file=/etc/loki/local-config.yaml
  volumes:
    - ./loki:/etc/loki
  environment:
    - LOKI_CONFIG_FILE=/etc/loki/local-config.yaml
```

**Loki's Storage Strategy:**
```yaml
# loki/local-config.yaml
schema_config:
  configs:
    - from: 2020-10-24
      store: boltdb-shipper
      object_store: filesystem
      schema: v11
      index:
        prefix: index_
        period: 24h

# How your logs are stored:
# /loki/chunks/
#   ├── index_19708/  (daily indexes)  
#   ├── fake/          (chunk storage)
#   └── compactor/     (optimization)
```

**🎓 Understanding Loki's Magic:**

Loki doesn't index log content (like Elasticsearch). Instead, it indexes **labels**:

```bash
# Your log with labels:
{service="accounts", customer_tier="PREMIUM", level="INFO"} 
"Transfer completed for customer CUST_12345 amount $5000"

# Loki creates chunk:
Chunk ID: chunk_001
Labels: service=accounts,customer_tier=PREMIUM,level=INFO  
Content: compressed log lines
Time Range: 2025-09-24T10:15:00Z to 2025-09-24T10:16:00Z
```

**Test Loki Storage:**
```bash
# Query by labels (fast - uses index)
curl -G 'http://localhost:3100/loki/api/v1/query' \
  --data-urlencode 'query={service="accounts", customer_tier="PREMIUM"}'

# Full-text search (slower - scans chunks) 
curl -G 'http://localhost:3100/loki/api/v1/query' \
  --data-urlencode 'query={service="accounts"} |= "CUST_12345"'

# See storage structure
docker exec -it loki ls -la /loki/chunks/
```

### **3.2 Prometheus - Metrics Storage and Time Series**

**How Prometheus Stores Your Metrics:**

```yaml
# Prometheus Configuration 
prometheus:
  image: prom/prometheus:latest
  command:
    - '--config.file=/etc/prometheus/prometheus.yml'
    - '--storage.tsdb.path=/prometheus'
    - '--storage.tsdb.retention.time=30d'
  volumes:
    - ./prometheus:/etc/prometheus
```

**Time Series Database Structure:**
```bash
# Your metric from Alloy:
banking_transactions_total{service="accounts",customer_tier="PREMIUM"} 156

# Prometheus stores as time series:
Series ID: 12345
Labels: __name__=banking_transactions_total, service=accounts, customer_tier=PREMIUM
Samples: [(1727172923, 155), (1727172938, 156), (1727172953, 157)]
         ^timestamp    ^value
```

**🎓 Time Series Storage Deep Dive:**

```bash  
# See internal Prometheus data structure
docker exec -it prometheus sh

# Prometheus storage files:
ls -la /prometheus/
# 01HJKM7... (block directories)
# chunks_head/ (current data)
# wal/ (write-ahead log)

# Query internal metrics
curl 'http://localhost:9090/api/v1/query?query=prometheus_tsdb_head_samples_appended_total'
```

**Storage Optimization:**
- **Blocks**: 2-hour immutable chunks
- **Compaction**: Merges blocks, removes old data
- **Compression**: Efficient delta encoding

### **3.3 Tempo - Trace Storage and Correlation**

**How Tempo Stores Distributed Traces:**

```yaml
# Tempo Configuration
tempo:
  image: grafana/tempo:latest
  command: ["-config.file=/etc/tempo.yaml"]
  volumes:
    - ./tempo:/etc/tempo
```

**Trace Storage Strategy:**
```yaml
# tempo/tempo.yaml
storage:
  trace:
    backend: local
    local:
      path: /tmp/tempo/traces
    
compactor:
  compaction:
    block_retention: 24h
```

**🎓 Understanding Trace Storage:**

Your distributed trace gets stored as:
```json
{
  "traceID": "abc123xyz789",
  "spans": [
    {
      "spanID": "span-gateway-001",
      "operationName": "POST /api/v1/accounts/transfer",
      "startTime": 1727172923456000000,
      "duration": 120000000,
      "tags": {"service.name": "gateway", "customer.id": "CUST_12345"}
    },
    {
      "spanID": "span-accounts-001", 
      "parentSpanID": "span-gateway-001",
      "operationName": "transfer_funds",
      "startTime": 1727172923458000000,
      "duration": 98000000,
      "tags": {"service.name": "accounts", "customer.tier": "PREMIUM"}
    }
  ]
}
```

**Test Trace Storage:**
```bash
# Query traces by trace ID
curl 'http://localhost:3200/api/traces/abc123xyz789'

# Search traces by tags
curl 'http://localhost:3200/api/search?tags=customer.id=CUST_12345&start=1727172900&end=1727172999'

# See trace storage
docker exec -it tempo ls -la /tmp/tempo/traces/
```

---

# 📊 **Chapter 4: Grafana - Bringing It All Together**

## **Step 4: Creating Intelligence from Raw Data**

### **4.1 Data Source Configuration**

**Connecting Grafana to All Storage Backends:**

```yaml
# grafana/provisioning/datasources/datasources.yml
apiVersion: 1

datasources:
  # Loki for logs
  - name: Loki
    type: loki
    access: proxy  
    url: http://loki:3100
    
  # Prometheus for metrics
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    
  # Tempo for traces
  - name: Tempo
    type: tempo
    access: proxy
    url: http://tempo:3200
    
    # Enable trace correlation
    jsonData:
      tracesToLogs:
        datasourceUid: loki
        tags: ['trace_id']
        mappedTags: [{ key: 'trace_id', value: 'trace_id' }]
      
      tracesToMetrics:
        datasourceUid: prometheus
        tags: [{ key: 'service', value: 'service' }]
```

### **4.2 Building Intelligent Dashboards**

Let's create a **comprehensive banking dashboard** that shows the complete customer journey:

**`grafana/provisioning/dashboards/banking-overview.json`**

```json
{
  "dashboard": {
    "title": "Banking Microservices - Customer Intelligence",
    "panels": [
      {
        "title": "🏦 Transaction Volume by Customer Tier",
        "type": "stat",
        "targets": [
          {
            "datasource": "Prometheus",
            "expr": "sum by (customer_tier) (rate(banking_transactions_total[5m]))",
            "legendFormat": "{{customer_tier}} customers"
          }
        ],
        "fieldConfig": {
          "mappings": [
            {"value": 0, "text": "No Activity", "color": "red"},
            {"value": 100, "text": "High Activity", "color": "green"}
          ]
        }
      },
      
      {
        "title": "💰 Revenue Impact by Service",
        "type": "bargauge", 
        "targets": [
          {
            "datasource": "Prometheus",
            "expr": "sum by (service) (banking_transaction_amount_sum)",
            "legendFormat": "{{service}}"
          }
        ]
      },
      
      {
        "title": "🔍 Customer Transaction Journey",
        "type": "logs",
        "targets": [
          {
            "datasource": "Loki",
            "expr": "{service=~\"accounts|cards|loans\"} | json | customer_id=\"$customer_id\"",
            "refId": "A"
          }
        ]
      },
      
      {
        "title": "📈 Response Time Distribution",
        "type": "heatmap",
        "targets": [
          {
            "datasource": "Prometheus", 
            "expr": "rate(banking_transaction_amount_bucket[5m])",
            "format": "heatmap",
            "legendFormat": "{{le}}"
          }
        ]
      }
    ],
    
    "templating": {
      "list": [
        {
          "name": "customer_id",
          "type": "textbox", 
          "label": "Customer ID",
          "placeholder": "Enter customer ID (e.g., CUST_12345)"
        },
        {
          "name": "time_range",
          "type": "interval",
          "options": ["5m", "15m", "1h", "6h", "24h"]
        }
      ]
    }
  }
}
```

### **4.3 Advanced Correlation Queries**

**🎓 Learning Exercise - Building Correlation Queries:**

**Query 1: Find All Data for a Customer Transaction**
```bash
# Step 1: Find the trace ID from logs
{service="accounts"} | json | customer_id="CUST_12345" | trace_id != ""

# Step 2: Get metrics for that timeframe  
banking_transactions_total{service="accounts"}[1h] 

# Step 3: Find the complete trace
# (Use trace_id from step 1 in Tempo)
```

**Query 2: Business Impact Analysis**
```promql
# Revenue at risk from slow transactions
(
  rate(banking_transaction_amount_sum[5m]) 
  * 
  on(service) group_left
  (http_request_duration_seconds{quantile="0.95"} > 2)
) * 60 * 60  # Convert to hourly revenue impact
```

**Query 3: Compliance Correlation**
```logql
# Find all high-risk transactions with their traces
{service=~"accounts|cards|loans"} 
| json 
| risk_category="high_risk" 
| trace_id != ""
| line_format "{{.timestamp}} [{{.service}}] Customer: {{.customer_id}} Amount: ${{.transaction_amount}} Trace: {{.trace_id}}"
```

### **4.4 Creating Intelligent Alerts**

**Alert Rules that Understand Your Business:**

```yaml
# grafana/provisioning/alerting/rules.yml
groups:
  - name: banking_business_alerts
    rules:
      - alert: HighValueTransactionSpike
        expr: |
          rate(banking_high_value_transactions_total[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
          team: compliance
        annotations:
          summary: "Unusual high-value transaction activity detected"
          description: |
            High-value transactions (>$10k) are occurring at {{ $value }} per second.
            Normal rate is <0.05/sec. This may indicate:
            - Legitimate business activity increase
            - Potential fraudulent activity  
            - System processing backlog
            
            Investigation required within 15 minutes.
          
          runbook_url: "https://wiki.company.com/banking/high-value-alert-response"
          dashboard_url: "http://grafana:3000/d/banking-compliance"
          
      - alert: CustomerExperienceDegradation  
        expr: |
          (
            histogram_quantile(0.95, 
              rate(http_request_duration_seconds_bucket{service=~"accounts|cards|loans"}[5m])
            ) > 2
          ) 
          and 
          (
            sum by (service) (rate(banking_transactions_total{customer_tier="PREMIUM"}[5m])) > 0
          )
        for: 5m
        labels:
          severity: critical
          team: engineering
          customer_impact: premium
        annotations:
          summary: "Premium customer experience degraded - slow response times"
          description: |
            95th percentile response time is {{ $value }}s (>2s threshold).
            This is affecting {{ with query "sum(rate(banking_transactions_total{customer_tier=\"PREMIUM\"}[5m]))" }}{{ . | first | value | humanize }}{{ end }} premium customers per second.
            
            Estimated revenue impact: ${{ with query "sum(rate(banking_transaction_amount_sum{customer_tier=\"PREMIUM\"}[5m])) * 60 * 60" }}{{ . | first | value | humanize }}{{ end }}/hour
            
            🎯 Immediate Actions Required:
            1. Check database connection pool status
            2. Review application logs for errors  
            3. Verify infrastructure resource utilization
            4. Consider scaling if needed
```

---

# 🔧 **Chapter 5: Hands-On Troubleshooting Exercise**

## **Step 5: Becoming an Observability Detective**

### **5.1 Scenario: Mystery Performance Issue**

**📞 The Call:** *"Our premium banking customers are complaining about slow transfers. Revenue is at risk!"*

**🕵️ Your Mission:** Use the observability stack to identify the root cause and solve it.

### **5.2 Investigation Methodology**

**Step 1: Start with Business Impact**
```promql
# How many premium customers are affected?
sum(rate(banking_transactions_total{customer_tier="PREMIUM"}[5m]))

# What's the revenue impact?
sum(rate(banking_transaction_amount_sum{customer_tier="PREMIUM"}[5m])) * 3600
```

**Expected Output:**
```
Query 1: 2.3 transactions/sec (premium customers)
Query 2: $45,000/hour revenue impact
```

**Step 2: Identify the Performance Problem**
```promql
# Check response times by service
histogram_quantile(0.95, 
  rate(http_request_duration_seconds_bucket{service=~"accounts|cards|loans"}[5m])
)

# Compare with normal baseline
histogram_quantile(0.95,
  rate(http_request_duration_seconds_bucket{service=~"accounts|cards|loans"}[1h] offset 1d)
)
```

**Expected Results:**
```
Current 95th percentile: 4.2 seconds
Yesterday's baseline: 0.8 seconds
🚨 Problem confirmed: 5x slower than normal!
```

**Step 3: Drill Down to Root Cause**
```logql
# Find error patterns in logs
{service="accounts", level="ERROR"} 
| json 
| line_format "{{.timestamp}} {{.message}}"
| count_over_time(5m)
```

**Sample Output:**
```
2025-09-24T10:15:23Z Connection timeout to database: accounts_db
2025-09-24T10:15:25Z Connection timeout to database: accounts_db  
2025-09-24T10:15:27Z Connection timeout to database: accounts_db
Count: 47 errors in last 5 minutes
```

**Step 4: Trace the Complete Customer Journey**
```bash
# Find a slow trace
curl 'http://localhost:3200/api/search?tags=customer.tier=PREMIUM&minDuration=2s'

# Get specific trace details
curl 'http://localhost:3200/api/traces/abc123xyz789' | jq '.traces[0].spans[] | {service: .process.serviceName, operation: .operationName, duration: .duration}'
```

**Trace Analysis:**
```json
[
  {"service": "gateway", "operation": "POST /transfer", "duration": 120000},
  {"service": "accounts", "operation": "transfer_funds", "duration": 4180000}, 
  {"service": "accounts", "operation": "db_query", "duration": 4150000}
]
```

**🔍 Root Cause Identified:** Database query taking 4.15 seconds (normal: 0.05s)

### **5.3 Advanced Correlation Analysis**

**Find All Related Evidence:**
```logql
# Get all logs for the problematic trace
{service=~"accounts|gateway"} 
| json 
| trace_id="abc123xyz789"
| line_format "{{.timestamp}} [{{.service}}] {{.message}}"
```

**Check Infrastructure Metrics:**
```promql
# Database connection pool exhaustion?
mysql_global_status_threads_connected / mysql_global_variables_max_connections

# Database CPU/Memory usage
rate(mysql_global_status_slow_queries[5m])
```

### **5.4 Resolution and Verification**

**Apply Fix:**
```bash
# Scale database connection pool
kubectl scale deployment accounts-db --replicas=3

# Or tune connection pool settings
kubectl patch configmap accounts-config --patch='{"data":{"db.pool.size":"50"}}'
```

**Verify Resolution:**
```promql
# Response times back to normal?
histogram_quantile(0.95,
  rate(http_request_duration_seconds_bucket{service="accounts"}[5m])
)

# Error rate decreased?
rate(banking_transactions_errors_total[5m])

# Customer satisfaction restored?
sum(rate(banking_transactions_total{customer_tier="PREMIUM"}[5m]))
```

---

# 🎯 **Chapter 6: Advanced Topics and Optimization**

## **Step 6: Becoming a Performance Tuning Expert**

### **6.1 Alloy Performance Optimization**

**Memory and CPU Tuning:**
```hcl
// Optimize Alloy for high throughput
loki.process "optimized_banking" {
    // Batch processing for efficiency
    forward_to = [loki.write.optimized.receiver]
    
    // Reduce regex operations (expensive)
    stage.match {
        selector = `{service="accounts"}`
        // Only process account service logs with this expensive regex
        stage.regex {
            expression = `complex_pattern_here`
        }
    }
    
    // Use JSON parsing instead of regex when possible
    stage.json {
        expressions = {
            customer_id = "context.customerId",  // Faster than regex
            amount = "context.amount"
        }
    }
}

// Tuned write configuration
loki.write "optimized" {
    endpoint {
        url = "http://loki:3100/loki/api/v1/push"
        
        // Batch configuration for performance
        batch_wait = "1s"        // Wait 1s to batch logs
        batch_size = 1048576     // 1MB batches
        
        // Retry configuration  
        max_retries = 3
        min_backoff = "500ms"
        max_backoff = "5m"
    }
}
```

### **6.2 Storage Optimization Strategies**

**Loki Index Optimization:**
```yaml
# loki/local-config.yaml
limits_config:
  # Optimize for your cardinality
  max_streams_per_user: 10000
  max_line_size: 256000
  
# Choose retention based on compliance needs
table_manager:
  retention_deletes_enabled: true
  retention_period: 2555h  # SOX compliance: 7 years

# Optimize chunk size for your log volume
chunk_store_config:
  max_look_back_period: 24h
  
ingester:
  # Tune for performance vs memory
  chunk_target_size: 1572864  # 1.5MB chunks
  max_chunk_age: 2h
```

**Prometheus Storage Optimization:**
```yaml
# prometheus/prometheus.yml
global:
  scrape_interval: 15s      # Balance between granularity and storage
  evaluation_interval: 15s
  
# Retention based on business needs
rule_files:
  - "/etc/prometheus/recording_rules.yml"  # Pre-aggregate expensive queries

# Recording rules for common queries
recording_rules.yml: |
  groups:
    - name: banking_aggregations
      interval: 30s
      rules:
        # Pre-calculate expensive business metrics
        - record: banking:transaction_rate_5m
          expr: sum(rate(banking_transactions_total[5m])) by (service, customer_tier)
          
        - record: banking:revenue_rate_5m  
          expr: sum(rate(banking_transaction_amount_sum[5m])) by (service)
```

### **6.3 Capacity Planning and Scaling**

**Resource Requirements Calculator:**
```bash
# Calculate your observability resource needs

# Log volume estimation
DAILY_LOG_LINES=$(echo "scale=0; $TRANSACTIONS_PER_DAY * $SERVICES * $LOGS_PER_TRANSACTION" | bc)
DAILY_LOG_SIZE_GB=$(echo "scale=2; $DAILY_LOG_LINES * $AVG_LOG_LINE_BYTES / 1024 / 1024 / 1024" | bc)

# Metrics cardinality
METRIC_SERIES=$(echo "$SERVICES * $CUSTOMERS * $METRIC_TYPES" | bc)
DAILY_METRIC_SIZE_GB=$(echo "scale=2; $METRIC_SERIES * $SAMPLES_PER_DAY * 16 / 1024 / 1024 / 1024" | bc)

# Trace volume
DAILY_TRACE_SIZE_GB=$(echo "scale=2; $TRANSACTIONS_PER_DAY * $AVG_SPANS_PER_TRACE * $AVG_SPAN_SIZE_BYTES / 1024 / 1024 / 1024" | bc)

echo "Daily Storage Requirements:"
echo "Logs: ${DAILY_LOG_SIZE_GB}GB"
echo "Metrics: ${DAILY_METRIC_SIZE_GB}GB"  
echo "Traces: ${DAILY_TRACE_SIZE_GB}GB"
echo "Total: $(echo "$DAILY_LOG_SIZE_GB + $DAILY_METRIC_SIZE_GB + $DAILY_TRACE_SIZE_GB" | bc)GB"
```

### **6.4 Advanced Alerting Strategies**

**Intelligent Alert Correlation:**
```yaml
# grafana/provisioning/alerting/smart_alerts.yml
groups:
  - name: intelligent_banking_alerts
    rules:
      # Multi-signal correlation alert
      - alert: BusinessImpactCorrelation
        expr: |
          # High error rate
          (rate(banking_transactions_errors_total[5m]) > 0.01)
          and
          # Affecting high-value customers  
          (sum(rate(banking_transactions_total{customer_tier=~"PREMIUM|GOLD"}[5m])) > 0)
          and
          # During business hours (9 AM - 5 PM UTC)
          (hour() >= 9 and hour() <= 17)
        for: 3m
        labels:
          severity: critical
          alert_type: business_impact
          escalation: immediate
        annotations:
          summary: "Business-critical issue affecting premium customers"
          description: |
            🚨 BUSINESS IMPACT ALERT 🚨
            
            Multiple signals indicate a critical business issue:
            • Error rate: {{ with query "rate(banking_transactions_errors_total[5m])" }}{{ . | first | value | humanizePercentage }}{{ end }}
            • Premium customers affected: {{ with query "sum(rate(banking_transactions_total{customer_tier=\"PREMIUM\"}[5m]))" }}{{ . | first | value | humanize }}/sec{{ end }}
            • Revenue at risk: ${{ with query "sum(rate(banking_transaction_amount_sum{customer_tier=~\"PREMIUM|GOLD\"}[5m])) * 3600" }}{{ . | first | value | humanize }}/hour{{ end }}
            
            🎯 IMMEDIATE ACTIONS:
            1. Page on-call engineer
            2. Activate incident response 
            3. Notify customer success team
            4. Prepare customer communication
          
          incident_response_url: "https://incident.company.com/create"
          customer_comms_template: "https://wiki.company.com/templates/customer-incident"
```

---

# 🎓 **Chapter 7: Mastery Verification**

## **Step 7: Your Final Observability Challenge**

### **7.1 The Ultimate Test Scenario**

**🎯 Challenge:** *A new banking product launch is causing mysterious issues. Premium customers report intermittent transfer failures, but only during peak hours (2-4 PM). Your CEO needs answers in 30 minutes.*

**Your toolkit:**
- Complete observability stack
- 30 days of historical data
- Full access to logs, metrics, and traces

### **7.2 Investigation Checklist**

**✅ Business Impact Assessment (5 minutes)**
- [ ] Customer tier analysis: `sum by (customer_tier) (rate(banking_transactions_errors_total[1h]))`
- [ ] Revenue impact: `sum(rate(banking_transaction_amount_sum{status="failed"}[1h])) * 24`
- [ ] Geographic distribution: `sum by (region) (rate(banking_transactions_total[1h]))`

**✅ Time Pattern Analysis (5 minutes)**
- [ ] Peak hour correlation: `rate(banking_transactions_errors_total[5m])[2h:1m]`
- [ ] Day-over-day comparison: `rate(banking_transactions_errors_total[5m] offset 1d)`
- [ ] Weekly pattern: `rate(banking_transactions_errors_total[5m] offset 7d)`

**✅ System Deep Dive (10 minutes)**
- [ ] Service error rates: `rate(http_requests_total{status=~"5.."}[5m]) by (service)`
- [ ] Database performance: `mysql_global_status_slow_queries`
- [ ] Infrastructure: `rate(container_cpu_usage_seconds_total[5m]) by (container_name)`

**✅ Root Cause Isolation (5 minutes)**
- [ ] Find affected traces: Search Tempo for failed premium transactions
- [ ] Correlate logs: `{service=~".*"} | json | customer_tier="PREMIUM" | status="error"`
- [ ] Identify bottleneck: Analyze trace span durations

**✅ Solution Verification (5 minutes)**
- [ ] Validate hypothesis with historical data
- [ ] Estimate fix impact and timeline
- [ ] Prepare executive summary

### **7.3 Expected Findings and Solutions**

**Sample Investigation Results:**

**Business Impact:**
- 347 premium customers affected per hour during peak
- $23,400/hour revenue at risk  
- 12% of premium transactions failing

**Root Cause Discovery:**
- New product launch increased database connections by 300%
- Connection pool exhausted during peak hours (2-4 PM)
- Database queries timing out after 30 seconds
- Cascading failures across all services

**Solution:**
- Immediate: Scale connection pool from 20 to 80 connections
- Short-term: Implement connection pool monitoring alerts
- Long-term: Database read replicas for transaction queries

**🎯 Real Architecture Verification Commands:**

```bash
# Verify your working Prometheus targets (should show 7 healthy targets)
curl -s http://localhost:9090/api/v1/targets | \
  jq '.data.activeTargets[] | {job: .labels.job, health: .health, lastScrape: .lastScrape}'

# Test direct microservice metrics access
curl -s http://localhost:8081/actuator/prometheus | grep banking_ | head -5

# Verify Alloy is processing logs (not metrics in our architecture)
curl -s http://localhost:3100/loki/api/v1/label/service/values

# Check trace collection via OTEL collector  
curl -s http://localhost:3200/api/search | jq '.data | length'
```

---

# 🏆 **Graduation: You're Now an Observability Expert!**

## **🎯 What You've Mastered**

### **Technical Expertise**
✅ **Data Pipeline Architecture**: Raw logs → Alloy processing → Storage → Dashboards  
✅ **Multi-signal Correlation**: Logs + Metrics + Traces working together  
✅ **Performance Optimization**: Resource tuning, storage efficiency, query optimization  
✅ **Advanced Troubleshooting**: Root cause analysis using complete observability data  
✅ **Business Intelligence**: Converting technical metrics into business insights  

### **Practical Skills**
✅ **Configuration Mastery**: Can configure every component from scratch  
✅ **Query Expertise**: Write complex LogQL, PromQL, and trace queries  
✅ **Alert Engineering**: Create intelligent, business-aware alerts  
✅ **Performance Tuning**: Optimize for scale, cost, and reliability  
✅ **Incident Response**: Use observability for rapid issue resolution  

### **Business Acumen**
✅ **ROI Calculation**: Measure observability business value  
✅ **Risk Assessment**: Identify customer and revenue impact  
✅ **Compliance Integration**: Banking regulatory requirements  
✅ **Strategic Planning**: Capacity planning and scaling decisions  

---

## 🚀 **Your Observability Toolkit**

You now have a **production-ready observability stack** with:

```
📊 COMPLETE DASHBOARD SUITE
├── Business Intelligence Dashboard
├── Technical Performance Dashboard  
├── Customer Journey Visualization
├── Compliance and Risk Dashboard
└── Infrastructure Health Dashboard

🔍 ADVANCED QUERY LIBRARY
├── Business Impact Queries
├── Performance Analysis Queries
├── Customer Experience Queries  
├── Compliance Reporting Queries
└── Root Cause Analysis Queries

🚨 INTELLIGENT ALERTING
├── Business Impact Alerts
├── Customer Experience Alerts
├── Performance Degradation Alerts
├── Compliance Violation Alerts  
└── Predictive Maintenance Alerts

🛠️ TROUBLESHOOTING RUNBOOKS
├── Performance Issue Investigation
├── Customer Impact Assessment
├── Database Problem Resolution
├── Service Failure Response
└── Capacity Planning Procedures
```

---

## 🎯 **Next Steps: Advanced Mastery**

### **1. AI-Powered Observability**
- Implement machine learning for anomaly detection
- Build predictive alerting based on historical patterns
- Create auto-remediation workflows

### **2. Advanced Business Intelligence**
- Real-time customer experience scoring
- Revenue impact prediction models
- Competitive analysis through performance metrics

### **3. Compliance and Security**
- Advanced PII detection and redaction
- Audit trail automation
- Regulatory reporting dashboards

### **4. Scale and Optimization**
- Multi-region observability architecture
- Cost optimization strategies  
- Performance tuning for massive scale

---

## 🎉 **Congratulations!**

You've transformed from observability novice to **expert practitioner**. You now understand:

- ✨ **How data flows** from application output to business insights
- 🔧 **How to configure** every component in the stack
- 🔍 **How to troubleshoot** any issue using observability data
- 📊 **How to create** intelligent dashboards and alerts
- 💰 **How to demonstrate** business value and ROI

**You're ready to:**
- Lead observability initiatives at your organization
- Design and implement production observability stacks
- Train other team members on observability best practices
- Make data-driven decisions about system performance and reliability

---

## 📚 **Quick Reference Guide**

### **Essential Commands**
```bash
# Health checks
curl -s http://localhost:12345/metrics | grep "alloy_build_info"    # Alloy health
curl http://localhost:3100/ready                                    # Loki health
curl http://localhost:9090/-/healthy                                # Prometheus health  
curl http://localhost:3200/ready                                    # Tempo health

# Alloy component status
curl -s http://localhost:12345/metrics | grep "loki_source_docker_target_entries_total"
curl -s http://localhost:12345/metrics | grep "prometheus_scrape_samples_scraped"
curl -s http://localhost:12345/metrics | grep "otelcol_receiver_accepted_spans"

# Quick queries
curl -G 'http://localhost:3100/loki/api/v1/query' --data-urlencode 'query={service="accounts"}'
curl 'http://localhost:9090/api/v1/query?query=banking_transactions_total'
curl 'http://localhost:3200/api/search?tags=customer.tier=PREMIUM'

# Performance monitoring
docker stats                                     # Resource usage
docker logs alloy-unified                       # Alloy processing logs
```

### **Key Configuration Files**
- `alloy/unified/alloy-unified.alloy` - Main observability pipeline
- `prometheus/prometheus.yml` - Metrics scraping config
- `loki/local-config.yaml` - Log storage configuration
- `tempo/tempo.yaml` - Trace storage configuration  
- `grafana/provisioning/` - Dashboard and alert definitions

### **🔧 Modern Alloy Troubleshooting**

**Common Issues and Solutions:**

```bash
# Issue: API endpoints return HTML instead of JSON
# Solution: Use the modern Web UI or metrics endpoints

# ✅ Correct: Check component health via metrics
curl -s http://localhost:12345/metrics | grep "alloy_component_controller_running_components"

# ✅ Correct: Monitor data flow
curl -s http://localhost:12345/metrics | grep "_target_entries_total"

# ✅ Correct: Check for errors
curl -s http://localhost:12345/metrics | grep "_errors_total"

# Issue: Old debug/targets endpoint doesn't work
# Solution: Use service discovery metrics
curl -s http://localhost:12345/metrics | grep "prometheus_sd_refresh_duration_seconds_count"

# Issue: Can't see live configuration
# Solution: Open Web UI in browser
open http://localhost:12345  # Modern Alloy dashboard
```

**Verification Checklist:**
- [ ] Alloy container is healthy: `docker ps | grep alloy-unified`
- [ ] Docker discovery working: `curl -s http://localhost:12345/metrics | grep "prometheus_sd_refresh_failures_total"`  
- [ ] Logs being collected: `curl -s http://localhost:12345/metrics | grep "loki_source_docker_target_entries_total"`
- [ ] Metrics being scraped: `curl -s http://localhost:12345/metrics | grep "prometheus_scrape_samples"`
- [ ] Traces being received: `curl -s http://localhost:12345/metrics | grep "otelcol_receiver_accepted"`
- [ ] No processing errors: `curl -s http://localhost:12345/metrics | grep "_errors_total"`

**🎯 Remember: Modern Alloy (v1.10+) uses a web-based interface and metrics-driven monitoring instead of REST API debugging endpoints. This provides better performance and a more intuitive experience.**

**🎯 Observability is not just about collecting data—it's about transforming that data into actionable business intelligence that helps you deliver better customer experiences and business outcomes.**

**Happy Observing! 🔭✨**