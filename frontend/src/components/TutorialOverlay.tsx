import type { CSSProperties } from 'react';
import type { TutorialStep } from '../tutorial-steps';

type Props = {
  isActive: boolean;
  currentStepIndex: number;
  totalSteps: number;
  currentStep: TutorialStep | null;
  targetRect: DOMRect | null;
  secondaryRect: DOMRect | null;
  onNext: () => void;
  onPrev: () => void;
  onSkip: () => void;
};

export function TutorialOverlay({
  isActive, currentStepIndex, totalSteps, currentStep,
  targetRect, secondaryRect, onNext, onPrev, onSkip,
}: Props) {
  if (!isActive || !currentStep) return null;

  const pad = currentStep.spotlightPadding ?? 14;
  const isLast = currentStepIndex === totalSteps - 1;

  const vw = document.documentElement.clientWidth;
  const vh = document.documentElement.clientHeight;

  // Cutout rects — expand outward by padding
  const makeCut = (rect: DOMRect, p: number) => ({
    x: rect.left - p, y: rect.top - p,
    w: rect.width + p * 2, h: rect.height + p * 2, rx: 12,
  });

  const cut = targetRect ? makeCut(targetRect, pad) : null;
  const sec = secondaryRect ? makeCut(secondaryRect, 6) : null;

  const cutPath = (c: { x: number; y: number; w: number; h: number; rx: number }) =>
    `M${c.x + c.rx},${c.y} ` +
    `H${c.x + c.w - c.rx} ` +
    `Q${c.x + c.w},${c.y} ${c.x + c.w},${c.y + c.rx} ` +
    `V${c.y + c.h - c.rx} ` +
    `Q${c.x + c.w},${c.y + c.h} ${c.x + c.w - c.rx},${c.y + c.h} ` +
    `H${c.x + c.rx} ` +
    `Q${c.x},${c.y + c.h} ${c.x},${c.y + c.h - c.rx} ` +
    `V${c.y + c.rx} ` +
    `Q${c.x},${c.y} ${c.x + c.rx},${c.y} Z`;

  // SVG path: outer (full viewport) + inner cutouts (reverse winding for evenodd holes)
  let path = `M0,0 H${vw} V${vh} H0 Z`;
  if (cut) path += ' ' + cutPath(cut);
  if (sec) path += ' ' + cutPath(sec);

  const tooltipStyle = computeTooltipPosition(targetRect, currentStep.placement, vw, vh, pad);

  return (
    <div className="tutorial-overlay">
      <svg className="tutorial-svg" viewBox={`0 0 ${vw} ${vh}`} onClick={onSkip}>
        <path d={path} fillRule="evenodd" className="tutorial-backdrop" />
        {cut && (
          <rect
            x={cut.x} y={cut.y} width={cut.w} height={cut.h}
            rx={cut.rx}
            className="tutorial-spotlight-border"
          />
        )}
        {sec && (
          <rect
            x={sec.x} y={sec.y} width={sec.w} height={sec.h}
            rx={sec.rx}
            className="tutorial-spotlight-border"
          />
        )}
      </svg>

      <div className="tutorial-tooltip" style={tooltipStyle} key={currentStepIndex}>
        <div className="tutorial-tooltip-header">
          <span className="tutorial-step-counter">{currentStepIndex + 1} / {totalSteps}</span>
          <button type="button" className="tutorial-skip-btn" onClick={onSkip}>건너뛰기</button>
        </div>
        <h4 className="tutorial-tooltip-title">{currentStep.title}</h4>
        <p className="tutorial-tooltip-desc">{currentStep.description}</p>
        <div className="tutorial-tooltip-footer">
          {currentStepIndex > 0 && (
            <button type="button" className="tutorial-prev-btn" onClick={onPrev}>이전</button>
          )}
          <button type="button" className="tutorial-next-btn" onClick={onNext}>
            {isLast ? '완료' : '다음'}
          </button>
        </div>
      </div>
    </div>
  );
}

function computeTooltipPosition(
  rect: DOMRect | null,
  placement: string,
  vw: number,
  vh: number,
  pad: number,
): CSSProperties {
  if (!rect) return { top: '50%', left: '50%', transform: 'translate(-50%, -50%)' };

  const gap = 16;
  const tw = 340;
  const estimatedTh = 220;

  let top: number;
  let left: number;

  switch (placement) {
    case 'right':
      top = rect.top - pad;
      left = rect.right + pad + gap;
      break;
    case 'left':
      top = rect.top - pad;
      left = rect.left - pad - tw - gap;
      break;
    case 'top':
      top = rect.top - pad - estimatedTh - gap;
      left = rect.left;
      break;
    case 'bottom':
    default:
      top = rect.bottom + pad + gap;
      left = rect.left;
      break;
  }

  // Clamp within viewport
  left = Math.max(16, Math.min(left, vw - tw - 16));
  top = Math.max(16, Math.min(top, vh - estimatedTh - 16));

  return { top, left };
}
