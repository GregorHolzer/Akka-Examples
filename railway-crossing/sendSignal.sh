#!/bin/bash

while getopts "p:?" opt; do
  case $opt in
    p)
        PORT=${OPTARG}
        ;;
    *)
        echo "Usage: ./sendSignal -p <port> <signal>"
        exit 0
        ;;
  esac
done

shift $((OPTIND-1))

CONTROLLER=${CONTROLLER:-sample}
PORT=${PORT:-8000}
OPERATION=${1:-}

function trainSeen {
    echo "Sending signal trainSeen to controller via port $PORT"
    curl -w "\n" -X POST http://localhost:$PORT/railway-crossing/controller/trainSeen
}

function trainNotSeen {
    echo "Sending signal trainNotSeen to controller via port $PORT"
    curl -w "\n" -X POST http://localhost:$PORT/railway-crossing/controller/trainNotSeen
}

case $OPERATION in
    trainSeen)
        trainSeen
        ;;
    trainNotSeen)
        trainNotSeen
        ;;
    *)
        echo "Signals: trainSeen, trainNotSeen"
        ;;
esac