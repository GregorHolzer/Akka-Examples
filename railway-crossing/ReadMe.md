# Akka Railway-Crossing Use Case

<img src="railway.png" width="600" alt="Railway Diagram" />

## Configuration

The application is expecting a file path as argument to load the configuration from a JSON file.

### JSON Configuration Structure

The configuration is structured as follows:

* **crossings**: A list of Railway-Crossings relevant to this node. Each element is structured:
    * **crossingId**: The unique identifier for this Railway-Crossing.
    * **components**: A list of components to be created for the Railway-Crossing. Available components:
        * `Controller`
        * `LightMachine`
        * `Gate`
        * `Bell`
* **service_server_addr**: The host of the Railway-Service.
* **service_server_port**: The port of the Railway-Service.
* **nats_server_addr**: The host of the Nats-Server.
* **nats_server_port**: The port of the Nats-Server.
* **export_server_addr**: The host of the service that collects OpenTelemetry metrics (currently unused).
* **export_server_port**: The port of the service that collects OpenTelemetry metrics (currently unused).
### Example

```json
{
  "crossings": [
    {
      "crossingId": "crossing0",
      "components": [
        "Controller"
      ]
    },
    {
      "crossingId": "crossing1",
      "components": [
        "LightMachine",
        "Gate"
      ]
    }
  ],
  "service_server_addr": "localhost",
  "service_server_port": 8000,
  "nats_server_addr": "localhost",
  "nats_server_port": 4222,
  "export_server_addr": "localhost",
  "export_server_port": 4317
}
```

If the application is started with this configuration file it will:

- Create the *Controller* component for the crossing with id *crossing0* 
- Create the *Gate* and *LightMachine* components for the crossing with id *crossing1*
- Send service-invocations to *http://localhost:8000/<some-endpoint>*
- Connect to the Nats Server at *nats://localhost:4222*

Note that a component will only be started if all other components it depends on have been started:

- *Gate* depends on *Bell*
- *Controller* depends on *Gate* and *LightMachine*

## Running locally

### 1. Build the application

```bash
   mvn clean install
```

### 2. Run the Nats Server and the Railway Service

```bash
    docker compose up -d
```

### 3. Run the Akka-Application

The following script provides an easy way to run the application:

```bash
    ./runNode.sh -n <number-of-node> -c <path-to-config-file>
```

or:

```bash
    mvn exec:java -Dexec.mainClass=Main \
  -Dakka.remote.artery.canonical.port=<node-port> \
  -Dakka.management.http.port=<management-port> \
  -Dexec.args=<path-to-config-file>
```

Example:

Run node0:

```bash
    #assumes -n 0
    ./runNode.sh -c configs/readMe/node0.json
```

The config file *node0.json* creates the *Controller* for *crossing0*. After running the first application 
you should see the following logs:

```
# Logs of node0:

WARN actors.setup.ControllerSetup -- For class interface actors.Gate$GateCommand no instances found
WARN actors.setup.ControllerSetup -- For class interface actors.LightMachine$LightMachineCommand no instances found
# indicates that for the Controller of crossing0 no Gate and LightMachine has been found
```
Run node1:


```bash
    ./runNode.sh -n 1 -c configs/readMe/node1.json
```

The config *node1.json* creates the *LightMachine* and *Gate* for *crossing0*:

```
# Logs of node1:

INFO actors.setup.LightMachineSetup -- LightMachine registered with ServiceKey: ServiceKey[actors.LightMachine$LightMachineCommand](crossing0_LightMachine)
# the LightMachine of crossing0 has been created and registered for discovery 

WARN actors.setup.GateSetup -- For class interface actors.Bell$BellCommand no instances found
# indicates that for the Gate of crossing0 no Bell has been found

# Logs of node0:
INFO actors.setup.ControllerSetup -- For class interface actors.LightMachine$LightMachineCommand exactly one instance found
# the LightMachine component on node1 has been discovered by node0
```

Run node2:

```bash
    ./runNode.sh -n 2 -c configs/readMe/node2.json
```

