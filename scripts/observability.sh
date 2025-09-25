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

# Prometheus configuration
PROMETHEUS_PORT=9090

# Loki configuration
LOKI_PORT=3100
LOKI_GRPC_PORT=9096

# Unified Alloy configuration (fully replaced Promtail)
ALLOY_PORT=12345

# Customer Analytics API port (integrated into unified Alloy)
CUSTOMER_ANALYTICS_PORT=12346
CUSTOMER_ANALYTICS_METRICS_PORT=12347

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

# Function to start Loki
start_loki() {
  log_step "Starting Loki..."
  
  ensure_port_free "$LOKI_PORT" "loki-api"
  ensure_port_free "$LOKI_GRPC_PORT" "loki-grpc"
  
  stop_container "loki-service"
  
  log_step "Building Loki image..."
  if (cd "$REPO_ROOT" && docker build -t loki-service:latest ./loki > /dev/null 2>&1); then
    log_success "Loki image built."
  else
    log_error "Failed to build Loki image."
    return 1
  fi
  
  log_step "Starting Loki container..."
  if docker run -d --name "loki-service" \
      --network microservices-network \
      -p "${LOKI_PORT}:3100" \
      -p "${LOKI_GRPC_PORT}:9096" \
      --add-host=host.docker.internal:host-gateway \
      loki-service:latest > /dev/null; then
    log_success "Loki container started."
  else
    log_error "Failed to start Loki container."
    return 1
  fi
  
  wait_for_loki
}

# Function to wait for Loki readiness
wait_for_loki() {
  local retries=20
  local delay=3
  log_step "Waiting for Loki readiness..."
  
  while (( retries > 0 )); do
    if curl -f "http://localhost:${LOKI_PORT}/ready" >/dev/null 2>&1; then
      log_success "Loki is ready."
      return 0
    fi
    retries=$((retries - 1))
    sleep "$delay"
    echo -n "."
  done
  
  echo ""
  log_error "Loki did not become ready in time."
  docker logs loki-service 2>/dev/null | tail -n 10
  return 1
}

# Promtail functions removed - fully replaced by unified Alloy

# Function to start Unified Alloy (replaces Promtail entirely)
start_alloy() {
  log_step "Starting Unified Grafana Alloy (replacing Promtail)..."
  
  ensure_port_free "$ALLOY_PORT" "alloy"
  ensure_port_free "$CUSTOMER_ANALYTICS_PORT" "alloy-customer-api"
  
  stop_container "alloy-unified"
  
  log_step "Building Unified Alloy image..."
  if (cd "$REPO_ROOT" && docker build -t alloy-unified:latest ./alloy/unified > /dev/null 2>&1); then
    log_success "Unified Alloy image built."
  else
    log_error "Failed to build Unified Alloy image."
    return 1
  fi
  
  log_step "Starting Unified Alloy container..."
  if docker run -d --name "alloy-unified" \
      --network microservices-network \
      -p "${ALLOY_PORT}:12345" \
      -p "${CUSTOMER_ANALYTICS_PORT}:12346" \
      -v /var/run/docker.sock:/var/run/docker.sock:ro \
      -v /var/lib/docker/containers:/var/lib/docker/containers:ro \
      --add-host=host.docker.internal:host-gateway \
      alloy-unified:latest > /dev/null; then
    log_success "Unified Alloy container started."
  else
    log_error "Failed to start Unified Alloy container."
    return 1
  fi
  
  wait_for_alloy
}

# Function to wait for Alloy readiness
wait_for_alloy() {
  local retries=15
  local delay=2
  log_step "Waiting for Alloy readiness..."
  
  while (( retries > 0 )); do
    if curl -f "http://localhost:${ALLOY_PORT}/-/ready" >/dev/null 2>&1; then
      log_success "Alloy is ready."
      return 0
    fi
    retries=$((retries - 1))
    sleep "$delay"
    echo -n "."
  done
  
  echo ""
  log_error "Alloy did not become ready in time."
  docker logs alloy-service 2>/dev/null | tail -n 10
  return 1
}

# Function to start Customer Analytics Engine (Alloy-based)
start_customer_analytics() {
  log_step "Starting Customer Analytics Engine..."
  
  ensure_port_free "$CUSTOMER_ANALYTICS_PORT" "customer-analytics-api"
  ensure_port_free "$CUSTOMER_ANALYTICS_METRICS_PORT" "customer-analytics-metrics"
  
  stop_container "alloy-customer-analytics"
  
  log_step "Building Customer Analytics image..."
  if (cd "$REPO_ROOT" && docker build -t alloy-customer-analytics:latest ./alloy/customer-analytics > /dev/null 2>&1); then
    log_success "Customer Analytics image built."
  else
    log_error "Failed to build Customer Analytics image."
    return 1
  fi
  
  log_step "Starting Customer Analytics container..."
  if docker run -d --name "alloy-customer-analytics" \
      --network microservices-network \
      -p "${CUSTOMER_ANALYTICS_PORT}:12346" \
      -p "${CUSTOMER_ANALYTICS_METRICS_PORT}:12345" \
      --add-host=host.docker.internal:host-gateway \
      -e CUSTOMER_ANALYTICS_ENABLED=true \
      -e LOKI_ENDPOINT=http://loki-service:3100 \
      -e PROMETHEUS_ENDPOINT=http://prometheus-service:9090 \
      alloy-customer-analytics:latest > /dev/null; then
    log_success "Customer Analytics container started."
  else
    log_error "Failed to start Customer Analytics container."
    return 1
  fi
  
  wait_for_customer_analytics
}

