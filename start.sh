#!/bin/bash

# Script to run HLTV API locally (outside Docker)
# while connecting to dockerized browserless

set -e

echo "Starting HLTV API in local mode..."

# Load environment variables from .env file
if [ -f .env ]; then
    echo "Loading environment variables from .env..."
    export $(grep -v '^#' .env | xargs)
else
    echo "Warning: .env file not found. Using default values."
fi

# Check if browserless is running
echo "Checking if browserless is running on localhost:3000..."
if ! curl -s http://localhost:3000/json/version > /dev/null 2>&1; then
    echo "Error: browserless is not running on localhost:3000"
    echo "Please start browserless first with: docker compose up -d browserless"
    exit 1
fi

echo "Browserless is running!"

# Build the application if needed
if [ ! -f build/libs/hltv-api-1.0.0.jar ]; then
    echo "Building application..."
    ./gradlew build -x test
fi

# Run the Spring Boot application
echo "Starting Spring Boot application..."
echo "API will be available at http://localhost:8080"
echo "Press Ctrl+C to stop"
echo ""

./gradlew bootRun
