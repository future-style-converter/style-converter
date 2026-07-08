// PageBreakAfterApplier.ts — emits { pageBreakAfter }.  MDN: page-break-after.
import type { CSSProperties } from 'react';
import type { PageBreakAfterConfig } from './PageBreakAfterConfig';
export function applyPageBreakAfter(c: PageBreakAfterConfig): CSSProperties {
  return c.value === undefined ? {} : { pageBreakAfter: c.value } as CSSProperties;
}
