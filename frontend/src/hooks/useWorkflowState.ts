import { useEffect, useMemo, useState } from 'react';
import type {
  SkillListResponse,
  RunWorkflowRequest,
  SupportedCli,
} from '../types';
import type { McpProfileOption } from '../workflow-constants';
import {
  buildMcpSetupPrompt,
  defaultProjectPath,
  MainView,
  mcpProfileOptions as hardcodedMcpProfileOptions,
  mcpSetupSkillName,
} from '../workflow-constants';
import { useWorkflowSkills } from './useWorkflowSkills';

const normPath = (p: string) => p.replace(/\\/g, '/');

export function useWorkflowState() {
  /* ── State ── */
  const [cli, setCli] = useState<SupportedCli>('claude');
  const [skillPath, setSkillPath] = useState('');
  const dryRun = false;
  const [selectedMcpProfiles, setSelectedMcpProfiles] = useState<string[]>([]);
  const [view, setView] = useState<MainView>('workflow');
  const [skillsData, setSkillsData] = useState<SkillListResponse | null>(null);
  const [selectedSkills, setSelectedSkills] = useState<string[]>([]);
  const [activeSkill, setActiveSkill] = useState<string | null>(null);
  const [stepPrompts, setStepPrompts] = useState<Record<string, string>>({});
  const [loadingSkills, setLoadingSkills] = useState(false);
  const [error, setError] = useState('');
  const [skillsError, setSkillsError] = useState('');
  const [skillQuery, setSkillQuery] = useState('');
  const [mcpProfilePath, setMcpProfilePath] = useState('');
  const [dynamicMcpProfiles, setDynamicMcpProfiles] = useState<McpProfileOption[]>([]);
  const [workflowFilePath, _setWorkflowFilePath] = useState('workflow');
  const [workflowNameDuplicate, setWorkflowNameDuplicate] = useState(false);
  const setWorkflowFilePath = (v: string) => { _setWorkflowFilePath(v); setWorkflowNameDuplicate(false); };

  // Final prompt template
  const [finalPromptTemplate, setFinalPromptTemplate] = useState('');

  // Generate + Terminal state
  const [generatingMd, setGeneratingMd] = useState(false);
  const [generatedFile, setGeneratedFile] = useState<string | null>(null);
  const [generatedFilePath, setGeneratedFilePath] = useState<string | null>(null);
  const [generatedFileContent, setGeneratedFileContent] = useState<string | null>(null);
  const [launchingTerminal, setLaunchingTerminal] = useState(false);

  // Saved workflow (불러오기) state
  type SavedWorkflow = { name: string; path: string };
  const [savedWorkflows, setSavedWorkflows] = useState<SavedWorkflow[]>([]);
  const [selectedSavedWorkflow, _setSelectedSavedWorkflow] = useState<SavedWorkflow | null>(null);
  const [workflowTab, setWorkflowTab] = useState<'create' | 'load'>('create');

  /* ── Derived ── */
  const availableSkills = useMemo(() => {
    if (!skillsData) return [];
    return skillsData.skills.filter((s) => s.hasSkillMd && s.name !== mcpSetupSkillName);
  }, [skillsData]);

  const filteredAvailableSkills = useMemo(() => {
    const keyword = skillQuery.trim().toLowerCase();
    if (!keyword) return availableSkills;
    return availableSkills.filter((s) => s.name.toLowerCase().includes(keyword));
  }, [availableSkills, skillQuery]);

  const activeMcpProfileOptions = useMemo(() => {
    return dynamicMcpProfiles;
  }, [dynamicMcpProfiles]);

  const effectiveMcpProfiles = useMemo(() => {
    const selected = new Set(selectedMcpProfiles);
    return activeMcpProfileOptions.map((o) => o.id).filter((id) => selected.has(id));
  }, [selectedMcpProfiles, activeMcpProfileOptions]);

  const effectiveSelectedSkills = useMemo(() => {
    return selectedSkills.filter((s) => s !== mcpSetupSkillName);
  }, [selectedSkills]);

  const activeSkillPrompt = activeSkill ? (stepPrompts[activeSkill] ?? '') : '';

  /* ── Payload ── */
  const buildPayload = (): RunWorkflowRequest => {
    const prompts: Record<string, string> = {};
    effectiveSelectedSkills.forEach((s) => { prompts[s] = stepPrompts[s]?.trim() ?? ''; });
    return {
      projectPath: skillPath.trim(), issueKey: null, requestText: '', cli, dryRun,
      selectedSkills: effectiveSelectedSkills, stepPrompts: prompts,
      commandStepSkills: [],
      mcpProfiles: effectiveMcpProfiles,
      mcpProfilePath: mcpProfilePath.trim(),
      openTerminalAfter: false,
      workflowName: workflowFilePath.trim() || 'workflow',
    };
  };

  /* ── Validation ── */
  const validateWorkflowInput = (): boolean => {
    if (!skillPath.trim()) { setError('Skill Path를 먼저 선택하세요.'); return false; }
    if (effectiveSelectedSkills.length === 0) { setError('캔버스에 스킬을 1개 이상 추가하세요.'); return false; }
    for (const skill of effectiveSelectedSkills) {
      if (!(stepPrompts[skill]?.trim())) { setError(`스킬별 프롬프트가 필요합니다: ${skill}`); return false; }
    }
    return true;
  };

  /* ── Skill / drag / MCP helpers (extracted) ── */
  const {
    appendSkillToWorkflow, reorderSkill, removeSelectedSkill, updateStepPrompt,
    toggleMcpProfile, toggleAllMcpProfiles, checkUniqueName,
    onCatalogDragStart, onWorkflowDragStart, onCanvasDrop, onBoxDrop,
  } = useWorkflowSkills(
    selectedSkills, setSelectedSkills, setStepPrompts, setActiveSkill,
    effectiveMcpProfiles, skillPath, workflowFilePath,
    setWorkflowNameDuplicate, setSelectedMcpProfiles, activeMcpProfileOptions,
  );

  /* ── Async: loadSkills ── */
  const loadSkills = async (root?: string) => {
    setLoadingSkills(true); setSkillsError('');
    try {
      const params = root ? `?agentRunnerRoot=${encodeURIComponent(root)}` : '';
      const res = await fetch(`/api/skills${params}`);
      const body = await res.json();
      if (!res.ok) throw new Error(body.error ?? `skills load failed: ${res.status}`);
      const data = body as SkillListResponse;
      setSkillsData(data);
      if (!root) {
        setSkillPath(normPath(data.agentRunnerRoot));
      }
      const validNames = new Set(data.skills.filter((s) => s.hasSkillMd).map((s) => s.name));
      setSelectedSkills((prev) => prev.filter((n) => validNames.has(n)));
      setStepPrompts((prev) => {
        const next: Record<string, string> = {};
        Object.entries(prev).forEach(([k, v]) => { if (validNames.has(k)) next[k] = v; });
        return next;
      });
    } catch (e) {
      setSkillsError(e instanceof Error ? e.message : 'Unknown error');
    } finally { setLoadingSkills(false); }
  };

  /* ── Async: generateWorkflow (Section 2) ── */
  const generateWorkflow = async () => {
    setError('');
    if (!validateWorkflowInput()) return;
    if (workflowNameDuplicate) { setError('중복된 파일 이름입니다. 다른 이름을 입력하세요.'); return; }
    setGeneratingMd(true);
    try {
      const res = await fetch('/api/workflows/linked-list/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(buildPayload()),
      });
      const body = await res.json();
      if (!res.ok) throw new Error(body.error ?? `생성 실패: ${res.status}`);
      const result = body as { fileName: string; filePath: string; skillCount: number; skills: string[] };
      setGeneratedFile(result.fileName);
      setGeneratedFilePath(normPath(result.filePath));
      // Auto-load file content for preview
      try {
        const readRes = await fetch(`/api/workflows/file/read?path=${encodeURIComponent(normPath(result.filePath))}`);
        const readBody = await readRes.json();
        if (readRes.ok) setGeneratedFileContent(readBody.content);
      } catch { /* ignore */ }
    } catch (e) {
      const msg = e instanceof Error ? e.message : '만들기 실패';
      setError(msg);
    } finally {
      setGeneratingMd(false);
    }
  };

  /* ── Open generated file ── */
  const openGeneratedFile = async () => {
    if (!generatedFilePath) return;
    try {
      const res = await fetch(`/api/workflows/file/read?path=${encodeURIComponent(generatedFilePath)}`);
      const body = await res.json();
      if (!res.ok) throw new Error(body.error ?? '파일 읽기 실패');
      setGeneratedFileContent(body.content);
    } catch (e) {
      setError(e instanceof Error ? e.message : '파일 열기 실패');
    }
  };

  /* ── Async: openTerminal (Section 3) ── */
  const openTerminal = async () => {
    setError('');
    // 스킬 1개: 워크플로우 파일 자동 생성
    if (!generatedFile) {
      if (effectiveSelectedSkills.length <= 1) {
        await generateWorkflow();
      } else {
        setError('먼저 워크플로우 파일을 생성하세요.');
        return;
      }
    }

    setLaunchingTerminal(true);
    try {
      const res = await fetch('/api/workflows/linked-list/run/terminal', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(buildPayload()),
      });
      const body = await res.json();
      if (!res.ok) throw new Error(body.error ?? `터미널 실행 실패: ${res.status}`);
    } catch (e) {
      const msg = e instanceof Error ? e.message : '터미널 실행 실패';
      setError(msg);
    } finally {
      setLaunchingTerminal(false);
    }
  };

  /* ── Effects ── */
  useEffect(() => { loadSkills(); }, []); // initial load

  const reloadSkillsFromPath = (path: string) => {
    setSkillPath(normPath(path));
    loadSkills(path);
  };

  /* ── Async: loadMcpProfiles ── */
  const loadMcpProfiles = async (path: string) => {
    if (!path.trim()) { setDynamicMcpProfiles([]); return; }
    try {
      const res = await fetch(`/api/mcp/profiles?path=${encodeURIComponent(path)}`);
      if (!res.ok) return;
      const entries: { id: string; name: string }[] = await res.json();
      setDynamicMcpProfiles(entries.map((e) => ({ id: e.id, label: e.name, desc: '' })));
      setSelectedMcpProfiles([]);
    } catch { /* ignore */ }
  };

  const reloadMcpProfilesFromPath = (path: string) => {
    setMcpProfilePath(normPath(path));
    loadMcpProfiles(path);
  };

  /* ── Async: loadSavedWorkflows ── */
  const [workflowLoadPath, setWorkflowLoadPath] = useState('');
  const loadSavedWorkflows = async (customPath?: string) => {
    const basePath = customPath ?? (workflowLoadPath || skillPath);
    if (!basePath.trim()) { setSavedWorkflows([]); return; }
    try {
      const res = await fetch(`/api/workflows/file/list?projectPath=${encodeURIComponent(basePath)}`);
      if (!res.ok) return;
      const entries: SavedWorkflow[] = await res.json();
      setSavedWorkflows(entries);
    } catch { /* ignore */ }
  };
  const reloadWorkflowsFromPath = (path: string) => {
    const normalized = normPath(path);
    setWorkflowLoadPath(normalized);
    loadSavedWorkflows(normalized);
  };

  const selectSavedWorkflow = async (wf: SavedWorkflow | null) => {
    _setSelectedSavedWorkflow(wf);
    if (!wf) { setGeneratedFileContent(null); setGeneratedFile(null); return; }
    // Auto-load file content for preview
    try {
      const res = await fetch(`/api/workflows/file/read?path=${encodeURIComponent(wf.path)}`);
      const body = await res.json();
      if (!res.ok) throw new Error(body.error ?? '파일 읽기 실패');
      setGeneratedFile(wf.name + '.md');
      setGeneratedFileContent(body.content);
    } catch (e) {
      setError(e instanceof Error ? e.message : '파일 열기 실패');
    }
  };

  const previewSavedWorkflow = async () => {
    if (!selectedSavedWorkflow) return;
    try {
      const res = await fetch(`/api/workflows/file/read?path=${encodeURIComponent(selectedSavedWorkflow.path)}`);
      const body = await res.json();
      if (!res.ok) throw new Error(body.error ?? '파일 읽기 실패');
      setGeneratedFile(selectedSavedWorkflow.name + '.md');
      setGeneratedFileContent(body.content);
    } catch (e) {
      setError(e instanceof Error ? e.message : '파일 열기 실패');
    }
  };

  const openSavedWorkflowTerminal = async () => {
    if (!selectedSavedWorkflow) return;
    setError('');
    setLaunchingTerminal(true);
    try {
      const res = await fetch('/api/workflows/linked-list/run/terminal', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          projectPath: skillPath.trim(), issueKey: null, requestText: '', cli, dryRun: false,
          selectedSkills: [], stepPrompts: {}, commandStepSkills: [],
          mcpProfiles: effectiveMcpProfiles, mcpProfilePath: mcpProfilePath.trim(),
          openTerminalAfter: false, workflowName: selectedSavedWorkflow.name,
        }),
      });
      const body = await res.json();
      if (!res.ok) throw new Error(body.error ?? `터미널 실행 실패: ${res.status}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : '터미널 실행 실패');
    } finally {
      setLaunchingTerminal(false);
    }
  };

  // Reload saved workflows, MCP profiles, and final prompt template when skillPath changes
  useEffect(() => {
    if (!skillPath) return;
    setWorkflowLoadPath(normPath(skillPath));
    loadSavedWorkflows(skillPath);
    // MCP Profile Path도 skillPath 기반으로 자동 설정
    const mcpDefault = normPath(skillPath) + '/mcp-profiles';
    setMcpProfilePath(mcpDefault);
    loadMcpProfiles(mcpDefault);
    // Load FINAL_PROMPT.md template
    const templatePath = normPath(skillPath) + '/FINAL_PROMPT.md';
    fetch(`/api/workflows/file/read?path=${encodeURIComponent(templatePath)}`)
      .then((r) => r.ok ? r.json() : null)
      .then((body) => { if (body?.content) setFinalPromptTemplate(body.content.trim()); })
      .catch(() => { /* ignore */ });
  }, [skillPath]);

  useEffect(() => {
    if (selectedSkills.length === 0) { setActiveSkill(null); return; }
    if (!activeSkill || !selectedSkills.includes(activeSkill)) setActiveSkill(selectedSkills[0]);
  }, [selectedSkills, activeSkill]);

  useEffect(() => {
    if (effectiveMcpProfiles.length === 0) return;
    setStepPrompts((prev) => ({ ...prev, [mcpSetupSkillName]: buildMcpSetupPrompt(effectiveMcpProfiles) }));
  }, [effectiveMcpProfiles]);

  /* ── Progress ── */
  const step1Done = effectiveSelectedSkills.length > 0;
  const step2Done = step1Done && effectiveSelectedSkills.every((s) => !!stepPrompts[s]?.trim());
  const step3Done = step2Done && !!generatedFile;
  const progressPercent = step3Done ? 100 : step2Done ? 66 : step1Done ? 33 : 0;

  return {
    cli, setCli, skillPath, setSkillPath, reloadSkillsFromPath,
    mcpProfilePath, reloadMcpProfilesFromPath, activeMcpProfileOptions,
    workflowFilePath, setWorkflowFilePath, workflowNameDuplicate,
    view, setView, skillsData, selectedSkills, activeSkill, setActiveSkill,
    stepPrompts, loadingSkills, error, skillsError, skillQuery, setSkillQuery,
    generatingMd, generatedFile, generatedFilePath, generatedFileContent, setGeneratedFileContent, openGeneratedFile, launchingTerminal,
    filteredAvailableSkills, effectiveMcpProfiles, effectiveSelectedSkills, activeSkillPrompt,
    appendSkillToWorkflow, removeSelectedSkill, updateStepPrompt, toggleMcpProfile, toggleAllMcpProfiles, checkUniqueName,
    onCatalogDragStart, onWorkflowDragStart, onCanvasDrop, onBoxDrop,
    loadSkills, generateWorkflow, openTerminal,
    workflowTab, setWorkflowTab, savedWorkflows, loadSavedWorkflows, workflowLoadPath, reloadWorkflowsFromPath,
    selectedSavedWorkflow, setSelectedSavedWorkflow: selectSavedWorkflow, previewSavedWorkflow, openSavedWorkflowTerminal, finalPromptTemplate,
    step1Done, step2Done, step3Done, progressPercent,
  };
}

export type WorkflowState = ReturnType<typeof useWorkflowState>;
