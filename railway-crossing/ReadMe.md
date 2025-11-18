# Akka Railway-Crossing Use Case

### Test local example:

Build the project:

```bash
    mvn clean install
```

Create the Nats Server and the Railway-Crossing Service:

```bash
    docker compose up -d 
```

An Akka-Node can be launched with:

```bash
    mvn exec:java -Dexec.mainClass=Main \
  -Dakka.remote.artery.canonical.port=<port> \
  -Dakka.management.http.port=<management_port> \
  -Dexec.args=<path_to_config>
```

There is also a script to launch nodes:

```bash
    ./runNode.sh -n <number_of_node> -c <path_to_config>
```

To run two nodes with an example configuration:

```bash
    ./runNode.sh -c ./configs/node0.json
```

```bash
    ./runNode.sh -n 1 -c ./configs/node1.json
```

Publish a Sensor-Event to the Server via Nats-Cli:

```bash
    nats pub -s nats://localhost:4222 Sensor "TrainSeen"
```

```bash
    nats pub -s nats://localhost:4222 Sensor "TrainNotSeen"
```

[See the Status](http://localhost:8080/status)

### Example Config

```json
{
  "crossings": [
    {
      "crossingId": "crossing0",
      "components": [
          "Bell",
          "LightMachine",
          "Gate"
      ]
    },
    {
      "crossingId": "crossing1",
      "components": [
        "Bell",
        "LightMachine",
        "Gate"
      ]
    }
  ],
  "service_location": "Local",
  "remote_service_name": "",
  "nats_server_addr": "localhost",
  "nats_server_port": "4222"
}
```

If this config is passed to an Actor-System this system will launch:
- For Crossing with id `crossing0`:
    - `Bell`
    - `LightMachine`
    - `Bell`
- For Crossing with id `crossing1`:
    - `Bell`
    - `LightMachine`
    - `Bell`

