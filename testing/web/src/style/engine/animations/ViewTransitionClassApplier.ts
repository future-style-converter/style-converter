// ViewTransitionClassApplier.ts — csstype-widened.
import type { CSSProperties } from 'react';
import type { ViewTransitionClassConfig } from './ViewTransitionClassConfig';
export type ViewTransitionClassStyles = Record<string, string>;
export function applyViewTransitionClass(c: ViewTransitionClassConfig): ViewTransitionClassStyles {
  if (c.value === undefined) return {};
  return ({ viewTransitionClass: c.value } as unknown as CSSProperties) as ViewTransitionClassStyles;
}
