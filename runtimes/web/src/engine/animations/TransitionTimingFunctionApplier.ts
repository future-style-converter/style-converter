// TransitionTimingFunctionApplier.ts — native.
import type { CSSProperties } from 'react';
import type { TransitionTimingFunctionConfig } from './TransitionTimingFunctionConfig';
export type TransitionTimingFunctionStyles = Pick<CSSProperties, 'transitionTimingFunction'>;
export function applyTransitionTimingFunction(c: TransitionTimingFunctionConfig): TransitionTimingFunctionStyles {
  if (c.value === undefined) return {};
  return { transitionTimingFunction: c.value };
}
