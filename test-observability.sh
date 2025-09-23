#!/bin/bash

# Observability Stack Test Script
# Tests the complete logs, metrics, and traces correlation

set -e

echo "🧪 Testing Complete Observability Stack..."
echo "================================================"

# Configuration
BASE_URL="http://localhost:8072"
GRAFANA_URL="http://localhost:3000"
TEMPO_URL="http://localhost:3200"
LOKI_URL="http://localhost:3100"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counter
TESTS_PASSED=0
TESTS_FAILED=0

test_status() {
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ PASS${NC}: $1"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}✗ FAIL${NC}: $1"
        ((TESTS_FAILED++))
    fi
}

echo -e "${BLUE}Phase 1: Infrastructure Health Checks${NC}"
echo "-------------------------------------------"

# Test Grafana
echo "Testing Grafana connectivity..."
curl -s "$GRAFANA_URL/api/health" > /dev/null
test_status "Grafana is accessible"

# Test Tempo
echo "Testing Tempo connectivity..."
curl -s "$TEMPO_URL/ready" > /dev/null
test_status "Tempo is ready"

# Test Loki
echo "Testing Loki connectivity..."
curl -s "$LOKI_URL/ready" > /dev/null
test_status "Loki is ready"

# Test Gateway
echo "Testing Gateway connectivity..."
curl -s "$BASE_URL/actuator/health" > /dev/null
test_status "Gateway is accessible"

echo ""
echo -e "${BLUE}Phase 2: Generate Test Traffic${NC}"
echo "-----------------------------------"

# Generate correlated test traffic
echo "Generating test requests to create traces and logs..."

for i in {1..5}; do
    echo "Request $i: Creating customer account..."
    
    # Create customer
    CUSTOMER_RESPONSE=$(curl -s -X POST "$BASE_URL/eazybank/customers" \
        -H "Content-Type: application/json" \
        -d '{
            "name": "Test User '$i'",
            "email": "test'$i'@example.com",
            "mobileNumber": "123456789'$i'"
        }')
    
    if echo "$CUSTOMER_RESPONSE" | grep -q "Test User"; then
        echo -e "${GREEN}✓${NC} Customer created successfully"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}✗${NC} Customer creation failed"
        ((TESTS_FAILED++))
    fi
    
    # Get customer details
    echo "Fetching customer details..."
    curl -s "$BASE_URL/eazybank/customers?mobileNumber=123456789$i" > /dev/null
    test_status "Customer details retrieved"
    
    # Create account
    echo "Creating account..."
    curl -s -X POST "$BASE_URL/eazybank/accounts" \
        -H "Content-Type: application/json" \
        -d '{
            "customerId": "'$i'",
            "accountType": "Savings",
            "branchAddress": "123 Main St"
        }' > /dev/null
    test_status "Account created"
    
    # Create card
    echo "Creating card..."
    curl -s -X POST "$BASE_URL/eazybank/cards" \
        -H "Content-Type: application/json" \
        -d '{
            "customerId": "'$i'",
            "cardType": "Credit",
            "totalLimit": 10000
        }' > /dev/null
    test_status "Card created"
    
    # Small delay between requests
    sleep 2
done

echo ""
echo -e "${BLUE}Phase 3: Test Observability Data${NC}"
echo "-----------------------------------"

# Wait for data to be ingested
echo "Waiting for data ingestion (30 seconds)..."
sleep 30

# Test Loki logs
echo "Testing Loki log ingestion..."
LOKI_RESPONSE=$(curl -s "$LOKI_URL/loki/api/v1/query?query={job=\"microservices\"}" | jq -r '.data.result | length')
if [ "$LOKI_RESPONSE" -gt 0 ]; then
    echo -e "${GREEN}✓ PASS${NC}: Loki has ingested logs ($LOKI_RESPONSE streams)"
    ((TESTS_PASSED++))
else
    echo -e "${RED}✗ FAIL${NC}: Loki has no log data"
    ((TESTS_FAILED++))
fi

