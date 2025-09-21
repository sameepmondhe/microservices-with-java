#!/bin/bash

# Test script for modular components

source "$(dirname "$0")/scripts/common.sh"

test_observability_only() {
  log_step "🧪 Testing Observability Stack Only..."
  
  source "$(dirname "$0")/scripts/observability.sh"
  
  check_prerequisites || return 1
  start_observability_stack || return 1
  
  log_step "Testing OTEL collector endpoint..."
  if curl -f "http://localhost:4318/v1/traces" -X POST -H "Content-Type: application/json" -d '{}' >/dev/null 2>&1; then
    log_success "OTEL collector is accepting traces."
  else
    log_error "OTEL collector not responding to trace requests."
  fi
  
  log_step "Testing Tempo connectivity..."
  if curl -f "http://localhost:3200/ready" >/dev/null 2>&1; then
    log_success "Tempo API is accessible."
  else
    log_error "Tempo API not accessible."
  fi
  
  log_step "Testing Grafana UI..."
  if curl -fs "http://localhost:3000/api/health" >/dev/null 2>&1; then
    log_success "Grafana UI is accessible."
  else
    log_error "Grafana UI not accessible."
  fi
  
  log_success "Observability stack test completed!"
  echo "
  📊 Access Grafana: http://localhost:3000 (admin/admin)
  📡 OTEL Collector: http://localhost:4318
  🔍 Tempo API: http://localhost:3200"
}

test_microservices_only() {
  log_step "🧪 Testing Microservices Only..."
  
  source "$(dirname "$0")/scripts/microservices.sh"
  
  check_prerequisites || return 1
  start_microservices || return 1
  
  log_step "Testing config server..."
  if curl -fs "http://localhost:8888/actuator/health" >/dev/null 2>&1; then
    log_success "Config server is healthy."
  else
    log_error "Config server not responding."
  fi
  
  log_step "Testing accounts service..."
  if curl -fs "http://localhost:8081/actuator/health" >/dev/null 2>&1; then
    log_success "Accounts service is healthy."
  else
    log_error "Accounts service not responding."
  fi
  
  log_success "Microservices test completed!"
}

case "${1:-}" in
  "observability")
    test_observability_only
    ;;
  "microservices") 
    test_microservices_only
    ;;
  *)
    echo "Usage: $0 {observability|microservices}"
    echo "  observability - Test only the observability stack"
    echo "  microservices - Test only the microservices"
    exit 1
    ;;
esac