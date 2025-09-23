# Banking Platform Demo Activity Generator

## Quick Start

The fastest way to generate impressive demo data for your dashboards:

```bash
# Install Newman (if not already installed)
npm install -g newman

# Run demo with defaults (5 minutes, 3 virtual users)
./generate-demo-activity.sh

# Run extended demo (10 minutes, 5 users, faster transactions)
./generate-demo-activity.sh -d 600 -u 5 -i 1

# Run with verbose output to see what's happening
./generate-demo-activity.sh --verbose
```

## What This Script Does

### 🎭 Realistic Virtual Users
- Simulates **multiple concurrent banking customers**
- Each user follows realistic banking workflows
- Generates variety: account creation, card operations, loan processing
- Creates both **successful transactions and realistic error scenarios**

### 🎯 Intelligent Traffic Patterns
- **70%** - Full banking transaction sequences (create customer → accounts → cards → loans)
- **20%** - Distributed transaction testing (inter-service communication)
- **10%** - Random API calls and error simulation (404s, validation errors)

### 📊 Perfect for Dashboard Demos
- Populates **all three observability pillars**:
  - ✅ **Metrics** - Service performance, business KPIs, error rates
  - ✅ **Logs** - Structured logging with trace correlation
  - ✅ **Traces** - Distributed request flows across microservices

### 🛠️ Enterprise-Grade Features
- **Health checks** - Verifies all services before starting
- **Progress monitoring** - Real-time updates and completion tracking
- **Graceful interruption** - Ctrl+C to stop cleanly
- **Detailed reports** - Newman reports saved for analysis
- **Configurable parameters** - Duration, users, intervals

## Command Line Options

| Option | Description | Default | Example |
|--------|-------------|---------|---------|
| `-d, --duration` | Demo duration in seconds | 300 (5 min) | `-d 600` |
| `-u, --users` | Concurrent virtual users | 3 | `-u 5` |
| `-i, --interval` | Seconds between transactions | 2 | `-i 1` |
| `-v, --verbose` | Show detailed activity | false | `--verbose` |
| `-h, --help` | Show help message | - | `--help` |

## Prerequisites

### Required Tools
```bash
# Newman (Postman CLI) - Primary tool
npm install -g newman
# OR on macOS
brew install newman

# jq (optional but recommended) - JSON processing
brew install jq  # macOS
# OR
apt-get install jq  # Linux
```

### Required Services
Make sure your banking platform is running:
```bash
./start-services-new.sh
```

The script will check these services:
- ✅ Gateway Server (8072)
- ✅ Accounts Service (8081)
- ✅ Cards Service (8082)
- ✅ Loans Service (8083)
- ✅ Customers Service (8084)
- ✅ Eureka Server (8761)

## Usage Examples

### Demo Scenarios

**🎪 Quick Demo** (3 minutes, light load)
```bash
./generate-demo-activity.sh -d 180 -u 2 -i 3
```

**🎯 Standard Demo** (5 minutes, moderate load)
```bash
./generate-demo-activity.sh
```

**🔥 Intensive Demo** (10 minutes, heavy load)
```bash
./generate-demo-activity.sh -d 600 -u 8 -i 1
```

**🐛 Debug Mode** (See everything happening)
```bash
./generate-demo-activity.sh --verbose
```

### During Your Demo

Once the script is running, you can showcase:

1. **Executive Summary Dashboard** 📈
   - Business metrics populating in real-time
   - Customer acquisition trends
   - Transaction volume and success rates

2. **Banking Platform Overview** 🏦
   - Service health and performance
   - Request throughput across all services
   - Response time distribution

3. **Enhanced Logs Dashboard** 📝
   - Structured log analysis
   - Error detection and categorization
   - Trace correlation (click log → see full trace)

4. **Distributed Tracing Dashboard** 🕸️
   - Service dependency mapping
   - Request flow visualization
   - Performance bottleneck identification

5. **Service Deep Dive Dashboards** 🔍
   - Individual service metrics
   - JVM performance and resource usage
   - Database query performance

6. **Infrastructure Monitoring** 🖥️
   - Resource utilization trends
   - Container performance
   - System-level metrics

## Output and Reports

### Real-time Feedback
```
🏦 Banking Platform Demo Activity Generator
================================================

🔹 Starting virtual user 1 for 300 seconds...
🔹 Starting virtual user 2 for 300 seconds...
🔹 Starting virtual user 3 for 300 seconds...

✅ Started 3 virtual users (PIDs: 1234 1235 1236)

📊 Watch your Grafana dashboards: http://localhost:3000
🔍 Monitor logs: http://localhost:3000/explore
📈 View metrics: http://localhost:9090

Progress: 3/3 users completed
```

### Generated Reports
Reports are saved in `postman-collections/reports/`:
- Individual transaction reports (JSON format)
- Newman execution summaries
- Timestamp-based file naming

### Dashboard URLs
- **Grafana Dashboards**: http://localhost:3000
- **Prometheus Metrics**: http://localhost:9090
- **Log Exploration**: http://localhost:3000/explore

## Troubleshooting

### Common Issues

**❌ "Newman not found"**
```bash
# Install Newman
npm install -g newman
```

**❌ "Services not responding"**
```bash
# Start the banking platform
./start-services-new.sh

# Wait 2-3 minutes for all services to be ready
# Then run the demo script
```

**❌ "Permission denied"**
```bash
# Make script executable
chmod +x generate-demo-activity.sh
```

**❌ "Postman collections not found"**
- Ensure you're running from the project root directory
- Verify `postman-collections/` folder exists with the JSON files

### Monitoring Script Progress

**Check active processes:**
```bash
ps aux | grep newman
```

**Monitor in real-time:**
```bash
# Run with verbose mode
./generate-demo-activity.sh --verbose

# OR monitor logs in another terminal
tail -f *.log
```

**Stop early if needed:**
- Press `Ctrl+C` - Script handles graceful shutdown
- Kills all background Newman processes
- Preserves generated reports

## Integration with Your Demo

### Perfect Demo Flow

1. **Start the platform** - `./start-services-new.sh`
2. **Wait for health** - 2-3 minutes
3. **Open Grafana** - http://localhost:3000
4. **Start demo activity** - `./generate-demo-activity.sh`
5. **Navigate dashboards** - Show real-time data flowing in
6. **Explain observability** - Three pillars working together
7. **Stop when done** - Ctrl+C or let it complete

### Key Demo Points
- **Real-time data** - Watch metrics populate live
- **Realistic scenarios** - Not just hello world APIs
- **Error handling** - Shows how to detect and diagnose issues
- **Correlation** - Click from dashboard → logs → traces
- **Business context** - Technical metrics tied to business outcomes

This script transforms your observability stack from static to dynamic, making your demo compelling and professional! 🚀