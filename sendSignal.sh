#!/bin/bash

while getopts "n:p:?" opt; do
  case $opt in
    n)
        CONTROLLER=${OPTARG}
        ;;
    p)
        PORT=${OPTARG}
        ;;
    *)
        echo "Usage: ./sendSignal -p <port> -n <controllerName> <signal>"
        exit 0
        ;;
  esac
done

shift $((OPTIND-1))

CONTROLLER=${CONTROLLER:-sample}
PORT=${PORT:-8000}
OPERATION=${1:-}

function trainSeen {
    echo "Sending signal trainSeen to controller $CONTROLLER via port $PORT"
    curl -w "\n" -X POST http://localhost:$PORT/railway-crossing/controller/$CONTROLLER/trainSeen
}

function trainNotSeen {
    echo "Sending signal trainNotSeen to controller $CONTROLLER via port $PORT"
    curl -w "\n" -X POST http://localhost:$PORT/railway-crossing/controller/$CONTROLLER/trainNotSeen
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
