#!/bin/bash

# ===== Banking Platform Demo Activity Generator =====
# Creates realistic banking activity to showcase observability dashboards
# Uses Newman (Postman CLI) + additional activity generators

set -e

# Color codes for pretty output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POSTMAN_DIR="$SCRIPT_DIR/postman-collections"
ENVIRONMENT_FILE="$POSTMAN_DIR/local-environment.json"
REPORTS_DIR="$POSTMAN_DIR/reports"

# Default settings
DURATION=300  # 5 minutes default
CONCURRENT_USERS=3
TRANSACTION_INTERVAL=2
VERBOSE=false

# Logging helpers
log_banner() {
  echo -e "\n${CYAN}================================================"
  echo -e "🏦 $1"
  echo -e "================================================${NC}\n"
}

log_step() {
  echo -e "${BLUE}🔹 $1${NC}"
}

log_success() {
  echo -e "  ${GREEN}✅ $1${NC}"
}

log_error() {
  echo -e "  ${RED}❌ $1${NC}"
}

log_warn() {
  echo -e "  ${YELLOW}⚠️ $1${NC}"
}

log_info() {
  echo -e "  ${PURPLE}ℹ️ $1${NC}"
}

# Help function
show_help() {
  echo "Banking Platform Demo Activity Generator"
  echo ""
  echo "Usage: $0 [OPTIONS]"
  echo ""
  echo "Options:"
  echo "  -d, --duration SECONDS    Duration to run demo activity (default: 300)"
  echo "  -u, --users COUNT         Number of concurrent virtual users (default: 3)"
  echo "  -i, --interval SECONDS    Interval between transactions (default: 2)"
  echo "  -v, --verbose             Verbose output"
  echo "  -h, --help                Show this help message"
  echo ""
  echo "Examples:"
  echo "  $0                        # Run with defaults (5 min, 3 users)"
  echo "  $0 -d 600 -u 5 -i 1      # Run for 10 min with 5 users, 1s interval"
  echo "  $0 --verbose              # Run with verbose logging"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    -d|--duration)
      DURATION="$2"
      shift 2
      ;;
    -u|--users)
      CONCURRENT_USERS="$2"
      shift 2
      ;;
    -i|--interval)
      TRANSACTION_INTERVAL="$2"
      shift 2
      ;;
    -v|--verbose)
      VERBOSE=true
      shift
      ;;
    -h|--help)
      show_help
      exit 0
      ;;
    *)
      echo "Unknown option $1"
      show_help
      exit 1
      ;;
  esac
done

# Check prerequisites
check_prerequisites() {
  log_step "Checking prerequisites..."
  
  # Check if Newman is installed
  if ! command -v newman &> /dev/null; then
    log_error "Newman (Postman CLI) is not installed"
    echo "  Install with: npm install -g newman"
    echo "  Or: brew install newman (macOS)"
    return 1
  fi
  log_success "Newman is available"
  
  # Check if jq is installed
  if ! command -v jq &> /dev/null; then
    log_warn "jq is not installed - some features will be limited"
    echo "  Install with: brew install jq (macOS) or apt-get install jq (Linux)"
  else
    log_success "jq is available"
  fi
  
  # Check if postman collections exist
  if [[ ! -f "$POSTMAN_DIR/banking-microservices-fixed-sequence.postman_collection.json" ]]; then
    log_error "Banking microservices collection not found"
    return 1
  fi
  log_success "Postman collections found"
  
  # Check if environment file exists
  if [[ ! -f "$ENVIRONMENT_FILE" ]]; then
    log_error "Postman environment file not found"
    return 1
  fi
  log_success "Postman environment file found"
  
  # Create reports directory
  mkdir -p "$REPORTS_DIR"
  log_success "Reports directory ready"
  
  return 0
}

# Check if services are running
check_services() {
  log_step "Checking if banking services are running..."
  
  local services=(
    "Gateway:8072:/actuator/health"
    "Accounts:8081:/actuator/health"
    "Cards:8082:/actuator/health"
    "Loans:8083:/actuator/health"
    "Customers:8084:/actuator/health"
    "Eureka:8761:/actuator/health"
  )
  
  local all_healthy=true
  
  for service in "${services[@]}"; do
    local name=$(echo $service | cut -d: -f1)
    local port=$(echo $service | cut -d: -f2)
    local endpoint=$(echo $service | cut -d: -f3)
    
    if curl -s "http://localhost:${port}${endpoint}" > /dev/null; then
      log_success "${name} service is healthy (port ${port})"
    else
      log_error "${name} service is not responding (port ${port})"
      all_healthy=false
    fi
  done
  
  if [[ "$all_healthy" == "false" ]]; then
    log_error "Some services are not running. Please start them first with:"
    echo "  ./start-services-new.sh"
    return 1
  fi
  
  log_success "All banking services are healthy!"
  return 0
}

