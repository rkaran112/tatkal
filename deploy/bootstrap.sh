#!/bin/bash
# Sets up a fresh Ubuntu 24.04 server (a 1 GB t3.micro is enough) so that Tatkal is running on it.
#
# The easy way to use it: when you launch the EC2 instance, paste this entire file into
#   Advanced details > User data
# and the server configures itself on first boot. Nothing else to type.
# Progress is written to /var/log/cloud-init-output.log (look there if the site does not appear).
#
# The server does NOT build anything. GitHub Actions builds the images, and this script only
# downloads them, which is what keeps a 1 GB server (and the bill) small.

set -euxo pipefail
export DEBIAN_FRONTEND=noninteractive

COMPOSE_URL="https://raw.githubusercontent.com/rkaran112/tatkal/main/docker-compose.prod.yml"
APP_DIR="/opt/tatkal"

# Cost guard: the server stops ITSELF after this many days, so forgetting about it cannot use up
# your AWS credits. (Stopped servers cost almost nothing; start it again from the console.)
LIFETIME_DAYS=170

# 1. Swap space: a 1 GB server needs a little breathing room.
if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# 2. Install Docker and Docker Compose.
apt-get update -y
apt-get install -y docker.io docker-compose-v2 curl
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'JSON'
{ "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "3" } }
JSON
systemctl enable docker
systemctl restart docker

# 3. Download the compose file and the prebuilt images, then start everything.
#    Containers restart by themselves after a reboot.
mkdir -p "$APP_DIR"
cd "$APP_DIR"
curl -fsSL "$COMPOSE_URL" -o docker-compose.yml
docker compose pull
docker compose up -d

# 4. Wait until the backend answers.
for i in $(seq 1 60); do
  if curl -sf http://localhost:8080/actuator/health > /dev/null; then
    break
  fi
  sleep 5
done

# 5. It is a public demo: visitors will book every seat, so free all seats again every 15 minutes.
#    (/api/admin is reachable from this server only; nginx hides it from the internet.)
cat > /etc/cron.d/tatkal-reset <<'CRON'
*/15 * * * * root curl -s -X POST http://localhost:8080/api/admin/reset > /dev/null 2>&1
CRON
chmod 644 /etc/cron.d/tatkal-reset
curl -s -X POST http://localhost:8080/api/admin/reset > /dev/null

# 6. Cost guard: remember when to stop, and check every 10 minutes.
if [ ! -f "$APP_DIR/stop-after" ]; then
  echo $(( $(date +%s) + LIFETIME_DAYS * 86400 )) > "$APP_DIR/stop-after"
fi
cat > /etc/cron.d/tatkal-selfstop <<'CRON'
*/10 * * * * root [ "$(date +\%s)" -gt "$(cat /opt/tatkal/stop-after)" ] && /sbin/shutdown -h now
CRON
chmod 644 /etc/cron.d/tatkal-selfstop

echo "Tatkal is up. Open http://<this server's public IP>. It stops itself in $LIFETIME_DAYS days."
