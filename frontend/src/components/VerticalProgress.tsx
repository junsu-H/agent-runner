import { useEffect, useRef, useState } from 'react';

type Step = {
  id: string;
  label: string;
  done: boolean;
};

type Props = {
  steps: Step[];
};

export function VerticalProgress({ steps }: Props) {
  const [activeIdx, setActiveIdx] = useState(0);
  const observerRef = useRef<IntersectionObserver | null>(null);

  useEffect(() => {
    observerRef.current?.disconnect();

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            const idx = steps.findIndex((s) => s.id === entry.target.id);
            if (idx >= 0) setActiveIdx(idx);
          }
        }
      },
      { rootMargin: '-20% 0px -60% 0px', threshold: 0.1 },
    );

    observerRef.current = observer;

    for (const step of steps) {
      const el = document.getElementById(step.id);
      if (el) observer.observe(el);
    }

    return () => observer.disconnect();
  }, [steps]);

  const scrollTo = (id: string) => {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  return (
    <nav className="vertical-progress">
      {steps.map((step, idx) => (
        <div key={step.id} className="vp-step-wrapper">
          {idx > 0 && <div className={`vp-line ${idx <= activeIdx ? 'active' : ''}`} />}
          <button
            type="button"
            className={`vp-dot ${idx === activeIdx ? 'current' : ''} ${step.done ? 'done' : ''}`}
            onClick={() => scrollTo(step.id)}
            title={step.label}
          >
            {step.done ? '\u2713' : idx + 1}
          </button>
          <span className={`vp-label ${idx === activeIdx ? 'current' : ''}`}>{step.label}</span>
        </div>
      ))}
    </nav>
  );
}
