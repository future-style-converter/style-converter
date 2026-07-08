// TransitionPropertyApplier.ts — native `transition-property`.
import type { CSSProperties } from 'react';
import type { TransitionPropertyConfig } from './TransitionPropertyConfig';
export type TransitionPropertyStyles = Pick<CSSProperties, 'transitionProperty'>;
export function applyTransitionProperty(c: TransitionPropertyConfig): TransitionPropertyStyles {
  if (c.value === undefined) return {};
  return { transitionProperty: c.value };
}
