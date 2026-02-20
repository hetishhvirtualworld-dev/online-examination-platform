#!/bin/bash

# API Gateway - Quick Start Script
# This script helps you get the gateway running locally quickly

set -e

echo "=================================="
echo "API Gateway - Quick Start"
echo "=================================="
echo ""

# Check prerequisites
echo "Checking prerequisites..."

# Check Java
if ! command -v java &> /dev/null; then
    echo "❌ Java 17+ is required but not found"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Java 17+ is required, found Java $JAVA_VERSION"
    exit 1
fi
echo "✅ Java $JAVA_VERSION found"

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven is required but not found"
    exit 1
fi
echo "✅ Maven found"

# Check Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is required but not found"
    exit 1
fi
echo "✅ Docker found"

echo ""
echo "=================================="
echo "Starting Redis (required for rate limiting)..."
echo "=================================="
echo ""

# Start Redis if not running
if ! docker ps | grep -q api-gateway-redis; then
    echo "Starting Redis container..."
    docker run -d \
        --name api-gateway-redis \
        -p 6379:6379 \
        redis:7-alpine
    
    # Wait for Redis to be ready
    echo "Waiting for Redis to be ready..."
    sleep 3
    echo "✅ Redis started"
else
    echo "✅ Redis already running"
fi

echo ""
echo "=================================="
echo "Building API Gateway..."
echo "=================================="
echo ""

# Build the application
mvn clean package -DskipTests

echo ""
echo "=================================="
echo "Starting API Gateway..."
echo "=================================="
echo ""

# Run the application
export SPRING_PROFILES_ACTIVE=dev
java -jar target/api-gateway-*.jar &

GATEWAY_PID=$!
echo "Gateway PID: $GATEWAY_PID"

# Wait for application to start
echo "Waiting for gateway to start..."
sleep 10

# Check health
echo ""
echo "=================================="
echo "Checking Gateway Health..."
echo "=================================="
echo ""

if curl -f -s http://localhost:8080/actuator/health > /dev/null; then
    echo "✅ Gateway is healthy!"
    echo ""
    echo "=================================="
    echo "Gateway is ready!"
    echo "=================================="
    echo ""
    echo "📋 Available Endpoints:"
    echo "   Health Check:     http://localhost:8080/actuator/health"
    echo "   Prometheus:       http://localhost:8080/actuator/prometheus"
    echo "   Gateway Routes:   http://localhost:8080/actuator/gateway/routes"
    echo ""
    echo "📝 API Routes (configure backend services first):"
    echo "   Auth:             http://localhost:8080/api/auth/*"
    echo "   Users:            http://localhost:8080/api/users/*"
    echo "   Exams:            http://localhost:8080/api/exams/*"
    echo ""
    echo "🛑 To stop:"
    echo "   kill $GATEWAY_PID"
    echo "   docker stop api-gateway-redis"
    echo ""
else
    echo "❌ Gateway health check failed"
    kill $GATEWAY_PID
    exit 1
fi
