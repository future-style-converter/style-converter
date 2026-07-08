// ColumnWidthApplier.ts — emits { columnWidth }.  MDN: column-width.
import type { CSSProperties } from 'react';
import type { ColumnWidthConfig } from './ColumnWidthConfig';
export function applyColumnWidth(c: ColumnWidthConfig): CSSProperties {
  return c.value === undefined ? {} : { columnWidth: c.value } as CSSProperties;
}
