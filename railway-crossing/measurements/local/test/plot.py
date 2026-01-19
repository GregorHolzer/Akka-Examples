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
        'trace_id', 'calc_train_arrival', 'interval', 'published_time',
        'gate_invocation_type', 'gate_invocation_start', 'gate_invocation_end', 'gate_action_type', 'gate_action_start', 'gate_action_end',
        'light_invocation_type', 'light_invocation_start', 'light_invocation_end', 'light_action_type', 'light_action_start', 'light_action_end',
        'bell_invocation_type', 'bell_invocation_start', 'bell_invocation_end', 'bell_action_type', 'bell_action_start', 'bell_action_end'
    ]].copy()

    # Latency lists
    x_axis_light, y_axis_light = [], []
    x_axis_gate, y_axis_gate = [], []
    x_axis_bell, y_axis_bell = [], []

    # Safety/Missed deadline lists
    x_axis_missed_gates, y_axis_missed_gates = [], []
    x_axis_missed_lights, y_axis_missed_lights = [], []
    x_axis_missed_bells, y_axis_missed_bells = [], []

    for _, row in values_cols.iterrows():
        if pd.isna(row['published_time']):
            continue

        # --- GATE LOGIC ---
        if pd.notna(row['gate_invocation_end']):
            x_axis_gate.append(row['interval'])
            y_axis_gate.append(get_duration(row['published_time'], row['gate_invocation_end']))
            if pd.notna(row['gate_action_end']):
                x_axis_missed_gates.append(row['interval'])
                y_axis_missed_gates.append(get_duration(row['gate_action_end'], row['calc_train_arrival']) < 0)

        # --- LIGHT LOGIC ---
        if pd.notna(row['light_invocation_end']):
            x_axis_light.append(row['interval'])
            y_axis_light.append(get_duration(row['published_time'], row['light_invocation_end']))
            if pd.notna(row['light_action_end']):
                x_axis_missed_lights.append(row['interval'])
                y_axis_missed_lights.append(get_duration(row['light_action_end'], row['calc_train_arrival']) < 0)

        # --- BELL LOGIC ---
        if pd.notna(row['bell_invocation_end']):
            x_axis_bell.append(row['interval'])
            y_axis_bell.append(get_duration(row['published_time'], row['bell_invocation_end']))
            if pd.notna(row['bell_action_end']):
                x_axis_missed_bells.append(row['interval'])
                y_axis_missed_bells.append(get_duration(row['bell_action_end'], row['calc_train_arrival']) < 0)

    # --- INVOCATION PLOTS ---
    plot_scatter(x_axis_gate, y_axis_gate, "Event Interval [sec]", "Response Time [ms]", "Gate Service Invocation", "gate_invocation.pdf")
    plot_scatter(x_axis_light, y_axis_light, "Event Interval [sec]", "Response Time [ms]", "Light Service Invocation", "light_invocation.pdf")
    plot_scatter(x_axis_bell, y_axis_bell, "Event Interval [sec]", "Response Time [ms]", "Bell Service Invocation", "bell_invocation.pdf")

    # --- MISSED ACTION PLOTS ---
    plot_scatter(x_axis_missed_gates, y_axis_missed_gates, "Event Interval [sec]", "Missed Deadline (Boolean)", "Missed Gates Safety Check", "missed_gates.pdf")
    plot_scatter(x_axis_missed_lights, y_axis_missed_lights, "Event Interval [sec]", "Missed Deadline (Boolean)", "Missed Lights Safety Check", "missed_lights.pdf")
    plot_scatter(x_axis_missed_bells, y_axis_missed_bells, "Event Interval [sec]", "Missed Deadline (Boolean)", "Missed Bells Safety Check", "missed_bells.pdf")

    print("Number of Gate Invocations: " + str(len(x_axis_gate)))
    print("Number of Light Invocations: " + str(len(x_axis_light)))
    print("Number of Bell Invocations: " + str(len(x_axis_bell)))

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python your_script.py <data>")
        sys.exit(1)

    gather_data(sys.argv[1])


