#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

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
