// PageBreakInsideApplier.ts — emits { pageBreakInside }.  MDN: page-break-inside.
import type { CSSProperties } from 'react';
import type { PageBreakInsideConfig } from './PageBreakInsideConfig';
export function applyPageBreakInside(c: PageBreakInsideConfig): CSSProperties {
  return c.value === undefined ? {} : { pageBreakInside: c.value } as CSSProperties;
}
