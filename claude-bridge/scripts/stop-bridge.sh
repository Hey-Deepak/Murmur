#!/data/data/com.termux/files/usr/bin/bash
# stop-bridge.sh — Stop the Claude Bridge Ktor server
# Usage: stop-bridge.sh

set -euo pipefail

WORK_DIR="$HOME/murmur"
PID_FILE="$WORK_DIR/bridge.pid"

# Nothing to do if no PID file
if [ ! -f "$PID_FILE" ]; then
    echo "Bridge is not running (no PID file)"
    exit 0
fi

PID=$(cat "$PID_FILE")

# Already dead?
if ! kill -0 "$PID" 2>/dev/null; then
    echo "Bridge process $PID already stopped"
    rm -f "$PID_FILE"
    exit 0
fi

# Graceful SIGTERM
echo "Sending SIGTERM to bridge (PID $PID)..."
kill "$PID" 2>/dev/null || true

# Poll up to 5 seconds
for i in $(seq 1 10); do
    if ! kill -0 "$PID" 2>/dev/null; then
        echo "Bridge stopped gracefully"
        rm -f "$PID_FILE"
        exit 0
    fi
    sleep 0.5
done

# Force SIGKILL
echo "Bridge did not stop gracefully, sending SIGKILL..."
kill -9 "$PID" 2>/dev/null || true
sleep 0.5
rm -f "$PID_FILE"
echo "Bridge killed"
exit 0
