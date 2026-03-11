import { useState } from 'react';
import type { WorkflowState } from '../hooks/useWorkflowState';
import { FolderBrowserModal } from './FolderBrowserModal';

type Props = Pick<WorkflowState,
  | 'cli' | 'setCli' | 'error'
  | 'skillPath' | 'reloadSkillsFromPath'
  | 'mcpProfilePath' | 'reloadMcpProfilesFromPath'
  | 'workflowFilePath' | 'setWorkflowFilePath' | 'workflowNameDuplicate'
  | 'selectedSkills' | 'activeSkill' | 'setActiveSkill'
  | 'stepPrompts' | 'activeSkillPrompt' | 'updateStepPrompt'
  | 'onWorkflowDragStart' | 'onCanvasDrop' | 'onBoxDrop' | 'removeSelectedSkill'
  | 'effectiveSelectedSkills'
  | 'effectiveMcpProfiles' | 'activeMcpProfileOptions' | 'toggleMcpProfile' | 'toggleAllMcpProfiles' | 'checkUniqueName'
  | 'generateWorkflow' | 'generatingMd' | 'generatedFile' | 'generatedFilePath' | 'generatedFileContent' | 'setGeneratedFileContent' | 'openGeneratedFile'
  | 'openTerminal' | 'launchingTerminal'
  | 'workflowTab' | 'setWorkflowTab' | 'savedWorkflows' | 'loadSavedWorkflows' | 'workflowLoadPath' | 'reloadWorkflowsFromPath'
  | 'selectedSavedWorkflow' | 'setSelectedSavedWorkflow' | 'previewSavedWorkflow' | 'openSavedWorkflowTerminal' | 'finalPromptTemplate'
  | 'step1Done' | 'step2Done' | 'step3Done' | 'progressPercent'
>;

