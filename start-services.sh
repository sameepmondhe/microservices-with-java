#!/bin/bash

# Clear previous logs
> config-server.log
> accounts.log
> cards.log
> loans.log

# Helper function to wait for a port
wait_for_port() {
  local port=$1
  local service=$2
  echo "Waiting for $service on port $port..."
  until nc -z localhost $port; do
    sleep 2
  done
  echo "✅ $service is up on port $port"
}

# Start config-server
echo "Starting config-server..."
./gradlew :config-server:bootRun > config-server.log 2>&1 &
CONFIG_PID=$!

# Wait for config-server (port 8888)
wait_for_port 8888 "config-server"

# Start accounts service (port 8081)
echo "Starting accounts service..."
./gradlew :accounts:bootRun > accounts.log 2>&1 &
ACCOUNTS_PID=$!

# Start cards service (port 8082)
echo "Starting cards service..."
./gradlew :cards:bootRun > cards.log 2>&1 &
CARDS_PID=$!

# Start loans service (port 8083)
echo "Starting loans service..."
./gradlew :loans:bootRun > loans.log 2>&1 &
LOANS_PID=$!

# Wait for each service
wait_for_port 8081 "accounts service"
wait_for_port 8082 "cards service"
wait_for_port 8083 "loans service"

echo "🎉 All services are up and running!"

