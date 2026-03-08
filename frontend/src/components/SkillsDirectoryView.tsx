import { useState } from 'react';
import type { WorkflowState } from '../hooks/useWorkflowState';
import type { SkillDirectory } from '../types';

type Props = Pick<WorkflowState,
  | 'skillsData' | 'loadSkills' | 'loadingSkills' | 'skillsError'
>;

export function SkillsDirectoryView(props: Props) {
  const { skillsData, loadSkills, loadingSkills, skillsError } = props;
  const [selectedSkill, setSelectedSkill] = useState<SkillDirectory | null>(null);
  const [skillContent, setSkillContent] = useState<string | null>(null);
  const [loadingContent, setLoadingContent] = useState(false);

  const handleSkillClick = async (skill: SkillDirectory) => {
    if (selectedSkill?.path === skill.path) {
      setSelectedSkill(null);
      setSkillContent(null);
      return;
    }
    setSelectedSkill(skill);
    setSkillContent(null);
    if (!skill.hasSkillMd || !skill.skillMdPath) return;
    setLoadingContent(true);
    try {
      const res = await fetch(`/api/workflows/file/read?path=${encodeURIComponent(skill.skillMdPath)}`);
      if (!res.ok) return;
      const body = await res.json();
      setSkillContent(body.content);
    } catch { /* ignore */ }
    finally { setLoadingContent(false); }
  };

  return (
    <>
      <section className="card">
        <div className="skills-head">
          <h3>Skills Directory</h3>
          <button type="button" className="ghost" onClick={() => loadSkills()} disabled={loadingSkills}>
            {loadingSkills ? '불러오는 중...' : '새로고침'}
          </button>
        </div>

        {skillsError ? <p className="error-inline">{skillsError}</p> : null}

        {!skillsData && !loadingSkills ? (
          <p className="muted">스킬 목록을 불러오지 못했습니다.</p>
        ) : null}

        {skillsData ? (
          <div className="skills-tree">
            <p className="muted mono">
              {skillsData.skillsRoot} ({skillsData.total} skills)
            </p>
            <div className="skills-dir-list">
              {skillsData.skills.map((skill) => (
                <button
                  key={skill.path}
                  type="button"
                  className={`skills-dir-item ${selectedSkill?.path === skill.path ? 'active' : ''}`}
                  onClick={() => { void handleSkillClick(skill); }}
                >
                  <span className="skills-dir-icon">{skill.hasSkillMd ? '\u{1F4C4}' : '\u{1F4C1}'}</span>
                  <span className="skills-dir-name">{skill.name}</span>
                </button>
              ))}
            </div>
          </div>
        ) : null}
      </section>

      {selectedSkill && (
        <section className="card">
          <div className="canvas-head">
            <h3>{selectedSkill.name}</h3>
            <button type="button" className="ghost tiny" onClick={() => { setSelectedSkill(null); setSkillContent(null); }}>닫기</button>
          </div>
          {loadingContent ? (
            <p className="muted">불러오는 중...</p>
          ) : skillContent ? (
            <pre className="file-preview">{skillContent}</pre>
          ) : (
            <p className="muted">SKILL.md 파일이 없습니다.</p>
          )}
        </section>
      )}
    </>
  );
}
