#!/bin/bash

clear
set -e

DEBUG=true  # Set to true for verbose logs

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

# Function to start Docker Compose services
start_docker_compose_services() {
  local services=$1

  log_step "Starting observability services with Docker Compose..."

  if [[ "$DEBUG" == "true" ]]; then
    docker-compose up -d $services
  else
    docker-compose up -d $services > /dev/null 2>&1
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
stop_container "eureka-server-service"
stop_container "accounts-service"
stop_container "cards-service"
stop_container "loans-service"
stop_container "customers-service"
stop_container "gateway-server-service"
log_success "Cleanup complete."

# Step 2: Start config-server locally
start_local_service "config-server" 8888
sleep 5  # Give it time to fully initialize

# Step 3: Start Eureka server in Docker
build_and_run_service "eureka-server" 8761
wait_for_service "eureka-server" 8761
sleep 5  # Give Eureka time to fully initialize

# Step 4: Start other services in Docker
build_and_run_service "accounts" 8081
wait_for_service "accounts" 8081

build_and_run_service "loans" 8082
wait_for_service "loans" 8082

build_and_run_service "cards" 8083
wait_for_service "cards" 8083

build_and_run_service "customers" 8084
wait_for_service "customers" 8084

# Step 5: Start Gateway Server in Docker
build_and_run_service "gateway-server" 8072
wait_for_service "gateway-server" 8072

# Step 6: Start Loki, Promtail, and Grafana services
log_step "Starting observability stack (Loki, Promtail, Grafana)..."
start_docker_compose_services "loki promtail grafana"

# Wait for Grafana to be ready
log_step "Waiting for Grafana to be ready..."
grafana_retries=15
while [[ $grafana_retries -gt 0 ]]; do
  response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/api/health || echo "")

  if [[ "$response" == "200" ]]; then
    log_success "Grafana is ready."
    break
  fi

  grafana_retries=$((grafana_retries - 1))
  sleep 2
  echo -n "."
done

if [[ $grafana_retries -eq 0 ]]; then
  log_error "Grafana did not become ready in time, but continuing..."
fi

# Final Summary
echo -e "\n🎉 All services started successfully!"
echo "
  - 🛠  Config Server: http://localhost:8888
  - 🌐 Eureka Server:  http://localhost:8761
  - 💰 Accounts:      http://localhost:8081
  - 💳 Cards:         http://localhost:8083
  - 🏦 Loans:         http://localhost:8082
  - 👥 Customers:      http://localhost:8084
  - 🚪 Gateway:        http://localhost:8072
  - 📊 Grafana:        http://localhost:3000 (admin/admin123)
  - 📝 Loki:           http://localhost:3100"

echo -e "\n📈 To access the Grafana dashboard:"
echo "   1. Open http://localhost:3000 in your browser"
echo "   2. Login with username: admin, password: admin123"
echo "   3. Navigate to Dashboards > Microservices > Microservices Dashboard"
echo "   4. You can now monitor logs from all your microservices"

echo -e "\n🧪 To run API tests, execute: ./run-api-tests.sh"
echo -e "🌟 Deployment complete! Your microservices environment is ready."
