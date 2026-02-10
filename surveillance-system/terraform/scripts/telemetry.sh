#!/bin/bash

echo "Installing Docker..."
sudo dnf install docker -y
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ec2-user

cat <<EOF > /home/ec2-user/telegraf.conf
[[inputs.opentelemetry]]
[[outputs.influxdb_v2]]
  urls = ["http://influxdb:8086"]
  token = "bzO10KmR8xcpm"
  organization = "org"
  bucket = "bucket"
EOF

sudo docker network create telemetry

echo "Starting InfluxDB..."
sudo docker run -d \
  --name influxdb \
  --network telemetry \
  -p 8086:8086 \
  -e DOCKER_INFLUXDB_INIT_MODE=setup \
  -e DOCKER_INFLUXDB_INIT_USERNAME=admin \
  -e DOCKER_INFLUXDB_INIT_PASSWORD=adminadmin \
  -e DOCKER_INFLUXDB_INIT_ORG=org \
  -e DOCKER_INFLUXDB_INIT_BUCKET=bucket \
  -e DOCKER_INFLUXDB_INIT_ADMIN_TOKEN=bzO10KmR8xcpm \
  influxdb:2

echo "Starting Telegraf..."
sudo docker run -d \
  --name telegraf \
  --network telemetry \
  -p 4317:4317 \
  -v /home/ec2-user/telegraf.conf:/etc/telegraf/telegraf.conf:ro \
  telegraf:latest