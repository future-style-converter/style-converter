// GridAutoColumnsApplier.ts — emits `grid-auto-columns`.  Native Grid 1.
// Spec: https://developer.mozilla.org/docs/Web/CSS/grid-auto-columns.

import type { CSSProperties } from 'react';
import type { GridAutoColumnsConfig } from './GridAutoColumnsConfig';

export type GridAutoColumnsStyles = Pick<CSSProperties, 'gridAutoColumns'>;

export function applyGridAutoColumns(config: GridAutoColumnsConfig): GridAutoColumnsStyles {
  if (config.value === undefined) return {};
  return { gridAutoColumns: config.value } as GridAutoColumnsStyles;
}