export function WorkflowView(props: Props) {
  const {
    cli, setCli, error,
    skillPath, reloadSkillsFromPath,
    mcpProfilePath, reloadMcpProfilesFromPath,
    workflowFilePath, setWorkflowFilePath, workflowNameDuplicate,
    selectedSkills, activeSkill, setActiveSkill,
    stepPrompts, activeSkillPrompt, updateStepPrompt,
    onWorkflowDragStart, onCanvasDrop, onBoxDrop, removeSelectedSkill,
    effectiveSelectedSkills,
    effectiveMcpProfiles, activeMcpProfileOptions, toggleMcpProfile, toggleAllMcpProfiles, checkUniqueName,
    generateWorkflow, generatingMd, generatedFile, generatedFilePath, generatedFileContent, setGeneratedFileContent, openGeneratedFile,
    openTerminal, launchingTerminal,
    workflowTab, setWorkflowTab, savedWorkflows, loadSavedWorkflows, workflowLoadPath, reloadWorkflowsFromPath,
    selectedSavedWorkflow, setSelectedSavedWorkflow, previewSavedWorkflow, openSavedWorkflowTerminal, finalPromptTemplate,
    step1Done, step2Done, step3Done, progressPercent,
  } = props;

  const [browserOpen, setBrowserOpen] = useState(false);
  const [mcpBrowserOpen, setMcpBrowserOpen] = useState(false);
  const [wfBrowserOpen, setWfBrowserOpen] = useState(false);
  const [copied, setCopied] = useState(false);

  const isLoad = workflowTab === 'load';

  const getPromptText = () => {
    const fn = isLoad && selectedSavedWorkflow
      ? selectedSavedWorkflow.name + '.md'
      : (workflowFilePath.trim() || 'workflow') + '.md';
    if (finalPromptTemplate) return finalPromptTemplate.replace('{{WORKFLOW_FILE}}', fn);
    return `@workflow/${fn} 이 워크플로우 plan mode로 실행해 줘.`;
  };

  return (
    <>
      {/* ── Section 1: CLI 선택 ── */}
      <div id="step-1" className="wizard-section">
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

      {/* ── Section 2: 워크플로우 ── */}
      <div id="step-2" className="wizard-section">
        <h3 className="wizard-section-title"><span className="wizard-section-num">2</span>워크플로우</h3>

        <section className="card">
          <div className="wf-tabs">
            <button type="button" className={`wf-tab ${workflowTab === 'create' ? 'active' : ''}`} onClick={() => setWorkflowTab('create')}>새로 만들기</button>
            <button type="button" className={`wf-tab ${workflowTab === 'load' ? 'active' : ''}`} onClick={() => { setWorkflowTab('load'); void loadSavedWorkflows(); }}>불러오기</button>
          </div>

          {workflowTab === 'create' ? (
            <p className="muted" style={{ margin: '8px 0 0', fontSize: 13 }}>아래에서 스킬과 프롬프트를 입력한 뒤 워크플로우 파일을 생성하세요.</p>
          ) : (
            <>
              <div style={{ marginBottom: 8 }}>
                <label className="muted" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>불러올 경로</label>
                <div className="project-path-row">
                  <input className="project-path-input" value={workflowLoadPath || ''} readOnly placeholder="경로를 선택하세요" />
                  <button type="button" className="tiny ghost-light project-path-browse" onClick={() => setWfBrowserOpen(true)}>찾기</button>
                </div>
              </div>
              <FolderBrowserModal browserOpen={wfBrowserOpen} setBrowserOpen={setWfBrowserOpen} initialPath={workflowLoadPath || skillPath} onConfirm={reloadWorkflowsFromPath} title="워크플로우 폴더 선택" />
              {savedWorkflows.length === 0 ? (
                <p className="muted" style={{ margin: '8px 0 0', fontSize: 13 }}>저장된 워크플로우 파일이 없습니다.</p>
              ) : (
                <div className="saved-wf-list">
                  {savedWorkflows.map((wf) => (
                    <button
                      key={wf.path}
                      type="button"
                      className={`saved-wf-item ${selectedSavedWorkflow?.path === wf.path ? 'active' : ''}`}
                      onClick={() => { void setSelectedSavedWorkflow(wf); }}
                    >
                      {wf.name}.md
                    </button>
                  ))}
                </div>
              )}
            </>
          )}
        </section>
      </div>

      {/* ── Section 3: MCP 선택 ── */}
      <div id="step-3" className="wizard-section">
        <h3 className="wizard-section-title"><span className="wizard-section-num">3</span>MCP 선택</h3>

        <section className="card">
          <label className="muted" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>MCP Profile Path</label>
          <div className="project-path-row">
            <input className="project-path-input" value={mcpProfilePath} readOnly placeholder="MCP 프로필 JSON 경로를 선택하세요" />
            <button type="button" className="tiny ghost-light project-path-browse" onClick={() => setMcpBrowserOpen(true)}>찾기</button>
          </div>
        </section>

        <FolderBrowserModal browserOpen={mcpBrowserOpen} setBrowserOpen={setMcpBrowserOpen} initialPath={mcpProfilePath} onConfirm={reloadMcpProfilesFromPath} title="MCP 프로필 폴더 선택" />

        <section className="card mcp-setup-card">
          <div className="mcp-profile-header">
            <p className="mcp-toggle-title">MCP 프로필</p>
            <div className="mcp-summary-row">
              <span className="mcp-summary-pill">selected {effectiveMcpProfiles.length}</span>
              {effectiveMcpProfiles.length < activeMcpProfileOptions.length
                ? <button type="button" className="ghost tiny" onClick={() => toggleAllMcpProfiles(true)}>전체 선택</button>
                : <button type="button" className="ghost tiny" onClick={() => toggleAllMcpProfiles(false)}>전체 해제</button>
              }
            </div>
          </div>

          <div className="mcp-profile-groups">
            <div className="mcp-profile-group">
              <div className="mcp-profile-grid">
                {activeMcpProfileOptions.map((profile) => (
                  <label key={profile.id} className="mcp-profile-item">
                    <input type="checkbox" checked={effectiveMcpProfiles.includes(profile.id)} onChange={(e) => toggleMcpProfile(profile.id, e.target.checked)} />
                    <span className="mcp-profile-name">{profile.label}</span>
                    {profile.desc && <span className="mcp-profile-desc">{profile.desc}</span>}
                  </label>
                ))}
              </div>
            </div>
          </div>
        </section>
      </div>

      {workflowTab === 'create' && (
        <>
          {/* ── Section 4: 스킬 선택 ── */}
          <div id="step-4" className="wizard-section">
            <h3 className="wizard-section-title"><span className="wizard-section-num">4</span>스킬 선택</h3>

            <section className="card">
              <label className="muted" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>Skill Path</label>
              <div className="project-path-row">
                <input className="project-path-input" value={skillPath} readOnly placeholder="스킬 경로를 선택하세요" />
                <button type="button" className="tiny ghost-light project-path-browse" onClick={() => setBrowserOpen(true)}>찾기</button>
              </div>
            </section>

            <FolderBrowserModal browserOpen={browserOpen} setBrowserOpen={setBrowserOpen} initialPath={skillPath} onConfirm={reloadSkillsFromPath} title="Skill 폴더 선택" />

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

          {/* ── Section 5: 프롬프트 입력 ── */}
          <div id="step-5" className="wizard-section">
            <h3 className="wizard-section-title">
              <span className="wizard-section-num">5</span>
              프롬프트 입력
            </h3>

            {effectiveSelectedSkills.length > 0 && (
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

            {activeSkill && (
              <section className="card">
                <div className="skill-detail">
                  <textarea rows={5} value={activeSkillPrompt} onChange={(e) => updateStepPrompt(activeSkill, e.target.value)} className="main-textarea" placeholder="이 스킬과 함께 사용할 프롬프트를 적어주세요" />
                </div>
              </section>
            )}

            {!activeSkill && (
              <section className="card">
                <p className="muted" style={{ margin: 0, fontSize: 13 }}>스킬을 먼저 추가해 주세요.</p>
              </section>
            )}

            {/* 워크플로우 파일 생성 */}
            <section className="card">
              <h3>워크플로우 파일 생성</h3>
              <div style={{ marginBottom: 8 }}>
                <label className="muted" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>출력 경로</label>
                <input type="text" value={skillPath ? `${skillPath}/workflow` : ''} readOnly placeholder="Skill Path를 먼저 선택하세요" style={{ width: '100%', boxSizing: 'border-box' }} />
                <span className="muted" style={{ fontSize: 11 }}>{skillPath ? `${skillPath}/workflow/${workflowFilePath || 'workflow'}.md` : ''}</span>
              </div>
              <div className="button-row" style={{ alignItems: 'center' }}>
                <div className="workflow-name-input">
                  <input type="text" value={workflowFilePath} onChange={(e) => setWorkflowFilePath(e.target.value)} onBlur={() => { void checkUniqueName(); }} placeholder="workflow" className={workflowNameDuplicate ? 'input-error' : ''} />
                  <span className="workflow-name-label">.md</span>
                  {workflowNameDuplicate && <span className="input-error-msg">중복된 이름입니다</span>}
                </div>
                <button type="button" onClick={() => { void generateWorkflow(); }} disabled={generatingMd || !step2Done || workflowNameDuplicate || !!generatedFile}>
                  {generatingMd ? '만드는 중...' : generatedFile ? '완료' : '만들기'}
                </button>
              </div>
            </section>

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
        </>
      )}

      {/* ── 실행 ── */}
      <div id="step-6" className="wizard-section">
        <h3 className="wizard-section-title"><span className="wizard-section-num">{isLoad ? 4 : 6}</span>실행</h3>

        {error && <p className="error-inline">{error}</p>}

        {!step2Done && (
          <section className="card">
            <p className="muted" style={{ margin: 0, fontSize: 13 }}>
              {isLoad ? '워크플로우를 먼저 선택하세요.' : '스킬과 프롬프트를 먼저 입력하세요.'}
            </p>
          </section>
        )}

        <section className="card">
          <div className="button-row">
            <button
              type="button"
              onClick={() => { isLoad && selectedSavedWorkflow ? void openSavedWorkflowTerminal() : void openTerminal(); }}
              disabled={!step2Done || launchingTerminal || (effectiveSelectedSkills.length > 1 && !generatedFile && !isLoad)}
            >
              {launchingTerminal ? '실행 중...' : '터미널 실행'}
            </button>
          </div>
          {step2Done && effectiveSelectedSkills.length > 1 && !generatedFile && !isLoad && (
            <p className="muted" style={{ margin: '8px 0 0', fontSize: 13 }}>먼저 워크플로우 파일을 만들어 주세요.</p>
          )}
        </section>

        <section className="card">
          <div className="canvas-head">
            <h3>프롬프트에 직접 입력</h3>
            <button type="button" className="ghost tiny" disabled={!step2Done} onClick={() => {
              void navigator.clipboard.writeText(getPromptText());
              setCopied(true);
              setTimeout(() => setCopied(false), 2000);
            }}>{copied ? '✓ 복사됨' : '클립보드에 복사'}</button>
          </div>
          <pre className="file-preview" style={!step2Done ? { opacity: 0.4 } : undefined}>
            {step2Done ? getPromptText() : ''}
          </pre>
        </section>

      </div>
    </>
  );
}
