// ColumnCountApplier.ts — emits { columnCount }.  MDN: column-count.
import type { CSSProperties } from 'react';
import type { ColumnCountConfig } from './ColumnCountConfig';
export function applyColumnCount(c: ColumnCountConfig): CSSProperties {
  return c.value === undefined ? {} : { columnCount: c.value } as CSSProperties;
}
