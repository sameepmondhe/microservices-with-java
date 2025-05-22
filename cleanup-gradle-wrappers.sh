#!/bin/bash

# Script to remove redundant Gradle wrapper files from microservice directories
# Run this from the root of your microservices project

# List of subdirectories to clean up
SERVICES=("accounts" "loans" "cards" "config-server" "customer")

echo "Removing redundant Gradle wrapper files from service directories..."

for SERVICE in "${SERVICES[@]}"; do
  if [ -d "$SERVICE" ]; then
    echo "Cleaning up $SERVICE directory..."

    # Remove Gradle wrapper files
    rm -f "$SERVICE/gradlew" "$SERVICE/gradlew.bat"
    rm -rf "$SERVICE/gradle"

    echo "Done cleaning $SERVICE"
  else
    echo "Warning: $SERVICE directory not found"
  fi
done

echo "Cleanup complete. You can now use the root-level Gradle wrapper for all services."
echo "Example: './gradlew :accounts:build' to build the accounts service"
