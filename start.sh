#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

# --- Kill existing processes on required ports ---
kill_port() {
  local port=$1
  local pids
  pids=$(lsof -ti :"$port" 2>/dev/null || true)
  if [ -n "$pids" ]; then
    echo "[agent-runner] Killing existing process on port $port (PID: $pids)"
    echo "$pids" | xargs kill -9 2>/dev/null || true
    sleep 1
  fi
}

kill_port 19876
kill_port 15432

cleanup() {
  echo ""
  echo "[agent-runner] Shutting down..."
  kill $BACKEND_PID $FRONTEND_PID 2>/dev/null || true
  wait $BACKEND_PID $FRONTEND_PID 2>/dev/null || true
  echo "[agent-runner] Stopped."
}
trap cleanup EXIT INT TERM

# --- Backend (Spring Boot :19876) ---
echo "[agent-runner] Starting backend..."
cd "$ROOT_DIR/backend"
./gradlew bootRun --quiet &
BACKEND_PID=$!

echo "[agent-runner] Waiting for backend to be ready..."
until curl -sf http://localhost:19876/api/prerequisites/check > /dev/null 2>&1; do
  sleep 2
done
echo "[agent-runner] Backend is ready."

# --- Frontend (Vite dev :15432) ---
echo "[agent-runner] Starting frontend..."
cd "$ROOT_DIR/frontend"
npm install --silent 2>/dev/null
npx vite --host &
FRONTEND_PID=$!

echo ""
echo "================================================"
echo "  Agent Runner"
echo "  Backend  : http://localhost:19876"
echo "  Frontend : http://localhost:15432"
echo "================================================"
echo ""

wait
