# spring-api

Spring Boot REST API 개발 스킬.

## 역할
- Spring Boot 4.0 기반 REST API 개발
- Java 25 / Gradle 9.2.1 빌드 환경
- 워크플로우 오케스트레이션 로직 구현
- MCP 연동 및 스킬 디렉토리 관리

## 주요 경로
- `backend/src/main/java/com/agentrunner/` — 메인 소스
- `backend/src/main/resources/application.yml` — 설정
- `backend/build.gradle.kts` — 빌드 설정

## API 엔드포인트
- `GET /api/skills` — 스킬 목록 조회
- `GET /api/directories` — 디렉토리 탐색
- `POST /api/workflows/linked-list/generate` — 워크플로우 생성
- `POST /api/workflows/linked-list/run/terminal` — 터미널 실행
- `GET /api/mcp/info` — MCP 연결 정보

## 실행
```bash
cd backend && ./gradlew bootRun
```
서버 포트: 19876
