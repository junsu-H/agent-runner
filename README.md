# agent-runner

AI CLI 도구(Claude, Codex, Copilot, Gemini)를 위한 워크플로우 오케스트레이션

## 필수 요구사항

- Java 25+
- Node.js 20+
- npm

## 시작하기

```bash
git clone https://github.com/junsu-H/agent-runner.git
cd agent-runner
chmod +x start.sh
./start.sh
```

실행 후 브라우저에서 `http://localhost:15432` 접속

## 사용 방법

### 1. CLI 선택

워크플로우에서 사용할 AI CLI를 선택합니다.

- **claude** — Anthropic Claude Code
- **codex** — OpenAI Codex CLI
- **copilot** — GitHub Copilot CLI
- **gemini** — Google Gemini CLI

### 2. 스킬 선택

좌측 **Skill Palette**에서 스킬을 드래그하여 우측 **Workflow Canvas**에 놓습니다.

- 스킬 클릭으로도 추가 가능
- 드래그로 실행 순서 변경 가능
- 여러 스킬을 체이닝하여 순차 실행

### 3. 프롬프트 입력

각 스킬에 대한 프롬프트를 작성합니다.

- Canvas에서 스킬 클릭 → 프롬프트 입력창 활성화
- 스킬별로 개별 프롬프트 지정
- 모든 스킬에 프롬프트를 입력해야 다음 단계 진행 가능

### 4. MCP 선택 (선택사항)

필요한 MCP 프로필을 선택합니다.

- API key 불필요: sequential-thinking, serena, AWS 시리즈, datadog, opensearch 등
- API key 필요: sonarqube, wiki (Confluence)

### 5. 워크플로우 파일 생성

출력 경로와 파일명을 확인 후 **생성** 버튼을 클릭합니다.

- 기본 경로: `{Skill Path}/.workflow/`
- 생성된 `.md` 파일을 **확인** 버튼으로 미리보기 가능

### 6. 터미널 실행

**터미널 실행** 버튼을 클릭하면 선택한 CLI가 워크플로우를 실행합니다.

## 스킬 추가

`skills/` 디렉토리에 새 폴더를 만들고 `SKILL.md` 파일을 작성하면 자동으로 인식됩니다.

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
