#!/usr/bin/env bash
# Creates .env for docker-compose.public.yml: asks for your domain, Gemini key
# and admin password, and generates the database password and JWT secret.
set -euo pipefail
cd "$(dirname "$0")/../.."

if [ -f .env ]; then
  echo ".env already exists. Edit it directly, or delete it to start over."
  exit 1
fi

read -rp "Domain (e.g. mentalist.duckdns.org): " domain
read -rsp "Gemini API key: " gemini; echo
while true; do
  read -rsp "Admin password (at least 12 characters): " admin; echo
  [ "${#admin}" -ge 12 ] && break
  echo "Too short, try again."
done

secret() { openssl rand -base64 36 | tr -d '\n/+=' ; }

umask 077
cat > .env <<ENV
DOMAIN=${domain}
GEMINI_API_KEY=${gemini}
POSTGRES_PASSWORD=$(secret)
JWT_SECRET=$(secret)$(secret)
ADMIN_PASSWORD=${admin}
SIGNUP_ENABLED=true
MAX_USERS=1000
LIMIT_ANALYSES_PER_USER_PER_DAY=3
LIMIT_QUESTIONS_PER_USER_PER_DAY=30
LIMIT_ANALYSES_PER_DAY=60
LIMIT_QUESTIONS_PER_DAY=900
# Company imports from official filings (each downloads several large reports)
LIMIT_IMPORTS_PER_USER_PER_DAY=5
ENV
echo "Created .env (readable only by you). Next: docker compose -f docker-compose.public.yml up -d --build"
