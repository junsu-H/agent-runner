# agent-runner

AI CLI 도구(Claude, Codex, Copilot, Gemini)를 위한 워크플로우 오케스트레이션 플랫폼

## 필수 요구사항

- Java 25+
- Node.js 20+
- npm

## 설치

### macOS / Linux

```bash
git clone https://github.com/junsu-H/agent-runner.git
cd agent-runner
chmod +x install.sh
./install.sh
```

### Windows

```cmd
git clone https://github.com/junsu-H/agent-runner.git
cd agent-runner
install.bat
```

> 설치 스크립트는 Java 25+와 Node.js 20+를 자동으로 확인하고, 없으면 설치합니다.
> 이미 설치된 항목은 건너뜁니다.

## 시작하기

### macOS / Linux

```bash
chmod +x start.sh
./start.sh
```

### Windows

```cmd
start.bat
```

실행 후 브라우저에서 `http://localhost:15432` 접속

## 사용 방법

처음 사용하시면 좌측 상단의 **튜토리얼 시작하기** 버튼을 클릭하여 단계별 안내를 받을 수 있습니다.

### 1. CLI 선택

워크플로우에서 사용할 AI CLI를 선택합니다.

- **claude** — Anthropic Claude Code
- **codex** — OpenAI Codex CLI
- **copilot** — GitHub Copilot CLI
- **gemini** — Google Gemini CLI

### 2. 스킬 선택

Skill Path를 지정하고, 좌측 **Skill Palette**에서 스킬을 우측 **Workflow Canvas**에 추가합니다.

- 스킬 클릭 또는 드래그로 추가
- 드래그로 실행 순서 변경
- 여러 스킬을 체이닝하여 순차 실행

### 3. 프롬프트 입력

각 스킬에 대한 프롬프트를 작성합니다.

- Canvas에서 스킬 클릭 → 프롬프트 입력창 활성화
- 스킬별로 개별 프롬프트 지정
- 모든 스킬에 프롬프트를 입력해야 다음 단계 진행 가능

### 4. 워크플로우 파일

프롬프트를 체이닝한 워크플로우 파일을 생성하거나 기존 파일을 불러옵니다.

- **새로 만들기**: 출력 경로와 파일명을 지정하고 **만들기** 클릭
- **불러오기**: 기존에 저장된 워크플로우 파일을 선택

### 5. MCP 선택 (선택사항)

MCP 연결이 필요하면 MCP profile JSON 폴더 경로를 지정하고 프로필을 선택합니다.

### 6. 실행

두 가지 방법으로 워크플로우를 실행할 수 있습니다.

- **터미널 실행**: 버튼 클릭으로 터미널에서 자동 실행
- **프롬프트에 직접 입력**: 프롬프트를 클립보드에 복사하여 직접 붙여넣기 (MCP 연결 불가)

> MCP를 사용하는 경우, 터미널에서 `/mcp`로 연결 여부를 확인할 수 있습니다.

## 스킬 추가

Skill Path 디렉토리에 새 폴더를 만들고 `SKILL.md` 파일을 작성하면 자동으로 인식됩니다.

```
skills/
├── spring-api/
│   └── SKILL.md
└── react-ui/
    └── SKILL.md
```

## 포트 구성

| 서비스 | 포트 |
|---|---|
| Backend (Spring Boot) | 19876 |
| Frontend (Vite) | 15432 |
