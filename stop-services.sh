#!/bin/bash

set -e

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
  if docker inspect "$container_name" &>/dev/null; then
    docker rm -f "$container_name" 2>/dev/null || true
    echo "    ✅ $container_name stopped"
  else
    echo "    $container_name was not running"
  fi
}

# Stop config-server running locally
echo "🛑 Stopping config-server..."
stop_local_service 8888 "config-server"

# Stop Docker containers
echo -e "\n🛑 Stopping microservices running in Docker containers..."
stop_container "eureka-server-service"
stop_container "accounts-service"
stop_container "cards-service"
stop_container "loans-service"
stop_container "customers-service"
stop_container "gateway-server-service"

# Stop observability stack using docker-compose
echo -e "\n🛑 Stopping observability stack..."
if command -v docker-compose &> /dev/null; then
  echo "  - Using docker-compose to stop observability services (loki, promtail, grafana)..."
  docker-compose down 2>/dev/null || echo "  ⚠️ Warning: docker-compose command failed, containers may still be running"
  echo "    ✅ Observability stack stopped"
else
  # Fallback to individual container stop if docker-compose is not available
  stop_container "grafana"
  stop_container "promtail"
  stop_container "loki"
fi

echo -e "\n🎉 All services have been stopped!"
