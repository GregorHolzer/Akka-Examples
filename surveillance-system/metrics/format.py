import sys
import pandas as pd
from datetime import datetime, timedelta

class SpanData:
    def __init__(self, span_type, end_time, duration):
        self.span_type = span_type
        self.end_time = end_time
        self.duration = duration


class TraceData:
    def __init__(self):
        self.spans = {}


def calc_start_time(end_time, duration):
    end_time = datetime.fromisoformat(end_time.replace('Z', '+00:00'))
    return end_time - timedelta(microseconds=int(duration) / 1000)

def extract_attributes(kind_path, duration_path, output_path):

    trace_dict = {}

    durations = pd.read_csv(duration_path, skiprows=3)

    kinds = pd.read_csv(kind_path, skiprows=3)

    kind_cols = kinds[['trace_id', 'span_id', '_value']].copy()

    dur_cols = durations[['trace_id', 'span_id', '_value', '_time']].copy()

    for _, row in kind_cols.iterrows():
        trace_id = row['trace_id']
        span_id = row['span_id']

        span = SpanData(row['_value'], None, None)

        if trace_id not in trace_dict:
            data = TraceData()
            data.spans[span_id] = span
            trace_dict[trace_id] = data
        else:
            data = trace_dict[trace_id]
            data.spans[span_id] = span

    for _, row in dur_cols.iterrows():
        trace_id = row['trace_id']
        span_id = row['span_id']

        if trace_id in trace_dict and span_id in trace_dict[trace_id].spans:
            span_data = trace_dict[trace_id].spans[span_id]
            span_data.end_time = row['_time']
            span_data.duration = row['_value']
            span_data.start_time = calc_start_time(span_data.end_time, span_data.duration)

    output_rows = []

    for trace_id, trace_data in trace_dict.items():

        def format_time(time):
            return datetime.fromisoformat(time.replace('Z', '+00:00'))

        pictureCaptured = None

        for _, span in trace_data.spans.items():
            if span.span_type == "cameraCapture":
                pictureCaptured = span.end_time
                break

        if pictureCaptured is None:
            print("No picture captured")
            continue

        for _, span in trace_data.spans.items():
            row = {
                "trace_id": trace_id,
                "pictureCaptured": format_time(pictureCaptured),
                "type":  span.span_type,
                "start_time": span.start_time,
                "end_time": span.end_time
            }

            if not span.span_type == "cameraCapture":
                output_rows.append(row)

    df_output = pd.DataFrame(output_rows)
    df_output.to_csv(output_path, index=False)
    print(f"Saved output to {output_path}")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("Usage: python script.py <kind_file> <duration_file> <output_file>")
        sys.exit(1)

    extract_attributes(sys.argv[1], sys.argv[2], sys.argv[3])