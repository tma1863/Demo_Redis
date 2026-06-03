#!/bin/bash

echo "[1/3] Starting Docker Containers..."
docker compose up -d

echo "[2/3] Waiting for the database to be ready..."
sleep 3

echo "[3/3] Fetch environment variables from .env file..."
export $(cat .env | xargs)

if [ "$1" == "--skip-tests" ]; then
    echo "Running Spring Boot (SKIPPING TESTS)..."
    ./mvnw spring-boot:run -DskipTests
else
    echo "Running Spring Boot (WITH TESTS)..."
    ./mvnw spring-boot:run
fi

# ./run.sh
# export $(grep -v '^#' .env | xargs)     # loads REDIS_PASSWORD, REDIS_PORT, etc.
# docker exec ecom_redis_cache redis-cli -a "$REDIS_PASSWORD" --no-auth-warning DEL "trending-products::getTrendingProducts"

