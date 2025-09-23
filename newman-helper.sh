#!/bin/bash

# ===== Newman Setup and Testing Helper =====
# Helps install Newman and test Postman collections

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_step() { echo -e "${BLUE}🔹 $1${NC}"; }
log_success() { echo -e "  ${GREEN}✅ $1${NC}"; }
log_error() { echo -e "  ${RED}❌ $1${NC}"; }
log_info() { echo -e "  ${YELLOW}ℹ️ $1${NC}"; }

# Install Newman if not present
install_newman() {
  log_step "Checking Newman installation..."
  
  if command -v newman &> /dev/null; then
    log_success "Newman is already installed ($(newman --version))"
    return 0
  fi
  
  log_info "Newman not found. Installing..."
  
  # Check if npm is available
  if ! command -v npm &> /dev/null; then
    log_error "npm is not installed. Please install Node.js first:"
    echo "  - Visit: https://nodejs.org/"
    echo "  - Or use brew: brew install node"
    return 1
  fi
  
  # Install Newman globally
  log_info "Installing Newman via npm..."
  if npm install -g newman; then
    log_success "Newman installed successfully!"
  else
    log_error "Failed to install Newman"
    return 1
  fi
}

# Test single collection run
test_collection() {
  local collection="$1"
  local collection_path="postman-collections/$collection"
  local env_path="postman-collections/local-environment.json"
  
  log_step "Testing collection: $collection"
  
  if [[ ! -f "$collection_path" ]]; then
    log_error "Collection not found: $collection_path"
    return 1
  fi
  
  if [[ ! -f "$env_path" ]]; then
    log_error "Environment file not found: $env_path"
    return 1
  fi
  
  log_info "Running single test iteration..."
  
  if newman run "$collection_path" \
      -e "$env_path" \
      --iteration-count 1 \
      --timeout-request 10000 \
      --delay-request 1000 \
      --reporters cli; then
    log_success "Collection test completed successfully!"
  else
    log_error "Collection test failed"
    return 1
  fi
}

# Quick health check of services
quick_health_check() {
  log_step "Quick health check of banking services..."
  
  local gateway_health="http://localhost:8072/actuator/health"
  
  if curl -s "$gateway_health" | grep -q "UP"; then
    log_success "Gateway is healthy - services appear to be running"
    return 0
  else
    log_error "Gateway is not responding"
    log_info "Please start services first: ./start-services-new.sh"
    return 1
  fi
}

# Main menu
show_menu() {
  echo "Newman Setup and Testing Helper"
  echo ""
  echo "Choose an option:"
  echo "  1) Install Newman"
  echo "  2) Test Banking Collection"
  echo "  3) Test Distributed Transactions Collection"
  echo "  4) Quick Health Check"
  echo "  5) Run Full Demo Activity"
  echo "  q) Quit"
  echo ""
  read -p "Enter your choice [1-5,q]: " choice
  
  case $choice in
    1)
      install_newman
      ;;
    2)
      quick_health_check && test_collection "banking-microservices-fixed-sequence.postman_collection.json"
      ;;
    3)
      quick_health_check && test_collection "distributed-transactions.postman_collection.json"
      ;;
    4)
      quick_health_check
      ;;
    5)
      if [[ -x "./generate-demo-activity.sh" ]]; then
        log_info "Starting full demo activity..."
        ./generate-demo-activity.sh
      else
        log_error "Demo activity script not found or not executable"
      fi
      ;;
    q|Q)
      echo "Goodbye!"
      exit 0
      ;;
    *)
      log_error "Invalid choice. Please try again."
      ;;
  esac
}

# Main execution
main() {
  echo -e "${BLUE}================================================"
  echo -e "🛠️  Newman Setup and Testing Helper"
  echo -e "================================================${NC}\n"
  
  if [[ $# -eq 0 ]]; then
    # Interactive mode
    while true; do
      show_menu
      echo ""
      read -p "Press Enter to continue or Ctrl+C to exit..."
      echo ""
    done
  else
    # Command line mode
    case $1 in
      install)
        install_newman
        ;;
      test-banking)
        quick_health_check && test_collection "banking-microservices-fixed-sequence.postman_collection.json"
        ;;
      test-distributed)
        quick_health_check && test_collection "distributed-transactions.postman_collection.json"
        ;;
      health)
        quick_health_check
        ;;
      demo)
        ./generate-demo-activity.sh "${@:2}"
        ;;
      *)
        echo "Usage: $0 [install|test-banking|test-distributed|health|demo]"
        echo ""
        echo "Commands:"
        echo "  install          Install Newman"
        echo "  test-banking     Test banking collection"
        echo "  test-distributed Test distributed transactions collection"
        echo "  health           Quick health check"
        echo "  demo [options]   Run demo activity (passes options to generate-demo-activity.sh)"
        echo ""
        echo "Run without arguments for interactive mode."
        ;;
    esac
  fi
}

main "$@"