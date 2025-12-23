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

cd ./Akka-Examples/surveillance-system/services/iotservices || exit

echo 'Building IoT Service Image...'
sudo docker build -t akka-surveillance-system-iot-service:latest -f Dockerfile .


echo "Starting IoT-Service..."
sudo docker run -d \
  --name iot-service \
  --restart always \
  -p 8001:8000 \
  akka-surveillance-system-iot-service:latest