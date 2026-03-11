export type TutorialStep = {
  target: string;
  fallbackTarget?: string;
  secondaryTarget?: string;
  title: string;
  description: string;
  placement: 'top' | 'bottom' | 'left' | 'right';
  panel: 'left' | 'main';
  spotlightPadding?: number;
};

export const TUTORIAL_STEPS: TutorialStep[] = [
  {
    target: '.prerequisites-group',
    title: '1. 필수 설치 항목',
    description: '필수 설치 항목들을 설치해 주세요. 설치가 완료되면 체크 표시가 나타납니다.',
    placement: 'right',
    panel: 'left',
  },
  {
    target: '#step-1',
    title: '2. CLI 선택',
    description: '사용할 AI CLI를 선택해 주세요. Claude, Codex, Copilot, Gemini 중에서 고를 수 있습니다.',
    placement: 'bottom',
    panel: 'main',
  },
  {
    target: '#step-2',
    title: '3. 워크플로우 파일',
    description: '새로 만들기 또는 불러오기를 선택하세요. 기존 워크플로우를 불러오면 스킬/프롬프트 단계를 건너뛸 수 있습니다.',
    placement: 'bottom',
    panel: 'main',
  },
  {
    target: '#step-3',
    secondaryTarget: '.skill-picker',
    title: '4. 스킬 선택',
    description: '어떤 스킬을 사용할지 경로를 정하고, 좌측에 나오는 스킬들을 클릭하여 워크플로우 캔버스를 생성해 주세요.',
    placement: 'bottom',
    panel: 'main',
  },
  {
    target: '#step-4',
    title: '5. 프롬프트 입력',
    description: '스킬들을 클릭하여 어떤 프롬프트를 요청할지 적어주세요.',
    placement: 'bottom',
    panel: 'main',
  },
  {
    target: '#step-5',
    title: '6. MCP 선택',
    description: 'MCP 연결이 필요하면 JSON 폴더 경로를 지정하고 프로필을 선택하세요.',
    placement: 'bottom',
    panel: 'main',
  },
  {
    target: '#step-6',
    title: '7. 실행',
    description: '터미널 실행 또는 프롬프트 복사로 워크플로우를 실행하세요. MCP를 사용하면 터미널에서 /mcp로 연결 여부를 확인할 수 있습니다. "프롬프트에 직접 입력"을 사용할 경우 MCP 연결이 안 됩니다.',
    placement: 'top',
    panel: 'main',
  },
];
