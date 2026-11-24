#!/bin/bash
# Sets up a fresh Ubuntu 24.04 server so that the whole Tatkal app is running on it.
#
# The easy way to use it: when you launch the EC2 instance, paste this entire file into
#   Advanced details > User data
# and the server configures itself on first boot. Nothing else to type.
# Progress is written to /var/log/cloud-init-output.log (look there if the site does not appear).
#
# It can also be run by hand on an existing server:  sudo bash deploy/bootstrap.sh

set -euxo pipefail
export DEBIAN_FRONTEND=noninteractive

REPO_URL="https://github.com/rkaran112/tatkal.git"
APP_DIR="/opt/tatkal"

# 1. Swap space: building the Java and React images needs more memory than a small server has.
if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# 2. Install Docker, Docker Compose and git.
apt-get update -y
apt-get install -y docker.io docker-compose-v2 git curl
systemctl enable --now docker

# 3. Get the code (the repository is public, so no password is needed).
if [ ! -d "$APP_DIR" ]; then
  git clone "$REPO_URL" "$APP_DIR"
fi
cd "$APP_DIR"
git pull --ff-only

# 4. Build and start everything. Containers restart by themselves after a reboot.
docker compose up -d --build

# 5. Wait until the backend answers.
for i in $(seq 1 60); do
  if curl -sf http://localhost:8080/actuator/health > /dev/null; then
    break
  fi
  sleep 5
done

# 6. It is a public demo: visitors will book every seat, so free all seats again every 15 minutes.
#    (/api/admin is reachable from this server only; nginx hides it from the internet.)
cat > /etc/cron.d/tatkal-reset <<'CRON'
*/15 * * * * root curl -s -X POST http://localhost:8080/api/admin/reset > /dev/null 2>&1
CRON
chmod 644 /etc/cron.d/tatkal-reset

# Start from a clean slate right now.
curl -s -X POST http://localhost:8080/api/admin/reset > /dev/null

echo "Tatkal is up. Open http://<this server's public IP>"
