import pandas as pd
import sys


def process_trace_data(input_file, output_file=None):
    """
    Process trace data by grouping by trace_id and pivoting service.name values.

    Args:
        input_file: Path to input CSV file
        output_file: Path to output CSV file (optional, defaults to input_file with '_processed' suffix)
    """
    # Read the CSV file
    df = pd.read_csv(input_file)

    # Select only the required columns
    df_selected = df[['service.name', '_value', 'trace_id', '_time']].copy()

    # Create pivot columns for each service
    # Group by trace_id and service.name, taking the first value for each group
    pivot_df = df_selected.groupby(['trace_id', 'service.name']).first().reset_index()

    # Pivot the data to create separate columns for each service
    result = pivot_df.pivot(
        index='trace_id',
        columns='service.name',
        values=['_value', '_time']
    )

    # Flatten the multi-level column names
    result.columns = [f'{col[1]}-{col[0].replace("_", "")}' for col in result.columns]

    # Reset index to make trace_id a column
    result = result.reset_index()

    # Reorder columns to match expected output format
    # Get all unique service names
    services = df_selected['service.name'].unique()

    # Build column order: trace_id, then for each service: value, time
    cols = ['trace_id']
    for service in sorted(services):
        value_col = f'{service}-value'
        time_col = f'{service}-time'
        if value_col in result.columns:
            cols.append(value_col)
        if time_col in result.columns:
            cols.append(time_col)

    # Select and reorder columns
    result = result[cols]

    # Generate output filename if not provided
    if output_file is None:
        output_file = input_file.rsplit('.', 1)[0] + '_processed.csv'

    # Save to CSV
    result.to_csv(output_file, index=False)
    print(f"Processed data saved to: {output_file}")
    print(f"Total trace IDs: {len(result)}")
    print(f"\nFirst few rows:")
    print(result.head())

    return result


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python script.py <input_file> [output_file]")
        print("Example: python script.py traces.csv traces_processed.csv")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else None

    process_trace_data(input_file, output_file)