#!/bin/bash

# Write telegraf.conf
cat > /home/ec2-user/telegraf.conf << 'EOF'
[[inputs.opentelemetry]]
[[outputs.influxdb_v2]]
  urls = ["http://influxdb:8086"]
  token = "bzO10KmR8x"
  organization = "org"
  bucket = "bucket"
EOF

# Write docker-compose.yaml
cat > /home/ec2-user/docker-compose.yaml << 'EOF'
configs:
  telegraf_config:
    file: ./telegraf.conf

services:
  influxdb:
    image: "influxdb:2"
    ports:
      - "8086:8086"
    environment:
      - DOCKER_INFLUXDB_INIT_MODE=setup
      - DOCKER_INFLUXDB_INIT_USERNAME=admin
      - DOCKER_INFLUXDB_INIT_PASSWORD=adminadmin
      - DOCKER_INFLUXDB_INIT_ORG=org
      - DOCKER_INFLUXDB_INIT_BUCKET=bucket
      - DOCKER_INFLUXDB_INIT_ADMIN_TOKEN=bzO10KmR8x
    networks:
      - open_telemetry

  telegraf:
    image: "telegraf:latest"
    ports:
      - "4317:4317"
    configs:
      - source: telegraf_config
        target: /etc/telegraf/telegraf.conf
    networks:
      - open_telemetry

networks:
  open_telemetry:
    name: open_telemetry
    driver: bridge
EOF

# Set ownership
chown ec2-user:ec2-user /home/ec2-user/telegraf.conf
chown ec2-user:ec2-user /home/ec2-user/docker-compose.yaml

# Install Docker
echo "Installing Docker..."
dnf install docker -y
systemctl start docker
systemctl enable docker
usermod -aG docker ec2-user
chmod 666 /var/run/docker.sock

# Install Docker Compose
echo "Installing Docker Compose..."
mkdir -p /usr/libexec/docker/cli-plugins
curl -SL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" -o /usr/libexec/docker/cli-plugins/docker-compose
chmod +x /usr/libexec/docker/cli-plugins/docker-compose

# Launch Stack
echo "Launching Stack..."
cd /home/ec2-user
docker compose -f docker-compose.yaml up -d