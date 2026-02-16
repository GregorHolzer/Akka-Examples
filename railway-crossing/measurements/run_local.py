import sys
import subprocess
from datetime import datetime
from influxdb_client import InfluxDBClient
from pathlib import Path
import time
import json
from format import extract_attributes
import csv


URL = "http://localhost:8086"
TOKEN = "bzO10KmR8x"
ORG = "org"
BUCKET = "bucket"

name_query = f"""
from(bucket: "{BUCKET}")
  |> range(start: -24h) 
  |> filter(fn: (r) => r["_measurement"] == "spans")
  |> filter(fn: (r) => r["_field"] == "span.name")
  |> filter(fn: (r) => r["service.name"] == "railway-consumer" or r["service.name"] == "railway-simulation")    
"""

duration_query = f"""
from(bucket: "{BUCKET}")
  |> range(start: -24h)
  |> filter(fn: (r) => r["_measurement"] == "spans")
  |> filter(fn: (r) => r["_field"] == "duration_nano")
  |> filter(fn: (r) => r["service.name"] == "railway-consumer" or r["service.name"] == "railway-simulation")
"""

attribute_query = """
from(bucket: "bucket")
  |> range(start: -24h)
  |> filter(fn: (r) => r["_measurement"] == "spans")
  |> filter(fn: (r) => r["_field"] == "attributes")
  |> filter(fn: (r) => r["service.name"] == "railway-consumer" or r["service.name"] == "railway-simulation")
"""

base_compose_file = f"""configs:
  telegraf_config:
    file: ./telegraf.conf
    
services:
  influxdb:
    image: "influxdb:2"
    ports:
      - "8086:8086"
    environment:
      - DOCKER_INFLUXDB_INIT_MODE=setup
      - DOCKER_INFLUXDB_INIT_USERNAME=admin
      - DOCKER_INFLUXDB_INIT_PASSWORD=adminadmin
      - DOCKER_INFLUXDB_INIT_ORG=org
      - DOCKER_INFLUXDB_INIT_BUCKET=bucket
      - DOCKER_INFLUXDB_INIT_ADMIN_TOKEN=bzO10KmR8x
      
  telegraf:
    image: "telegraf:latest"
    ports:
      - "4317:4317"
    configs:
      - source: telegraf_config
        target: /etc/telegraf/telegraf.conf

  railway-crossing-services:
    build:
      context: ./services/services
      dockerfile: ./Dockerfile
    environment:
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://telegraf:4317
      
"""

class RailwayComponent:
    def __init__(self, comp_type: str, crossing: str):
        self.type = comp_type
        self.crossing = crossing

class CirrinaRuntime:
    def __init__(self):
        self.components = {}

    def print_components(self):
        for c_idx, _ in self.components.items():
            print(f"\t{c_idx}:")
            for component in self.components[c_idx]:
                print(f"\t\t{component.type}")

def get_runtimes(n_runtimes: int, n_crossings: int) -> list[CirrinaRuntime]:
    types = ["controller", "light", "gate", "bell"]

    runtimes = [CirrinaRuntime() for _ in range(n_runtimes)]

    round_robin_counter = 0

    for c_idx in range(n_crossings):
        crossing_id = f"crossing_{c_idx}"

        for t in types:
            runtime_target = runtimes[round_robin_counter % n_runtimes]

            new_comp = RailwayComponent(t, crossing_id)

            if crossing_id not in runtime_target.components:
                runtime_target.components[crossing_id] = []

            runtime_target.components[crossing_id].append(new_comp)

            round_robin_counter = round_robin_counter + 1
    print("================================")
    print("Runtime Configuration:")
    for idx, runtime in enumerate(runtimes):
        print(f"Runtime {idx}:")
        runtime.print_components()
    print("================================")
    return runtimes

def write_config_file(runtimes: list[CirrinaRuntime]):
    print("Writing config file...")

    component_map = {
        "controller": "Controller",
        "light": "LightMachine",
        "gate": "Gate",
        "bell": "Bell"
    }

    crossings = {}
    for idx, runtime in enumerate(runtimes):
        runtime_crossings = []
        for crossing_id, components in runtime.components.items():
            crossing_obj = {
                "crossingId": crossing_id,
                "components": [component_map[comp.type] for comp in components]
            }
            runtime_crossings.append(crossing_obj)

        crossings[str(idx)] = runtime_crossings

    config = {
        "crossings": crossings,
        "service_server_addr": "railway-crossing-services",
        "service_server_port": 8000,
        "nats_server_addr": "nats-server",
        "nats_server_port": 4222,
        "export_server_addr": "telegraf",
        "export_server_port": 4317
    }
    with open("./config.json", "w") as file:
        json.dump(config, file, indent=2)

    print("Config file written to ./src/config.json")

