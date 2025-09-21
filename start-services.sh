#!/bin/bash

clear
# NOTE: Previously used 'set -euo pipefail' but '-u' caused an 'unbound variable' error
# originating from a word containing '-javaagent'. To keep forward progress with
# observability enablement, we temporarily drop '-u'. We can re-introduce it later
# after auditing any scripts/Gradle wrappers that may indirectly reference unset vars.
set -eo pipefail

DEBUG=true  # Set to true for verbose logs

# -----------------------------------------------------------------------------
# Configuration (centralized)
# -----------------------------------------------------------------------------
# Resolve absolute path to repo root (directory containing this script)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

CONFIG_SERVER_PORT=8888
EUREKA_PORT=8761
ACCOUNTS_PORT=8081
LOANS_PORT=8082
CARDS_PORT=8083
CUSTOMERS_PORT=8084
GATEWAY_PORT=8072

OBS_STACK_SERVICES=(loki promtail grafana) # currently skipped (placeholder)

# OpenTelemetry Collector ports (added as first docker service)
OTEL_COLLECTOR_GRPC_PORT=4317
OTEL_COLLECTOR_HTTP_PORT=4318
# Health/diagnostic port exposed by collector (13133 by default)
OTEL_COLLECTOR_HEALTH_PORT=13133

JAVA_REQUIRED_MAJOR=21

# Optional docker build args (space-delimited). Keep empty if none.
DOCKER_BUILD_ARGS="" # e.g. "--no-cache --build-arg SOME_FLAG=value"

SERVICES_DOCKER_ORDER=( \
  "otel-collector:$OTEL_COLLECTOR_GRPC_PORT" \
  "jaeger:16686" \
  "eureka-server:$EUREKA_PORT" \
  "accounts:$ACCOUNTS_PORT" \
  "loans:$LOANS_PORT" \
  "cards:$CARDS_PORT" \
  "customers:$CUSTOMERS_PORT" \
  "gateway-server:$GATEWAY_PORT" \
)

LOCAL_SERVICES=("config-server:$CONFIG_SERVER_PORT")

# Logging helpers
log_step() { echo -e "\n🔹 $1"; }

log_success() { echo -e "  ✅ $1"; }

log_error() { echo -e "  ❌ $1"; }

log_debug() { [[ "$DEBUG" == "true" ]] && echo "  🐛 $1"; }

# Ensure cleanup on unexpected exit
cleanup_on_exit() {
  local code=$?
  if [[ $code -ne 0 ]]; then
    log_error "Script exited unexpectedly (code=$code). Some services may still be running."
  fi
}
trap cleanup_on_exit EXIT

# Function to stop a local Java process running on a specific port
stop_local_service() {
  local port=$1
  local name=${2:-"process"}
  local pid
  pid=$(lsof -ti:$port || true)
  if [[ -n "$pid" ]]; then
    log_step "Stopping $name running on port $port (PID: $pid)..."
    if kill "$pid" 2>/dev/null; then
      sleep 1
      if lsof -ti:$port >/dev/null 2>&1; then
        log_debug "Process still alive, sending SIGKILL..."
        kill -9 "$pid" 2>/dev/null || true
        sleep 1
      fi
      if lsof -ti:$port >/dev/null 2>&1; then
        log_error "Failed to free port $port (PID $pid persists)."
        return 1
      fi
      log_success "$name stopped."
    else
      log_error "Failed to send SIGTERM to PID $pid on port $port."
      return 1
    fi
  fi
  return 0
}

# Function to stop a Docker container
stop_container() {
  local container_name=$1
  docker rm -f "$container_name" > /dev/null 2>&1 && log_success "$container_name container stopped." || true
}

## (Removed dedicated start_otel_collector; collector handled via generic docker service flow)

