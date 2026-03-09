import { useMemo } from 'react';
import { useWorkflowState } from './hooks/useWorkflowState';
import { LeftPanel } from './components/LeftPanel';
import { WorkflowView } from './components/WorkflowView';
import { SkillsDirectoryView } from './components/SkillsDirectoryView';
import { PromptGuideView } from './components/PromptGuideView';
// VerticalProgress removed — now horizontal in LeftPanel

export function App() {
  const state = useWorkflowState();

  const progressSteps = useMemo(() => [
    { id: 'step-1', label: 'CLI', done: true },
    { id: 'step-2', label: '스킬', done: state.step1Done },
    { id: 'step-3', label: '프롬프트', done: state.step2Done },
    { id: 'step-4', label: 'MCP', done: state.step3Done },
    { id: 'step-5', label: '실행', done: state.step3Done },
  ], [state.step1Done, state.step2Done, state.step3Done]);

  return (
    <div className="layout">
      <LeftPanel {...state} progressSteps={progressSteps} />

      <main className="main-panel">
        {state.view === 'skills' ? (
          <SkillsDirectoryView {...state} />
        ) : state.view === 'promptGuide' ? (
          <PromptGuideView />
        ) : (
          <WorkflowView {...state} />
        )}
      </main>
    </div>
  );
}
