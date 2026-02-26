#!/data/data/com.termux/files/usr/bin/bash
# start-bridge.sh — Start the Claude Bridge Ktor server
# Usage: start-bridge.sh [port]

set -euo pipefail

PORT="${1:-8735}"
WORK_DIR="$HOME/murmur"
JAR="$WORK_DIR/claude-bridge-all.jar"
PID_FILE="$WORK_DIR/bridge.pid"
LOG_FILE="$WORK_DIR/bridge.log"

mkdir -p "$WORK_DIR"

# Check JAR exists
if [ ! -f "$JAR" ]; then
    echo "ERROR: JAR not found at $JAR" >&2
    exit 1
fi

# Check for stale PID
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if kill -0 "$OLD_PID" 2>/dev/null; then
        echo "Bridge already running (PID $OLD_PID)"
        exit 0
    else
        echo "Removing stale PID file (PID $OLD_PID no longer running)"
        rm -f "$PID_FILE"
    fi
fi

# Start bridge
echo "Starting Claude Bridge on port $PORT..."
nohup java -jar "$JAR" -port="$PORT" > "$LOG_FILE" 2>&1 &
BRIDGE_PID=$!
echo "$BRIDGE_PID" > "$PID_FILE"

# Wait and verify
sleep 2
if kill -0 "$BRIDGE_PID" 2>/dev/null; then
    echo "Bridge started successfully (PID $BRIDGE_PID, port $PORT)"
    exit 0
else
    echo "ERROR: Bridge process died after start. Check $LOG_FILE" >&2
    rm -f "$PID_FILE"
    exit 1
fi
