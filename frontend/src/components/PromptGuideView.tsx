export function PromptGuideView() {
  return (
    <>
      <section className="card">
        <h3>AI Agent 프롬프트 작성 요령</h3>
        <div className="prompt-guide">
          <div className="guide-item">
            <strong>1. 스킬은 참조만 걸기</strong>
            <p className="guide-bad">✗ "너의 스킬은 백엔드 개발이야. 이렇게 저렇게 해"</p>
            <p className="guide-good">✓ "@senior.md 참조해서 구현해 줘"</p>
          </div>
          <div className="guide-item">
            <strong>2. 검증 기준을 먼저 제시</strong>
            <p className="guide-bad">✗ "잘 되게 만들어 줘"</p>
            <p className="guide-good">✓ "테스트 케이스: X→true, Y→false. 테스트 실행 후 확인"</p>
          </div>
          <div className="guide-item">
            <strong>3. 증상을 구체적으로</strong>
            <p className="guide-bad">✗ "로그인 버그 고쳐줘"</p>
            <p className="guide-good">✓ "세션 만료 후 로그인 실패. src/auth/ 확인, 재현 테스트 작성 후 수정"</p>
          </div>
          <div className="guide-item">
            <strong>4. 계획 → 구현 분리</strong>
            <p className="guide-bad">✗ "전체 설계하고 바로 구현까지 해 줘"</p>
            <p className="guide-good">✓ "plan 모드로 먼저 탐색 후 구현"</p>
          </div>
          <div className="guide-item">
            <strong>5. 파일 참조는 직접 지정</strong>
            <p className="guide-bad">✗ "기존 패턴 참고해서 만들어"</p>
            <p className="guide-good">✓ "@src/api/auth.ts 패턴 참고해서 구현"</p>
          </div>
          <div className="guide-item">
            <strong>6. 한 번에 하나씩</strong>
            <p className="guide-bad">✗ "리팩터링하고 테스트 추가하고 배포까지 해 줘"</p>
            <p className="guide-good">✓ "리팩터링만 해 줘. /clear 후 다음 작업 요청"</p>
          </div>
        </div>
      </section>
    </>
  );
}
