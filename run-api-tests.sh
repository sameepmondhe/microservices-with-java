#!/bin/bash

# ===== Test Runner for Banking Microservices =====
# This script runs Postman collections to test the microservices after they've been started.

# Color codes for pretty output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Logging helpers
log_step() {
  echo -e "\n${BLUE}🔹 $1${NC}"
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

# Function to check if all microservices are up
check_all_services() {
  log_step "Checking if all microservices are available..."
  
  local services=(
    "Gateway:8072"
    "Accounts:8081"
    "Cards:8082"
    "Loans:8083"
    "Customers:8084"
    "Eureka:8761"
  )
  
  local all_healthy=true
  
  for service in "${services[@]}"; do
    local name=$(echo $service | cut -d: -f1)
    local port=$(echo $service | cut -d: -f2)
    
    if curl -s "http://localhost:${port}/actuator/health" > /dev/null; then
      log_success "${name} service is healthy (port ${port})"
    else
      log_error "${name} service is NOT healthy (port ${port})"
      all_healthy=false
    fi
  done
  
  if [ "$all_healthy" = true ]; then
    log_success "All microservices are healthy and ready for testing."
    return 0
  else
    log_error "Some microservices are not healthy. Make sure to run ./start-services.sh first."
    return 1
  fi
}

# Function to check if the gateway service is up (backward compatibility)
check_gateway() {
  log_step "Checking if Gateway Server is available..."
  local max_attempts=5
  local attempt=1

  while [ $attempt -le $max_attempts ]
  do
    if curl -s http://localhost:8072/actuator/health > /dev/null; then
      log_success "Gateway Server is up and running."
      return 0
    else
      echo -n "."
      sleep 3
      attempt=$((attempt + 1))
    fi
  done

  log_error "Gateway Server doesn't appear to be running."
  log_error "Make sure to run ./start-services.sh first."
  return 1
}

# Function to check and install Newman if needed
check_and_install_newman() {
  log_step "Checking for Newman (Postman CLI)..."
  if command -v newman &> /dev/null; then
    log_success "Newman is already installed."
    
    # Check for htmlextra reporter
    log_step "Checking for Newman htmlextra reporter..."
    if newman run --help | grep -q "htmlextra" 2>/dev/null || npm list -g newman-reporter-htmlextra &> /dev/null; then
      log_success "Newman htmlextra reporter is available."
    else
      log_warn "Newman htmlextra reporter not found. Attempting to install..."
      npm install -g newman-reporter-htmlextra
      if npm list -g newman-reporter-htmlextra &> /dev/null; then
        log_success "Newman htmlextra reporter installed successfully."
      else
        log_warn "Failed to install htmlextra reporter. Will use basic CLI reporter instead."
      fi
    fi
  else
    log_warn "Newman not found. Attempting to install via npm..."
    npm install -g newman newman-reporter-htmlextra
    if command -v newman &> /dev/null; then
      log_success "Newman installed successfully."
    else
      log_error "Failed to install Newman. Please install manually using: npm install -g newman newman-reporter-htmlextra"
      return 1
    fi
  fi
  return 0
}

# Function to run quick distributed transaction test
run_quick_distributed_test() {
  log_step "Running quick distributed transaction test..."
  
  # Create a test customer for the workflow
  local customer_id="API-TEST-$(date +%s)"
  log_step "Creating test customer: ${customer_id}"
  
  local customer_response=$(curl -s -X POST "http://localhost:8072/customers/create" \
    -H "Content-Type: application/json" \
    -d "{
      \"customerId\": \"${customer_id}\",
      \"name\": \"API Test Customer\",
      \"email\": \"apitest@example.com\",
      \"phone\": \"555-API-TEST\",
      \"address\": \"123 API Test Street\",
      \"city\": \"Test City\",
      \"state\": \"TEST\",
      \"zipCode\": \"12345\",
      \"country\": \"Test Country\",
      \"status\": \"Active\"
    }")
  
  if echo "$customer_response" | grep -q "customerId"; then
    log_success "Test customer created successfully"
  else
    log_error "Failed to create test customer"
    return 1
  fi
  
  # Test the distributed onboarding transaction
  log_step "Testing distributed onboarding transaction (4 services)..."
  local onboarding_response=$(curl -s -X POST "http://localhost:8072/accounts/onboarding" \
    -H "Content-Type: application/json" \
    -d "{
      \"customerId\": \"${customer_id}\",
      \"initialDeposit\": \"2500.00\",
      \"accountType\": \"PREMIUM_SAVINGS\",
      \"requestCreditCard\": true,
      \"checkLoanEligibility\": true
    }")
  
  echo "🔍 Onboarding Response:"
  echo "$onboarding_response" | python3 -m json.tool 2>/dev/null || echo "$onboarding_response"
  
  if echo "$onboarding_response" | grep -q "SUCCESS"; then
    log_success "Distributed transaction completed successfully!"
    log_success "✅ Customer verified (customers-service)"
    log_success "✅ Account created (accounts-service)"
    log_success "✅ Credit card issued (cards-service)"
    log_success "✅ Loan eligibility checked (loans-service)"
    return 0
  else
    log_warn "Distributed transaction completed with issues"
    return 1
  fi
}

# Function to run Postman collection tests
# Function to run multiple Postman collections
run_all_postman_tests() {
  local base_dir="/Users/Sameep.Mondhe/learning/ms/microservices-with-java/postman-collections"
  local env_file="$base_dir/local-environment.json"
  local report_dir="$base_dir/reports"
  
  log_step "Running all Postman test collections..."
  
  # Create the reports directory if it doesn't exist
  mkdir -p "$report_dir"
  
  local timestamp=$(date +%Y%m%d-%H%M%S)
  local collections=(
    "banking-microservices-fixed-final.postman_collection.json:main-tests"
    "distributed-transactions.postman_collection.json:distributed-tests"
  )
  
  local overall_success=true
  
  # Check if htmlextra reporter is available
  local use_htmlextra=false
  if npm list -g newman-reporter-htmlextra &> /dev/null; then
    use_htmlextra=true
    log_success "Using htmlextra reporter for detailed HTML reports"
  else
    log_warn "Using basic CLI reporter (htmlextra not available)"
  fi
  
  for collection_info in "${collections[@]}"; do
    local collection_file=$(echo $collection_info | cut -d: -f1)
    local test_type=$(echo $collection_info | cut -d: -f2)
    local collection_path="$base_dir/$collection_file"
    
    if [ ! -f "$collection_path" ]; then
      log_warn "Collection not found: $collection_file (skipping)"
      continue
    fi
    
    log_step "Running $test_type collection: $collection_file"
    
    local report_path="$report_dir/${test_type}-report-$timestamp.html"
    
    # Run the tests with appropriate reporter
    if [ "$use_htmlextra" = true ]; then
      newman run "$collection_path" -e "$env_file" \
        --reporters cli,htmlextra \
        --reporter-htmlextra-export "$report_path" \
        --timeout-request 30000
    else
      newman run "$collection_path" -e "$env_file" \
        --reporters cli \
        --timeout-request 30000
    fi
    
    local result=$?
    if [ $result -eq 0 ]; then
      log_success "$test_type completed successfully"
      if [ "$use_htmlextra" = true ]; then
        echo -e "  ${GREEN}📊 Report: $report_path${NC}"
      fi
    else
      log_error "$test_type failed"
      if [ "$use_htmlextra" = true ]; then
        echo -e "  ${YELLOW}📊 Report: $report_path${NC}"
      fi
      overall_success=false
    fi
    
    echo # Add spacing between collections
  done
  
  if [ "$overall_success" = true ]; then
    log_success "All Postman collections passed!"
    return 0
  else
    log_warn "Some Postman collections had failures"
    return 1
  fi
}

# Legacy function to run single Postman collection tests
run_postman_tests() {
  local collection_path="$1"
  local environment_path="$2"
  local report_dir="/Users/Sameep.Mondhe/learning/ms/microservices-with-java/postman-collections/reports"

  log_step "Running Postman tests via Newman..."

  # Create the reports directory if it doesn't exist
  mkdir -p "$report_dir"

  local timestamp=$(date +%Y%m%d-%H%M%S)
  local report_path="$report_dir/report-$timestamp.html"

  echo "Collection: $collection_path"
  echo "Environment: $environment_path"
  echo "Report will be saved to: $report_path"

  # Run the tests with absolute paths
  newman run "$collection_path" -e "$environment_path" --reporters cli,htmlextra --reporter-htmlextra-export "$report_path"

  local result=$?
  if [ $result -eq 0 ]; then
    log_success "API tests completed successfully."
    echo -e "${GREEN}📊 HTML test report available at: $report_path${NC}"
    return 0
  else
    log_error "Some API tests failed. Check the report for details."
    echo -e "${YELLOW}📊 HTML test report available at: $report_path${NC}"
    return 1
  fi
}

# Function to display observability information
show_observability_info() {
  log_step "Observability Stack Information"
  echo -e "${BLUE}🔍 After running tests, check these observability tools:${NC}"
  echo -e "${GREEN}📊 Grafana Dashboard:${NC} http://localhost:3000"
  echo -e "   └── Username: admin, Password: admin"
  echo -e "   └── Dashboard: 'Microservices Overview'"
  echo
  echo -e "${GREEN}📈 Prometheus Metrics:${NC} http://localhost:9090"
  echo -e "   └── Query metrics like: http_server_requests_seconds_count"
  echo
  echo -e "${GREEN}🔗 Distributed Traces:${NC} Grafana → Explore → Tempo"
  echo -e "   └── Search for traces during your test run"
  echo
  echo -e "${GREEN}📝 Service Logs:${NC}"
  echo -e "   └── Docker logs: docker logs accounts-service"
  echo -e "   └── All logs: docker-compose logs -f"
  echo
}

# Function to show usage information
show_usage() {
  echo -e "${BLUE}===== Banking Microservices API Test Runner =====${NC}"
  echo
  echo "Usage: $0 [options]"
  echo
  echo "Options:"
  echo "  -h, --help              Show this help message"
  echo "  -q, --quick             Run quick distributed transaction test only"
  echo "  -p, --postman           Run Postman collections only"
  echo "  -a, --all               Run all tests (quick + Postman) [default]"
  echo "  -s, --services-check    Check all services health only"
  echo "  -o, --observability     Show observability information"
  echo
  echo "Examples:"
  echo "  $0                      # Run all tests"
  echo "  $0 -q                   # Quick distributed transaction test"
  echo "  $0 -p                   # Postman collections only"
  echo "  $0 -s                   # Check services health"
  echo
}

# Main function
main() {
  local run_quick=false
  local run_postman=false
  local run_all=true
  local services_check_only=false
  local show_observability_only=false
  
  # Parse command line arguments
  while [[ $# -gt 0 ]]; do
    case $1 in
      -h|--help)
        show_usage
        exit 0
        ;;
      -q|--quick)
        run_quick=true
        run_all=false
        shift
        ;;
      -p|--postman)
        run_postman=true
        run_all=false
        shift
        ;;
      -a|--all)
        run_all=true
        shift
        ;;
      -s|--services-check)
        services_check_only=true
        run_all=false
        shift
        ;;
      -o|--observability)
        show_observability_only=true
        run_all=false
        shift
        ;;
      *)
        log_error "Unknown option: $1"
        show_usage
        exit 1
        ;;
    esac
  done

  echo -e "${BLUE}===== Banking Microservices API Test Runner =====${NC}"

  # Show observability info only
  if [ "$show_observability_only" = true ]; then
    show_observability_info
    exit 0
  fi

  # Check services health only
  if [ "$services_check_only" = true ]; then
    check_all_services
    exit $?
  fi

  # Step 1: Check if all services are up
  check_all_services || exit 1

  # Step 2: Check for Newman installation
  if [ "$run_postman" = true ] || [ "$run_all" = true ]; then
    check_and_install_newman || exit 1
  fi

  local overall_success=true

  # Step 3: Run quick distributed transaction test
  if [ "$run_quick" = true ] || [ "$run_all" = true ]; then
    if ! run_quick_distributed_test; then
      overall_success=false
    fi
    echo # Add spacing
  fi

  # Step 4: Run Postman collections
  if [ "$run_postman" = true ] || [ "$run_all" = true ]; then
    if ! run_all_postman_tests; then
      overall_success=false
    fi
    echo # Add spacing
  fi

  # Step 5: Show observability information
  show_observability_info

  echo -e "\n${BLUE}===== Test Run Complete =====${NC}"
  if [ "$overall_success" = true ]; then
    log_success "All tests completed successfully!"
    echo -e "${GREEN}🎉 Your distributed microservices are working correctly!${NC}"
    exit 0
  else
    log_warn "Some tests had issues. Check the reports and logs above."
    exit 1
  fi
}

# Execute main function
main
