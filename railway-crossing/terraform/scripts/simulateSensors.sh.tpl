#!/bin/bash

echo "Installing Docker..."
sudo dnf install docker -y
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ec2-user

sleep 100

echo "Running simulate-sensors..."
docker run -d \
  --name simulate-sensors \
  --pull always \
  -e NATS_URL=${nats_ip}:4222 \
  -e OTEL_EXPORTER_OTLP_ENDPOINT=http://${telegraf_ip}:4317 \
  gregor2323/akka-railway-simple-sensors:latest