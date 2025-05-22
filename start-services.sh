#!/bin/bash

clear
set -e

DEBUG=false  # Set to true for verbose logs

# Logging helpers
log_step() {
  echo -e "\n🔹 $1"
}

log_success() {
  echo -e "  ✅ $1"
}

log_error() {
  echo -e "  ❌ $1"
}

log_debug() {
  if [[ "$DEBUG" == "true" ]]; then
    echo "  🐛 $1"
  fi
}

# Function to stop a local Java process running on a specific port
stop_local_service() {
  local port=$1
  local name=$2
  local pid=$(lsof -ti:$port || true)
  if [[ -n "$pid" ]]; then
    log_step "Stopping $name on port $port (PID: $pid)..."
    kill "$pid" 2>/dev/null || true
    sleep 2
    log_success "$name stopped."
  fi
}

# Function to stop a Docker container
stop_container() {
  local container_name=$1
  docker rm -f "$container_name" > /dev/null 2>&1 && log_success "$container_name container stopped." || true
}

# Function to build and run a Docker service
build_and_run_service() {
  local service_name=$1
  local port=$2
  local image_name="${service_name}-service:latest"

  log_step "Building Docker image for $service_name..."
  if docker build -t "$image_name" "./$service_name" > /dev/null 2>&1; then
    log_success "$service_name image built."
  else
    log_error "Failed to build Docker image for $service_name."
    exit 1
  fi

  log_step "Running $service_name container on port $port..."
  if docker run -d --name "${service_name}-service" -p "$port:$port" --add-host=host.docker.internal:host-gateway "$image_name" > /dev/null; then
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
  local retries=30

  log_step "Waiting for $service_name readiness on port $port..."
  while [[ $retries -gt 0 ]]; do
    local response=$(curl -sf "$health_url" 2>/dev/null || echo '')
    local status=$(echo "$response" | jq -r '.status' 2>/dev/null || echo '')

    if [[ "$status" == "UP" ]]; then
      log_success "$service_name is UP."
      return 0
    fi

    retries=$((retries - 1))
    sleep 2
    echo -n "."
  done

  echo ""
  log_error "$service_name did not become ready in time."
  echo "🔍 Last 20 lines of logs for $service_name:"
  docker logs "${service_name}-service" 2>/dev/null | tail -n 20
  exit 1
}

# Function to start a local service using Gradle
start_local_service() {
  local service_name=$1
  local port=$2

  log_step "Starting $service_name locally on port $port..."
  # Use the root Gradle wrapper instead of the service-specific one
  (./gradlew :"$service_name":bootRun > /dev/null 2>&1 &)  # background + quiet
  wait_for_service "$service_name" "$port"
}

# ─────────────────────────────────────────────────────────────

echo -e "\n🔄 Starting microservices deployment..."

# Step 0: Build all services using root Gradle wrapper
log_step "Building all services..."
if ./gradlew clean build -x test > /dev/null 2>&1; then
  log_success "All services built successfully."
else
  log_error "Failed to build services."
  exit 1
fi

# Step 1: Cleanup
log_step "Stopping any existing services..."
stop_local_service 8888 "config-server"
stop_container "accounts-service"
stop_container "cards-service"
stop_container "loans-service"
stop_container "customer-service"
log_success "Cleanup complete."

# Step 2: Start config-server locally
start_local_service "config-server" 8888
sleep 5  # Give it time to fully initialize

# Step 3: Start services in Docker
build_and_run_service "accounts" 8081
wait_for_service "accounts" 8081

build_and_run_service "loans" 8082
wait_for_service "loans" 8082

build_and_run_service "cards" 8083
wait_for_service "cards" 8083

build_and_run_service "customer" 8084
wait_for_service "customer" 8084

# Final Summary
echo -e "\n🎉 All services started successfully!"
echo "
  - 🛠  Config Server: http://localhost:8888
  - 💰 Accounts:      http://localhost:8081
  - 💳 Cards:         http://localhost:8083
  - 🏦 Loans:         http://localhost:8082
  - 👥 Customer:      http://localhost:8084"
