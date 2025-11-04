#!/usr/bin/env bash

# Installing Kind: https://kind.sigs.k8s.io/docs/user/quick-start/#installation

echo "🤖 Cleaning up"
kind delete clusters --all

echo "🤖 Creating clusters"
kind create cluster --config ./kubernetes/cluster_config.yaml

echo "🤖 Installing helm chart"

helm install railway-chart ./railway-chart