# Function to build and run a Docker service
build_and_run_service() {
  local service_name=$1
  local port=$2
  local image_name="${service_name}-service:latest"

  # Ensure port free (might have been used by stray local run)
  stop_local_service "$port" "$service_name" || true
  stop_container "${service_name}-service" || true

  # Create Docker network if it doesn't exist (for service communication)
  if ! docker network ls | grep -q "microservices-network"; then
    log_step "Creating Docker network for microservices..."
    docker network create microservices-network > /dev/null 2>&1 || true
    log_success "Docker network created."
  fi

  # Skip building for services that use pre-built images (only Jaeger)
  if [[ "$service_name" == "jaeger" ]]; then
    log_step "Using pre-built image for $service_name (no build required)..."
  else
    log_step "Building Docker image for $service_name..."
    # Build (handle optional args via eval-safe array construction)
    local cmd=(docker build -t "$image_name")
    if [[ -n "$DOCKER_BUILD_ARGS" ]]; then
      # shellcheck disable=SC2206 # intentional word splitting for args
      local extra=( $DOCKER_BUILD_ARGS )
      cmd+=("${extra[@]}")
    fi
    cmd+=("./$service_name")
    if "${cmd[@]}" > /dev/null 2>&1; then
      log_success "$service_name image built."
    else
      log_error "Failed to build Docker image for $service_name."
      exit 1
    fi
  fi

  log_step "Running $service_name container on port $port..."
  
  # Set image name based on service type
  local final_image_name="$image_name"
  if [[ "$service_name" == "jaeger" ]]; then
    final_image_name="jaegertracing/all-in-one:latest"
  fi
  
  # Configure port mappings based on service requirements
  local docker_cmd=(docker run -d --name "${service_name}-service" --network microservices-network)
  
  if [[ "$service_name" == "otel-collector" ]]; then
    docker_cmd+=(-p "${OTEL_COLLECTOR_GRPC_PORT}:4317")
    docker_cmd+=(-p "${OTEL_COLLECTOR_HTTP_PORT}:4318") 
    docker_cmd+=(-p "${OTEL_COLLECTOR_HEALTH_PORT}:13133")
  elif [[ "$service_name" == "jaeger" ]]; then
    docker_cmd+=(-p "16686:16686")  # Jaeger UI
    docker_cmd+=(-p "14250:14250")  # Jaeger collector port for OTEL
  else
    docker_cmd+=(-p "$port:$port")
  fi
  
  # Add host gateway for all services
  docker_cmd+=(--add-host=host.docker.internal:host-gateway)
  docker_cmd+=("$final_image_name")
  
  if "${docker_cmd[@]}" > /dev/null; then
    log_success "$service_name container started."
  else
    log_error "Failed to start $service_name container."
    exit 1
  fi
}

# Function to wait for service readiness
wait_for_service() {
  local service_name=$1
  local port=$2
  local health_url="http://localhost:$port/actuator/health/readiness"
  local retries=40
  local delay=2

  if [[ "$service_name" == "otel-collector" ]]; then
    health_url="http://localhost:${OTEL_COLLECTOR_HEALTH_PORT}/healthz"
    retries=20
    delay=1
    log_step "Waiting for $service_name readiness (health port ${OTEL_COLLECTOR_HEALTH_PORT})..."
    while (( retries > 0 )); do
      if curl -fs "$health_url" >/dev/null 2>&1; then
        log_success "$service_name is UP."
        # Additional check - verify OTLP HTTP endpoint is ready
        log_debug "Verifying OTLP HTTP endpoint readiness..."
        sleep 2
        if curl -f "http://localhost:${OTEL_COLLECTOR_HTTP_PORT}/v1/traces" -X POST -H "Content-Type: application/json" -d '{}' >/dev/null 2>&1; then
          log_debug "OTLP HTTP endpoint is ready to accept requests."
        else
          log_debug "OTLP HTTP endpoint not fully ready yet (this may be normal)."
        fi
        return 0
      fi
      retries=$((retries - 1))
      sleep "$delay"
      echo -n "."
    done
    echo ""
    log_error "$service_name did not become healthy in time; proceeding (exporters/SDK will retry)."
    # Show collector logs for debugging
    log_debug "Last 20 lines of otel-collector logs:"
    docker logs "${service_name}-service" 2>/dev/null | tail -n 20 | while read -r line; do log_debug "$line"; done
    return 1
  elif [[ "$service_name" == "jaeger" ]]; then
    health_url="http://localhost:16686/"
    retries=20
    delay=2
    log_step "Waiting for Jaeger UI readiness (port 16686)..."
    while (( retries > 0 )); do
      if curl -fs "$health_url" >/dev/null 2>&1; then
        log_success "Jaeger UI is UP and accessible."
        return 0
      fi
      retries=$((retries - 1))
      sleep "$delay"
      echo -n "."
    done
    echo ""
    log_error "Jaeger did not become accessible in time; proceeding anyway."
    return 1
  fi

  log_step "Waiting for $service_name readiness on port $port..."
  while (( retries > 0 )); do
    local response status
    response=$(curl -sf "$health_url" 2>/dev/null || true)
    status=$(echo "$response" | jq -r '.status' 2>/dev/null || echo '')
    if [[ "$status" == "UP" ]]; then
      log_success "$service_name is UP."
      return 0
    fi
    retries=$((retries - 1))
    sleep "$delay"
    echo -n "."
  done
  echo ""
  log_error "$service_name did not become ready in time (timeout=$((40*delay))s)."
  if docker ps --format '{{.Names}}' | grep -q "${service_name}-service"; then
    echo "🔍 Last 40 lines of logs for $service_name:"; docker logs "${service_name}-service" 2>/dev/null | tail -n 40
  else
    log_debug "Container ${service_name}-service not found when collecting logs."
  fi
  exit 1
}

