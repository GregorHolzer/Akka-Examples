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

chown ec2-user:ec2-user /home/ec2-user/telegraf.conf

echo "Installing Docker..."
dnf install docker -y
systemctl start docker
systemctl enable docker
usermod -aG docker ec2-user
chmod 666 /var/run/docker.sock

echo "Creating Docker network..."
docker network create open_telemetry || true

echo "Starting InfluxDB..."
docker run -d \
  --name influxdb \
  --network open_telemetry \
  -p 8086:8086 \
  -e DOCKER_INFLUXDB_INIT_MODE=setup \
  -e DOCKER_INFLUXDB_INIT_USERNAME=admin \
  -e DOCKER_INFLUXDB_INIT_PASSWORD=adminadmin \
  -e DOCKER_INFLUXDB_INIT_ORG=org \
  -e DOCKER_INFLUXDB_INIT_BUCKET=bucket \
  -e DOCKER_INFLUXDB_INIT_ADMIN_TOKEN=bzO10KmR8x \
  influxdb:2

echo "Starting Telegraf..."
docker run -d \
  --name telegraf \
  --network open_telemetry \
  -p 4317:4317 \
  -v /home/ec2-user/telegraf.conf:/etc/telegraf/telegraf.conf:ro \
  telegraf:latest
