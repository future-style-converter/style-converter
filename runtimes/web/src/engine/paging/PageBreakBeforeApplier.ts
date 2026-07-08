// PageBreakBeforeApplier.ts — emits { pageBreakBefore }.  MDN: page-break-before.
import type { CSSProperties } from 'react';
import type { PageBreakBeforeConfig } from './PageBreakBeforeConfig';
export function applyPageBreakBefore(c: PageBreakBeforeConfig): CSSProperties {
  return c.value === undefined ? {} : { pageBreakBefore: c.value } as CSSProperties;
}
