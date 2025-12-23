#!/bin/bash

echo "Installing Docker..."
sudo dnf install docker -y
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker fedora

#Temp solution for dockerhub issue
echo "Installing Git..."
sudo dnf install git -y

cd /home/ec2-user || exit
rm -rf Akka-Examples
git clone https://github.com/GregorHolzer/Akka-Examples

cd ./Akka-Examples/surveillance-system/services/cloudservices || exit

echo 'Building Cloud Service Image...'
sudo docker build -t akka-surveillance-system-cloud-service:latest -f Dockerfile .


echo "Starting Cloud-Service..."
sudo docker run -d \
  --name cloud-service \
  --restart always \
  -p 8001:8000 \
  akka-surveillance-system-cloud-service:latest