import { useWorkflowState } from './hooks/useWorkflowState';
import { LeftPanel } from './components/LeftPanel';
import { WorkflowView } from './components/WorkflowView';
import { SkillsDirectoryView } from './components/SkillsDirectoryView';
import { PromptGuideView } from './components/PromptGuideView';

export function App() {
  const state = useWorkflowState();

  return (
    <div className="layout">
      <LeftPanel {...state} />

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
