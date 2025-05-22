#!/bin/bash

set -e

# Function to stop a local Java process running on a specific port
stop_local_service() {
  local port=$1
  local name=$2
  local pid=$(lsof -ti:$port || true)
  if [[ -n "$pid" ]]; then
    echo "  - Stopping $name on port $port (PID: $pid)..."
    kill "$pid" 2>/dev/null || true
    sleep 2
  fi
}

# Function to stop a Docker container
stop_container() {
  local container_name=$1
  echo "  - Stopping container $container_name if running..."
  docker rm -f "$container_name" 2>/dev/null || true
}

# Function to build and run a Docker service
build_and_run_service() {
  local service_name=$1
  local port=$2
  local image_name="${service_name}-service:latest"

  echo -e "\n🐳 Building Docker image for $service_name service..."
  docker build -t "$image_name" "./$service_name"

  echo "🚀 Running $service_name service container on port $port..."
  # Map the host port to the same container port, as specified in application.yml
  docker run -d --name "${service_name}-service" -p "$port:$port" --add-host=host.docker.internal:host-gateway "$image_name"
}

# Function to wait for service readiness
wait_for_service() {
  local service_name=$1
  local port=$2
  local health_url="http://localhost:$port/actuator/health/readiness"
  local retries=30

  echo -n "⌛ Waiting for $service_name service readiness..."
  while [[ $retries -gt 0 ]]; do
    echo -n "."
    # First check if the port is accepting connections
    if nc -z localhost $port >/dev/null 2>&1; then
      # Port is open, now check the health endpoint
      local response=$(curl -sf "$health_url" 2>/dev/null || echo '')
      if [[ -n "$response" ]]; then
        local status=$(echo "$response" | jq -r '.status' 2>/dev/null || echo '')
        if [[ "$status" == "UP" ]]; then
          echo " ✅ $service_name service UP"
          return 0
        fi
      fi
    fi
    retries=$((retries-1))
    sleep 2
  done

  echo -e "\n❌ $service_name service did not become ready in time. Exiting."
  echo "   Trying to get raw response from health endpoint:"
  curl -v "$health_url" || echo "   Failed to connect to $health_url"
  echo "   Container logs:"
  if [[ "$service_name" != "config-server" ]]; then
    docker logs "${service_name}-service" | tail -20 || echo "   Could not get container logs"
  fi
  exit 1
}

# Function to start a local service using Gradle
start_local_service() {
  local service_name=$1
  local port=$2

  echo "🚀 Starting $service_name locally on port $port..."
  (cd "$service_name" && ./gradlew bootRun &)

  # Wait for service to be ready
  wait_for_service "$service_name" "$port"
}

echo "🔄 Starting microservices deployment..."

# Step 0: Stop any existing services
echo "🛑 Stopping any existing services..."
stop_local_service 8888 "config-server"
stop_container "accounts-service"
stop_container "cards-service"
stop_container "loans-service"
echo "✅ Cleanup complete"

# Step 1: Start config-server locally
start_local_service "config-server" 8888
sleep 5 # Give config-server time to fully initialize

# Step 2: Start microservices in Docker
build_and_run_service "accounts" 8081
wait_for_service "accounts" 8081

build_and_run_service "loans" 8082
wait_for_service "loans" 8082

build_and_run_service "cards" 8083
wait_for_service "cards" 8083


echo -e "\n🎉 All services started successfully!"
echo "
  - Config Server: http://localhost:8888
  - Accounts:      http://localhost:8081
  - Cards:         http://localhost:8082
  - Loans:         http://localhost:8083
"
