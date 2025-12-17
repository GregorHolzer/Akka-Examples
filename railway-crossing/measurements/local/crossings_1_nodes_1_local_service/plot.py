import pandas as pd
import sys
import json
import matplotlib.pyplot as plt
import os

def process_trace_data(input_file, output_file=None):
    df = pd.read_csv(input_file)
    df_selected = df[['service.name', '_value', 'trace_id', '_time']].copy()

    trace_dict = {}

    for _, row in df_selected.iterrows():
        trace_id = row['trace_id']
        service_name = row['service.name']
        value = row['_value']
        time = row['_time']

        if trace_id not in trace_dict:
            trace_dict[trace_id] = {
                'trace_id': trace_id,
                'service.name': None,
                'event_rate': None,
                'number_of_generated_trains': None,
                'start_time': None,
                'end_time': None,
                'total_time': None,
                'num_of_service_invocations': None
            }
        if service_name == 'railway-simulation':
            trace_dict[trace_id]['service.name'] = service_name
            trace_dict[trace_id]['event_rate'] = value
            trace_dict[trace_id]['number_of_generated_trains'] = value
            trace_dict[trace_id]['start_time'] = time
        elif service_name == 'railway-consumer':
            trace_dict[trace_id]['end_time'] = time
        if trace_dict[trace_id]['start_time'] is not None and trace_dict[trace_id]['end_time'] is not None:
            start_time = pd.to_datetime(trace_dict[trace_id]['start_time'])
            end_time = pd.to_datetime(trace_dict[trace_id]['end_time'])
            trace_dict[trace_id]['total_time'] = (end_time - start_time).total_seconds() * 1000

    for trace_id in list(trace_dict.keys()):
        if trace_dict[trace_id]['total_time'] is None:
            trace_dict.pop(trace_id)

    sorted_trace_list = sorted(
        trace_dict.values(),
        key=lambda x: (
            pd.to_datetime(x['start_time'], errors="coerce")
            .tz_localize(None) if x['start_time'] is not None else pd.Timestamp.min
        )
    )
    sum = 0
    for pos, trace in enumerate(sorted_trace_list):
        trace['num_of_service_invocations'] = pos + 1
        sum += trace['total_time']

    print("Average response time: " + str(sum / len(sorted_trace_list)))

    result_df = pd.DataFrame(sorted_trace_list)

    result_df = result_df[result_df['total_time'].notna()]

    def extract_event_rate(json_str):
        if pd.isna(json_str):
            return None
        try:
            data = json.loads(json_str)
            return data['interval']
        except:
            return json_str


    result_df['event_rate'] = result_df['event_rate'].apply(extract_event_rate)

    result_df = result_df[['trace_id', 'event_rate', 'start_time', 'end_time', 'total_time']]


    if output_file:
        result_df.to_csv(output_file, index=False)

    return result_df


def plot_event_rate_to_total_time(input_file, output_file=None):
    df = pd.read_csv(input_file)

    fig, (ax1) = plt.subplots(1, 1, figsize=(20, 8))

    ax1.scatter(df['event_rate'], df['total_time'], alpha=0.6, label='Data points')


    ax1.set_xlabel('Events per Second')
    ax1.set_ylabel('Response Time (ms)')
    ax1.set_title('Response Time')
    ax1.grid(True, alpha=0.3)
    ax1.set_ylim(0, df['total_time'].max() + 50)
    ax1.set_xlim(0, df['event_rate'].max() + 10)

    title = "AKKA Railway UseCase, Events from " + str(df['event_rate'].min()) + " to " + str(df['event_rate'].max()) + " per second"
    fig.suptitle(title, fontsize=16, fontweight='bold')

    plt.tight_layout()

    plt.savefig(output_file, bbox_inches='tight')
    print(f"Plot saved to {output_file}")

    plt.close()


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python script.py <input_file>")
        print("Example: python plot.py traces.csv")
        sys.exit(1)

    input_file = sys.argv[1]
    base = os.path.splitext(input_file)[0]
    output_file = base + "_processed.csv"

    process_trace_data(input_file, output_file)
    plot_file = base + "_plot.pdf"
    plot_event_rate_to_total_time(output_file, plot_file)