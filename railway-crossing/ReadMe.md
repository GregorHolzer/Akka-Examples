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


## Running on multiple EC2 instances with Terraform

The current Terraform setup creates and connects the following instances:

| Number | Instance                  | Applications hosted     | 
|:-------|:--------------------------|:------------------------|
| 1      | Nats-Instance             | Nats                    |
| 1      | OpenTelemetry-Instance    | Telegraf & InfluxDB     |
| 1      | Railway-Service-Instance  | Railway-Service         |
| 1      | Akka-Seed-Instance        | ActorSystem (Seed Node) |
| 2      | Akka-Worker-Instance      | ActorSystem             |
| 1      | Simulate-Sensors-Instance | Simulate-Sensors        |                  

Within the Akka-Cluster there exists one Crossing with Id *crossing0*:
* `Controller` at Akka-Seed-Instance
* `LightMachine` and `Gate` at Akka-Worker-Instance-1
* `Bell` at Akka-Worker-Instance-1

### Deploy this Example

1. Install and configure **AWS CLI**
2. Install **Terraform**
3. Initialize **Terraform**:
   ```bash
    (cd ./terraform && terraform init)
    ```
4. Apply **Terraform**:
    ```bash
    (cd ./terraform && terraform apply -auto-approve)
    ```

You may connect via SSH to the created Instances with the created *railway-crossing-key.pem*.

### InfluxDB

* Username: admin
* Password: adminadmin

Terraform will output the public dns of the created Instances. You may connect to the InfluxDB to receive metrics:

```
<openTelemetry_public_dns>:8086
```

### Changing the Example

* Instances: You may change the number of Akka-Instances within the *./terraform/akka-instances.tf* file. Each instance needs its own configuration file that should be created within *./terraform/configs*.
* Crossings and Components: Can be changed by changing the configurations within *./terraform/configs*.
* Instances that host a `Controller` Component of a crossing should receive a readyCheck:
```
# Ensures that the Controller of <crossingId> is ready and subscribed to NATS
resource "null_resource" "wait_for_crossing0" {
  depends_on = [aws_instance.<Instance>]

  provisioner "remote-exec" {
    inline = [
      "until grep -q '<crossingId>_Controller subscribed to Topic: peripheral.sensor' /var/log/cloud-init-output.log; do sleep 5; done",
      "echo '<crossingId> subscribed to Nats'"
    ]

    connection {
      type        = "ssh"
      user        = "ec2-user"
      private_key = tls_private_key.railway-crossing-key.private_key_pem
      host        = aws_instance.<Instance>
    }
  }
}
```
And add this as dependency for the **Simple-Sensors** Instance:

```
resource "aws_instance" "Simulate_Sensors" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = aws_key_pair.railway-crossing-node.key_name
  vpc_security_group_ids = [aws_security_group.Railway-Default.id]

  #All ready Check Resources
  depends_on = [null_resource.wait_for_crossing0]

  user_data = templatefile("${path.module}/scripts/simulateSensors.sh.tpl", {
    nats_ip = aws_instance.Nats-Server.public_ip
    telegraf_ip = aws_instance.OpenTelemetry.public_ip
  })

  tags = {
    Name = "Simulate-Sensors"
  }
}
```


