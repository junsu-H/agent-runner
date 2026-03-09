import { DragEvent } from 'react';
import type { McpProfileOption } from '../workflow-constants';
import {
  buildMcpSetupPrompt,
  dragMimeType,
  mcpSetupSkillName,
  readDragPayload,
} from '../workflow-constants';

export function useWorkflowSkills(
  selectedSkills: string[],
  setSelectedSkills: React.Dispatch<React.SetStateAction<string[]>>,
  setStepPrompts: React.Dispatch<React.SetStateAction<Record<string, string>>>,
  setActiveSkill: React.Dispatch<React.SetStateAction<string | null>>,
  effectiveMcpProfiles: string[],
  skillPath: string,
  workflowFilePath: string,
  setWorkflowNameDuplicate: React.Dispatch<React.SetStateAction<boolean>>,
  setSelectedMcpProfiles: React.Dispatch<React.SetStateAction<string[]>>,
  activeMcpProfileOptions: McpProfileOption[],
) {
  /* ── Skill management ── */
  const appendSkillToWorkflow = (skillName: string) => {
    setSelectedSkills((prev) => prev.includes(skillName) ? prev : [...prev, skillName]);
    setStepPrompts((prev) => prev[skillName] ? prev : {
      ...prev,
      [skillName]: skillName === mcpSetupSkillName ? buildMcpSetupPrompt(effectiveMcpProfiles) : '',
    });
    setActiveSkill(skillName);
  };

  const reorderSkill = (fromIndex: number, toIndex: number) => {
    setSelectedSkills((prev) => {
      if (fromIndex < 0 || fromIndex >= prev.length || toIndex < 0 || toIndex >= prev.length || fromIndex === toIndex) return prev;
      const next = [...prev];
      const [moved] = next.splice(fromIndex, 1);
      next.splice(toIndex, 0, moved);
      return next;
    });
  };

  const removeSelectedSkill = (skillName: string) => {
    setSelectedSkills((prev) => prev.filter((n) => n !== skillName));
  };

  const updateStepPrompt = (skillName: string, prompt: string) => {
    setStepPrompts((prev) => ({ ...prev, [skillName]: prompt }));
  };

  /* ── MCP profile functions ── */
  const toggleMcpProfile = (profileId: string, enabled: boolean) => {
    if (!activeMcpProfileOptions.find((item) => item.id === profileId)) return;
    setSelectedMcpProfiles((prev) =>
      enabled ? (prev.includes(profileId) ? prev : [...prev, profileId]) : prev.filter((id) => id !== profileId),
    );
  };

  const toggleAllMcpProfiles = (enabled: boolean) => {
    setSelectedMcpProfiles(enabled ? activeMcpProfileOptions.map((p) => p.id) : []);
  };

  /* ── Check unique name ── */
  const checkUniqueName = async () => {
    const name = workflowFilePath.trim() || 'workflow';
    try {
      const res = await fetch(`/api/workflows/file/unique-name?projectPath=${encodeURIComponent(skillPath)}&name=${encodeURIComponent(name)}`);
      if (!res.ok) return;
      const body = await res.json() as { uniqueName: string; exists: boolean };
      setWorkflowNameDuplicate(body.exists);
    } catch { /* ignore */ }
  };

  /* ── Drag handlers ── */
  const onCatalogDragStart = (event: DragEvent<HTMLButtonElement>, skillName: string) => {
    event.dataTransfer.effectAllowed = 'copy';
    event.dataTransfer.setData(dragMimeType, JSON.stringify({ source: 'catalog', skillName }));
  };

  const onWorkflowDragStart = (event: DragEvent<HTMLDivElement>, skillName: string, index: number) => {
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData(dragMimeType, JSON.stringify({ source: 'workflow', skillName, index }));
  };

  const onCanvasDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    const d = readDragPayload(event);
    if (!d) return;
    if (d.source === 'catalog') { appendSkillToWorkflow(d.skillName); return; }
    if (d.source === 'workflow' && typeof d.index === 'number') reorderSkill(d.index, selectedSkills.length - 1);
  };

  const onBoxDrop = (event: DragEvent<HTMLDivElement>, targetIndex: number) => {
    event.preventDefault();
    event.stopPropagation();
    const d = readDragPayload(event);
    if (!d) return;
    if (d.source === 'catalog') {
      setSelectedSkills((prev) => {
        if (prev.includes(d.skillName)) return prev;
        const next = [...prev]; next.splice(targetIndex, 0, d.skillName); return next;
      });
      setStepPrompts((prev) => prev[d.skillName] ? prev : {
        ...prev, [d.skillName]: d.skillName === mcpSetupSkillName ? buildMcpSetupPrompt(effectiveMcpProfiles) : '',
      });
      setActiveSkill(d.skillName);
      return;
    }
    if (d.source === 'workflow' && typeof d.index === 'number') reorderSkill(d.index, targetIndex);
  };

  return {
    appendSkillToWorkflow,
    reorderSkill,
    removeSelectedSkill,
    updateStepPrompt,
    toggleMcpProfile,
    toggleAllMcpProfiles,
    checkUniqueName,
    onCatalogDragStart,
    onWorkflowDragStart,
    onCanvasDrop,
    onBoxDrop,
  };
}
