import ContextVariable_pb2

import cv2
import uvicorn

import numpy as np

from fastapi import FastAPI, HTTPException, Request, Response
from google.protobuf.json_format import MessageToDict
from starlette.status import HTTP_200_OK
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import SERVICE_NAME, Resource
from opentelemetry.trace import SpanContext, TraceFlags
from opentelemetry import trace

import base64
import json
import hashlib
import os
import time

FPS = 30

ROOT_DIR = os.path.dirname(os.path.abspath(__file__))

VIDEOS_CAPTURES = {
    0: cv2.VideoCapture(os.path.join(ROOT_DIR, "resources", "1.avi")),
    1: cv2.VideoCapture(os.path.join(ROOT_DIR, "resources", "2.avi")),
    2: cv2.VideoCapture(os.path.join(ROOT_DIR, "resources", "3.avi")),
    3: cv2.VideoCapture(os.path.join(ROOT_DIR, "resources", "4.avi")),
    4: cv2.VideoCapture(os.path.join(ROOT_DIR, "resources", "5.avi")),
}

app = FastAPI()

# Decide whether protobuf should be used (optional environment variable)
proto = "PROTO" not in os.environ or os.environ["PROTO"].lower() in ["true", "t", "1"]

resource = Resource(attributes={SERVICE_NAME: "surveillance-iot"})
trace_provider = TracerProvider(resource=resource)
span_exporter = OTLPSpanExporter()
span_processor = BatchSpanProcessor(span_exporter)
trace_provider.add_span_processor(span_processor)
trace.set_tracer_provider(trace_provider)

tracer = trace.get_tracer(__name__)


# Get the current frame number
def get_frame_number() -> int:
    return round(int(time.time()) * FPS)


# For logging detection times
def log_hash(data: bytes):
    # Compute a consistent hash
    sha256 = hashlib.sha256()
    sha256.update(data)
    hash = sha256.hexdigest()

    # Acquire the current timestamp in milliseconds
    timestamp = time.time_ns() / 1_000_000.0
    log_entry = f"{hash},{timestamp}\n"

    # Append to log file
    with open("/tmp/log_send.csv", "a") as log_file:
        log_file.write(log_entry)

async def get_parent_context(request: Request):
    body = await request.body()
    context_variables = ContextVariable_pb2.ContextVariables()
    context_variables.ParseFromString(body)
    traceId = spanId = None
    for context_variable in context_variables.data:
        if context_variable.name == "traceId":
            traceId = context_variable.value.string
        if context_variable.name == "spanId":
            spanId = context_variable.value.string

    if traceId and spanId:
        try:
            trace_id_int = int(traceId, 16)
            span_id_int = int(spanId, 16)

            span_ctx = SpanContext(
                trace_id=trace_id_int,
                span_id=span_id_int,
                is_remote=True,
                trace_flags=TraceFlags(0x01),
            )
            parent_context = trace.set_span_in_context(trace.NonRecordingSpan(span_ctx))
            return parent_context
        except Exception as e:
            print("Failed to extract span: " + e.message)
    return None

@app.post("/alarm/on")
async def alarm_on(request: Request):
    parent_context = await get_parent_context(request)
    with tracer.start_as_current_span("alarmOn", context=parent_context):
        return Response(status_code=HTTP_200_OK)


@app.post("/alarm/off")
async def alarm_on():
    return Response(status_code=HTTP_200_OK)


@app.post("/capture")
async def capture(request: Request):
    with tracer.start_as_current_span("cameraCapture") as span:
        video_number = None

        # Attempt to read the video number
        if proto:
            # Read the raw request body
            body = await request.body()

            # Parse the protobuf message
            context_variables = ContextVariable_pb2.ContextVariables()
            context_variables.ParseFromString(body)

            # Extract specific values
            for context_variable in context_variables.data:
                context_variable_dict = MessageToDict(context_variable)

                # Check for cameraId and delay
                if context_variable_dict["name"] == "cameraId":
                    video_number = context_variable_dict["value"].get("integer")
        else:
            # Parse the request variables
            context_variables = await request.json()

            # Get context variables from the request json
            if "cameraId" in context_variables:
                video_number = int(context_variables["cameraId"])

        if video_number not in VIDEOS_CAPTURES:
            raise HTTPException(
                status_code=400, detail=f"Invalid video_number: {video_number}"
            )

        # Acquire the video frame
        cap = VIDEOS_CAPTURES[video_number]

        if not cap.isOpened():
            print(f"Failed to open video {video_number}")
            raise HTTPException(
                status_code=400, detail=f"Failed to open video {video_number}"
            )

        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        frame_number = get_frame_number() % total_frames
        cap.set(cv2.CAP_PROP_POS_FRAMES, frame_number)

        ret, frame = cap.read()
        if not ret:
            raise HTTPException(status_code=400, detail="Unable to capture frame")

        frame = cv2.resize(frame, (640, 480))

        # Introduce entropy to make every frame unique
        random_values = (np.random.rand(1, frame.shape[1], frame.shape[2]) * 256).astype(
            np.uint8
        )

        frame[:1, :, :] = random_values

        # JPEG compression
        jpeg_params = [int(cv2.IMWRITE_JPEG_QUALITY), 80]

        _, buffer = cv2.imencode(".jpg", frame, jpeg_params)

        buffer_bytes = buffer.tobytes()

        # Prepare output data
        if proto:
            ctx = span.get_span_context()
            trace_id_hex = format(ctx.trace_id, '032x')
            span_id_hex = format(ctx.span_id, '032x')
            # Create response protobuf message
            response_context_variables = ContextVariable_pb2.ContextVariables()
            image_context_variable = ContextVariable_pb2.ContextVariable(
                name="image", value=ContextVariable_pb2.Value(bytes=buffer_bytes)
            )
            trace_context_variable = ContextVariable_pb2.ContextVariable(
                name="traceId", value=ContextVariable_pb2.Value(string=trace_id_hex)
            )
            span_context_variable = ContextVariable_pb2.ContextVariable(
                name="spanId", value=ContextVariable_pb2.Value(string=span_id_hex)
            )
            response_context_variables.data.append(image_context_variable)
            response_context_variables.data.append(trace_context_variable)
            response_context_variables.data.append(span_context_variable)
            # Serialize the response to protobuf format
            response = response_context_variables.SerializeToString()
            media_type = "application/x-protobuf"
        else:
            buffer_base64 = base64.b64encode(buffer_bytes).decode("utf-8")

            response = json.dumps({"image": buffer_base64})
            media_type = "application/json"

        log_hash(buffer_bytes)

        # Return the protobuf response
        return Response(content=response, media_type=media_type)


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
