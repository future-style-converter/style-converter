// TableLayoutApplier.ts — emits { tableLayout }.  MDN: table-layout.
import type { CSSProperties } from 'react';
import type { TableLayoutConfig } from './TableLayoutConfig';
export function applyTableLayout(c: TableLayoutConfig): CSSProperties {
  return c.value === undefined ? {} : { tableLayout: c.value } as CSSProperties;
}