# Generate synthetic customer data
generate_customer_data() {
  local customer_id=$1
  local names=("Alice Johnson" "Bob Smith" "Carol Davis" "David Wilson" "Eva Brown" "Frank Miller" "Grace Lee" "Henry Taylor" "Ivy Chen" "Jack Rodriguez")
  local cities=("New York" "Los Angeles" "Chicago" "Houston" "Phoenix" "Philadelphia" "San Antonio" "San Diego" "Dallas" "San Jose")
  local states=("NY" "CA" "IL" "TX" "AZ" "PA" "TX" "CA" "TX" "CA")
  
  local random_name=${names[$((RANDOM % ${#names[@]}))]}
  local random_city=${cities[$((RANDOM % ${#cities[@]}))]}
  local random_state=${states[$((RANDOM % ${#states[@]}))]}
  local random_zip=$((10000 + RANDOM % 90000))
  local random_phone="555-$(printf "%03d" $((RANDOM % 1000)))-$(printf "%04d" $((RANDOM % 10000)))"
  
  echo "{
    \"customerId\": \"DEMO-${customer_id}-\$(date +%s)\",
    \"name\": \"${random_name}\",
    \"email\": \"$(echo $random_name | tr ' ' '.' | tr '[:upper:]' '[:lower:]')@bank-demo.com\",
    \"phone\": \"${random_phone}\",
    \"address\": \"$((RANDOM % 9999 + 1)) Demo Street\",
    \"city\": \"${random_city}\",
    \"state\": \"${random_state}\",
    \"zipCode\": \"${random_zip}\",
    \"country\": \"USA\",
    \"status\": \"Active\"
  }"
}

# Run Newman collection with minimal reporting
run_newman_collection() {
  local collection_name="$1"
  local iteration="$2"
  local user_id="$3"
  
  if [[ "$VERBOSE" == "true" ]]; then
    newman run "$POSTMAN_DIR/${collection_name}" \
      -e "$ENVIRONMENT_FILE" \
      --iteration-count 1 \
      --reporters cli \
      --timeout-request 10000 \
      --delay-request 500 \
      --color on
  else
    newman run "$POSTMAN_DIR/${collection_name}" \
      -e "$ENVIRONMENT_FILE" \
      --iteration-count 1 \
      --timeout-request 10000 \
      --delay-request 500 \
      --silent > /dev/null 2>&1
  fi
  
  return $?
}

# Generate individual API calls with curl for additional variety
generate_random_api_calls() {
  local user_id="$1"
  
  # Random API endpoints to hit
  local endpoints=(
    "GET:8081:/api/accounts/health:Accounts Health Check"
    "GET:8082:/api/cards/health:Cards Health Check"
    "GET:8083:/api/loans/health:Loans Health Check"
    "GET:8084:/api/customers/health:Customers Health Check"
    "GET:8072:/actuator/metrics:Gateway Metrics"
    "GET:8761:/actuator/info:Eureka Info"
  )
  
  local endpoint=${endpoints[$((RANDOM % ${#endpoints[@]}))]}
  local method=$(echo $endpoint | cut -d: -f1)
  local port=$(echo $endpoint | cut -d: -f2)
  local path=$(echo $endpoint | cut -d: -f3)
  local description=$(echo $endpoint | cut -d: -f4)
  
  if [[ "$VERBOSE" == "true" ]]; then
    log_info "User ${user_id}: ${description}"
  fi
  
  curl -s "http://localhost:${port}${path}" > /dev/null 2>&1 || true
}

# Simulate error scenarios occasionally
simulate_error_scenarios() {
  local user_id="$1"
  
  # 10% chance of hitting non-existent endpoints to generate 404s
  if [[ $((RANDOM % 10)) -eq 0 ]]; then
    if [[ "$VERBOSE" == "true" ]]; then
      log_info "User ${user_id}: Generating 404 error scenario"
    fi
    curl -s "http://localhost:8072/api/nonexistent/endpoint" > /dev/null 2>&1 || true
  fi
  
  # 5% chance of hitting endpoints with invalid data to generate 4xx errors
  if [[ $((RANDOM % 20)) -eq 0 ]]; then
    if [[ "$VERBOSE" == "true" ]]; then
      log_info "User ${user_id}: Generating validation error scenario"
    fi
    curl -s -X POST "http://localhost:8072/customers/create" \
      -H "Content-Type: application/json" \
      -d '{"invalid": "data"}' > /dev/null 2>&1 || true
  fi
}

# Virtual user simulation
simulate_virtual_user() {
  local user_id="$1"
  local duration="$2"
  local interval="$3"
  
  log_info "Starting virtual user ${user_id} for ${duration} seconds..."
  
  local end_time=$(($(date +%s) + duration))
  local transaction_count=0
  
  while [[ $(date +%s) -lt $end_time ]]; do
    transaction_count=$((transaction_count + 1))
    
    # 70% chance to run full banking sequence
    if [[ $((RANDOM % 10)) -lt 7 ]]; then
      if run_newman_collection "banking-microservices-fixed-sequence.postman_collection.json" "$transaction_count" "$user_id"; then
        if [[ "$VERBOSE" == "true" ]]; then
          log_success "User ${user_id}: Completed banking transaction ${transaction_count}"
        fi
      else
        if [[ "$VERBOSE" == "true" ]]; then
          log_warn "User ${user_id}: Banking transaction ${transaction_count} had issues"
        fi
      fi
    # 20% chance to run distributed transaction test
    elif [[ $((RANDOM % 10)) -lt 2 ]]; then
      if run_newman_collection "distributed-transactions.postman_collection.json" "$transaction_count" "$user_id"; then
        if [[ "$VERBOSE" == "true" ]]; then
          log_success "User ${user_id}: Completed distributed transaction ${transaction_count}"
        fi
      fi
    # 10% chance for random API calls and error scenarios
    else
      generate_random_api_calls "$user_id"
      simulate_error_scenarios "$user_id"
    fi
    
    # Random sleep between transactions (with some variation)
    local sleep_time=$((interval + RANDOM % 3))
    sleep "$sleep_time"
  done
  
  log_success "Virtual user ${user_id} completed ${transaction_count} transactions"
}

# Main execution
main() {
  log_banner "Banking Platform Demo Activity Generator"
  
  log_info "Configuration:"
  echo "  Duration: ${DURATION} seconds ($((DURATION / 60)) minutes)"
  echo "  Concurrent Users: ${CONCURRENT_USERS}"
  echo "  Transaction Interval: ${TRANSACTION_INTERVAL} seconds"
  echo "  Verbose Mode: ${VERBOSE}"
  
  # Prerequisites check
  check_prerequisites || exit 1
  
  # Services health check
  check_services || exit 1
  
  log_step "Starting demo activity generation..."
  
  # Start virtual users in background
  local pids=()
  for ((i=1; i<=CONCURRENT_USERS; i++)); do
    simulate_virtual_user "$i" "$DURATION" "$TRANSACTION_INTERVAL" &
    pids+=($!)
  done
  
  log_success "Started ${CONCURRENT_USERS} virtual users (PIDs: ${pids[*]})"
  
  # Show progress
  log_step "Demo activity in progress..."
  echo "  📊 Watch your Grafana dashboards: http://localhost:3000"
  echo "  🔍 Monitor logs: http://localhost:3000/explore"
  echo "  📈 View metrics: http://localhost:9090"
  echo ""
  echo "  Press Ctrl+C to stop early..."
  
  # Wait for all virtual users to complete
  local completed=0
  for pid in "${pids[@]}"; do
    if wait "$pid"; then
      completed=$((completed + 1))
      echo -ne "\\r  Progress: ${completed}/${CONCURRENT_USERS} users completed"
    fi
  done
  
  echo ""
  log_success "All virtual users completed!"
  
  # Summary
  log_step "Demo Activity Summary:"
  echo "  Duration: ${DURATION} seconds"
  echo "  Virtual Users: ${CONCURRENT_USERS}"
  echo "  Focus: Dashboard data generation (minimal file output)"
  
  log_banner "Demo Activity Generation Complete!"
  echo "Your dashboards should now be populated with realistic banking data!"
  echo ""
  echo "🎯 Recommended viewing order:"
  echo "1. Executive Summary Dashboard - High-level business view"
  echo "2. Banking Platform Overview - Operational metrics"
  echo "3. Enhanced Logs Dashboard - Log analysis with trace correlation"
  echo "4. Distributed Tracing Dashboard - Service interactions"
  echo "5. Service Deep Dive Dashboard - Individual service performance"
  echo "6. Infrastructure Monitoring Dashboard - Resource utilization"
}

# Create start marker for report counting
touch /tmp/demo_start

# Handle interruption gracefully
cleanup() {
  echo ""
  log_warn "Demo activity interrupted!"
  log_step "Cleaning up background processes..."
  jobs -p | xargs -r kill
  exit 0
}
trap cleanup SIGINT SIGTERM

# Run main function
main "$@"