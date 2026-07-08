// TransitionDelayApplier.ts — native.
import type { CSSProperties } from 'react';
import type { TransitionDelayConfig } from './TransitionDelayConfig';
export type TransitionDelayStyles = Pick<CSSProperties, 'transitionDelay'>;
export function applyTransitionDelay(c: TransitionDelayConfig): TransitionDelayStyles {
  if (c.value === undefined) return {};
  return { transitionDelay: c.value };
}
