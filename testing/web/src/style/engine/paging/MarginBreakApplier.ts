// MarginBreakApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/margin-break.
import type { CSSProperties } from 'react';
import type { MarginBreakConfig } from './MarginBreakConfig';
export function applyMarginBreak(c: MarginBreakConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ marginBreak: c.value } as unknown as CSSProperties) as Record<string, string>;
}
