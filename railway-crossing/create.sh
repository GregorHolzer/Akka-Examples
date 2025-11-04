#!/usr/bin/env bash

# Installing subctl: https://submariner.io/operations/deployment/
# Installing Kind: https://kind.sigs.k8s.io/docs/user/quick-start/#installation

# Clean up
echo "🤖 Cleaning up"
kind delete clusters --all

# Create two clusters
echo "🤖 Creating clusters"
kind create cluster --config ./kubernetes/cluster_config.yaml

echo "🤖 Installing helm chart"

helm install railway-chart ./railway-chart
#echo "🤖 Apply deployment to Cluster"
#kubectl apply -f ./permissions.yaml --context kind-akka
#kubectl apply -f ./railway_crossing_node.yaml --context kind-akka
#kubectl apply -f ./railway_crossing_service.yaml --context kind-akka