// ColumnSpanApplier.ts — emits { columnSpan }.  MDN: column-span.
import type { CSSProperties } from 'react';
import type { ColumnSpanConfig } from './ColumnSpanConfig';
export function applyColumnSpan(c: ColumnSpanConfig): CSSProperties {
  return c.value === undefined ? {} : { columnSpan: c.value } as CSSProperties;
}
