// BreakAfterApplier.ts — emits { breakAfter }.  MDN: break-after.
import type { CSSProperties } from 'react';
import type { BreakAfterConfig } from './BreakAfterConfig';
export function applyBreakAfter(c: BreakAfterConfig): CSSProperties {
  return c.value === undefined ? {} : { breakAfter: c.value } as CSSProperties;
}
