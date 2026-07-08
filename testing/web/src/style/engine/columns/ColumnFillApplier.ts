// ColumnFillApplier.ts — emits { columnFill }.  MDN: column-fill.
import type { CSSProperties } from 'react';
import type { ColumnFillConfig } from './ColumnFillConfig';
export function applyColumnFill(c: ColumnFillConfig): CSSProperties {
  return c.value === undefined ? {} : { columnFill: c.value } as CSSProperties;
}
