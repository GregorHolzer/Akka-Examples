#!/bin/bash

echo "Installing Docker..."
sudo dnf install docker -y
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ec2-user

TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" \
      -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
PRIVATE_IP=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" \
      http://169.254.169.254/latest/meta-data/local-ipv4)

cd /home/ec2-user/|| exit

cat > ./config.json << EOF
    ${config_json}
EOF

docker run -d \
  --name akka-node \
  --network host \
  -v /home/ec2-user/config.json:/app/config.json \
  -e AKKA_ARTERY_HOST=$PRIVATE_IP \
  -e AKKA_CLUSTER_SEED_NODE=akka://railway-crossing@$PRIVATE_IP:2551 \
  gregor2323/akka-railway-crossing-node:latest \
  /app/config.json