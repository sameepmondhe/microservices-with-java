#!/bin/bash

# Microservices management

# Use REPO_ROOT from parent script if available, otherwise calculate it
if [[ -z "$REPO_ROOT" ]]; then
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
fi

# Source common functions (use REPO_ROOT/scripts path)
if [[ -z "$COMMON_SOURCED" ]]; then
  source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
fi

# Microservices configuration
CONFIG_SERVER_PORT=8888
EUREKA_PORT=8761
ACCOUNTS_PORT=8081
LOANS_PORT=8082
CARDS_PORT=8083
CUSTOMERS_PORT=8084
GATEWAY_PORT=8072

# Local services (run as JAR)
LOCAL_SERVICES=("config-server:$CONFIG_SERVER_PORT")

# Docker services (run as containers)
DOCKER_SERVICES=( \
  "eureka-server:$EUREKA_PORT" \
  "accounts:$ACCOUNTS_PORT" \
  "loans:$LOANS_PORT" \
  "cards:$CARDS_PORT" \
  "customers:$CUSTOMERS_PORT" \
  "gateway-server:$GATEWAY_PORT" \
)

# Function to start a local service using Gradle
start_local_service() {
  local service_name=$1
  local port=$2

  stop_local_service "$port" "$service_name"

  log_step "Starting $service_name locally on port $port..."

  local jar_dir="${REPO_ROOT}/${service_name}/build/libs"
  local jar_file
  jar_file=$(ls -1 "$jar_dir"/*.jar 2>/dev/null | grep -v plain | head -n1 || true)
  if [[ -z "$jar_file" ]]; then
    log_step "Building jar for $service_name..."
    if ! (cd "$REPO_ROOT" && ./gradlew :"$service_name":bootJar > /dev/null 2>&1); then
      log_error "Failed to build jar for $service_name"
      return 1
    fi
    jar_file=$(ls -1 "$jar_dir"/*.jar 2>/dev/null | grep -v plain | head -n1 || true)
  fi
  if [[ -z "$jar_file" ]]; then
    log_error "Could not locate built jar for $service_name in $jar_dir"
    return 1
  fi
  log_debug "Using jar: $jar_file"
  
  # Start service with logs redirected to file for local services
  local log_file="${REPO_ROOT}/${service_name}.log"
  log_debug "Redirecting $service_name logs to: $log_file"
  java -jar "$jar_file" > "$log_file" 2>&1 &
  
  wait_for_spring_service "$service_name" "$port"
  log_success "$service_name started locally (logs: $log_file)."
}

# Function to build and run a Docker service
start_docker_service() {
  local service_name=$1
  local port=$2
  local image_name="${service_name}-service:latest"

  ensure_port_free "$port" "$service_name"
  stop_container "${service_name}-service"

  log_step "Building Docker image for $service_name..."
  if (cd "$REPO_ROOT" && docker build -t "$image_name" "./$service_name" > /dev/null 2>&1); then
    log_success "$service_name image built."
  else
    log_error "Failed to build Docker image for $service_name."
    return 1
  fi

  log_step "Starting $service_name container on port $port..."
  if docker run -d --name "${service_name}-service" \
      --network microservices-network \
      -p "$port:$port" \
      --label "logging=promtail" \
      --label "service=${service_name}" \
      --add-host=host.docker.internal:host-gateway \
      "$image_name" > /dev/null; then
    log_success "$service_name container started."
  else
    log_error "Failed to start $service_name container."
    return 1
  fi
  
  wait_for_spring_service "$service_name" "$port"
}

# Function to wait for Spring Boot service readiness
wait_for_spring_service() {
  local service_name=$1
  local port=$2
  local health_url="http://localhost:$port/actuator/health/readiness"
  local retries=40
  local delay=2

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
  log_error "$service_name did not become ready in time."
  if docker ps --format '{{.Names}}' | grep -q "${service_name}-service"; then
    echo "🔍 Last 10 lines of logs for $service_name:"; 
    docker logs "${service_name}-service" 2>/dev/null | tail -n 10
  fi
  return 1
}

# Function to build all services
build_all_services() {
  log_step "Building all microservices..."
  if (cd "$REPO_ROOT" && ./gradlew clean build -x test > /dev/null 2>&1); then
    log_success "All services built successfully."
    return 0
  else
    log_error "Failed to build services."
    return 1
  fi
}

# Main function to start all microservices
start_microservices() {
  log_step "🚀 Starting Microservices..."
  
  ensure_docker_network
  build_all_services || return 1
  
  # Start local services first (config-server)
  for spec in "${LOCAL_SERVICES[@]}"; do
    IFS=: read -r name port <<<"$spec"
    start_local_service "$name" "$port" || return 1
    sleep 2  # Give config server time to start
  done

  # Start Docker services in order
  for spec in "${DOCKER_SERVICES[@]}"; do
    IFS=: read -r name port <<<"$spec"
    start_docker_service "$name" "$port" || return 1
    
    # Extra pause for Eureka to be ready for registrations
    if [[ "$name" == "eureka-server" ]]; then
      sleep 3
    fi
  done
  
  log_success "All microservices started successfully!"
  echo "
  🛠  Config Server: http://localhost:$CONFIG_SERVER_PORT
  🌐 Eureka Server:  http://localhost:$EUREKA_PORT
  💰 Accounts:       http://localhost:$ACCOUNTS_PORT
  💳 Cards:          http://localhost:$CARDS_PORT
  🏦 Loans:          http://localhost:$LOANS_PORT
  👥 Customers:      http://localhost:$CUSTOMERS_PORT
  🚪 Gateway:        http://localhost:$GATEWAY_PORT"
}

# Function to stop all microservices
stop_microservices() {
  log_step "🛑 Stopping Microservices..."
  
  # Stop local services
  for spec in "${LOCAL_SERVICES[@]}"; do
    IFS=: read -r name port <<<"$spec"
    stop_local_service "$port" "$name"
  done
  
  # Stop Docker services
  for spec in "${DOCKER_SERVICES[@]}"; do
    IFS=: read -r name _port <<<"$spec"
    stop_container "${name}-service"
  done
  
  log_success "All microservices stopped."
}