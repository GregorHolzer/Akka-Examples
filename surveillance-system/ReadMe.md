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

## Run on EC2

The current Terraform setup creates and connects the following instances:

| Number | Instance                  | Applications hosted                               | 
|:-------|:--------------------------|:--------------------------------------------------|
| 1      | Cloud-Service-Instance    | Cloud-Service                                     |
| 1      | Edge-Service-Instance     | Edge-Service                                      |
| 1      | Akka-Seed-Instance        | ActorSystem (Seed Node) <br/>  Local Edge-Service |
| 1      | Akka-Worker-Instance      | ActorSystem <br/>  Local Edge-Service             |

Within the Akka-Cluster there exists:
* `Surveillance` Actor with id *surveillance01* at the Akka-Worker-Instance
* `Detector` Actor with id *detector01* at the Akka-Seed-Instance
* `Detector` Actor with id *detector02* at the Akka-Seed-Instance

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

You may connect via SSH to the created Instances with the created *surveillance-system-key.pem*.

Inspect the Akka applications on EC2:

```bash
    sudo docker logs akka-node
```

### Extending the Example

The number of Instances with Akka-Cluster Members can simply be changed within *./terraform/akka-instances.tf*:

```terraform
#Workers
resource "aws_instance" "Akka-Worker" {
  depends_on = [null_resource.wait_for_cloud_service, null_resource.wait_for_iot_service]

  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.medium"
  key_name               = aws_key_pair.surveillance-system-node.key_name
  vpc_security_group_ids = [aws_security_group.Surveillance-Default.id]

  count = <number-of-instances>

  user_data = templatefile("${path.module}/scripts/worker.sh.tpl", {
    seed_node_ip = aws_instance.Akka-Seed-Node.private_ip
    config_json = templatefile("${path.module}/configs/node${count.index + 1}.json.tpl", {
      cloud_service_ip = aws_instance.Cloud-Service.public_ip
      iot_service_ip = aws_instance.IoT-Service.public_ip
    })
  })

  tags = {
    Name = "Akka-Worker-${count.index}"
  }
```

For every instance Terraform expects a config.json within *./terraform/configs*:

*node0.json.tpl*: for Seed-Instance
*node<n>.json.tpl*: for nth Worker Instance
