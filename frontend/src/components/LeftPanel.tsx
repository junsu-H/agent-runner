import { useCallback, useEffect, useState } from 'react';
import type { WorkflowState } from '../hooks/useWorkflowState';
import { FolderBrowserModal } from './FolderBrowserModal';

type Props = Pick<WorkflowState,
  | 'view' | 'setView' | 'skillPath' | 'reloadSkillsFromPath'
  | 'skillQuery' | 'setSkillQuery' | 'filteredAvailableSkills' | 'skillsData'
  | 'onCatalogDragStart' | 'appendSkillToWorkflow'
  | 'effectiveSelectedSkills' | 'activeSkill' | 'setActiveSkill' | 'stepPrompts'
  | 'cli' | 'effectiveMcpProfiles'
>;

type DirEntry = { name: string; path: string };
type DirListResponse = { current: string; parent: string | null; directories: DirEntry[] };

type PrereqStatus = {
  id: string; name: string; installed: boolean; version: string;
  installCmd: string; optional: boolean;
};
type InstallState = 'idle' | 'installing' | 'success' | 'failed';

export function LeftPanel(props: Props) {
  const {
    view, setView, skillPath, reloadSkillsFromPath,
    skillQuery, setSkillQuery, filteredAvailableSkills, skillsData,
    onCatalogDragStart, appendSkillToWorkflow,
    effectiveSelectedSkills, activeSkill, setActiveSkill, stepPrompts,
    cli, effectiveMcpProfiles,
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

  /* ── Folder Browser state ── */
  const [browserOpen, setBrowserOpen] = useState(false);
  const [browsePath, setBrowsePath] = useState('');
  const [selectedDir, setSelectedDir] = useState<string | null>(null);
  const [dirList, setDirList] = useState<DirEntry[]>([]);
  const [parentPath, setParentPath] = useState<string | null>(null);
  const [loadingDirs, setLoadingDirs] = useState(false);
  const [addressInput, setAddressInput] = useState('');

  const fetchDirs = useCallback(async (path: string) => {
    setLoadingDirs(true);
    setSelectedDir(null);
    try {
      const res = await fetch(`/api/directories?path=${encodeURIComponent(path)}`);
      if (!res.ok) return;
      const data: DirListResponse = await res.json();
      setBrowsePath(data.current);
      setAddressInput(data.current);
      setParentPath(data.parent);
      setDirList(data.directories);
    } catch { /* ignore */ }
    finally { setLoadingDirs(false); }
  }, []);

  const openBrowser = () => {
    setBrowserOpen(true);
    fetchDirs(skillPath || '');
  };

  const confirmSelection = () => {
    const selected = selectedDir ?? browsePath;
    reloadSkillsFromPath(selected);
    setBrowserOpen(false);
  };

  useEffect(() => {
    if (!browserOpen) {
      setDirList([]);
      setParentPath(null);
      setSelectedDir(null);
    }
  }, [browserOpen]);

  return (
    <aside className="left-panel">
      <h2>Agent Runner</h2>

      <div className="group left-nav-group">
        <label className="group-title">화면</label>
        <div className="left-view-tabs">
          <button type="button" className={`left-view-tab ${view === 'workflow' ? 'active' : ''}`} onClick={() => setView('workflow')}>
            워크플로우
          </button>
          <button type="button" className={`left-view-tab ${view === 'skills' ? 'active' : ''}`} onClick={() => setView('skills')}>
            SKILL 정보 보기
          </button>
          <button type="button" className={`left-view-tab ${view === 'promptGuide' ? 'active' : ''}`} onClick={() => setView('promptGuide')}>
            프롬프트 작성 요령
          </button>
        </div>
      </div>

      <div className="group left-project-group">
        <label className="group-title">Skill Path</label>
        <div className="project-path-row">
          <input
            className="project-path-input"
            value={skillPath}
            readOnly
            placeholder="스킬 경로를 선택하세요"
          />
          <button type="button" className="tiny ghost-light project-path-browse" onClick={openBrowser}>
            찾기
          </button>
        </div>
      </div>

      {/* ── Folder Browser Modal ── */}
      <FolderBrowserModal
        browserOpen={browserOpen}
        setBrowserOpen={setBrowserOpen}
        browsePath={browsePath}
        selectedDir={selectedDir}
        setSelectedDir={setSelectedDir}
        dirList={dirList}
        parentPath={parentPath}
        loadingDirs={loadingDirs}
        addressInput={addressInput}
        setAddressInput={setAddressInput}
        fetchDirs={fetchDirs}
        confirmSelection={confirmSelection}
      />

      {view === 'workflow' && (
        <>
          {/* Skill Palette */}
          <div className="group">
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

          {/* Execution Summary */}
          <div className="group">
            <label className="group-title">실행 요약</label>
            <div className="execution-summary">
              <p><strong>CLI:</strong> {cli}</p>
              <p><strong>스킬 수:</strong> {effectiveSelectedSkills.length}개</p>
              <p><strong>MCP:</strong> {effectiveMcpProfiles.length > 0 ? `${effectiveMcpProfiles.length} profiles` : 'None'}</p>
            </div>
          </div>
        </>
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

      <p className="made-by">Made By junsu-H</p>
    </aside>
  );
}
