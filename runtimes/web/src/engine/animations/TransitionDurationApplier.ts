// TransitionDurationApplier.ts — native.
import type { CSSProperties } from 'react';
import type { TransitionDurationConfig } from './TransitionDurationConfig';
export type TransitionDurationStyles = Pick<CSSProperties, 'transitionDuration'>;
export function applyTransitionDuration(c: TransitionDurationConfig): TransitionDurationStyles {
  if (c.value === undefined) return {};
  return { transitionDuration: c.value };
}
