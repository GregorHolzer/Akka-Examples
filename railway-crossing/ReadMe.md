# Akka Railway-Crossing within a single Akka Cluster

## Usage

Each Crossing is identified by a `crossingId` and consists of four Components: 

- `Bell`
- `Gate`
- `LightMachine`
- `Controller`

To create a component for a crossing the following variables have to be set:

- `CROSSING_ID=<id>`
- `COMPONENT_TYPE=<type>`

A component will only be created if all subcomponents are ready (e.g. a Gate will only be created if the Bell is ready).

### Build Docker-Image

```bash
 mvn -Ddocker.useConfigFile=true -Ddocker.config.path=/home/gregor/.docker/config.json package  docker:push
```

### Running in Kubernetes

To form an Akka-Cluster within Kubernetes the following Permissions need to be granted:

```yaml
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: pod-reader
rules:
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get", "watch", "list"]
```
## Akka Edge - Approach for Multi-Cluster-Setup

Akka Edge is based on `Akka  Projection gRPC` 