The config *node2.json* will create the *Bell* of *crossing0*.

```
# Logs on node2:

INFO actors.setup.BellSetup -- Bell registered with ServiceKey: ServiceKey[actors.Bell$BellCommand](crossing0_Bell)

# Logs on node1:
INFO actors.setup.GateSetup -- For class interface actors.Bell$BellCommand exactly one instance found
INFO actors.setup.GateSetup -- Gate registered with ServiceKey: ServiceKey[actors.Gate$GateCommand](crossing0_Gate)

# Logs on node0:

INFO actors.setup.ControllerSetup -- For class interface actors.Gate$GateCommand exactly one instance found
INFO actors.setup.ControllerSetup -- crossing0_Controller subscribed to Topic: peripheral.sensor
# the Controller will receive sensor events from Nats
```

### 4. Simulate Sensor Events

After the log

```
INFO actors.setup.ControllerSetup -- <crossingId>_Controller subscribed to Topic: peripheral.sensor
```

this Controller is listening to the Nats server for sensor-events.

Launch the simulate-sensor container:

```bash
    cd services/simple_sensors
    docker compose up -d
```

The components will log their current state.


## Running on multiple EC2 instances

Create Instances with image: Amazon Linux 2023 kernel-6.1 AMI

### Adjust Security Rules for EC2 instances

Instances that host *ActorSystems*

| Port | Description | 
|:----:| ---: |
 | 2551 | Akka Remoting |

Instances that host services:

| Port | Description |
| :---: | ---: |
| 4222 | Nats |
| 8000 | Railway Service |
| 4317 | Telegraf |
| 8086 | IfluxDb |


### Setup EC2 Instances that host Services:

Install **Docker**_

```bash
  sudo dnf install docker -y
  sudo systemctl start docker
  sudo systemctl enable docker
```

* Nats

```bash
  sudo docker run -d \
  --name nats-server \
  -p 4222:4222 \
  nats
```
* InfluxDB

```bash
  sudo docker run -d \
  --name influxdb \
  -p 8086:8086 \
  -e DOCKER_INFLUXDB_INIT_MODE=setup \
  -e DOCKER_INFLUXDB_INIT_USERNAME=admin \
  -e DOCKER_INFLUXDB_INIT_PASSWORD=adminadmin \
  -e DOCKER_INFLUXDB_INIT_ORG=org \
  -e DOCKER_INFLUXDB_INIT_BUCKET=bucket \
  -e DOCKER_INFLUXDB_INIT_ADMIN_TOKEN=bzO10KmR8x \
  influxdb:2
```

* Telegraf

```bash
  sudo docker run -d \
  --name telegraf \
  -p 4317:4317 \
  -e TELEGRAF_CONFIG_CONTENTS='
[agent]
  interval = "10s"
  flush_interval = "10s"

[[inputs.opentelemetry]]

[[outputs.influxdb_v2]]
  urls = ["http://<influxdb_public_ip>:8086"]
  token = "bzO10KmR8x"
  organization = "org"
  bucket = "bucket"
' \
  telegraf:latest
```

* Railway-Service

```bash
  sudo  docker run -d \
  --name akka-railway-service \
  -p 8000:8000 \
  -e OTEL_EXPORTER_OTLP_ENDPOINT=http://<telegraf_public_ip>:4317 \
  gregor2323/akka-railway-crossing-service:latest
```

### Setup EC2 Instances that host *ActorSystems*

1. Copy `ex2-setup.sh` to the Instance
2. Run  `ex2-setup.sh` on the Instance

```bash
    #Option -s sets the Seed Node IP of the Akka Cluster, leave empty if this instance is the Seed Node 
    #or if no ActorSystem is hosted
    . ex2-setup.sh -s <public_seed_node_ip>
```

* Adjust JSON configuration file to point to correct hosts
* Run ActorSystem via:

```bash
    ./runNode -c <path_to_json_config>
```

### Start simulating Sensor Events

On the same Instance that runs the Nats-Server and Telegraf:

```bash
    (cd ./services/simple_sensors && docker compose up -d)
```



