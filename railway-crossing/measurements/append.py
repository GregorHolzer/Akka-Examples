import sys


def merge_csv_files(output_file, files):

    if output_file in files:
        files.remove(output_file)

    if not files:
        print("No files found.")
        return

    with open(output_file, 'w', encoding='utf-8') as outfile:
        for i, filename in enumerate(files):
            print(f"Processing: {filename}")
            with open(filename, 'r', encoding='utf-8') as infile:
                if i == 0:
                    for line in infile:
                        outfile.write(line)
                else:
                    next(infile)
                    for line in infile:
                        outfile.write(line)

    print(f"--- Done! Combined {len(files)} files into {output_file} ---")


if __name__ == "__main__":
    out_name = sys.argv[1] if len(sys.argv) > 1 else "combined_output.csv"
    merge_csv_files(out_name, sys.argv[2:])