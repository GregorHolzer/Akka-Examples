#!/usr/bin/env bash

# Installing subctl: https://submariner.io/operations/deployment/
# Installing Kind: https://kind.sigs.k8s.io/docs/user/quick-start/#installation

# Clean up
echo "🤖 Cleaning up"
kind delete clusters --all

# Create two clusters
echo "🤖 Creating clusters"
kind create cluster --config ./cluster_config.yaml

echo "🤖 Apply deployment to Cluster"
kubectl apply -f ./permissions.yaml --context kind-akka
kubectl apply -f ./akka-node.yaml --context kind-akka