def get_compose_simulation_string(min_sensor_val: float, max_sensor_val: float, train_speed: float, duration: int) -> str:
    return f"""  simulate-sensors:
    build:
      context: ./services/evaluation_generator
      dockerfile: ./Dockerfile
    environment:
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://telegraf:4317
      - MIN_EVENTS_PER_SEC={min_sensor_val}
      - MAX_EVENTS_PER_SEC={max_sensor_val}
      - MIN_TRAIN_SPEED_MS={train_speed}
      - MAX_TRAIN_SPEED_MS={train_speed}
      - DURATION_IN_SECONDS={duration}
      - START_DELAY=45
      - PYTHONUNBUFFERED=1
    """

def get_compose_cirrina_string(runtime_id: int) -> str:
    if runtime_id == 0:
        return f"""
  akka-seed-node:
    image: gregor2323/akka-railway-crossing-node:latest
    pull_policy: never
    environment:
      - NODE_ID=0
      - AKKA_ARTERY_HOST=akka-seed-node
      - AKKA_CLUSTER_SEED_NODE=akka://railway-crossing@akka-seed-node:2551
    volumes:
      - ./measurements/config.json:/config.json
    command:
      - /config.json
"""

    return f"""  akka-worker-{runtime_id}:
    image: gregor2323/akka-railway-crossing-node:latest
    pull_policy: never
    environment:
      - NODE_ID={runtime_id}
      - AKKA_ARTERY_HOST=akka-worker-{runtime_id}
      - AKKA_CLUSTER_SEED_NODE=akka://railway-crossing@akka-seed-node:2551
    volumes:
      - ./measurements/config.json:/config.json
    command:
      - /config.json
"""

def write_compose_file(runtimes: list[CirrinaRuntime], min_sensor_val: float, max_sensor_val: float, train_speed: float, duration: int):
    print("Writing docker compose file...")
    with open("../compose.yaml", "w") as file:
        file.write(base_compose_file)
        file.write(get_compose_simulation_string(min_sensor_val, max_sensor_val, train_speed, duration))
        for idx, _ in enumerate(runtimes):
            file.write(get_compose_cirrina_string(idx))

def get_data(result_folder: str):
    client = InfluxDBClient(url=URL, token=TOKEN, org=ORG)

    query_api = client.query_api()

    names = query_api.query_csv(query=name_query, org=ORG)

    attributes = query_api.query_csv(query=attribute_query, org=ORG)

    durations = query_api.query_csv(query=duration_query, org=ORG)

    with open(result_folder + "/name.csv", "w") as file:
        writer = csv.writer(file)
        for line in names:
            if line:
                writer.writerow(line)

    with open(result_folder + "/attributes.csv", "w") as file:
        writer = csv.writer(file)
        for line in attributes:
            if line:
                writer.writerow(line)

    with open(result_folder + "/durations.csv", "w") as file:
        writer = csv.writer(file)
        for line in durations:
            if line:
                writer.writerow(line)

    client.close()

if __name__ == "__main__":
    if len(sys.argv) != 7:
        print("Usage: python run_local.py <number_of_runtimes> <number_of_crossings> <min_event_rate> <max_event_rate> <train_velocity> <duration>")
        exit(1)

    n_runtimes = int(sys.argv[1])
    n_crossings = int(sys.argv[2])
    min_event_rate = float(sys.argv[3])
    max_event_rate = float(sys.argv[4])
    train_velocity = float(sys.argv[5])
    duration = int(sys.argv[6])

    runtimes = get_runtimes(n_runtimes, n_crossings)

    write_config_file(runtimes)

    write_compose_file(runtimes, min_event_rate, max_event_rate, train_velocity, duration)
    print("Starting experiment...")
    try:
        subprocess.run(["docker", "compose", "up", "-d"])
        print("Containers launched successfully 🚀")
    except subprocess.CalledProcessError as e:
        print("Failed to launch Containers 💥 Return Code:", e.returncode)
        exit(1)

    time.sleep(duration + 60)

    now = datetime.now().strftime("%m_%d_%H_%M")

    result_folder = (
        f"./local/runtimes_{n_runtimes}_crossings_{n_crossings}_"
        f"min_event_{str(min_event_rate).replace('.', '_')}_"
        f"max_event_{str(max_event_rate).replace('.', '_')}_"
        f"velocity_{str(train_velocity).replace('.', '_')}_"
        f"duration_{duration}/{now}"
    )

    Path(result_folder).mkdir(parents=True, exist_ok=True)

    print(f"Saving results at: {result_folder}")

    print("Gathering data...")
    get_data(result_folder)
    extract_attributes(result_folder + "/attributes.csv", result_folder + "/durations.csv", result_folder + "/formatted.csv")
    print("Shutting down...")
    try:
        subprocess.run(["docker", "compose", "down", "-v"])
        print("Containers shut down successfully!")
    except subprocess.CalledProcessError as e:
        print("Failed to stop Containers, Return Code:", e.returncode)
        exit(1)

