export type SupportedCli = 'codex' | 'claude' | 'copilot' | 'gemini';

export type LinkedListNode = {
  id: string;
  label: string;
  skillPath: string;
  nextId: string | null;
  commandPreview: string;
};

export type LinkedListDefinition = {
  name: string;
  executionMode: string;
  selectedCli: SupportedCli;
  headId: string;
  selectedSkills: string[];
  nodes: LinkedListNode[];
};

export type SkillDirectory = {
  name: string;
  path: string;
  hasSkillMd: boolean;
  skillMdPath: string | null;
};

export type SkillListResponse = {
  agentRunnerRoot: string;
  skillsRoot: string;
  total: number;
  skills: SkillDirectory[];
};

export type RunWorkflowRequest = {
  projectPath: string;
  issueKey?: string | null;
  requestText: string;
  cli: SupportedCli;
  dryRun?: boolean;
  selectedSkills: string[];
  stepPrompts: Record<string, string>;
  commandStepSkills: string[];
  mcpProfiles: string[];
  openTerminalAfter: boolean;
  workflowName?: string;
};

export type StepExecutionResult = {
  step: string;
  command: string;
  exitCode: number;
  success: boolean;
  logFile: string;
  message: string;
};

export type WorkflowRunResult = {
  success: boolean;
  selectedCli: SupportedCli;
  runDirectory: string;
  steps: StepExecutionResult[];
};

export type WorkflowRunStatus = {
  runId: string;
  status: 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED';
  selectedCli: SupportedCli;
  runDirectory: string;
  totalSteps: number;
  completedSteps: number;
  progressPercent: number;
  currentStep: string | null;
  currentCommand: string | null;
  cancelRequested: boolean;
  message: string;
  lastOutputLine: string | null;
  currentStepOutputTail: string[];
  steps: StepExecutionResult[];
};

export type McpInfoResponse = {
  cli: string;
  checkCommand: string;
  connected: boolean;
  summary: string;
  outputLines: string[];
};

export type MarkdownWorkflow = {
  workflowId: string;
  name: string;
  sourcePath: string;
  selectedCli: SupportedCli;
  projectPath: string;
  mcpProfiles: string[];
  steps: string[];
  registeredAt: string;
};

export type MarkdownWorkflowTerminalLaunchResponse = {
  workflowId: string;
  runId: string;
  selectedCli: SupportedCli;
  projectPath: string;
  scriptPath: string;
  summary: string;
  outputLines: string[];
};

export type TerminalWsEvent = {
  type: 'status' | 'stdout' | 'stdin';
  runId: string;
  status: string | null;
  message: string | null;
  currentStep: string | null;
  currentCommand: string | null;
  line: string | null;
  tail: string[] | null;
};
