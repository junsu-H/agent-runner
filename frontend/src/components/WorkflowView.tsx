import type { WorkflowState } from '../hooks/useWorkflowState';
import { mcpProfilesNoKey, mcpProfilesWithKey, mcpProfileOptions } from '../workflow-constants';

type Props = Pick<WorkflowState,
  | 'cli' | 'setCli' | 'error'
  | 'workflowFilePath' | 'setWorkflowFilePath' | 'workflowNameDuplicate'
  | 'selectedSkills' | 'activeSkill' | 'setActiveSkill'
  | 'stepPrompts' | 'activeSkillPrompt' | 'updateStepPrompt'
  | 'onWorkflowDragStart' | 'onCanvasDrop' | 'onBoxDrop' | 'removeSelectedSkill'
  | 'effectiveSelectedSkills'
  | 'effectiveMcpProfiles' | 'toggleMcpProfile' | 'toggleAllMcpProfiles' | 'checkUniqueName'
  | 'generateWorkflow' | 'generatingMd' | 'generatedFile' | 'generatedFilePath' | 'generatedFileContent' | 'setGeneratedFileContent' | 'openGeneratedFile'
  | 'openTerminal' | 'launchingTerminal'
  | 'step1Done' | 'step2Done' | 'step3Done' | 'progressPercent'
>;

