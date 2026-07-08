// TransitionBehaviorApplier.ts — csstype-widened.
// https://developer.mozilla.org/docs/Web/CSS/transition-behavior
import type { CSSProperties } from 'react';
import type { TransitionBehaviorConfig } from './TransitionBehaviorConfig';
export type TransitionBehaviorStyles = Record<string, string>;
export function applyTransitionBehavior(c: TransitionBehaviorConfig): TransitionBehaviorStyles {
  if (c.value === undefined) return {};
  return ({ transitionBehavior: c.value } as unknown as CSSProperties) as TransitionBehaviorStyles;
}
