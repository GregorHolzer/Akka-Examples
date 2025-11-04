#!/bin/bash

while getopts "p:i:?" opt; do
  case $opt in
    p)
        PORT=${OPTARG}
        ;;
    i)
        CONTROLLER_ID=${OPTARG}
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
CONTROLLER_ID=${CONTROLLER_ID:-1}
function trainSeen {
    echo "Sending signal trainSeen to controller $CONTROLLER_ID via port $PORT"
    curl -w "\n" -X POST http://localhost:"$PORT"/railway-crossing/controller/"$CONTROLLER_ID"/trainSeen
}

function trainNotSeen {
    echo "Sending signal trainNotSeen to controller $CONTROLLER_ID via port $PORT"
    curl -w "\n" -X POST http://localhost:"$PORT"/railway-crossing/controller/"$CONTROLLER_ID"/trainNotSeen
}



if [[ "$CONTROLLER_ID" == "broadcast" ]]; then
    echo "Broadcasting to all controllers on port $PORT"
    case $OPERATION in
        trainSeen)
            curl -w "\n" -X POST http://localhost:"$PORT"/railway-crossing/broadcast/trainSeen

            ;;
        trainNotSeen)
            curl -w "\n" -X POST http://localhost:"$PORT"/railway-crossing/broadcast/trainNotSeen
            ;;
        *)
            echo "Signals: trainSeen, trainNotSeen"
            ;;
    esac
    exit 0
fi



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