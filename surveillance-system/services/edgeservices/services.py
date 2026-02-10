import ContextVariable_pb2

import cv2
import uvicorn

import numpy as np

from fastapi import FastAPI, Request, HTTPException, Response
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import SERVICE_NAME, Resource
from opentelemetry.trace import SpanContext, TraceFlags
from opentelemetry import trace

import json
import os
import base64
import time

app = FastAPI()

# Decide whether protobuf should be used (optional environment variable)
proto = "PROTO" not in os.environ or os.environ["PROTO"].lower() in ["true", "t", "1"]

# Create a global HOG descriptor
hog = cv2.HOGDescriptor()
hog.setSVMDetector(cv2.HOGDescriptor_getDefaultPeopleDetector())

resource = Resource(attributes={SERVICE_NAME: "surveillance-edge"})
trace_provider = TracerProvider(resource=resource)
span_exporter = OTLPSpanExporter()
span_processor = BatchSpanProcessor(span_exporter)
trace_provider.add_span_processor(span_processor)
trace.set_tracer_provider(trace_provider)

tracer = trace.get_tracer(__name__)

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

@app.post("/detect")
async def detect(request: Request):
    time_start = time.time_ns() / 1_000_000.0
    body = await request.body()
    parent_context = await get_parent_context(request)

    with tracer.start_as_current_span("detect", context=parent_context) as span:

        if proto:
            # Read the raw request body

            # Parse the protobuf message
            context_variables = ContextVariable_pb2.ContextVariables()
            context_variables.ParseFromString(body)

            # Extract the image from the protobuf message
            image_bytes = None
            for context_variable in context_variables.data:
                if context_variable.name == "image":
                    image_bytes = context_variable.value.bytes
                    break
        else:
            # Parse the request variables
            context_variables = await request.json()

            # Get the image from the request json
            if "image" in context_variables:
                image_bytes = base64.b64decode(context_variables["image"].encode("utf-8"))

        if image_bytes is None:
            raise HTTPException(status_code=400, detail="No image provided in the request")

        # Read the image
        np_arr = np.frombuffer(image_bytes, np.uint8)
        image = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)

        if image is None:
            raise HTTPException(status_code=400, detail="Failed to decode the image")

        # Perform detection
        (regions, _) = hog.detectMultiScale(
            image, winStride=(4, 4), padding=(4, 4), scale=1.05
        )

        span.set_attribute("hasDetectedPersons", len(regions) > 0)

        # For drawing detections:
        # image_dbg = image.copy()
        # colors = generate_random_colors(len(regions))
        # for color, (x, y, w, h) in zip(colors, regions):
        #    cv2.rectangle(image_dbg, (x, y), (x + w, y + h), color, 2)
        # cv2.imwrite("image_detected.jpg", image_dbg)

        # Prepare output data
        if proto:
            ctx = span.get_span_context()
            trace_id_hex = format(ctx.trace_id, '032x')
            span_id_hex = format(ctx.span_id, '032x')
            # Create response protobuf message
            response_context_variables = ContextVariable_pb2.ContextVariables()
            detections_context_variable = ContextVariable_pb2.ContextVariable(
                name="hasDetectedPersons",
                # test: always 0
                # value=ContextVariable_pb2.Value(bool=False),
                value=ContextVariable_pb2.Value(bool=len(regions) > 0),
            )
            trace_context_variable = ContextVariable_pb2.ContextVariable(
                name="traceId", value=ContextVariable_pb2.Value(string=trace_id_hex)
            )
            span_context_variable = ContextVariable_pb2.ContextVariable(
                name="spanId", value=ContextVariable_pb2.Value(string=span_id_hex)
            )
            response_context_variables.data.append(detections_context_variable)
            response_context_variables.data.append(trace_context_variable)
            response_context_variables.data.append(span_context_variable)
            # Serialize the response to protobuf format
            response = response_context_variables.SerializeToString()
            media_type = "application/x-protobuf"
        else:
            response = json.dumps({"hasDetectedPersons": len(regions) > 0})
            media_type = "application/json"

        time_end = time.time_ns() / 1_000_000.0

        with open("/tmp/time_detection.csv", "a") as log_file:
            log_file.write(f"{time_end - time_start}\n")

        # Return the protobuf response
        return Response(content=response, media_type=media_type)


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
