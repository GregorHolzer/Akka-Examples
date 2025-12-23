#!/bin/bash

echo "Installing Docker..."
sudo dnf install docker -y
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker fedora

echo "Starting IoT-Service..."
sudo docker run -d \
  --name iot-service \
  --restart always \
  -p 8001:8001 \
  gregor2323/akka-surveillance-system-iot-service:latest