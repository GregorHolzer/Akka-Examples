# Akka Railway-Crossing within a single Kubernetes-Cluster

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

To run a single Cluster with one Railway-Crossing run:

```bash
    cd Kubernetes/
    ./create.sh
```

### Access the Railway-Crossing-Service

```bash
    kubectl port-forward svc/python-service-service 8080:8000
```

Access the status here: [Status](http://localhost:8080/status)

## Services

Services can not be discovered via a Service-Mesh but only via DNS. In this project the service-name is hardcoded:
```java
String serviceName = "python-service-service.default.svc.cluster.local";
CompletionStage<ServiceDiscovery.Resolved> result = discovery.lookup(serviceName, Duration.ofSeconds(3));
```

## Akka Multi-Datacenter Cluster

