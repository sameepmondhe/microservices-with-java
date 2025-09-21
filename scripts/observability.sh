#!/bin/bash

# Observability stack management (OTEL Collector + Tempo + Grafana)

# Use REPO_ROOT from parent script if available, otherwise calculate it
if [[ -z "$REPO_ROOT" ]]; then
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
fi

# Source common functions (use REPO_ROOT/scripts path)
if [[ -z "$COMMON_SOURCED" ]]; then
  source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
fi

# Observability stack configuration
OTEL_COLLECTOR_GRPC_PORT=4317
OTEL_COLLECTOR_HTTP_PORT=4318
OTEL_COLLECTOR_HEALTH_PORT=13133

# Use non-conflicting ports for Tempo (avoids OTEL collector conflicts)
TEMPO_API_PORT=3200
TEMPO_OTLP_GRPC_PORT=9317  # Different from OTEL collector
TEMPO_OTLP_HTTP_PORT=9318  # Different from OTEL collector

GRAFANA_PORT=3000

# Function to start OTEL collector
start_otel_collector() {
  log_step "Starting OpenTelemetry Collector..."
  
  ensure_port_free "$OTEL_COLLECTOR_GRPC_PORT" "otel-collector-grpc"
  ensure_port_free "$OTEL_COLLECTOR_HTTP_PORT" "otel-collector-http"
  ensure_port_free "$OTEL_COLLECTOR_HEALTH_PORT" "otel-collector-health"
  
  stop_container "otel-collector-service"
  
  log_step "Building OTEL collector image..."
  if (cd "$REPO_ROOT" && docker build -t otel-collector-service:latest ./otel-collector > /dev/null 2>&1); then
    log_success "OTEL collector image built."
  else
    log_error "Failed to build OTEL collector image."
    return 1
  fi
  
  log_step "Starting OTEL collector container..."
  if docker run -d --name "otel-collector-service" \
      --network microservices-network \
      -p "${OTEL_COLLECTOR_GRPC_PORT}:4317" \
      -p "${OTEL_COLLECTOR_HTTP_PORT}:4318" \
      -p "${OTEL_COLLECTOR_HEALTH_PORT}:13133" \
      --add-host=host.docker.internal:host-gateway \
      otel-collector-service:latest > /dev/null; then
    log_success "OTEL collector container started."
  else
    log_error "Failed to start OTEL collector container."
    return 1
  fi
  
  # Wait for collector to be ready
  wait_for_otel_collector
}

# Function to wait for OTEL collector readiness
wait_for_otel_collector() {
  local retries=20
  local delay=2
  log_step "Waiting for OTEL collector readiness..."
  
  while (( retries > 0 )); do
    if curl -fs "http://localhost:${OTEL_COLLECTOR_HEALTH_PORT}/healthz" >/dev/null 2>&1; then
      log_success "OTEL collector is ready."
      return 0
    fi
    retries=$((retries - 1))
    sleep "$delay"
    echo -n "."
  done
  
  echo ""
  log_error "OTEL collector did not become ready in time."
  docker logs otel-collector-service 2>/dev/null | tail -n 10
  return 1
}

# Function to start Tempo
start_tempo() {
  log_step "Starting Tempo..."
  
  ensure_port_free "$TEMPO_API_PORT" "tempo-api"
  ensure_port_free "$TEMPO_OTLP_GRPC_PORT" "tempo-otlp-grpc"
  ensure_port_free "$TEMPO_OTLP_HTTP_PORT" "tempo-otlp-http"
  
  stop_container "tempo-service"
  
  log_step "Building Tempo image..."
  if (cd "$REPO_ROOT" && docker build -t tempo-service:latest ./tempo > /dev/null 2>&1); then
    log_success "Tempo image built."
  else
    log_error "Failed to build Tempo image."
    return 1
  fi
  
  log_step "Starting Tempo container..."
  if docker run -d --name "tempo-service" \
      --network microservices-network \
      -p "${TEMPO_API_PORT}:3200" \
      -p "${TEMPO_OTLP_GRPC_PORT}:9317" \
      -p "${TEMPO_OTLP_HTTP_PORT}:9318" \
      --add-host=host.docker.internal:host-gateway \
      tempo-service:latest > /dev/null; then
    log_success "Tempo container started."
  else
    log_error "Failed to start Tempo container."
    return 1
  fi
  
  wait_for_tempo
}

# Function to wait for Tempo readiness
wait_for_tempo() {
  local retries=15
  local delay=3
  log_step "Waiting for Tempo readiness..."
  
  while (( retries > 0 )); do
    if curl -f "http://localhost:${TEMPO_API_PORT}/ready" >/dev/null 2>&1; then
      log_success "Tempo is ready."
      return 0
    fi
    retries=$((retries - 1))
    sleep "$delay"
    echo -n "."
  done
  
  echo ""
  log_error "Tempo did not become ready in time."
  docker logs tempo-service 2>/dev/null | tail -n 10
  return 1
}

# Function to start Grafana
start_grafana() {
  log_step "Starting Grafana..."
  
  ensure_port_free "$GRAFANA_PORT" "grafana"
  stop_container "grafana-service"
  
  log_step "Starting Grafana container..."
  if docker run -d --name "grafana-service" \
      --network microservices-network \
      -p "${GRAFANA_PORT}:3000" \
      -e "GF_SECURITY_ADMIN_PASSWORD=admin" \
      --add-host=host.docker.internal:host-gateway \
      grafana/grafana:latest > /dev/null; then
    log_success "Grafana container started."
  else
    log_error "Failed to start Grafana container."
    return 1
  fi
  
  wait_for_grafana
}

# Function to wait for Grafana readiness
wait_for_grafana() {
  local retries=20
  local delay=3
  log_step "Waiting for Grafana readiness..."
  
  while (( retries > 0 )); do
    if curl -fs "http://localhost:${GRAFANA_PORT}/api/health" >/dev/null 2>&1; then
      log_success "Grafana is ready."
      return 0
    fi
    retries=$((retries - 1))
    sleep "$delay"
    echo -n "."
  done
  
  echo ""
  log_error "Grafana did not become ready in time."
  return 1
}

# Main function to start observability stack
start_observability_stack() {
  log_step "🔭 Starting Observability Stack..."
  
  ensure_docker_network
  
  # Start in order: OTEL Collector first, then Tempo, then Grafana
  start_otel_collector || return 1
  start_tempo || return 1
  start_grafana || return 1
  
  log_success "Observability stack started successfully!"
  echo "
  📡 OTEL Collector: 
     - gRPC: localhost:${OTEL_COLLECTOR_GRPC_PORT}
     - HTTP: localhost:${OTEL_COLLECTOR_HTTP_PORT}
     - Health: localhost:${OTEL_COLLECTOR_HEALTH_PORT}
  🔍 Tempo: localhost:${TEMPO_API_PORT}
  📊 Grafana: http://localhost:${GRAFANA_PORT} (admin/admin)"
}

# Function to stop observability stack
stop_observability_stack() {
  log_step "🛑 Stopping Observability Stack..."
  
  stop_container "grafana-service"
  stop_container "tempo-service" 
  stop_container "otel-collector-service"
  
  log_success "Observability stack stopped."
}