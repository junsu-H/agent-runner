import { useState, useCallback, useEffect } from 'react';
import { TUTORIAL_STEPS } from '../tutorial-steps';

export function useTutorial() {
  const [isActive, setIsActive] = useState(false);
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [targetRect, setTargetRect] = useState<DOMRect | null>(null);
  const [secondaryRect, setSecondaryRect] = useState<DOMRect | null>(null);

  const currentStep = isActive ? TUTORIAL_STEPS[currentStepIndex] ?? null : null;

  /* ── Update target rect ── */
  const updateTargetRect = useCallback(() => {
    if (!currentStep) { setTargetRect(null); setSecondaryRect(null); return; }
    const el = document.querySelector(currentStep.target)
      ?? (currentStep.fallbackTarget ? document.querySelector(currentStep.fallbackTarget) : null);
    if (el) {
      setTargetRect(el.getBoundingClientRect());
    } else {
      setTargetRect(null);
    }
    // Secondary target
    if (currentStep.secondaryTarget) {
      const sel = document.querySelector(currentStep.secondaryTarget);
      setSecondaryRect(sel ? sel.getBoundingClientRect() : null);
    } else {
      setSecondaryRect(null);
    }
  }, [currentStep]);

  /* ── Scroll to target on step change ── */
  useEffect(() => {
    if (!isActive || !currentStep) return;
    const el = document.querySelector(currentStep.target)
      ?? (currentStep.fallbackTarget ? document.querySelector(currentStep.fallbackTarget) : null);
    if (el) {
      // For left panel elements, scroll within the left panel
      if (currentStep.panel === 'left') {
        const panel = el.closest('.left-panel');
        if (panel) {
          const elRect = el.getBoundingClientRect();
          const panelRect = panel.getBoundingClientRect();
          const scrollTop = panel.scrollTop + elRect.top - panelRect.top - panelRect.height / 3;
          panel.scrollTo({ top: scrollTop, behavior: 'smooth' });
        }
      } else {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    }
    // Update rect after scroll settles
    const t = setTimeout(updateTargetRect, 450);
    return () => clearTimeout(t);
  }, [isActive, currentStepIndex]);

  /* ── Track resize / scroll ── */
  useEffect(() => {
    if (!isActive) return;
    const handle = () => updateTargetRect();
    window.addEventListener('resize', handle);
    window.addEventListener('scroll', handle, true);
    handle();
    return () => {
      window.removeEventListener('resize', handle);
      window.removeEventListener('scroll', handle, true);
    };
  }, [isActive, updateTargetRect]);

  /* ── Keyboard ── */
  useEffect(() => {
    if (!isActive) return;
    const handle = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { skipTutorial(); e.preventDefault(); }
      if (e.key === 'ArrowRight' || e.key === 'Enter') { nextStep(); e.preventDefault(); }
      if (e.key === 'ArrowLeft') { prevStep(); e.preventDefault(); }
    };
    window.addEventListener('keydown', handle);
    return () => window.removeEventListener('keydown', handle);
  }, [isActive, currentStepIndex]);

  const startTutorial = useCallback(() => {
    setCurrentStepIndex(0);
    setIsActive(true);
  }, []);

  const nextStep = useCallback(() => {
    if (currentStepIndex < TUTORIAL_STEPS.length - 1) {
      setCurrentStepIndex((i) => i + 1);
    } else {
      setIsActive(false);
    }
  }, [currentStepIndex]);

  const prevStep = useCallback(() => {
    if (currentStepIndex > 0) setCurrentStepIndex((i) => i - 1);
  }, [currentStepIndex]);

  const skipTutorial = useCallback(() => {
    setIsActive(false);
    setCurrentStepIndex(0);
  }, []);

  return {
    isActive,
    currentStepIndex,
    totalSteps: TUTORIAL_STEPS.length,
    currentStep,
    targetRect,
    secondaryRect,
    startTutorial,
    nextStep,
    prevStep,
    skipTutorial,
  };
}
