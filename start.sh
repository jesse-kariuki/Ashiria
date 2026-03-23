#!/usr/bin/env bash
set -e

SAMPLE_MS="${SAMPLE_MS:-1000}"
HTTP_PORT="${HTTP_PORT:-7777}"
TRACK="${TRACK:-}"
AGENT_JAR="${AGENT_JAR:-target/memory-intel-agent-1.0.0-jar-with-dependencies.jar}"
APP_JAR="${1:-}"

if [ -z "$APP_JAR" ]; then
  echo "Usage: $0 <app.jar> [app args...]"
  exit 1
fi

AGENT_ARGS="sampleInterval=${SAMPLE_MS},httpPort=${HTTP_PORT}"
if [ -n "$TRACK" ]; then
  AGENT_ARGS="${AGENT_ARGS},track=${TRACK}"
fi
if [ -n "$VERBOSE" ]; then
  AGENT_ARGS="${AGENT_ARGS},verbose=true"
fi

echo "[start.sh] Agent args: $AGENT_ARGS"
echo "[start.sh] API will be at http://localhost:${HTTP_PORT}"

exec java \
  "-javaagent:${AGENT_JAR}=${AGENT_ARGS}" \
  -cp "${AGENT_JAR}" \
  com.memoryintel.demo.DemoApp