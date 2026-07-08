// PresentationLevelApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/presentation-level.
import type { CSSProperties } from 'react';
import type { PresentationLevelConfig } from './PresentationLevelConfig';
export function applyPresentationLevel(c: PresentationLevelConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ presentationLevel: c.value } as unknown as CSSProperties) as Record<string, string>;
}
