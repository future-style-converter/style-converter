// ViewTransitionNameApplier.ts — csstype-widened (View Transitions API L2).
import type { CSSProperties } from 'react';
import type { ViewTransitionNameConfig } from './ViewTransitionNameConfig';
export type ViewTransitionNameStyles = Record<string, string>;
export function applyViewTransitionName(c: ViewTransitionNameConfig): ViewTransitionNameStyles {
  if (c.value === undefined) return {};
  return ({ viewTransitionName: c.value } as unknown as CSSProperties) as ViewTransitionNameStyles;
}
