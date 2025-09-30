#!/usr/bin/env bash

# Installing subctl: https://submariner.io/operations/deployment/
# Installing Kind: https://kind.sigs.k8s.io/docs/user/quick-start/#installation

# Clean up
echo "🤖 Cleaning up"
kind delete clusters --all

# Create two clusters
echo "🤖 Creating clusters"
kind create cluster --config config/iot-config.yaml
kind create cluster --config config/edge-config.yaml
kind create cluster --config config/cloud-config.yaml

# Deploy broker
echo "🤖 Deploying broker (Submariner)"
subctl --context kind-cloud deploy-broker --globalnet

# Label gateways
echo "🤖 Labeling gateways (Submariner)"
kubectl --context kind-iot label nodes iot-control-plane "submariner.io/gateway=true" --overwrite
kubectl --context kind-edge label nodes edge-control-plane "submariner.io/gateway=true" --overwrite
kubectl --context kind-cloud label nodes cloud-control-plane "submariner.io/gateway=true" --overwrite

# Join clusters
echo "🤖 Joining clusters (Submariner)"
subctl --context kind-iot join broker-info.subm --clusterid iot --label-gateway=false --natt=true
subctl --context kind-edge join broker-info.subm --clusterid edge --label-gateway=false --natt=true
subctl --context kind-cloud join broker-info.subm --clusterid cloud --label-gateway=false --natt=true