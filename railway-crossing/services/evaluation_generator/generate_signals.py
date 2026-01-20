import os
import time
import asyncio
import uuid

import Event_pb2
import nats
from datetime import datetime, timezone

from opentelemetry import metrics
from opentelemetry import trace
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter
from opentelemetry.sdk.resources import SERVICE_NAME, Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter

MIN_SENSOR_INTERVAL = float(os.environ.get("MIN_SENSOR_INTERVAL", "1"))
MAX_SENSOR_INTERVAL = float(os.environ.get("MAX_SENSOR_INTERVAL", "1"))

MIN_TRAIN_SPEED_MS = float(os.environ.get("MIN_TRAIN_SPEED_MS", "1"))
MAX_TRAIN_SPEED_MS = float(os.environ.get("MAX_TRAIN_SPEED_MS", "1"))

START_DELAY = float(os.environ.get("START_DELAY", "60"))

SENSOR_POSITIONS = [500.0, 1000.0, 1500.0]

DURATION_IN_SECONDS = float(os.environ["DURATION_IN_SECONDS"])

TRAIN_LEN = 150

resource = Resource(attributes={SERVICE_NAME: "railway-simulation"})
metric_exporter = OTLPMetricExporter()
metric_reader = PeriodicExportingMetricReader(metric_exporter)
provider = MeterProvider(resource=resource, metric_readers=[metric_reader])
metrics.set_meter_provider(provider)

trace_provider = TracerProvider(resource=resource)
span_exporter = OTLPSpanExporter()
span_processor = BatchSpanProcessor(span_exporter)
trace_provider.add_span_processor(span_processor)
trace.set_tracer_provider(trace_provider)
tracer = trace.get_tracer(__name__)

class Train:
    def __init__(self, train_len, train_speed, sensor_positions):
        self.train_len = train_len
        self.train_speed = train_speed
        self.sensor_positions = sensor_positions
        self.start_time = time.time()
        #Represents if the train got checked in each position
        self.sensor_neg_flanks = 0
        self.last_sensor = False
        self.expected_arrival = datetime.fromtimestamp(self.start_time + self.sensor_positions[1] / self.train_speed)

    def check_sensor(self):
        passed_time = time.time() - self.start_time
        train_front = self.train_speed * passed_time
        train_back = self.train_speed * passed_time - self.train_len
        sensor = False
        for position in self.sensor_positions:
            if train_back <= position <= train_front:
                sensor = True
                break
        if self.last_sensor and not sensor:
            self.sensor_neg_flanks += 1
        self.last_sensor = sensor
        return sensor

    def on_track(self):
        passed_time = time.time() - self.start_time
        train_back = self.train_speed * passed_time - self.train_len
        return train_back < 2000

class Generator:
    def __init__(self, nc, duration):
        self.num_trains = 0
        self.nc = nc
        self.duration = duration
        self._start_time = time.time()
        self.train = None

        self._event_template = Event_pb2.Event()
        self._event_template.name = "sensor"
        self._event_template.channel = Event_pb2.Event.PERIPHERAL

        self._speed_var = self._event_template.data.add()
        self._speed_var.name = "trainSpeed"

        self._bool_var = self._event_template.data.add()
        self._bool_var.name = "value"

        self._trace_id_var = self._event_template.data.add()
        self._trace_id_var.name = "traceId"
        self._span_id_var = self._event_template.data.add()
        self._span_id_var.name = "spanId"

    def compute_broadcast_interval(self):
        elapsed_time = time.time() - self._start_time

        # Linear interpolation
        return MAX_SENSOR_INTERVAL - (
            MAX_SENSOR_INTERVAL - MIN_SENSOR_INTERVAL
        ) * (elapsed_time / DURATION_IN_SECONDS)

    def compute_train_speed(self):
        elapsed_time = time.time() - self._start_time

        # Linear interpolation
        return MIN_TRAIN_SPEED_MS + (
            MAX_TRAIN_SPEED_MS -MIN_TRAIN_SPEED_MS
        ) * (elapsed_time / DURATION_IN_SECONDS)



    async def publish_event(self, s_value, current_interval, current_speed, expected_arrival):
        self._bool_var.value.bool = s_value
        self._event_template.createdTime = time.time_ns() / 1.0e6
        self._event_template.id = str(uuid.uuid4())

        with tracer.start_as_current_span("broadcast_sensor") as span:
            ctx = span.get_span_context()
            self._trace_id_var.value.string = trace.format_trace_id(ctx.trace_id)
            self._span_id_var.value.string = trace.format_span_id(ctx.span_id)
            span.set_attribute("interval", current_interval)
            span.set_attribute("arrival", expected_arrival.isoformat())
            span.set_attribute("speed", current_speed)
            await self.nc.publish("peripheral.sensor", self._event_template.SerializeToString())


    async def run(self):
        while time.time() - self._start_time < self.duration:
            #Remove old Train
            if self.train is not None and not self.train.on_track():
                print("Removing Train")
                if self.train.sensor_neg_flanks == 3:
                    print("Train was measured correctly!")
                else:
                    print("Train was not measured correctly!")

                self.train = None

            if self.train is None:
                train_speed = self.compute_train_speed()
                print("Creating Train with speed: " + str(train_speed))
                self._speed_var.value.double = train_speed
                self.num_trains = 1 + self.num_trains
                self.train = Train(TRAIN_LEN, train_speed, SENSOR_POSITIONS)

            broadcast_interval = self.compute_broadcast_interval()
            await self.publish_event(self.train.check_sensor(), broadcast_interval, self.train.train_speed, self.train.expected_arrival)
            await asyncio.sleep(broadcast_interval)

async def main():
    await asyncio.sleep(START_DELAY)
    nats_url = os.environ.get("NATS_URL")
    try:
        nc = await nats.connect(nats_url)
    except Exception as e:
        print(f"Error connecting to NATS: {e}")
        return

    generator = Generator(nc, DURATION_IN_SECONDS)
    start = time.time()
    await generator.run()
    end = time.time()

    print(f"Done. Actual Duration: {end - start:.4f} seconds")
    print(f"Number of Trains: {generator.num_trains}")

    await nc.drain()

if __name__ == "__main__":
    asyncio.run(main())








