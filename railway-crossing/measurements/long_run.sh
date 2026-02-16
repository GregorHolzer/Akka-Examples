#!/bin/bash

# Define the crossing counts
CROSSINGS_LIST="8 16 32 64 128 256 512 1024 2048 4096 8192 16384"

for i in 1 2 3; do
  echo "Starting Iteration $i..."
  (
    cd ~/CSM/Cirrina-Examples/railway-crossing || exit
    for crossings in $CROSSINGS_LIST; do
        python run_local.py 4 $crossings 10 100000 300 600
    done
  )

  (
    for crossings in $CROSSINGS_LIST; do
      python ./run_local.py 4 $crossings 10 100000 300 600
    done
  )
done