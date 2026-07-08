// ViewTransitionGroupApplier.ts — csstype-widened.
import type { CSSProperties } from 'react';
import type { ViewTransitionGroupConfig } from './ViewTransitionGroupConfig';
export type ViewTransitionGroupStyles = Record<string, string>;
export function applyViewTransitionGroup(c: ViewTransitionGroupConfig): ViewTransitionGroupStyles {
  if (c.value === undefined) return {};
  return ({ viewTransitionGroup: c.value } as unknown as CSSProperties) as ViewTransitionGroupStyles;
}
