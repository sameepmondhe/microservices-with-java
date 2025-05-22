#!/bin/bash

set -e

echo "🔄 Stopping all microservices..."

# Function to stop a local Java process running on a specific port
stop_local_service() {
  local port=$1
  local name=$2
  local pid=$(lsof -ti:$port || true)
  if [[ -n "$pid" ]]; then
    echo "  - Stopping $name on port $port (PID: $pid)..."
    kill "$pid" 2>/dev/null || true
    echo "    ✅ $name stopped"
  else
    echo "  - $name was not running on port $port"
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
stop_container "accounts-service"
stop_container "cards-service"
stop_container "loans-service"

echo -e "\n🎉 All services have been stopped!"
