#!/usr/bin/env bash
# One-command setup of Mentalist on a fresh Ubuntu server (Oracle Cloud Always
# Free or Hetzner). Run it on the server, logged in as your normal user:
#
#   bash <(curl -fsSL https://raw.githubusercontent.com/Shreya4119/mentalist/main/deploy/public/setup-server.sh)
#
# It opens the web ports, adds swap on small servers, installs Docker and git,
# downloads Mentalist, asks for your domain / Gemini key / admin password,
# then builds and starts everything. Safe to run again: finished steps are skipped.
set -euo pipefail

REPO="${REPO:-https://github.com/Shreya4119/mentalist.git}"
DIR="${DIR:-$HOME/mentalist}"
COMPOSE_FILE="docker-compose.public.yml"

say() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m!!  %s\033[0m\n' "$*"; }

if [ "$(id -u)" -eq 0 ]; then
  warn "Run this as your normal user (e.g. ubuntu), not root. It uses sudo where needed."
  exit 1
fi
if ! grep -qi ubuntu /etc/os-release 2>/dev/null; then
  warn "This script is written for Ubuntu. Other systems may need manual steps (see deploy/PUBLIC_SERVER.md)."
fi
sudo -v

say "1/6 Opening ports 80 and 443"
# Oracle's Ubuntu images reject incoming traffic with an iptables rule; add
# ACCEPT rules just above it. Other providers usually need nothing here.
reject_line=$(sudo iptables -L INPUT --line-numbers -n | awk '/REJECT/ {print $1; exit}' || true)
if [ -n "${reject_line}" ]; then
  for port in 80 443; do
    if ! sudo iptables -C INPUT -m state --state NEW -p tcp --dport "$port" -j ACCEPT 2>/dev/null; then
      sudo iptables -I INPUT "$reject_line" -m state --state NEW -p tcp --dport "$port" -j ACCEPT
    fi
  done
  if command -v netfilter-persistent >/dev/null; then
    sudo netfilter-persistent save >/dev/null
  fi
  echo "Firewall rules added. Also open TCP 80 and 443 in your cloud provider's security list / firewall."
else
  echo "No blocking firewall rule found. Make sure ports 80 and 443 are open in your provider's firewall."
fi

say "2/6 Checking memory"
mem_mb=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)
if [ "$mem_mb" -lt 6000 ] && ! swapon --show | grep -q .; then
  echo "${mem_mb} MB RAM: adding a 4 GB swap file so the build doesn't run out of memory."
  sudo fallocate -l 4G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile >/dev/null
  sudo swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
else
  echo "${mem_mb} MB RAM: fine."
fi

say "3/6 Installing Docker and git"
if ! command -v git >/dev/null; then
  sudo apt-get update -qq && sudo apt-get install -y -qq git
fi
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sudo sh
fi
sudo usermod -aG docker "$USER"
echo "Docker $(sudo docker --version | awk '{print $3}' | tr -d ,) ready."

say "4/6 Downloading Mentalist"
if [ -d "$DIR/.git" ]; then
  git -C "$DIR" pull --ff-only
else
  git clone "$REPO" "$DIR"
fi
cd "$DIR"

say "5/6 Settings (.env)"
if [ -f .env ]; then
  echo ".env already exists, keeping it."
else
  ./deploy/public/make-env.sh
fi
domain=$(grep -E '^DOMAIN=' .env | cut -d= -f2-)

# Let's Encrypt can only issue the HTTPS certificate if the domain points here.
public_ip=$(curl -fsS --max-time 5 https://api.ipify.org || true)
domain_ip=$(getent ahostsv4 "$domain" | awk 'NR==1 {print $1}' || true)
if [ -n "$public_ip" ] && [ "$public_ip" != "$domain_ip" ]; then
  warn "$domain points to '${domain_ip:-nothing}', but this server's IP is $public_ip."
  warn "Update your DNS (e.g. on duckdns.org). HTTPS starts working once it matches."
fi

say "6/6 Building and starting (first time: 10-15 minutes)"
sudo docker compose -f "$COMPOSE_FILE" up -d --build

echo "Waiting for the backend to start..."
for _ in $(seq 1 120); do
  if sudo docker compose -f "$COMPOSE_FILE" logs backend 2>/dev/null | grep -q "Started BackendApplication"; then
    say "Mentalist is live: https://$domain"
    echo "Sign in as 'admin' with your admin password, then upload a few public annual reports as samples."
    echo "Logs:    cd $DIR && docker compose -f $COMPOSE_FILE logs -f backend"
    echo "Update:  cd $DIR && git pull && docker compose -f $COMPOSE_FILE up -d --build"
    echo "(Log out and back in once so you can use 'docker' without sudo.)"
    exit 0
  fi
  if sudo docker compose -f "$COMPOSE_FILE" logs backend 2>/dev/null | grep -q "APPLICATION FAILED TO START\|development settings"; then
    warn "The backend stopped. Last lines of its log:"
    sudo docker compose -f "$COMPOSE_FILE" logs --tail 40 backend
    exit 1
  fi
  sleep 10
done
warn "The backend hasn't finished starting after 20 minutes. Check: sudo docker compose -f $COMPOSE_FILE logs backend"
exit 1