export function WorkflowView(props: Props) {
  const {
    cli, setCli, error,
    workflowFilePath, setWorkflowFilePath, workflowNameDuplicate,
    selectedSkills, activeSkill, setActiveSkill,
    stepPrompts, activeSkillPrompt, updateStepPrompt,
    onWorkflowDragStart, onCanvasDrop, onBoxDrop, removeSelectedSkill,
    effectiveSelectedSkills,
    effectiveMcpProfiles, toggleMcpProfile, toggleAllMcpProfiles, checkUniqueName,
    generateWorkflow, generatingMd, generatedFile, generatedFilePath, generatedFileContent, setGeneratedFileContent, openGeneratedFile,
    openTerminal, launchingTerminal,
    step1Done, step2Done, step3Done, progressPercent,
  } = props;

  return (
    <>
      {/* ── Progress bar ── */}
      <div className="wizard-progress-bar">
        <div className="wizard-progress-labels">
          <span className="wizard-progress-label done">1. CLI</span>
          <span className={`wizard-progress-label ${step1Done ? 'done' : ''}`}>2. 스킬 선택</span>
          <span className={`wizard-progress-label ${step2Done ? 'done' : ''}`}>3. 프롬프트</span>
          <span className={`wizard-progress-label ${step3Done ? 'done' : ''}`}>4. MCP</span>
          <span className={`wizard-progress-label ${step3Done ? 'done' : ''}`}>5. 실행</span>
        </div>
        <div className="progress-track">
          <div className="progress-fill" style={{ width: `${progressPercent}%` }} />
        </div>
      </div>

      {/* ── Section 1: CLI 선택 ── */}
      <div className="wizard-section">
        <h3 className="wizard-section-title"><span className="wizard-section-num">1</span>CLI 선택</h3>
        <section className="card top-cli-card">
          <div className="cli-select-grid top-cli-grid">
            <button type="button" className={`cli-select-item ${cli === 'claude' ? 'active' : ''}`} onClick={() => setCli('claude')}>claude</button>
            <button type="button" className={`cli-select-item ${cli === 'codex' ? 'active' : ''}`} onClick={() => setCli('codex')}>codex</button>
            <button type="button" className={`cli-select-item ${cli === 'copilot' ? 'active' : ''}`} onClick={() => setCli('copilot')}>copilot</button>
            <button type="button" className={`cli-select-item ${cli === 'gemini' ? 'active' : ''}`} onClick={() => setCli('gemini')}>gemini</button>
          </div>
        </section>
      </div>

      {/* ── Section 2: 스킬 선택 ── */}
      <div className="wizard-section">
        <h3 className="wizard-section-title"><span className="wizard-section-num">2</span>스킬 선택</h3>

        <section className="card">
          <div className="canvas-head">
            <h3>Workflow Canvas (Drag &amp; Drop)</h3>
          </div>
          <div className="workflow-canvas" onDragOver={(e) => e.preventDefault()} onDrop={onCanvasDrop}>
            {selectedSkills.length === 0 ? (
              <p className="muted" style={{ margin: 0, fontSize: 13 }}>스킬을 드래그해서 여기에 놓으세요.</p>
            ) : (
              <div className="workflow-box-list">
                {selectedSkills.map((skill, idx) => (
                  <div
                    key={`${skill}-${idx}`}
                    className={`workflow-box ${activeSkill === skill ? 'active' : ''}`}
                    draggable
                    onDragStart={(e) => onWorkflowDragStart(e, skill, idx)}
                    onDragOver={(e) => e.preventDefault()}
                    onDrop={(e) => onBoxDrop(e, idx)}
                    onClick={() => setActiveSkill(skill)}
                  >
                    <div className="workflow-box-top">
                      <strong>{idx + 1}. {skill}</strong>
                      <button type="button" className="tiny danger" onClick={(e) => { e.stopPropagation(); removeSelectedSkill(skill); }}>삭제</button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </section>
      </div>

      {/* ── Section 3: 프롬프트 입력 ── */}
      <div className="wizard-section">
        <h3 className="wizard-section-title"><span className="wizard-section-num">3</span>프롬프트 입력</h3>

        {effectiveSelectedSkills.length > 1 && (
          <section className="card">
            <h3>스킬 체인 프리뷰</h3>
            <div className="chain-compact">
              {effectiveSelectedSkills.map((skill, idx) => (
                <div key={skill} className="chain-compact-wrapper">
                  {idx > 0 && <span className="chain-compact-arrow">&#8594;</span>}
                  <button type="button" className={`chain-compact-node ${activeSkill === skill ? 'active' : ''}`} onClick={() => setActiveSkill(skill)}>
                    <span className="chain-compact-num">{idx + 1}</span>
                    <span>{skill}</span>
                    {stepPrompts[skill]?.trim() ? <span className="chain-compact-check">&#10003;</span> : null}
                  </button>
                </div>
              ))}
            </div>
          </section>
        )}

        <section className="card" style={!activeSkill ? { padding: '12px 16px' } : undefined}>
          {!activeSkill ? (
            <p className="muted" style={{ margin: 0, fontSize: 13 }}>스킬을 먼저 추가해 주세요.</p>
          ) : (
            <div className="skill-detail">
              <textarea rows={5} value={activeSkillPrompt} onChange={(e) => updateStepPrompt(activeSkill, e.target.value)} className="main-textarea" placeholder="이 스킬과 함께 사용할 프롬프트를 적어주세요" />
            </div>
          )}
        </section>

        {effectiveSelectedSkills.length > 1 && (
          <section className="card">
            <h3>워크플로우 파일 생성</h3>
            <div className="button-row" style={{ alignItems: 'center' }}>
              <div className="workflow-name-input">
                <input type="text" value={workflowFilePath} onChange={(e) => setWorkflowFilePath(e.target.value)} onBlur={() => { void checkUniqueName(); }} placeholder="workflow" className={workflowNameDuplicate ? 'input-error' : ''} />
                <span className="workflow-name-label">.md</span>
                {workflowNameDuplicate && <span className="input-error-msg">중복된 이름입니다</span>}
              </div>
              <button type="button" onClick={() => { void generateWorkflow(); }} disabled={generatingMd || !step2Done || workflowNameDuplicate}>
                {generatingMd ? '생성 중...' : '생성'}
              </button>
              {generatedFilePath && (
                <button type="button" className="ghost" onClick={() => { void openGeneratedFile(); }}>
                  확인
                </button>
              )}
            </div>
          </section>
        )}

        {generatedFileContent && (
          <section className="card">
            <div className="canvas-head">
              <p className="execution-phase-status">{generatedFile}</p>
              <button type="button" className="ghost tiny" onClick={() => setGeneratedFileContent(null)}>닫기</button>
            </div>
            <pre className="file-preview">{generatedFileContent}</pre>
          </section>
        )}

      </div>

      {/* ── Section 4: MCP 선택 ── */}
      <div className="wizard-section">
        <h3 className="wizard-section-title"><span className="wizard-section-num">4</span>MCP 선택</h3>

        <section className="card mcp-setup-card">
          <div className="mcp-profile-header">
            <p className="mcp-toggle-title">MCP 프로필</p>
            <div className="mcp-summary-row">
              <span className="mcp-summary-pill">selected {effectiveMcpProfiles.length}</span>
              {effectiveMcpProfiles.length < mcpProfileOptions.length
                ? <button type="button" className="ghost tiny" onClick={() => toggleAllMcpProfiles(true)}>전체 선택</button>
                : <button type="button" className="ghost tiny" onClick={() => toggleAllMcpProfiles(false)}>전체 해제</button>
              }
            </div>
          </div>

          <div className="mcp-profile-groups">
            <div className="mcp-profile-group">
              <p className="mcp-group-label">API key 불필요</p>
              <div className="mcp-profile-grid">
                {mcpProfilesNoKey.map((profile) => (
                  <label key={profile.id} className="mcp-profile-item">
                    <input type="checkbox" checked={effectiveMcpProfiles.includes(profile.id)} onChange={(e) => toggleMcpProfile(profile.id, e.target.checked)} />
                    <span className="mcp-profile-name">{profile.label}</span>
                    <span className="mcp-profile-desc">{profile.desc}</span>
                  </label>
                ))}
              </div>
            </div>
            <div className="mcp-profile-group">
              <p className="mcp-group-label key-required">API key 필요 <span className="mcp-key-hint">{'\u2192'} ~/.zshrc에 setting</span></p>
              <div className="mcp-profile-grid">
                {mcpProfilesWithKey.map((profile) => (
                  <label key={profile.id} className="mcp-profile-item">
                    <input type="checkbox" checked={effectiveMcpProfiles.includes(profile.id)} onChange={(e) => toggleMcpProfile(profile.id, e.target.checked)} />
                    <span className="mcp-profile-name">{profile.label}</span>
                    <span className="mcp-profile-desc">{profile.desc}</span>
                  </label>
                ))}
              </div>
            </div>
          </div>
        </section>
      </div>

      {/* ── Section 5: 터미널 실행 ── */}
      <div className="wizard-section">
        <h3 className="wizard-section-title"><span className="wizard-section-num">5</span>터미널 실행</h3>

        {error && <p className="error-inline">{error}</p>}
        <section className="card" style={!step2Done ? { padding: '12px 16px' } : undefined}>
          {!step2Done ? (
            <p className="muted" style={{ margin: 0, fontSize: 13 }}>스킬과 프롬프트를 먼저 입력하세요.</p>
          ) : (
            <div className="button-row">
              <button type="button" onClick={() => { void openTerminal(); }} disabled={launchingTerminal || (effectiveSelectedSkills.length > 1 && !generatedFile)}>
                {launchingTerminal ? '실행 중...' : '터미널 실행'}
              </button>
              {effectiveSelectedSkills.length > 1 && !generatedFile && <span className="muted">먼저 워크플로우 파일을 생성하세요</span>}
            </div>
          )}
        </section>

      </div>
    </>
  );
}
