#!/bin/bash

OPTIND=1
SEED_IP=""
while getopts "s:" opt; do
  case $opt in
    s) SEED_IP="$OPTARG" ;;
    \?) echo "Usage: source setup.sh [-s seed_node_ip]" >&2; return 1 ;;
  esac
done

echo "Installing Java 21, Maven and Git..."
sudo dnf update -y
sudo dnf install java-21-amazon-corretto-devel maven -y
sudo dnf install git -y

echo "Installing Docker..."
sudo dnf install docker -y
sudo systemctl start docker
sudo systemctl enable docker

echo "Installing Docker Compose..."
sudo mkdir -p /usr/libexec/docker/cli-plugins
sudo curl -SL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" -o /usr/libexec/docker/cli-plugins/docker-compose
sudo chmod +x /usr/libexec/docker/cli-plugins/docker-compose
sudo chmod 666 /var/run/docker.sock

echo "Configuring Environment Variables..."

TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
PUBLIC_IP=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4)

FINAL_SEED_IP=${SEED_IP:-$PUBLIC_IP}

#force maven to use JAVA 21
JAVA_PATH="/usr/lib/jvm/java-21-amazon-corretto.x86_64"
export JAVA_HOME=$JAVA_PATH
export PATH=$JAVA_HOME/bin:$PATH

export AKKA_ARTERY_HOST=$PUBLIC_IP
export AKKA_CLUSTER_SEED_NODE="akka://railway-crossing@$FINAL_SEED_IP:2551"

echo "Cloning repo..."
rm -rf Akka-Examples
git clone https://github.com/GregorHolzer/Akka-Examples
cd Akka-Examples/railway-crossing || exit

echo "Starting Maven Build with Java 21..."
mvn clean install

echo "--------------------------------------------------"
echo "SETUP COMPLETE!"
echo "Your Local Public IP: $PUBLIC_IP"
echo "Target Seed Node IP:  $FINAL_SEED_IP"
echo "Full Seed Address:    $AKKA_CLUSTER_SEED_NODE"
echo "--------------------------------------------------"