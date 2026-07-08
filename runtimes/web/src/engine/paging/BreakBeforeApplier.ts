// BreakBeforeApplier.ts — emits { breakBefore }.  MDN: break-before.
import type { CSSProperties } from 'react';
import type { BreakBeforeConfig } from './BreakBeforeConfig';
export function applyBreakBefore(c: BreakBeforeConfig): CSSProperties {
  return c.value === undefined ? {} : { breakBefore: c.value } as CSSProperties;
}
