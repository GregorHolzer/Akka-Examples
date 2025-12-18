# Akka Surveillance-System Use Case

<img src="surveillance.png" width="500" alt="Surveillance Diagram" />

## Configuration

The application is expecting a file path as argument to load the configuration form a JSON file.

### JSON Configuration Structure

* *detectorsConfigs* - A list of all *Detector* components that will be started on this node:
  * *detectorId* - The unique identifier for this *Detector* component
  * *cameraId* - An identifier that is passed to the IoT-Service to capture an image from a camera
  * *surveillanceId* - The identifier of the *Surveillance* component that will do further image processing
* *surveillanceConfigs* - A list of all *Surveillance* components that will be started on this node:
  * *surveillanceId* - The unique identifier for this *Surveillance* component
* *cloud_service_addr* - The host of the Cloud-Service.
* *cloud_service_port* - The port of the Cloud-Service.
* *edge_service_addr* - The host of the Edge-Service.
* *edge_service_port* - The port of the Edge-Service.
* *iot_service_addr* - The host of the IoT-Service.
* *iot_service_port* - The port of the IoT-Service.

### Example

```json
{
  "detectorsConfigs": [
    {
      "detectorId": "detector01",
      "cameraId": 1,
      "surveillanceId": "surveillance01"
    },
    {
      "detectorId": "detector02",
      "cameraId": 2,
      "surveillanceId": "surveillance01"
    }
  ],
  "surveillanceConfigs": [
    {
      "surveillanceId": "surveillance01"
    }
  ],
  "cloud_service_addr": "localhost",
  "cloud_service_port": 8003,
  "edge_service_addr": "localhost",
  "edge_service_port": 8002,
  "iot_service_addr": "localhost",
  "iot_service_port": 8001
}
```

If the application is started with this configuration file it will:

- Create two *Detector* components that will forward their processed images to the *Surveillance* component with id *surveillance01*
- Create one *Surveillance* component with id *surveillance01*
- Send Cloud-Service-Invocations to *http://localhost:8003/<some-endpoint>*
- Send Edge-Service-Invocations to *http://localhost:8002/<some-endpoint>*
- Send IoT-Service-Invocations to *http://localhost:8001/<some-endpoint>*

Note that a component will only be started if all other components it depends on have been started:

- *Detector* depends on *Surveillance*

## Running locally

### 1. Build the application

```bash
   mvn clean install
```

### 2. Run the Services

```bash
    (cd ./services && docker compose up)
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
    ./runNode.sh -c configs/node0.json
```

The config file *node0.json* creates two *Detector* components that depend on the *Surveillance* component with id *surveillance01*. 
After running the first node you should see the following logs:

```
# Logs of node0:

08:34:18.109 [surveillance-system-akka.actor.default-dispatcher-14] WARN actors.DetectorSetup -- No Surveillance Actor with surveillanceId surveillance01 found
08:34:18.109 [surveillance-system-akka.actor.default-dispatcher-13] WARN actors.DetectorSetup -- No Surveillance Actor with surveillanceId surveillance01 found
# indicates that both Detectors could not discover the Surveillance Actor with id surveillance01
```
Run node1:


```bash
    ./runNode.sh -n 1 -c configs/node1.json
```

The config *node1.json* creates the *Surveillance* component with id *surveillance01*:

```
# Logs of node0:

08:37:02.354 [surveillance-system-akka.actor.default-dispatcher-4] INFO actors.DetectorSetup -- Registered detector with id detector02
08:37:02.354 [surveillance-system-akka.actor.default-dispatcher-18] INFO actors.DetectorSetup -- Registered detector with id detector01
# Detectors found the Surveillance component and started
```

The components will log their current state.
