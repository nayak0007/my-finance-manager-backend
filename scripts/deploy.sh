#!/bin/bash
# VPS Deploy Script - Run on VPS to pull latest images and redeploy
# Usage: ./deploy.sh

set -euo pipefail

cd /opt/my-finance-manager

echo "=== Pulling latest images ==="
docker compose pull

echo "=== Stopping old containers ==="
docker compose down --remove-orphans

echo "=== Starting new containers ==="
docker compose up -d

echo "=== Cleaning up unused images ==="
docker image prune -f

echo "=== Health check ==="
sleep 5
curl -fsS https://aiccloud.in/actuator/health || echo "Health check failed - check logs"

echo "=== Deploy complete ==="
docker compose ps