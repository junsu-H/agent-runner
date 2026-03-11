import { useCallback, useEffect, useState } from 'react';
import type { WorkflowState } from '../hooks/useWorkflowState';

type ProgressStep = { id: string; label: string; done: boolean };

type Props = Pick<WorkflowState,
  | 'view' | 'setView'
  | 'skillQuery' | 'setSkillQuery' | 'filteredAvailableSkills' | 'skillsData'
  | 'onCatalogDragStart' | 'appendSkillToWorkflow'
  | 'effectiveSelectedSkills' | 'activeSkill' | 'setActiveSkill' | 'stepPrompts'
> & { progressSteps?: ProgressStep[]; onStartTutorial?: () => void };

type PrereqStatus = {
  id: string; name: string; installed: boolean; version: string;
  installCmd: string; optional: boolean;
};
type InstallState = 'idle' | 'installing' | 'success' | 'failed';

export function LeftPanel(props: Props) {
  const {
    view, setView,
    skillQuery, setSkillQuery, filteredAvailableSkills, skillsData,
    onCatalogDragStart, appendSkillToWorkflow,
    effectiveSelectedSkills, activeSkill, setActiveSkill, stepPrompts,
    progressSteps, onStartTutorial,
  } = props;

  /* ── Prerequisites state ── */
  const [prereqs, setPrereqs] = useState<PrereqStatus[]>([]);
  const [installStates, setInstallStates] = useState<Record<string, InstallState>>({});

  const checkPrereqs = useCallback(async () => {
    try {
      const res = await fetch('/api/prerequisites/check');
      if (!res.ok) return;
      setPrereqs(await res.json());
    } catch { /* ignore */ }
  }, []);

  useEffect(() => { checkPrereqs(); }, [checkPrereqs]);

  const installPrereq = async (id: string) => {
    setInstallStates((prev) => ({ ...prev, [id]: 'installing' }));
    try {
      const res = await fetch(`/api/prerequisites/${id}/install`, { method: 'POST' });
      const body = await res.json();
      setInstallStates((prev) => ({ ...prev, [id]: body.success ? 'success' : 'failed' }));
      if (body.success) checkPrereqs();
    } catch {
      setInstallStates((prev) => ({ ...prev, [id]: 'failed' }));
    }
  };

  return (
    <aside className="left-panel">
      <button type="button" className="tutorial-start-btn" onClick={onStartTutorial}>
        튜토리얼 시작하기
      </button>
      <h2>Agent Runner</h2>

      <div className="group left-nav-group">
        <label className="group-title">화면</label>
        <nav className="left-view-tabs">
          <button type="button" className={`left-view-tab ${view === 'workflow' ? 'active' : ''}`} onClick={() => setView('workflow')}>
            <span className="left-view-tab-label">워크플로우</span>
            <span className="left-view-tab-chevron">›</span>
          </button>
          <button type="button" className={`left-view-tab ${view === 'skills' ? 'active' : ''}`} onClick={() => setView('skills')}>
            <span className="left-view-tab-label">SKILL 정보 보기</span>
            <span className="left-view-tab-chevron">›</span>
          </button>
          <button type="button" className={`left-view-tab ${view === 'promptGuide' ? 'active' : ''}`} onClick={() => setView('promptGuide')}>
            <span className="left-view-tab-label">프롬프트 작성 요령</span>
            <span className="left-view-tab-chevron">›</span>
          </button>
        </nav>
      </div>

      {view === 'workflow' && (
        <>
          {/* Skill Palette */}
          <div id="skill-palette" className="group">
            <div className="skills-head-left">
              <label className="group-title">Skill Palette (Drag)</label>
            </div>
            <p className="hint">좌측 스킬을 드래그해서 우측 Workflow Canvas에 놓으세요.</p>

            <label className="search-label">
              스킬 검색
              <input className="search-input" value={skillQuery} onChange={(e) => setSkillQuery(e.target.value)} placeholder="스킬 이름으로 검색" />
            </label>

            {!skillsData ? (
              <p className="hint">스킬 정보를 불러오는 중입니다.</p>
            ) : (
              <div className="skill-picker">
                {filteredAvailableSkills.length === 0 ? (
                  <p className="hint">검색 결과가 없습니다.</p>
                ) : (
                  filteredAvailableSkills.map((skill) => (
                    <button
                      key={skill.path}
                      type="button"
                      className="skill-chip"
                      draggable
                      onDragStart={(event) => onCatalogDragStart(event, skill.name)}
                      onClick={() => appendSkillToWorkflow(skill.name)}
                    >
                      {skill.name}
                    </button>
                  ))
                )}
              </div>
            )}
          </div>

          {/* Skill Navigator */}
          {effectiveSelectedSkills.length > 0 && (
            <div className="group">
              <label className="group-title">스킬 네비게이터</label>
              <div className="skill-navigator">
                {effectiveSelectedSkills.map((skill, idx) => (
                  <button
                    key={skill}
                    type="button"
                    className={`skill-nav-item ${activeSkill === skill ? 'active' : ''}`}
                    onClick={() => setActiveSkill(skill)}
                  >
                    <span className="skill-nav-num">{idx + 1}</span>
                    <span className="skill-nav-name">{skill}</span>
                    {stepPrompts[skill]?.trim() ? <span className="skill-nav-check">&#10003;</span> : null}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Prerequisites */}
          <div className="group prerequisites-group">
            <div className="prereq-header">
              <label className="group-title">필수 설치 항목</label>
              <button type="button" className="tiny ghost-light" onClick={() => { void checkPrereqs(); }}>새로고침</button>
            </div>
            <ul className="prerequisites-list">
              {prereqs.map((p) => {
                const st = installStates[p.id] ?? 'idle';
                return (
                  <li key={p.id} className={p.installed ? 'prereq-installed' : ''}>
                    <span className="prereq-name">
                      {p.installed ? <span className="prereq-ok">&#10003;</span> : <span className="prereq-miss">&#10007;</span>}
                      {p.name}
                      {p.optional && <span className="prereq-optional">선택</span>}
                    </span>
                    {p.installed ? (
                      <span className="prereq-version">{p.version}</span>
                    ) : p.installCmd ? (
                      <button
                        type="button"
                        className="prereq-install-btn"
                        disabled={st === 'installing'}
                        onClick={() => { void installPrereq(p.id); }}
                      >
                        {st === 'installing' ? '설치 중...' : st === 'failed' ? '재시도' : '설치'}
                      </button>
                    ) : (
                      <span className="prereq-hint">ghostty.org</span>
                    )}
                  </li>
                );
              })}
              {prereqs.length === 0 && <li className="prereq-loading">확인 중...</li>}
            </ul>
          </div>

          {/* Horizontal Progress */}
          {progressSteps && progressSteps.length > 0 && (
            <div className="group">
              <nav className="horizontal-progress">
                {progressSteps.map((step, idx) => (
                  <div key={step.id} className="hp-step-wrapper">
                    {idx > 0 && <div className={`hp-line ${step.done ? 'done' : ''}`} />}
                    <button
                      type="button"
                      className={`hp-dot ${step.done ? 'done' : ''}`}
                      onClick={() => document.getElementById(step.id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })}
                      title={step.label}
                    >
                      {step.done ? '\u2713' : idx + 1}
                    </button>
                    <span className="hp-label">{step.label}</span>
                  </div>
                ))}
              </nav>
            </div>
          )}

          <p className="made-by">Made By junsu-H</p>
        </>
      )}

    </aside>
  );
}
