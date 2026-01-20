import sys
import matplotlib.pyplot as plt
import pandas as pd
from datetime import datetime, timedelta

def plot_scatter(x, y, xlabel, ylabel, title, output_file):
    """Create and save a scatter plot."""
    plt.figure(figsize=(6, 4))
    plt.scatter(x, y, alpha=0.6, s=10)  # s=10 makes dots smaller
    plt.xlabel(xlabel)
    plt.ylabel(ylabel)
    plt.ylim(bottom=0)
    plt.title(title)
    plt.grid(True)
    plt.gca().invert_xaxis()  # Invert x-axis
    plt.tight_layout()
    plt.savefig(output_file)
    plt.close()
    print(f"Saved plot: {output_file}")

from datetime import datetime, timezone
import pandas as pd

def parse_iso_utc(ts):
    if pd.isna(ts):
        return None

    ts = str(ts)

    # Handle Zulu time
    if ts.endswith("Z"):
        ts = ts[:-1]

    # Trim nanoseconds → microseconds
    if "." in ts:
        date_part, frac = ts.split(".")
        ts = f"{date_part}.{frac[:6]}"

    dt = datetime.fromisoformat(ts)

    # FORCE UTC awareness
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)

    return dt


def get_duration(start_time, end_time):
    start = parse_iso_utc(start_time)
    end = parse_iso_utc(end_time)

    if start is None or end is None:
        return None

    return (end - start).total_seconds() * 1000


def gather_data(inputfile):
    values = pd.read_csv(inputfile)
    values_cols = values[[
        'trace_id', 'calc_train_arrival', 'interval', 'published_time', 'type', 'start_time', 'end_time',

    ]].copy()

    # Latency lists
    x_axis_light, y_axis_light = [], []
    x_axis_gate, y_axis_gate = [], []
    x_axis_bell, y_axis_bell = [], []

    missed_gates = {}
    missed_lights =  {}
    missed_bells = {}


    for _, row in values_cols.iterrows():
        if pd.isna([row['calc_train_arrival'], row['interval'], row['published_time'], row['type'], row['start_time'], row['end_time']]).any():
            continue

        match row['type']:
            case "gateUpInvocation" | "gateDownInvocation":
                x_axis_gate.append(row['interval'])
                y_axis_gate.append(get_duration(row['published_time'], row['end_time']))
            case "lightOnInvocation" | "lightOffInvocation":
                x_axis_light.append(row['interval'])
                y_axis_light.append(get_duration(row['published_time'], row['end_time']))
            case "bellOnInvocation" | "bellOffInvocation":
                x_axis_bell.append(row['interval'])
                y_axis_bell.append(get_duration(row['published_time'], row['end_time']))
            case "gateDownAction":
                if get_duration(row['end_time'], row['calc_train_arrival']) < 0:
                    missed_gates[row['interval']] = missed_gates.get(row['interval'], 0) + 1
            case "lightOnAction":
                if get_duration(row['end_time'], row['calc_train_arrival']) < 0:
                    missed_lights[row['interval']] = missed_lights.get(row['interval'], 0) + 1
            case "bellOnAction":
                if get_duration(row['end_time'], row['calc_train_arrival']) < 0:
                    missed_bells[row['interval']] = missed_bells.get(row['interval'], 0) + 1

    plot_scatter(x_axis_gate, y_axis_gate, "Event Interval [sec]", "Response Time [ms]", "Gate Service Invocation", "gate_invocation.pdf")
    plot_scatter(x_axis_light, y_axis_light, "Event Interval [sec]", "Response Time [ms]", "Light Service Invocation", "light_invocation.pdf")
    plot_scatter(x_axis_bell, y_axis_bell, "Event Interval [sec]", "Response Time [ms]", "Bell Service Invocation", "bell_invocation.pdf")

    plot_scatter(missed_gates.keys(), missed_gates.values(), "Event Interval [sec]", "Number of missed Trains", "Missed Gates", "missed_gates.pdf")
    plot_scatter(missed_lights.keys(), missed_lights.values(), "Event Interval [sec]", "Number of missed Trains", "Missed Lights", "missed_lights.pdf")
    plot_scatter(missed_bells.keys(), missed_bells.values(), "Event Interval [sec]", "Number of missed Trains", "Missed Bells", "missed_bells.pdf")

    print("Number of Gate Invocations: " + str(len(x_axis_gate)))
    print("Number of Light Invocations: " + str(len(x_axis_light)))
    print("Number of Bell Invocations: " + str(len(x_axis_bell)))

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python your_script.py <data>")
        sys.exit(1)

    gather_data(sys.argv[1])


