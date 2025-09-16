#!/bin/bash

DEFAULT_PORT_NUM=2551
DEFAULT_LISTEN_PORT=8000
DEFAULT_MANAGE_PORT=8558
NODE_NUM=0

while [ -n "$1" ]; do
  case "$1" in
    -n)
      if [ -z "$2" ]; then
        echo "Usage: ./runNode.sh -n <number_of_instance>"
        exit 1
      fi
      NODE_NUM=$2
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done

PORT_NUM=$((DEFAULT_PORT_NUM + NODE_NUM))
LISTEN_PORT=$((DEFAULT_LISTEN_PORT + NODE_NUM))
MANAGE_PORT=$((DEFAULT_MANAGE_PORT + NODE_NUM))


echo "Running node $NODE_NUM on port $PORT_NUM, manage via $MANAGE_PORT, listen on port $LISTEN_PORT"

mvn exec:java -Dexec.mainClass=Main -Dakka.remote.artery.canonical.port=$PORT_NUM -Dakka.management.http.port=$MANAGE_PORT -Dakka.http.server.default-http-port=$DEFAULT_LISTEN_PORT