// BreakInsideApplier.ts — emits { breakInside }.  MDN: break-inside.
import type { CSSProperties } from 'react';
import type { BreakInsideConfig } from './BreakInsideConfig';
export function applyBreakInside(c: BreakInsideConfig): CSSProperties {
  return c.value === undefined ? {} : { breakInside: c.value } as CSSProperties;
}
