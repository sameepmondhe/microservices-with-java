#!/bin/bash

# Removed 'set -e' to prevent the script from exiting on errors
# Instead, we'll handle errors more gracefully

echo "🔄 Stopping all microservices..."

# Function to stop a local Java process running on a specific port
stop_local_service() {
  local port=$1
  local name=$2
  local pids=($(lsof -ti:$port || true))

  if [[ ${#pids[@]} -gt 0 ]]; then
    echo "  - Found ${#pids[@]} process(es) on port $port:"

    # List the processes with more details
    echo "    Process details:"
    lsof -i:$port

    # Try to stop each process
    for pid in "${pids[@]}"; do
      echo "  - Stopping process $pid on port $port..."

      # First try graceful termination
      kill "$pid" 2>/dev/null || true

      # Give it a moment to terminate
      sleep 2

      # Check if it's still running
      if ps -p "$pid" > /dev/null 2>&1; then
        echo "    ⚠️ Process $pid still running, forcing termination..."
        kill -9 "$pid" 2>/dev/null || true
        sleep 1
      fi
    done

    # Verify port is actually free
    if lsof -ti:$port > /dev/null 2>&1; then
      echo "    ❌ Failed to free port $port. You may need to manually kill the process."
      echo "       Try running: sudo lsof -i :$port"
      echo "       Then: sudo kill -9 <PID>"
    else
      echo "    ✅ All processes on port $port stopped and port is free"
    fi
  else
    echo "  - No processes found running on port $port"
  fi
}

# Function to stop a Docker container
stop_container() {
  local container_name=$1
  echo "  - Stopping container $container_name..."
  if docker ps --format "{{.Names}}" | grep -E "$container_name" &>/dev/null; then
    docker rm -f "$container_name" 2>/dev/null || true
    echo "    ✅ $container_name stopped"
  else
    echo "    $container_name was not running"
  fi
}

# Stop config-server running locally
echo "🛑 Stopping config-server..."
stop_local_service 8888 "config-server" || echo "  ⚠️ Failed to stop config-server, continuing..."

# Stop Docker containers
echo -e "\n🛑 Stopping microservices running in Docker containers..."
stop_container "eureka-server-service" || echo "  ⚠️ Failed to stop eureka-server-service, continuing..."
stop_container "accounts-service" || echo "  ⚠️ Failed to stop accounts-service, continuing..."
stop_container "cards-service" || echo "  ⚠️ Failed to stop cards-service, continuing..."
stop_container "loans-service" || echo "  ⚠️ Failed to stop loans-service, continuing..."
stop_container "customers-service" || echo "  ⚠️ Failed to stop customers-service, continuing..."
stop_container "gateway-server-service" || echo "  ⚠️ Failed to stop gateway-server-service, continuing..."

# Stop observability stack using docker-compose
echo -e "\n🛑 Stopping observability stack..."
# Try both docker compose (new) and docker-compose (legacy) commands with explicit file path
if command -v docker &> /dev/null; then
  COMPOSE_FILE="/Users/Sameep.Mondhe/learning/ms/microservices-with-java/docker-compose.yml"

  if [ -f "$COMPOSE_FILE" ]; then
    echo "  - Using docker compose to stop observability services..."
    # Try the new Docker Compose command first (no hyphen) with --remove-orphans flag
    if docker compose -f "$COMPOSE_FILE" down --remove-orphans 2>/dev/null; then
      echo "    ✅ Observability stack stopped via docker compose"
    elif docker-compose -f "$COMPOSE_FILE" down --remove-orphans 2>/dev/null; then
      echo "    ✅ Observability stack stopped via docker-compose (legacy)"
    else
      echo "  ⚠️ Warning: docker compose command failed, will try stopping individual containers"

      # Force remove all observability containers specifically by name patterns
      echo "  - Stopping individual observability containers..."

      # Stop containers with exact names or with project prefixes
      docker ps -a --format "{{.Names}}" | grep -E '(prometheus|node-exporter|cadvisor|grafana|loki|alloy|promtail)' 2>/dev/null | while read container; do
        echo "    - Removing container: $container"
        docker rm -f "$container" 2>/dev/null || true
      done

      echo "    ✅ Individual observability containers stopped"
    fi
  else
    echo "  ⚠️ docker-compose.yml file not found at $COMPOSE_FILE"
    # Fallback to individual container stop with more thorough search
    echo "  - Stopping individual observability containers..."

    # Stop containers with exact names or with project prefixes
    docker ps -a --format "{{.Names}}" | grep -E '(prometheus|node-exporter|cadvisor|grafana|loki|alloy|promtail)' 2>/dev/null | while read container; do
      echo "    - Removing container: $container"
      docker rm -f "$container" 2>/dev/null || true
    done
  fi
else
  echo "  ⚠️ Docker not found, cannot stop observability stack"
fi

echo -e "\n🎉 All services have been stopped!"
