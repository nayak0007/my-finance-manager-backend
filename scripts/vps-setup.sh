#!/bin/bash
# VPS Initial Setup Script for aiccloud.in
# Run as root on a fresh Ubuntu 22.04/24.04 VPS

set -euo pipefail

echo "=== My Finance Manager VPS Setup ==="

# Update system
apt-get update && apt-get upgrade -y

# Install Docker
apt-get install -y ca-certificates curl gnupg lsb-release
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
apt-get update && apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Create app directory
mkdir -p /opt/my-finance-manager
cd /opt/my-finance-manager

# Create docker-compose.yml (copy from repo or use the one from CI)
cat > docker-compose.yml << 'EOF'
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: myfinance-postgres
    environment:
      POSTGRES_DB: myfinance
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d myfinance"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - myfinance-network
    restart: unless-stopped

  backend:
    image: ghcr.io/${GITHUB_REPOSITORY_OWNER}/${GITHUB_REPOSITORY}/backend:latest
    container_name: myfinance-backend
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/myfinance
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      DATABASE_URL: postgresql://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres:5432/myfinance
      DB_POOL_SIZE: "10"
      NEON_AUTH_URL: ${NEON_AUTH_URL}
      OPENROUTER_API_KEY: ${OPENROUTER_API_KEY}
      OPENROUTER_BASE_URL: ${OPENROUTER_BASE_URL}
      OPENROUTER_MODEL: ${OPENROUTER_MODEL}
      CORS_ALLOWED_ORIGINS: "https://aiccloud.in,https://aiccloud.in/,http://localhost:3000,http://localhost:8081,exp://192.168.*.*:8081"
      STORAGE_LOCATION: /app/data/uploads
    volumes:
      - uploads_data:/app/data/uploads
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - myfinance-network
    restart: unless-stopped

  nginx:
    image: nginx:alpine
    container_name: myfinance-nginx
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/conf.d:/etc/nginx/conf.d:ro
      - ./certbot/conf:/etc/letsencrypt:ro
      - ./certbot/www:/var/www/certbot:ro
    depends_on:
      - backend
    networks:
      - myfinance-network
    restart: unless-stopped

  certbot:
    image: certbot/certbot
    container_name: myfinance-certbot
    volumes:
      - ./certbot/conf:/etc/letsencrypt
      - ./certbot/www:/var/www/certbot
    entrypoint: "/bin/sh -c 'trap exit TERM; while :; do certbot renew; sleep 12h & wait $${!}; done'"
    networks:
      - myfinance-network

volumes:
  postgres_data:
  uploads_data:

networks:
  myfinance-network:
    driver: bridge
EOF

# Create nginx config
mkdir -p nginx/conf.d certbot/conf certbot/www
cat > nginx/conf.d/default.conf << 'EOF'
server {
    listen 80;
    server_name aiccloud.in;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl http2;
    server_name aiccloud.in;

    ssl_certificate /etc/letsencrypt/live/aiccloud.in/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/aiccloud.in/privkey.pem;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    client_max_body_size 20M;

    location / {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }
}
EOF

# Create .env from example
if [ ! -f .env ]; then
    echo "Create .env file with your secrets:"
    echo "POSTGRES_USER=myfinance"
    echo "POSTGRES_PASSWORD=<strong-password>"
    echo "NEON_AUTH_URL=https://..."
    echo "OPENROUTER_API_KEY=..."
    echo "OPENROUTER_BASE_URL=https://openrouter.ai/api/v1"
    echo "OPENROUTER_MODEL=openai/gpt-4o-mini"
    echo "CORS_ALLOWED_ORIGINS=https://aiccloud.in,..."
    exit 1
fi

# Obtain initial SSL certificate
echo "=== Obtaining initial SSL certificate ==="
docker compose run --rm certbot certonly --webroot -w /var/www/certbot -d aiccloud.in --email admin@aiccloud.in --agree-tos --no-eff-email

# Start services
docker compose up -d

echo "=== Setup Complete ==="
echo "Backend available at https://aiccloud.in"
echo "Check logs: docker compose logs -f"