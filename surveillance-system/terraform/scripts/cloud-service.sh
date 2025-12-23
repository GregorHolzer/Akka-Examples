#!/bin/bash

echo "Installing Docker..."
sudo dnf install docker -y
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker fedora

echo "Starting Cloud-Service..."
sudo docker run -d \
  --name cloud-service \
  --restart always \
  -p 8003:8003 \
  gregor2323/akka-surveillance-system-cloud-service:latest