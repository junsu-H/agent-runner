import { DragEvent } from 'react';

export const defaultProjectPath = '';
export const dragMimeType = 'application/x-agent-runner-skill';
export const mcpSetupSkillName = 'agent-runner-mcp-setup';

export type McpProfileOption = {
  id: string;
  label: string;
  desc: string;
};

export const mcpProfilesNoKey: McpProfileOption[] = [
  { id: 'sequential-thinking', label: 'sequential-thinking', desc: '단계별 사고 추론' },
  { id: 'serena', label: 'serena', desc: '코드베이스 탐색' },
  { id: 'aws-cost', label: 'aws-cost', desc: 'AWS 비용 추적' },
  { id: 'aws-db', label: 'aws-db', desc: 'AWS DB 관리' },
  { id: 'aws-docs', label: 'aws-docs', desc: 'AWS 문서 검색' },
  { id: 'aws-ecs', label: 'aws-ecs', desc: 'ECS 컨테이너 관리' },
  { id: 'aws-eks', label: 'aws-eks', desc: 'EKS 클러스터 관리' },
  { id: 'aws-iac', label: 'aws-iac', desc: 'IaC 인프라 관리' },
  { id: 'aws-lambda', label: 'aws-lambda', desc: 'Lambda 함수 관리' },
  { id: 'aws-mq', label: 'aws-mq', desc: '메시지 큐 관리' },
  { id: 'aws-observability', label: 'aws-observability', desc: 'AWS 모니터링' },
  { id: 'aws-security', label: 'aws-security', desc: 'AWS 보안 관리' },
  { id: 'datadog', label: 'datadog', desc: 'Datadog 모니터링' },
];

export const mcpProfilesWithKey: McpProfileOption[] = [
  { id: 'sonarqube', label: 'sonarqube', desc: '코드 품질 분석' },
  { id: 'wiki', label: 'wiki', desc: 'Confluence 위키' },
];

export const mcpProfileOptions: McpProfileOption[] = [...mcpProfilesNoKey, ...mcpProfilesWithKey];

export type MainView = 'workflow' | 'skills' | 'promptGuide';

export type DragPayload = {
  source: 'catalog' | 'workflow';
  skillName: string;
  index?: number;
};

export function readDragPayload(event: DragEvent<HTMLElement>): DragPayload | null {
  const raw = event.dataTransfer.getData(dragMimeType);
  if (!raw) return null;

  try {
    return JSON.parse(raw) as DragPayload;
  } catch {
    return null;
  }
}

export function buildMcpSetupPrompt(selectedProfiles: string[]): string {
  return [
    'agent-runner-mcp-setup 스킬을 사용해 MCP 프로필을 구성해줘.',
    '',
    '선택 프로필:',
    ...selectedProfiles.map((profile) => `- ${profile}`),
    '',
    '검증:',
    '1. MCP 서버 연결 상태 확인',
    '2. 적용된 서버 목록 요약',
  ].join('\n');
}

