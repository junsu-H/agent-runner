#!/usr/bin/env bash
set -euo pipefail

PROJECT_PATH="${1:-}"
ISSUE_KEY="${2:-NO-ISSUE}"

if [ -z "$PROJECT_PATH" ]; then
  echo "Usage: domain-analysis.sh <project_path> [issue_key]" >&2
  exit 2
fi

if [ ! -d "$PROJECT_PATH" ]; then
  echo "Project path not found: $PROJECT_PATH" >&2
  exit 3
fi

OUT_DIR="$PROJECT_PATH/.agent-runner"
SAFE_ISSUE="${ISSUE_KEY//\//-}"
OUT_FILE="$OUT_DIR/domain-analysis-${SAFE_ISSUE}.md"

mkdir -p "$OUT_DIR"

cat > "$OUT_FILE" <<EOF2
# Domain Analysis
project_path: $PROJECT_PATH
issue_key: $ISSUE_KEY
generated_at_utc: $(date -u +%Y-%m-%dT%H:%M:%SZ)

## Summary
- This is a scaffold output for domain analysis.
- Replace with real static analysis logic (wiki/serena/sequential-thinking integration).
EOF2

echo "domain-analysis completed"
echo "output: $OUT_FILE"
