#!/bin/bash

echo "Installing Docker..."
sudo dnf install docker -y
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker fedora

docker run -d \
  --name akka-railway-service \
  --pull always \
  -p 8000:8000 \
  -e OTEL_EXPORTER_OTLP_ENDPOINT=http://${telegraf_ip}:4317 \
  gregor2323/akka-railway-crossing-service:latest