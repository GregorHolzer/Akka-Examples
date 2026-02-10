import sys
import matplotlib.pyplot as plt
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
from datetime import datetime, timezone


def parse_iso_utc(ts):
    if pd.isna(ts):
        return None
    ts = str(ts)
    if ts.endswith("Z"):
        ts = ts[:-1]
    if "." in ts:
        date_part, frac = ts.split(".")
        ts = f"{date_part}.{frac[:6]}"
    dt = datetime.fromisoformat(ts)
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
        'trace_id',
        'pictureCaptured',
        'type',
        'start_time',
        'end_time',
    ]].copy()

    # Latency lists
    detect = []
    analyze = []
    alarmOn = []

    for _, row in values_cols.iterrows():
        if pd.isna([row['pictureCaptured'], row['type'], row['start_time'], row['end_time']]).any():
            continue

        match row['type']:
            case "analyze":
                analyze.append(get_duration(row['pictureCaptured'], row['end_time']))
            case "detect":
                detect.append(get_duration(row['pictureCaptured'], row['end_time']))
            case "alarmOn":
                alarmOn.append(get_duration(row['pictureCaptured'], row['end_time']))

    return detect, analyze, alarmOn


if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("Usage: python compare.py <csm_file> <akka_file> <outputfile>")
        sys.exit(1)

    csm = sys.argv[1]
    akka = sys.argv[2]
    outputfile = sys.argv[3]

    csm_detect, csm_analyze, csm_alarmOn = gather_data(csm)
    akka_detect, akka_analyze, akka_alarmOn = gather_data(akka)

    # Calculate statistics
    data_csm = {
        'detect': csm_detect,
        'analyze': csm_analyze,
        'alarmOn': csm_alarmOn
    }

    data_akka = {
        'detect': akka_detect,
        'analyze': akka_analyze,
        'alarmOn': akka_alarmOn
    }

    labels = ['detect', 'analyze', 'alarmOn']
    means_csm = []
    std_errs_csm = []
    counts_csm = []

    means_akka = []
    std_errs_akka = []
    counts_akka = []

    # Calculate statistics for CSM
    for label in labels:
        values = data_csm[label]
        if len(values) > 0:
            means_csm.append(np.mean(values))
            std_errs_csm.append(np.std(values, ddof=1) / np.sqrt(len(values)))
            counts_csm.append(len(values))
        else:
            means_csm.append(0)
            std_errs_csm.append(0)
            counts_csm.append(0)

    # Calculate statistics for Akka
    for label in labels:
        values = data_akka[label]
        if len(values) > 0:
            means_akka.append(np.mean(values))
            std_errs_akka.append(np.std(values, ddof=1) / np.sqrt(len(values)))
            counts_akka.append(len(values))
        else:
            means_akka.append(0)
            std_errs_akka.append(0)
            counts_akka.append(0)

    # Create the grouped bar plot
    fig, ax = plt.subplots(figsize=(10, 6))
    x_pos = np.arange(len(labels))
    width = 0.35  # Width of bars

    bars_csm = ax.bar(x_pos - width/2, means_csm, width, yerr=std_errs_csm,
                      capsize=5, alpha=0.7, label='CSM', color='#2E86AB')
    bars_akka = ax.bar(x_pos + width/2, means_akka, width, yerr=std_errs_akka,
                       capsize=5, alpha=0.7, label='Akka', color='#A23B72')

    ax.set_ylabel('Latency (ms)', fontsize=12)
    ax.set_title('Average Latency Comparison: CSM vs Akka', fontsize=14, fontweight='bold')
    ax.set_xticks(x_pos)
    ax.set_xticklabels(labels)
    ax.legend()
    ax.grid(axis='y', alpha=0.3, linestyle='--')

    # Add number of samples on top of bars
    for i, (bar, count, stderr) in enumerate(zip(bars_csm, counts_csm, std_errs_csm)):
        height = bar.get_height()
        ax.text(bar.get_x() + bar.get_width() / 2., height + stderr + 2,
                f'n={count}',
                ha='center', va='bottom', fontsize=9)

    for i, (bar, count, stderr) in enumerate(zip(bars_akka, counts_akka, std_errs_akka)):
        height = bar.get_height()
        ax.text(bar.get_x() + bar.get_width() / 2., height + stderr + 2,
                f'n={count}',
                ha='center', va='bottom', fontsize=9)

    plt.tight_layout()
    plt.savefig(outputfile, dpi=300, bbox_inches='tight')
    print(f"Plot saved to {outputfile}")