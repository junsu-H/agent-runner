#!/usr/bin/env bash
set -euo pipefail

PROJECT_PATH="${1:-}"
ISSUE_KEY="${2:-NO-ISSUE}"

if [ -z "$PROJECT_PATH" ]; then
  echo "Usage: pr-review.sh <project_path> [issue_key]" >&2
  exit 2
fi

if [ ! -d "$PROJECT_PATH" ]; then
  echo "Project path not found: $PROJECT_PATH" >&2
  exit 3
fi

OUT_DIR="$PROJECT_PATH/.agent-runner"
SAFE_ISSUE="${ISSUE_KEY//\//-}"
OUT_FILE="$OUT_DIR/pr-review-${SAFE_ISSUE}.md"

mkdir -p "$OUT_DIR"

GIT_STATUS=""
if [ -d "$PROJECT_PATH/.git" ]; then
  GIT_STATUS="$(cd "$PROJECT_PATH" && git status --short || true)"
else
  GIT_STATUS="not-a-git-repository"
fi

cat > "$OUT_FILE" <<EOF2
# PR Review Scaffold
project_path: $PROJECT_PATH
issue_key: $ISSUE_KEY
generated_at_utc: $(date -u +%Y-%m-%dT%H:%M:%SZ)

## Review Summary
- This is a scaffold output for PR review.
- Replace with real review logic (diff selection, policy checks, PR publishing).

## Git Status
$GIT_STATUS
EOF2

echo "pr-review completed"
echo "output: $OUT_FILE"
