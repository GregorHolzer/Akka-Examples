#!/bin/bash

echo "Installing Docker..."
sudo dnf install docker -y
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker fedora

echo "Starting NATS container..."
sudo docker run -d \
  --name nats-server \
  --restart always \
  -p 4222:4222 \
  -p 8222:8222 \
  nats:latest