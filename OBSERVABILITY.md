# 🔭 **Complete Observability Guide for Banking Microservices**

*A comprehensive 15-minute deep-dive into modern observability concepts, implementation patterns, and hands-on examples for microservices architecture.*

---

## 🎯 **What You'll Master**

By the end of this guide, you'll understand:
- **What observability means** and why it's critical for microservices
- **The three pillars**: Logs, Metrics, and Traces with practical examples
- **Modern data pipeline architecture** using Grafana Alloy
- **Banking-specific observability patterns** for financial services
- **Hands-on implementation** with real configuration examples

**Estimated reading time: 15 minutes** ⏱️

---

## 📚 **Table of Contents**

1. [**Observability Fundamentals**](#observability-fundamentals) - What, Why, and How
2. [**The Three Pillars Deep Dive**](#the-three-pillars-deep-dive) - Logs, Metrics, Traces
3. [**Modern Architecture Overview**](#modern-architecture-overview) - Our Tech Stack
4. [**Unified Data Pipeline**](#unified-data-pipeline) - Grafana Alloy Configuration
5. [**Banking-Specific Patterns**](#banking-specific-patterns) - Financial Services Focus
6. [**Practical Implementation**](#practical-implementation) - Hands-on Examples
7. [**Advanced Topics**](#advanced-topics) - Performance and Best Practices
8. [**Troubleshooting Guide**](#troubleshooting-guide) - Common Issues & Solutions

---

# 🔍 **Observability Fundamentals**

## **What is Observability?**

**Observability** is the ability to understand the internal state of your system by examining its external outputs. Unlike traditional monitoring (which asks *"Is my system working?"*), observability asks *"Why is my system behaving this way?"*

### **Traditional Monitoring vs. Observability**

| Traditional Monitoring | Modern Observability |
|----------------------|---------------------|
| ❌ **Reactive**: Alerts after problems occur | ✅ **Proactive**: Understand system behavior before issues |
| ❌ **Limited**: Pre-defined dashboards and metrics | ✅ **Exploratory**: Query any dimension of your data |
| ❌ **Siloed**: Separate tools for logs, metrics, traces | ✅ **Unified**: Correlated view across all data types |

### **Why Microservices Need Observability**

In our banking microservices architecture, a single customer transaction flows through multiple services:

```
👤 Customer Request
  ↓
🌐 Gateway Service (authentication, routing)
  ↓
👥 Customer Service (profile lookup)
  ↓
💳 Accounts Service (balance check)
  ↓
💰 Loans Service (eligibility check)
  ↓
📊 Multiple downstream services...
```

**Traditional monitoring would tell us**: *"Accounts Service is slow"*  
**Observability tells us**: *"Premium customer transactions are failing due to database connection timeouts during loan eligibility checks, affecting 15% of high-value customers in the last 30 minutes"*

---

# 🏗️ **The Three Pillars Deep Dive**

## **1. 📝 Logs: The Story of What Happened**

**Logs** are immutable, timestamped records of discrete events that happened in your system.

### **Log Anatomy**

```json
{
  "timestamp": "2025-09-23T10:15:30.123Z",
  "level": "INFO",
  "service": "accounts",
  "logger": "com.banking.accounts.TransactionService",
  "thread": "http-nio-8081-exec-2",
  "message": "Transaction processed successfully",
  "mdc": {
    "traceId": "a1b2c3d4e5f6g7h8",
    "spanId": "x1y2z3a4b5c6",
    "customerId": "CUST_12345"
  },
  "businessContext": {
    "customerId": "CUST_12345",
    "customerTier": "PREMIUM",
    "transactionType": "TRANSFER",
    "amount": 50000.00,
    "complianceRequired": true
  }
}
```

### **Log Levels Explained**

| Level | Purpose | Example Use Case |
|-------|---------|-----------------|
| **TRACE** | Detailed execution flow | Entry/exit of methods, variable values |
| **DEBUG** | Development debugging | SQL queries, API request/response details |
| **INFO** | General information | Business transactions, service lifecycle |
| **WARN** | Potentially harmful situations | Deprecated API usage, retry attempts |
| **ERROR** | Error events but system continues | Business validation failures, external API errors |
| **FATAL** | Serious failures causing termination | Database connection failures, memory exhaustion |

### **Banking-Specific Log Examples**

```bash
# ✅ Good: Structured, searchable, contextual
INFO [accounts] Transaction completed: customerId=CUST_12345, amount=5000.00, type=TRANSFER, compliance=REQUIRED

# ❌ Bad: Unstructured, hard to query
INFO User did something with money
```

## **2. 📊 Metrics: The Vital Signs**

**Metrics** are numerical measurements taken over time intervals, providing quantitative insights into system performance and business outcomes.

### **Types of Metrics**

#### **1. Counter Metrics** (Always Increasing)
```prometheus
# Total number of transactions processed
banking_transactions_total{service="accounts", type="transfer"} 1247

# Total HTTP requests
http_requests_total{method="POST", endpoint="/api/v1/accounts", status="200"} 892
```

#### **2. Gauge Metrics** (Current Value)
```prometheus
# Current number of active connections
database_connections_active{service="accounts", database="primary"} 15

# Current account balance (business metric)
account_balance_current{customer_tier="premium", account_type="savings"} 125000.50
```

#### **3. Histogram Metrics** (Distribution of Values)
```prometheus
# Response time distribution
http_request_duration_seconds_bucket{method="GET", endpoint="/accounts", le="0.1"} 245
http_request_duration_seconds_bucket{method="GET", endpoint="/accounts", le="0.5"} 432
http_request_duration_seconds_bucket{method="GET", endpoint="/accounts", le="1.0"} 487
```

### **Banking Business Metrics**

Beyond technical metrics, we track business-critical measurements:

```prometheus
# Daily transaction volume by customer tier
banking_daily_volume_total{customer_tier="premium"} 2547000.00
banking_daily_volume_total{customer_tier="standard"} 892000.00

# Loan approval rates
banking_loan_decisions_total{decision="approved", loan_type="personal"} 142
banking_loan_decisions_total{decision="rejected", loan_type="personal"} 58

# Compliance monitoring
banking_high_value_transactions_total{amount_range="50k_plus", flagged="true"} 23
```

## **3. 🔗 Traces: The Journey Map**

**Traces** track requests as they flow through multiple services, showing the complete journey and timing of each step.

### **Trace Anatomy**

A **trace** is composed of multiple **spans**. Each span represents work done by a single service.

```
Trace ID: a1b2c3d4e5f6g7h8
├── Span: gateway-service (200ms)
│   ├── Span: customer-service (50ms)
│   ├── Span: accounts-service (120ms)
│   │   ├── Span: database-query (80ms)
│   │   └── Span: compliance-check (30ms)
│   └── Span: notification-service (25ms)
└── Total Duration: 200ms
```

### **Distributed Tracing Example**

When a customer initiates a money transfer:

1. **Gateway Service** receives request
2. **Authentication** validates customer
3. **Customer Service** fetches profile
4. **Accounts Service** checks balance
5. **Compliance Service** runs AML checks
6. **Transaction Service** processes transfer
7. **Notification Service** sends confirmation

Each step creates a span with timing and metadata:

```json
{
  "traceId": "a1b2c3d4e5f6g7h8",
  "spanId": "span_accounts_001",
  "parentSpanId": "span_gateway_001",
  "serviceName": "accounts-service",
  "operationName": "check_balance",
  "startTime": "2025-09-23T10:15:30.100Z",
  "duration": 120000000,
  "tags": {
    "customer.id": "CUST_12345",
    "customer.tier": "PREMIUM",
    "account.type": "SAVINGS",
    "db.query": "SELECT balance FROM accounts WHERE customer_id = ?",
    "business.compliance_required": true
  }
}
```

---

# 🏛️ **Modern Architecture Overview**

## **Our Observability Stack**

```
                    🎨 GRAFANA (Visualization)
                           ↑
            ┌──────────────┼──────────────┐
            ↓              ↓              ↓
    📝 LOKI           📊 PROMETHEUS    🔗 TEMPO
    (Logs)            (Metrics)       (Traces)
       ↑                 ↑              ↑
       │                 │              │
    🤖 GRAFANA ALLOY (Unified Collection & Processing)
       ↑
    📦 DOCKER CONTAINERS (Microservices)
```

### **Component Responsibilities**

| Component | Purpose | Data Type | Port |
|-----------|---------|-----------|------|
| **Grafana Alloy** | Unified observability pipeline | All Types | 12345 |
| **Loki** | Log aggregation and storage | Logs | 3100 |
| **Prometheus** | Metrics collection and storage | Metrics | 9090 |
| **Tempo** | Distributed tracing backend | Traces | 3200 |
| **Grafana** | Unified visualization dashboard | All Types | 3000 |

## **Why Grafana Alloy?**

**Alloy** is Grafana's next-generation observability agent that replaces Promtail. Here's why we chose it:

### **Alloy vs. Traditional Agents**

| Traditional (Promtail) | Modern (Alloy) |
|-----------------------|----------------|
| ❌ Single-purpose (logs only) | ✅ Multi-telemetry (logs, metrics, traces) |
| ❌ Limited processing capabilities | ✅ Advanced parsing and enrichment |
| ❌ YAML configuration | ✅ HCL configuration (more flexible) |
| ❌ Static label assignment | ✅ Dynamic processing pipelines |

---

# 🔧 **Unified Data Pipeline**

## **Alloy Configuration Architecture**

Our unified Alloy configuration follows a clear pipeline pattern:

```
DISCOVERY → SOURCES → PROCESSING → OUTPUTS
    ↓         ↓          ↓          ↓
  Find     Collect    Enrich     Store
 Services   Data      Data       Data
```

### **1. Discovery Components**

**Discovery** components find and identify what to monitor:

```hcl
// Automatically discover all Docker containers
discovery.docker "containers" {
    host = "unix:///var/run/docker.sock"
}
```

This discovers containers and provides metadata like:
- Container name
- Image name
- Labels
- Network information

### **2. Source Components**

**Sources** collect raw telemetry data:

```hcl
// Collect logs from Docker containers
loki.source.docker "microservices" {
    host       = "unix:///var/run/docker.sock"
    targets    = discovery.docker.containers.targets
    forward_to = [loki.process.banking_enrichment.receiver]
    
    // Apply filtering rules
    relabel_rules = loki.relabel.service_filter.rules
}
```

### **3. Processing Components**

**Processing** components enrich and transform data:

```hcl
// Multi-stage log processing pipeline
loki.process "banking_enrichment" {
    forward_to = [loki.write.primary.receiver]
    
    // Stage 1: Extract log level
    stage.regex {
        expression = "(?P<level>INFO|WARN|ERROR|DEBUG)"
    }
    
    // Stage 2: Parse JSON from Spring Boot
    stage.match {
        selector = "{service_type=\"business_service\"}"
        stage.json {
            expressions = {
                customer_id = "businessContext.customerId",
                customer_tier = "businessContext.customerTier",
                transaction_type = "businessContext.transactionType",
            }
        }
    }
    
    // Stage 3: Add business context labels
    stage.match {
        selector = "{customer_tier=\"PREMIUM\"}"
        stage.labels {
            values = {
                priority = "high",
                alert_tier = "premium_customer",
            }
        }
    }
}
```

### **4. Output Components**

**Outputs** send processed data to storage systems:

```hcl
// Send enriched logs to Loki
loki.write "primary" {
    endpoint {
        url = "http://loki:3100/loki/api/v1/push"
        headers = {
            "X-Scope-OrgID" = "banking-microservices",
        }
    }
    
    external_labels = {
        cluster = "banking-dev",
        pipeline = "alloy-unified",
    }
}
```

## **Label Strategy**

Labels are key-value pairs that organize and filter your data. Our labeling strategy:

### **Static Labels** (Infrastructure-level)
```hcl
environment = "dev"
cluster = "banking-microservices"
service = "accounts"
service_type = "business_service"
```

### **Dynamic Labels** (Context-aware)
```hcl
customer_tier = "PREMIUM"    // From log content
level = "ERROR"              // From log level
alert_severity = "critical"  // From error patterns
```

### **Best Practices for Labels**

✅ **DO:**
- Use static labels for filtering (service, environment)
- Keep cardinality low (< 100 unique values per label)
- Use meaningful names (customer_tier vs tier)

❌ **DON'T:**
- Create labels from unique IDs (customer_id, transaction_id)
- Use labels for high-cardinality data
- Include sensitive information in labels

---

# 🏦 **Banking-Specific Patterns**

## **Financial Services Requirements**

Banking applications have unique observability needs:

### **1. Compliance and Audit Trails**

Every high-value transaction must be traceable:

```hcl
// Flag compliance-required transactions
stage.match {
    selector = "{compliance_flag=\"true\"}"
    stage.labels {
        values = {
            compliance = "required",
            audit_flag = "true",
        }
    }
}
```

### **2. Customer Tier-Based Monitoring**

Premium customers require different SLA monitoring:

```hcl
// Enhanced monitoring for premium customers
stage.match {
    selector = "{customer_tier=\"PREMIUM\"}"
    stage.labels {
        values = {
            priority = "high",
            alert_tier = "premium_customer",
            sla_target = "99.9",
        }
    }
}
```

### **3. Error Categorization**

Different error types require different responses:

```hcl
// Database connection errors (critical)
stage.match {
    selector = "{} |~ \"Connection.*refused|SQLException\""
    stage.labels {
        values = {
            error_category = "database",
            alert_severity = "critical",
        }
    }
}

// Business logic errors (medium priority)
stage.match {
    selector = "{} |~ \"InsufficientFundsException|AccountNotFoundException\""
    stage.labels {
        values = {
            error_category = "business_logic",
            alert_severity = "medium",
        }
    }
}
```

### **4. Transaction Lifecycle Tracking**

Monitor complete transaction journeys:

```hcl
// Track transaction lifecycle events
stage.match {
    selector = "{} |~ \"Transaction (started|completed|failed)\""
    stage.regex {
        expression = "Transaction (?P<transaction_status>started|completed|failed).*?ID: (?P<transaction_id>[a-f0-9-]+)"
    }
    stage.labels {
        values = {
            transaction_status = "",
            transaction_id = "",
            event_type = "transaction_lifecycle",
        }
    }
}
```

## **Business Intelligence Integration**

Our unified Alloy includes a customer analytics API:

```hcl
// HTTP endpoint for real-time customer events
loki.source.api "customer_events" {
    http {
        listen_address = "0.0.0.0"
        listen_port    = 12346
    }
    forward_to = [loki.process.customer_analytics.receiver]
    labels = {
        source = "customer_api",
        event_type = "customer_analytics",
    }
}
```

Applications can send enriched events:

```bash
curl -X POST http://localhost:12346/loki/api/v1/push \\
  -H "Content-Type: application/json" \\
  -d '{
    "streams": [{
      "stream": {"source": "accounts", "event": "high_value_transaction"},
      "values": [["1695456000000000000", "{\\"customerId\\": \\"CUST_12345\\", \\"amount\\": 75000.00, \\"channel\\": \\"mobile\\", \\"riskScore\\": 85}"]]
    }]
  }'
```

---

# 💻 **Practical Implementation**

## **Getting Started**

### **1. Start the Observability Stack**

```bash
# Navigate to project root
cd /Users/Sameep.Mondhe/learning/ms/microservices-with-java

# Start all observability services
./start-services-new.sh
```

This starts:
- ✅ Unified Grafana Alloy (replaces Promtail)
- ✅ Loki for log storage
- ✅ Tempo for trace storage  
- ✅ Prometheus for metrics
- ✅ Grafana for visualization

### **2. Verify Components**

Check that all components are healthy:

```bash
# Alloy health check
curl http://localhost:12345/-/ready

# Loki health check
curl http://localhost:3100/ready

# Prometheus health check
curl http://localhost:9090/-/healthy

# Access Grafana
open http://localhost:3000  # admin/admin
```

### **3. Generate Sample Data**

Start the microservices to generate observability data:

```bash
# This starts all banking microservices
./start-services-new.sh
```

Then generate some transactions:

```bash
# Create a customer
curl -X POST http://localhost:8072/banking/customers \\
  -H "Content-Type: application/json" \\
  -d '{"name": "John Doe", "email": "john@example.com", "mobileNumber": "1234567890"}'

# Create an account
curl -X POST http://localhost:8072/banking/accounts \\
  -H "Content-Type: application/json" \\
  -d '{"customerId": 1, "accountType": "SAVINGS", "branchAddress": "Main Branch"}'

# Perform a transaction (generates traces and logs)
curl -X GET "http://localhost:8072/banking/accounts?customerId=1"
```

## **Exploring Your Data**

### **1. Logs in Grafana**

1. Open Grafana: http://localhost:3000
2. Go to **Explore** → Select **Loki** as data source
3. Try these queries:

```logql
# All logs from accounts service
{service="accounts"}

# Error logs only
{service="accounts", level="ERROR"}

# Premium customer transactions
{customer_tier="PREMIUM"}

# High-value transactions requiring compliance
{compliance="required"}

# Logs with error patterns
{service="accounts"} |= "Exception"

# JSON parsing and filtering
{service="accounts"} | json | businessContext_amount > 10000
```

### **2. Metrics in Prometheus**

1. Open Prometheus: http://localhost:9090
2. Try these queries:

```promql
# Request rate by service
rate(http_requests_total[5m])

# Error rate
rate(http_requests_total{status=~"5.."}[5m]) / rate(http_requests_total[5m])

# Response time percentiles
histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))

# Business metrics
banking_transactions_total

# Active accounts by tier
banking_active_accounts{customer_tier="PREMIUM"}
```

### **3. Traces in Tempo**

1. In Grafana, go to **Explore** → Select **Tempo**
2. Query by trace ID or use service filters
3. View the complete request journey across services

---

# ⚡ **Advanced Topics**

## **Performance Optimization**

### **1. Log Volume Management**

High-volume services need careful log management:

```hcl
// Sample logs to reduce volume (keep every 10th log)
stage.sampling {
    rate = 0.1  // 10% sampling rate
}

// Drop debug logs in production
stage.match {
    selector = "{level=\"DEBUG\"}"
    action   = "drop"
}
```

### **2. Label Cardinality Control**

Monitor label cardinality to prevent performance issues:

```bash
# Check stream count in Loki
curl "http://localhost:3100/loki/api/v1/label" | jq

# Check series count in Prometheus  
curl "http://localhost:9090/api/v1/label/__name__/values" | jq
```

### **3. Resource Allocation**

Recommended resource allocation for banking workloads:

```yaml
# Alloy resource limits
resources:
  limits:
    memory: "512Mi"
    cpu: "500m"
  requests:
    memory: "256Mi"
    cpu: "100m"

# Loki resource allocation
loki:
  resources:
    limits:
      memory: "2Gi"
      cpu: "1000m"
```

## **Security Considerations**

### **1. Sensitive Data Handling**

Never log sensitive financial data:

```hcl
// Redact sensitive patterns
stage.replace {
    expression = "account_number=\\\\d{10,}"
    replace    = "account_number=***REDACTED***"
}

// Remove PII from logs
stage.replace {
    expression = "ssn=\\\\d{3}-\\\\d{2}-\\\\d{4}"
    replace    = "ssn=***-**-****"
}
```

### **2. Access Control**

Use Loki's multi-tenancy for access control:

```hcl
// Tenant-based routing
loki.write "primary" {
    endpoint {
        url = "http://loki:3100/loki/api/v1/push"
        headers = {
            "X-Scope-OrgID" = "banking-${TENANT_ID}",
        }
    }
}
```

---

# 🔧 **Troubleshooting Guide**

## **Common Issues**

### **1. Alloy Container Won't Start**

**Symptoms**: Container exits immediately or health checks fail

**Check logs**:
```bash
docker logs alloy-unified
```

**Common causes**:
- Configuration syntax errors
- Port conflicts
- Docker socket permissions

**Solutions**:
```bash
# Validate configuration
docker run --rm -v $(pwd)/alloy/unified:/etc/alloy grafana/alloy:latest fmt /etc/alloy/config.alloy

# Check port availability
lsof -i :12345

# Fix Docker permissions
sudo chmod 666 /var/run/docker.sock
```

### **2. No Logs Appearing in Loki**

**Symptoms**: Grafana shows no log data

**Debugging steps**:
```bash
# Check Loki health
curl http://localhost:3100/ready

# Check if Alloy is sending data
curl http://localhost:12345/debug/metrics | grep loki

# Test direct log ingestion
curl -X POST http://localhost:3100/loki/api/v1/push \\
  -H "Content-Type: application/json" \\
  -d '{"streams":[{"stream":{"service":"test"},"values":[["1695456000000000000","test log message"]]}]}'
```

### **3. High Memory Usage**

**Symptoms**: Containers consuming excessive memory

**Investigation**:
```bash
# Check container resource usage
docker stats

# Check Loki metrics
curl http://localhost:3100/metrics | grep memory

# Check stream count
curl http://localhost:3100/loki/api/v1/label | jq '. | length'
```

**Solutions**:
- Reduce log retention period
- Optimize label cardinality
- Implement log sampling

### **4. Missing Traces**

**Symptoms**: Traces not appearing in Tempo

**Check**:
```bash
# Verify OTEL collector is running
curl http://localhost:4318/v1/traces

# Check Tempo ingestion
curl http://localhost:3200/ready

# Verify service instrumentation
curl http://localhost:8081/actuator/metrics | grep tracing
```

---

## 🎓 **Key Takeaways**

After reading this guide, you now understand:

✅ **Observability Fundamentals**: The difference between monitoring and observability  
✅ **The Three Pillars**: How logs, metrics, and traces work together  
✅ **Modern Architecture**: Why Grafana Alloy is superior to traditional agents  
✅ **Banking-Specific Patterns**: Compliance, customer tiers, and business intelligence  
✅ **Practical Implementation**: Real configuration examples and troubleshooting  

## 🚀 **Next Steps**

1. **Practice**: Run the examples in your environment
2. **Experiment**: Modify configurations to suit your needs
3. **Explore**: Build custom dashboards in Grafana
4. **Scale**: Implement in production with proper security

---

## 📖 **Additional Resources**

- [Grafana Alloy Documentation](https://grafana.com/docs/alloy/latest/)
- [LogQL Query Language](https://grafana.com/docs/loki/latest/logql/)
- [PromQL Guide](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [OpenTelemetry Specification](https://opentelemetry.io/docs/)

---

*📝 This guide provides a complete foundation for implementing modern observability in banking microservices. Each example is production-ready and follows industry best practices for financial services.*