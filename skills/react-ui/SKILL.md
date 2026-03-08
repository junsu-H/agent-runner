# react-ui

React + TypeScript UI 개발 스킬.

## 역할
- React 19 + TypeScript 5.8 기반 UI 개발
- Vite 7 개발 서버 및 빌드
- 워크플로우 캔버스 (드래그 앤 드롭)
- 스킬 팔레트, 네비게이터, 실행 요약 UI
- 폴더 브라우저 모달

## 주요 경로
- `frontend/src/App.tsx` — 앱 루트
- `frontend/src/hooks/useWorkflowState.ts` — 상태 관리 훅
- `frontend/src/components/` — UI 컴포넌트
- `frontend/src/styles.css` — 스타일
- `frontend/vite.config.ts` — Vite 설정 (프록시 포함)

## 컴포넌트 구조
- `LeftPanel` — 좌측 사이드바 (Skill Path, 팔레트, 네비게이터)
- `WorkflowView` — 메인 워크플로우 화면
- `SkillsDirectoryView` — 스킬 디렉토리 조회 화면

## 실행
```bash
cd frontend && npm run dev
```
개발 서버 포트: 15432 (백엔드 19876으로 프록시)
