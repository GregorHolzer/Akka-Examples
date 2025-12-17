# Akka Railway-Crossing Use Case

## Configuration

The application is expecting a file path as argument to load the configuration form a JSON file.

### JSON Configuration Structure

The is to be structured as follows:

- `crossings` - A list of Railway-Crossings that are relevant to this node. Each list element is structured:

    - `crossingId` - The unique identifier for this Railway-Crossing
    - `components` - A list of Components that will be created for the Railway-Crossing. Available Components:
    
        - `Controller`
        - `LightMachine`
        - `Gate`
        - `Bell`
- `service_server_addr` - The host of the Railway-Service
- `service_server_port` - The port of the Railway-Service
- `nats_server_addr` - The host of the Nats-Server
- `nats_server_port` - The port of the Nats-Server
- `export_server_addr` - The host of the Service that collects the OpenTelemetry metrics (currently not used)
- `export_server_port` - The port of the Service that collects the OpenTelemetry metrics (currently not used)

## Local Example

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
