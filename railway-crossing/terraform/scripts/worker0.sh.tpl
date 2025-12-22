#!/bin/bash

echo "Installing Java 21, Maven and Git..."
sudo dnf update -y
sudo dnf install java-21-amazon-corretto-devel maven -y
sudo dnf install git -y

echo "Configuring Environment Variables..."

TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
PUBLIC_IP=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4)

SEED_IP="${seed_node_ip}"

#force maven to use JAVA 21
JAVA_PATH="/usr/lib/jvm/java-21-amazon-corretto.x86_64"
export JAVA_HOME=$JAVA_PATH
export PATH=$JAVA_HOME/bin:$PATH

export AKKA_ARTERY_HOST=$PUBLIC_IP
export AKKA_CLUSTER_SEED_NODE="akka://railway-crossing@$SEED_IP:2551"

cd /home/ec2-user || exit

echo "Cloning repo..."
rm -rf Akka-Examples
git clone https://github.com/GregorHolzer/Akka-Examples
cd ./Akka-Examples/railway-crossing || exit

echo "Starting Maven Build with Java 21..."
mvn clean install

cat > ./config.json << EOF
{
  "crossings": [
    {
      "crossingId": "crossing0",
      "components": [
        "Controller"
      ]
    }
  ],
  "service_server_addr": "${railway_service_ip}",
  "service_server_port": 8000,
  "nats_server_addr": "${nats_ip}",
  "nats_server_port": 4222,
  "export_server_addr": "localhost",
  "export_server_port": 4317
}
EOF

mvn exec:java -Dexec.mainClass=Main \
  -Dexec.args=./config.json

echo "--------------------------------------------------"
echo "SETUP COMPLETE!"
echo "Your Local Public IP: $PUBLIC_IP"
echo "Target Seed Node IP:  $SEED_IP"
echo "Full Seed Address:    $AKKA_CLUSTER_SEED_NODE"
echo "--------------------------------------------------"