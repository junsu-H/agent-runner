#!/usr/bin/env bash
set -euo pipefail

# ─── Colors ───
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

ok()   { echo -e "  ${GREEN}✓${NC} $1"; }
skip() { echo -e "  ${BLUE}→${NC} $1 (이미 설치됨)"; }
info() { echo -e "  ${YELLOW}…${NC} $1"; }
fail() { echo -e "  ${RED}✗${NC} $1"; }

echo ""
echo "================================================"
echo "  Agent Runner — 설치 스크립트"
echo "================================================"
echo ""

OS="$(uname -s)"
case "$OS" in
  Darwin) OS_NAME="macOS" ;;
  Linux)  OS_NAME="Linux" ;;
  *)      echo "지원하지 않는 OS: $OS"; exit 1 ;;
esac
echo "[install] OS: $OS_NAME"
echo ""

# ─── 1. Java 25+ ───
echo "[1/3] Java 25+ 확인"

JAVA_OK=false
if command -v java &>/dev/null; then
  JAVA_VER=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
  if [ "$JAVA_VER" -ge 25 ] 2>/dev/null; then
    skip "Java $JAVA_VER"
    JAVA_OK=true
  else
    info "Java $JAVA_VER 감지됨 (25+ 필요)"
  fi
fi

if [ "$JAVA_OK" = false ]; then
  # SDKMAN 설치 (없으면)
  if [ ! -d "$HOME/.sdkman" ]; then
    info "SDKMAN 설치 중..."
    curl -s "https://get.sdkman.io" | bash
    ok "SDKMAN 설치 완료"
  fi

  # SDKMAN 로드
  export SDKMAN_DIR="$HOME/.sdkman"
  # shellcheck disable=SC1091
  source "$SDKMAN_DIR/bin/sdkman-init.sh"

  info "Java 25 (Temurin) 설치 중..."
  sdk install java 25-tem < /dev/null || true
  ok "Java 25 설치 완료"
fi

echo ""

# ─── 2. Node.js 20+ ───
echo "[2/3] Node.js 20+ 확인"

NODE_OK=false
if command -v node &>/dev/null; then
  NODE_VER=$(node -v | sed 's/v//' | cut -d. -f1)
  if [ "$NODE_VER" -ge 20 ] 2>/dev/null; then
    skip "Node.js v$(node -v | sed 's/v//')"
    NODE_OK=true
  else
    info "Node.js v$(node -v | sed 's/v//') 감지됨 (20+ 필요)"
  fi
fi

if [ "$NODE_OK" = false ]; then
  # nvm 설치 (없으면)
  export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
  if [ ! -d "$NVM_DIR" ]; then
    info "nvm 설치 중..."
    curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
    ok "nvm 설치 완료"
  fi

  # nvm 로드
  # shellcheck disable=SC1091
  [ -s "$NVM_DIR/nvm.sh" ] && source "$NVM_DIR/nvm.sh"

  info "Node.js 20 LTS 설치 중..."
  nvm install 20
  nvm use 20
  ok "Node.js $(node -v) 설치 완료"
fi

echo ""

# ─── 3. npm 확인 ───
echo "[3/3] npm 확인"

if command -v npm &>/dev/null; then
  skip "npm v$(npm -v)"
else
  fail "npm을 찾을 수 없습니다. Node.js를 다시 설치해 주세요."
  exit 1
fi

echo ""

# ─── 결과 요약 ───
echo "================================================"
echo "  설치 완료"
echo ""
echo "  Java  : $(java -version 2>&1 | head -1)"
echo "  Node  : $(node -v)"
echo "  npm   : v$(npm -v)"
echo ""
echo "  다음 명령으로 실행하세요:"
echo "    chmod +x start.sh && ./start.sh"
echo "================================================"
echo ""
