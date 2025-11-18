FROM python:3.11

COPY requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir -r /tmp/requirements.txt

WORKDIR /app
COPY ContextVariable_pb2.py .
COPY Event_pb2.py .
COPY simulate_sensors.py .

ENV START_INTERVAL_IN_SECONDS=0.1
ENV END_INTERVAL_IN_SECONDS=0.01
ENV NATS_URL=nats://railway-crossing-nats-server-1:4222/
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://railway-crossing-telegraf-1:4317/

ENTRYPOINT ["python", "simulate_sensors.py"]