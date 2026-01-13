FROM python:3.11

COPY requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir -r /tmp/requirements.txt

WORKDIR /app
COPY ContextVariable_pb2.py .
COPY Event_pb2.py .
COPY generate_signals.py .

ENTRYPOINT ["python", "generate_signals.py"]