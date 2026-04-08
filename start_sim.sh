#!/bin/bash

# this system is designed to run n drones quickly, so this makes life easy.
if [ -z "$1" ]; then
    echo "Usage: ./start.sh <num_drones>"
    exit 1
fi

NUM_DRONES=$1
ROOT="$(cd "$(dirname "$0")" && pwd)"

# Compile (because we aren't building to .jar, so we gotta compile each time)
mkdir -p "$ROOT/out/intermediate" "$ROOT/out/server" "$ROOT/out/clients"
javac -d "$ROOT/out/intermediate" "$ROOT/intermediate/src/main/java"/*.java
javac -d "$ROOT/out/server"       "$ROOT/server/src/main/java"/*.java
javac -d "$ROOT/out/client"      "$ROOT/client/src/main/java"/*.java

# Start processes with sleep enabled to maintain order.
java -cp "$ROOT/out/intermediate" Main &
sleep 1

java -cp "$ROOT/out/server" Main &
sleep 1

for (( i=1; i<=NUM_DRONES; i++ )); do
    java -cp "$ROOT/out/client" Main $i &
    sleep 0.5
done

wait