FROM python:3.11

COPY requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir -r /tmp/requirements.txt

WORKDIR /app
COPY services.py .
#Changed workers to 1 to prevent inconsistent lists
CMD ["uvicorn", "services:app", "--host", "0.0.0.0", "--workers", "1", "--port", "8000"]