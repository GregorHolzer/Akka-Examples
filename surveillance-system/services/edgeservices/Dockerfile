FROM python:3.11-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY services.py ContextVariable_pb2.py Event_pb2.py ./

ENV PROTO=true

CMD ["uvicorn", "services:app", "--host", "0.0.0.0", "--workers", "33", "--port", "8000"]