# Function to wait for Customer Analytics readiness
wait_for_customer_analytics() {
  local retries=15
  local delay=2
  log_step "Waiting for Customer Analytics readiness..."
  
  while (( retries > 0 )); do
    if curl -f "http://localhost:${CUSTOMER_ANALYTICS_METRICS_PORT}/-/ready" >/dev/null 2>&1; then
      log_success "Customer Analytics is ready."
      return 0
    fi
    retries=$((retries - 1))
    sleep "$delay"
    echo -n "."
  done
  
  echo ""
  log_error "Customer Analytics did not become ready in time."
  docker logs alloy-customer-analytics 2>/dev/null | tail -n 10
  return 1
}

# Function to start Prometheus
start_prometheus() {
  log_step "Starting Prometheus..."
  
  ensure_port_free "$PROMETHEUS_PORT" "prometheus"
  stop_container "prometheus-service"
  
  log_step "Building Prometheus image..."
  if (cd "$REPO_ROOT" && docker build -t prometheus-service:latest ./prometheus > /dev/null 2>&1); then
    log_success "Prometheus image built."
  else
    log_error "Failed to build Prometheus image."
    return 1
  fi
  
  log_step "Starting Prometheus container..."
  if docker run -d --name "prometheus-service" \
      --network microservices-network \
      -p "${PROMETHEUS_PORT}:9090" \
      --add-host=host.docker.internal:host-gateway \
      prometheus-service:latest > /dev/null; then
    log_success "Prometheus container started."
  else
    log_error "Failed to start Prometheus container."
    return 1
  fi
  
  wait_for_prometheus
}

# Function to wait for Prometheus readiness
wait_for_prometheus() {
  local retries=20
  local delay=3
  log_step "Waiting for Prometheus readiness..."
  
  while (( retries > 0 )); do
    if curl -fs "http://localhost:${PROMETHEUS_PORT}/-/ready" >/dev/null 2>&1; then
      log_success "Prometheus is ready."
      return 0
    fi
    retries=$((retries - 1))
    sleep "$delay"
    echo -n "."
  done
  
  echo ""
  log_error "Prometheus did not become ready in time."
  docker logs prometheus-service 2>/dev/null | tail -n 10
  return 1
}

# Function to start Grafana
start_grafana() {
  log_step "Starting Grafana..."
  
  ensure_port_free "$GRAFANA_PORT" "grafana"
  stop_container "grafana-service"
  
  log_step "Building Grafana image..."
  if (cd "$REPO_ROOT" && docker build -t grafana-service:latest ./grafana > /dev/null 2>&1); then
    log_success "Grafana image built."
  else
    log_error "Failed to build Grafana image."
    return 1
  fi
  
  log_step "Starting Grafana container..."
  if docker run -d --name "grafana-service" \
      --network microservices-network \
      -p "${GRAFANA_PORT}:3000" \
      -e "GF_SECURITY_ADMIN_PASSWORD=admin" \
      --add-host=host.docker.internal:host-gateway \
      grafana-service:latest > /dev/null; then
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
  
  # Start in order: OTEL Collector, Tempo, Loki, Unified Alloy (replaces Promtail), Prometheus, then Grafana
  start_otel_collector || return 1
  start_tempo || return 1
  start_loki || return 1
  
  # Start Unified Alloy (replaces Promtail entirely)
  start_alloy || {
    log_error "Unified Alloy failed to start - this is critical for log collection"
    return 1
  }
  
  start_prometheus || return 1
  start_grafana || return 1
  
  log_success "Observability stack started successfully!"
  echo "
  📡 OTEL Collector: 
     - gRPC: localhost:${OTEL_COLLECTOR_GRPC_PORT}
     - HTTP: localhost:${OTEL_COLLECTOR_HTTP_PORT}
     - Health: localhost:${OTEL_COLLECTOR_HEALTH_PORT}
  🔍 Tempo: localhost:${TEMPO_API_PORT}
  📝 Loki: localhost:${LOKI_PORT}
  🤖 Unified Alloy: localhost:${ALLOY_PORT} (Modern log collection + processing)
     - Customer Events API: localhost:${CUSTOMER_ANALYTICS_PORT}
  📊 Prometheus: http://localhost:${PROMETHEUS_PORT}
  📊 Grafana: http://localhost:${GRAFANA_PORT} (admin/admin)"
}

# Function to stop observability stack
stop_observability_stack() {
  log_step "🛑 Stopping Observability Stack..."
  
  stop_container "grafana-service"
  stop_container "prometheus-service"
  stop_container "alloy-unified"
  stop_container "loki-service"
  stop_container "tempo-service" 
  stop_container "otel-collector-service"
  
  log_success "Observability stack stopped."
}