# Test Tempo traces
echo "Testing Tempo trace ingestion..."
TEMPO_RESPONSE=$(curl -s "$TEMPO_URL/api/search?tags=" | jq -r '.traces | length')
if [ "$TEMPO_RESPONSE" -gt 0 ]; then
    echo -e "${GREEN}✓ PASS${NC}: Tempo has traces ($TEMPO_RESPONSE traces)"
    ((TESTS_PASSED++))
else
    echo -e "${RED}✗ FAIL${NC}: Tempo has no trace data"
    ((TESTS_FAILED++))
fi

# Test Prometheus metrics
echo "Testing Prometheus metrics..."
PROMETHEUS_RESPONSE=$(curl -s "http://localhost:9090/api/v1/query?query=up" | jq -r '.data.result | length')
if [ "$PROMETHEUS_RESPONSE" -gt 0 ]; then
    echo -e "${GREEN}✓ PASS${NC}: Prometheus has metrics ($PROMETHEUS_RESPONSE targets)"
    ((TESTS_PASSED++))
else
    echo -e "${RED}✗ FAIL${NC}: Prometheus has no metric data"
    ((TESTS_FAILED++))
fi

echo ""
echo -e "${BLUE}Phase 4: Test Correlation${NC}"
echo "----------------------------"

# Test trace-log correlation
echo "Testing trace-log correlation..."
TRACE_WITH_LOGS=$(curl -s "$LOKI_URL/loki/api/v1/query?query={job=\"microservices\"} | json | traceId != \"\"" | jq -r '.data.result | length')
if [ "$TRACE_WITH_LOGS" -gt 0 ]; then
    echo -e "${GREEN}✓ PASS${NC}: Found logs with trace IDs ($TRACE_WITH_LOGS streams)"
    ((TESTS_PASSED++))
else
    echo -e "${RED}✗ FAIL${NC}: No logs contain trace IDs"
    ((TESTS_FAILED++))
fi

# Test Grafana datasources
echo "Testing Grafana datasources..."
DATASOURCES=$(curl -s -u admin:admin "$GRAFANA_URL/api/datasources" | jq -r '. | length')
if [ "$DATASOURCES" -ge 3 ]; then
    echo -e "${GREEN}✓ PASS${NC}: Grafana has required datasources ($DATASOURCES configured)"
    ((TESTS_PASSED++))
else
    echo -e "${RED}✗ FAIL${NC}: Grafana missing datasources"
    ((TESTS_FAILED++))
fi

echo ""
echo -e "${BLUE}Phase 5: Dashboard Validation${NC}"
echo "-----------------------------------"

# Test dashboard availability
echo "Testing dashboard availability..."
DASHBOARDS=$(curl -s -u admin:admin "$GRAFANA_URL/api/search?type=dash-db" | jq -r '. | length')
if [ "$DASHBOARDS" -gt 0 ]; then
    echo -e "${GREEN}✓ PASS${NC}: Grafana dashboards loaded ($DASHBOARDS dashboards)"
    ((TESTS_PASSED++))
else
    echo -e "${RED}✗ FAIL${NC}: No dashboards found in Grafana"
    ((TESTS_FAILED++))
fi

echo ""
echo "================================================"
echo -e "${BLUE}Observability Stack Test Results${NC}"
echo "================================================"
echo -e "Tests Passed: ${GREEN}$TESTS_PASSED${NC}"
echo -e "Tests Failed: ${RED}$TESTS_FAILED${NC}"

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}🎉 All tests passed! Observability stack is fully operational.${NC}"
    echo ""
    echo -e "${YELLOW}Next Steps:${NC}"
    echo "1. Open Grafana: $GRAFANA_URL (admin/admin)"
    echo "2. Navigate to Dashboards → Microservices Logs Dashboard"
    echo "3. Use Explore to correlate logs and traces"
    echo "4. Click trace IDs in logs to jump to Tempo"
    exit 0
else
    echo -e "${RED}❌ Some tests failed. Check the observability stack configuration.${NC}"
    exit 1
fi