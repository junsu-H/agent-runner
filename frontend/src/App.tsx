import { useMemo } from 'react';
import { useWorkflowState } from './hooks/useWorkflowState';
import { useTutorial } from './hooks/useTutorial';
import { LeftPanel } from './components/LeftPanel';
import { WorkflowView } from './components/WorkflowView';
import { SkillsDirectoryView } from './components/SkillsDirectoryView';
import { PromptGuideView } from './components/PromptGuideView';
import { TutorialOverlay } from './components/TutorialOverlay';

export function App() {
  const state = useWorkflowState();
  const tutorial = useTutorial();

  const startTutorial = () => {
    state.setView('workflow');
    tutorial.startTutorial();
  };

  const progressSteps = useMemo(() => [
    { id: 'step-1', label: 'CLI', done: true },
    { id: 'step-2', label: '워크플로우', done: state.workflowTab === 'load' ? !!state.selectedSavedWorkflow : !!state.generatedFile },
    { id: 'step-3', label: '스킬', done: state.step1Done },
    { id: 'step-4', label: '프롬프트', done: state.step2Done },
    { id: 'step-5', label: 'MCP', done: state.step3Done },
    { id: 'step-6', label: '실행', done: state.step3Done },
  ], [state.step1Done, state.step2Done, state.step3Done, state.workflowTab, state.selectedSavedWorkflow, state.generatedFile]);

  return (
    <div className="layout">
      <LeftPanel {...state} progressSteps={progressSteps} onStartTutorial={startTutorial} />

      <main className="main-panel">
        {state.view === 'skills' ? (
          <SkillsDirectoryView {...state} />
        ) : state.view === 'promptGuide' ? (
          <PromptGuideView />
        ) : (
          <WorkflowView {...state} />
        )}
      </main>

      <TutorialOverlay
        isActive={tutorial.isActive}
        currentStepIndex={tutorial.currentStepIndex}
        totalSteps={tutorial.totalSteps}
        currentStep={tutorial.currentStep}
        targetRect={tutorial.targetRect}
        secondaryRect={tutorial.secondaryRect}
        onNext={tutorial.nextStep}
        onPrev={tutorial.prevStep}
        onSkip={tutorial.skipTutorial}
      />
    </div>
  );
}