# Function to start a local service using Gradle
start_local_service() {
  local service_name=$1
  local port=$2

  stop_local_service "$port" "$service_name" || true

  log_step "Starting $service_name locally on port $port..."

  local jar_dir="${SCRIPT_DIR}/${service_name}/build/libs"
  local jar_file
  jar_file=$(ls -1 "$jar_dir"/*.jar 2>/dev/null | grep -v plain | head -n1 || true)
  if [[ -z "$jar_file" ]]; then
    log_step "Building jar for $service_name..."
    if ! ./gradlew :"$service_name":bootJar > /dev/null 2>&1; then
      log_error "Failed to build jar for $service_name"
      exit 1
    fi
    jar_file=$(ls -1 "$jar_dir"/*.jar 2>/dev/null | grep -v plain | head -n1 || true)
  fi
  if [[ -z "$jar_file" ]]; then
    log_error "Could not locate built jar for $service_name in $jar_dir"
    exit 1
  fi
  log_debug "Using jar: $jar_file"
  
  # Clear any OpenTelemetry environment variables to ensure clean startup
  unset OTEL_SERVICE_NAME OTEL_TRACES_EXPORTER OTEL_EXPORTER_OTLP_ENDPOINT OTEL_RESOURCE_ATTRIBUTES OTEL_JAVAAGENT_ENABLED 2>/dev/null || true
  
  # Start service with logs redirected to file for local services
  local log_file="${SCRIPT_DIR}/${service_name}.log"
  log_debug "Redirecting $service_name logs to: $log_file"
  java -jar "$jar_file" > "$log_file" 2>&1 &
  wait_for_service "$service_name" "$port"
  log_success "$service_name started locally (logs: $log_file)."
}

ensure_ports_free() {
  local -a ports=("$@")
  for p in "${ports[@]}"; do
    if lsof -ti:"$p" >/dev/null 2>&1; then
      # Try to map port to a known service name
      local name="unknown"
      case "$p" in
        $CONFIG_SERVER_PORT) name="config-server";;
        $EUREKA_PORT) name="eureka-server";;
        $ACCOUNTS_PORT) name="accounts";;
        $LOANS_PORT) name="loans";;
        $CARDS_PORT) name="cards";;
        $CUSTOMERS_PORT) name="customers";;
        $GATEWAY_PORT) name="gateway-server";;
        $OTEL_COLLECTOR_GRPC_PORT) name="otel-collector-grpc";;
        $OTEL_COLLECTOR_HTTP_PORT) name="otel-collector-http";;
        $OTEL_COLLECTOR_HEALTH_PORT) name="otel-collector-health";;
      esac
      stop_local_service "$p" "$name" || true
      if lsof -ti:"$p" >/dev/null 2>&1; then
        log_error "Port $p still in use after attempted stop. Aborting."
        exit 1
      fi
    fi
  done
  log_success "All required ports are free (after cleanup)."
}

# Function to start Docker Compose services
start_docker_compose_services() {
  local services=$1

  log_step "Starting observability services with Docker Compose..."

  if [[ "$DEBUG" == "true" ]]; then
    docker-compose up -d --remove-orphans $services
  else
    docker-compose up -d --remove-orphans $services > /dev/null 2>&1
  fi

  if [ $? -eq 0 ]; then
    log_success "Observability services started successfully."
    return 0
  else
    log_error "Failed to start observability services with Docker Compose."
    log_error "Try running 'docker-compose up -d loki promtail grafana' manually to see detailed errors."
    return 1
  fi
}

# ─────────────────────────────────────────────────────────────

echo -e "\n🔄 Starting microservices deployment..."

# Preflight checks
log_step "Running preflight checks..."

# 1. Check required commands
REQUIRED_CMDS=(java docker jq curl lsof)
missing=()
for cmd in "${REQUIRED_CMDS[@]}"; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    missing+=("$cmd")
  fi
done
if [[ ${#missing[@]} -gt 0 ]]; then
  log_error "Missing required commands: ${missing[*]}"
  echo "Install them and retry. (brew install openjdk jq curl lsof docker)"
  exit 1
fi
log_success "All required commands present."

# 2. Java version sanity (expect $JAVA_REQUIRED_MAJOR)
JAVA_VERSION_OUTPUT=$(java -version 2>&1 | head -n1)
JAVA_MAJOR=$(echo "$JAVA_VERSION_OUTPUT" | sed -E 's/.*version "([0-9]+).*/\1/')
if [[ "$JAVA_MAJOR" -ne "$JAVA_REQUIRED_MAJOR" ]]; then
  log_error "Java $JAVA_MAJOR detected. Project toolchain set to $JAVA_REQUIRED_MAJOR. Ensure JDK $JAVA_REQUIRED_MAJOR is default (e.g., via sdkman or export JAVA_HOME)."
  echo "Detected: $JAVA_VERSION_OUTPUT"
  exit 1
fi
log_success "Java runtime OK: $JAVA_VERSION_OUTPUT"

# 3. Docker daemon check
if ! docker info >/dev/null 2>&1; then
  log_error "Docker daemon not reachable. Start Docker Desktop and retry."
  exit 1
fi
log_success "Docker daemon reachable."

# 4. Ensure required ports are free (auto-stopping stray processes)
ensure_ports_free $CONFIG_SERVER_PORT $EUREKA_PORT $ACCOUNTS_PORT $LOANS_PORT $CARDS_PORT $CUSTOMERS_PORT $GATEWAY_PORT $OTEL_COLLECTOR_GRPC_PORT $OTEL_COLLECTOR_HTTP_PORT $OTEL_COLLECTOR_HEALTH_PORT

log_success "Preflight checks passed."

# Step 0: Cleanup (containers + local processes)
log_step "Performing cleanup of existing services (if any)..."
for spec in "${LOCAL_SERVICES[@]}"; do
  IFS=: read -r name port <<<"$spec"
  stop_local_service "$port" "$name" || true
done
for spec in "${SERVICES_DOCKER_ORDER[@]}"; do
  IFS=: read -r name _port <<<"$spec"
  stop_container "${name}-service" || true
done
## otel-collector will be stopped via unified SERVICES_DOCKER_ORDER loop (first entry)

log_step "Stopping any running observability stack pods... (currently none besides otel-collector if present)"
chmod +x ./cleanup-containers.sh
./cleanup-containers.sh | while read -r line; do log_debug "$line"; done
log_success "Cleanup complete."

# Step 1: Build all services using root Gradle wrapper (after cleanup)
log_step "Building all services..."
if ./gradlew clean build -x test > /dev/null 2>&1; then
  log_success "All services built successfully."
else
  log_error "Failed to build services."
  exit 1
fi

################################################################################
# Startup Sequence
################################################################################

# Local services first (config-server) after starting collector via docker flow
for spec in "${LOCAL_SERVICES[@]}"; do
  IFS=: read -r name port <<<"$spec"
  start_local_service "$name" "$port"
  sleep 3
done

# Dockerized services (in order)
for spec in "${SERVICES_DOCKER_ORDER[@]}"; do
  IFS=: read -r name port <<<"$spec"
  build_and_run_service "$name" "$port"
  wait_for_service "$name" "$port"
  # Additional grace period for service registration to Eureka (esp. after eureka itself)
  if [[ "$name" == "eureka-server" ]]; then
    sleep 5
  fi
  # Additional grace period for otel-collector to be ready for connections
  if [[ "$name" == "otel-collector" ]]; then
    sleep 3
    log_debug "Testing otel-collector connectivity from host..."
    # Test if the collector is accessible from host
    if curl -f "http://localhost:${OTEL_COLLECTOR_HTTP_PORT}" >/dev/null 2>&1; then
      log_debug "otel-collector HTTP port is accessible from host"
    else
      log_debug "otel-collector HTTP port test failed from host"
    fi
    
    # Show collector logs to see if it's receiving requests
    log_debug "Recent otel-collector logs:"
    docker logs "${name}-service" 2>/dev/null | tail -n 10 | while read -r line; do log_debug "$line"; done
  fi
done

log_step "OpenTelemetry collector integrated with Docker services; config-server runs without observability agent."

# Final Summary
echo -e "\n🎉 All services started successfully!"
echo "
  - 🛠  Config Server: http://localhost:$CONFIG_SERVER_PORT
  - 🌐 Eureka Server:  http://localhost:$EUREKA_PORT
  - 💰 Accounts:       http://localhost:$ACCOUNTS_PORT
  - 💳 Cards:          http://localhost:$CARDS_PORT
  - 🏦 Loans:          http://localhost:$LOANS_PORT
  - 👥 Customers:      http://localhost:$CUSTOMERS_PORT
  - 🚪 Gateway:        http://localhost:$GATEWAY_PORT
  - 📡 OTEL Collector: gRPC:${OTEL_COLLECTOR_GRPC_PORT} HTTP:${OTEL_COLLECTOR_HTTP_PORT} Health:${OTEL_COLLECTOR_HEALTH_PORT}
  - 🔍 Jaeger UI:      http://localhost:16686"

echo -e "\n📈 Observability: Traces exporting via OTLP -> collector -> Jaeger. View traces at http://localhost:16686"

echo -e "\n🧪 To run API tests, execute: ./run-api-tests.sh"
echo -e "🌟 Deployment complete! Your microservices environment is ready."

