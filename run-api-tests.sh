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

# Function to check if the gateway service is up
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
  else
    log_warn "Newman not found. Attempting to install via npm..."
    npm install -g newman newman-reporter-htmlextra
    if command -v newman &> /dev/null; then
      log_success "Newman installed successfully."
    else
      log_error "Failed to install Newman. Please install manually using: npm install -g newman"
      return 1
    fi
  fi
  return 0
}

# Function to run Postman collection tests
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

# Main function
main() {
  echo -e "${BLUE}===== Banking Microservices API Test Runner =====${NC}"

  # Step 1: Check if gateway service is up
  check_gateway || exit 1

  # Step 2: Check for Newman installation
  check_and_install_newman || exit 1

  # Step 3: Define paths for collection and environment files
  local env_file="/Users/Sameep.Mondhe/learning/ms/microservices-with-java/postman-collections/local-environment.json"
  local collection_path="/Users/Sameep.Mondhe/learning/ms/microservices-with-java/postman-collections/banking-microservices-fixed-final.postman_collection.json"

  # Step 4: Verify files exist
  if [ ! -f "$env_file" ]; then
    log_error "Environment file not found at: $env_file"
    exit 1
  fi

  if [ ! -f "$collection_path" ]; then
    log_error "Postman collection file not found at: $collection_path"
    exit 1
  fi

  log_success "Using environment file: $env_file"
  log_success "Using collection file: $collection_path"

  # Step 5: Run the tests
  run_postman_tests "$collection_path" "$env_file"
  exit_code=$?

  echo -e "\n${BLUE}===== Test Run Complete =====${NC}"
  exit $exit_code
}

# Execute main function
main
