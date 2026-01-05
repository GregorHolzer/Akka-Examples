#!/bin/bash

echo "Installing Docker..."
sudo dnf install docker -y
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ec2-user

echo "Starting Cloud-Service..."
sudo docker run -d \
  --name cloud-service \
  --restart always \
  -p 8003:8000 \
  gregor2323/akka-surveillance-system-cloud-service:latest