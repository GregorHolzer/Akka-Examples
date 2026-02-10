import sys
import json
import pandas as pd
from datetime import datetime, timedelta

class SpanData:
    def __init__(self, span_type, end_time, duration, interval, calc_train_arrival):
        self.span_type = span_type
        self.end_time = end_time
        self.duration = duration
        self.interval = interval
        self.calc_train_arrival = calc_train_arrival


class TraceData:
    def __init__(self):
        self.spans = {}


def extract_attributes(attribute_file, duration, output_file):

    trace_dict = {}

    attributes = pd.read_csv(attribute_file,skiprows=3)

    durations = pd.read_csv(duration,skiprows=3)

    attributes_cols = attributes[['span_id', 'trace_id', '_value', 'service.name']].copy()

    durations_cols = durations[['span_id', 'trace_id', '_time', '_value']].copy()

    def extract_interval(json_str):
        if pd.isna(json_str):
            return None
        try:
            data = json.loads(json_str)
            return data['interval']
        except:
            return json_str

    def extract_arrival(json_str):
        if pd.isna(json_str):
            return None
        try:
            data = json.loads(json_str)
            return data['arrival']
        except:
            return json_str

    def extract_service_type(json_str):
        if pd.isna(json_str):
            return None
        try:
            data = json.loads(json_str)
            return data['type']
        except:
            return json_str

    for _, row in attributes_cols.iterrows():
        trace_id = row['trace_id']
        span_id = row['span_id']
        service_name = row['service.name']
        span = None

        if service_name == "railway-consumer":
            type = extract_service_type(row['_value'])
            span = SpanData(type, None, None, None, None)
        elif service_name == "railway-simulation":
            interval = extract_interval(row['_value'])
            arrival = extract_arrival(row['_value'])
            span = SpanData("generator", None, None, interval, arrival)

        if trace_id not in trace_dict:
            data = TraceData()
            data.spans[span_id] = span
            trace_dict[trace_id] = data
        else:
            data = trace_dict[trace_id]
            data.spans[span_id] = span

    for _, row in durations_cols.iterrows():
        trace_id = row['trace_id']
        span_id = row['span_id']

        if trace_id not in trace_dict:
            print("Found duration for trace without Attributes")
        elif span_id not in trace_dict[trace_id].spans:
            print("Found duration for span without Attributes")
        else:
            trace_dict[trace_id].spans[span_id].end_time = row['_time']
            trace_dict[trace_id].spans[span_id].duration = row['_value']

    output_rows = []

    for trace_id, trace_data in trace_dict.items():

        def calc_start_time(end_time, duration):
            end_time = datetime.fromisoformat(end_time.replace('Z', '+00:00'))
            return end_time - timedelta(
                microseconds=int(duration) / 1000
            )
        
        def format_time(time):
            return datetime.fromisoformat(time.replace('Z', '+00:00'))

        interval = calc_arrival = published_time= None

        for _, span in trace_data.spans.items():
            if span.span_type == "generator":
                interval = span.interval
                calc_arrival = span.calc_train_arrival
                published_time = span.end_time

        for _, span in trace_data.spans.items():
            row = {
                "trace_id": trace_id,
                "calc_train_arrival": calc_arrival,
                "interval": interval,
                "published_time": published_time,
                "type": None,
                "start_time": None,
                "end_time": None
            }

            if not span.span_type == "generator":
                row.update({
                    "type": span.span_type,
                    "start_time": calc_start_time(span.end_time, span.duration),
                    "end_time": format_time(span.end_time)
                })
                output_rows.append(row)

    df_output = pd.DataFrame(output_rows)
    df_output.to_csv(output_file, index=False)
    print(f"Saved output to {output_file}")



if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("Usage: python your_script.py <attribute_file> <duration_file> <output_file>")
        sys.exit(1)

    attribute_file = sys.argv[1]
    duration_file = sys.argv[2]
    output_file = sys.argv[3]

    extract_attributes(attribute_file, duration_file, output_file)






