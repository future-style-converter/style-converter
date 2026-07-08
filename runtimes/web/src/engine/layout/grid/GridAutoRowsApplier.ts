// GridAutoRowsApplier.ts — emits `grid-auto-rows`.  Native Grid 1.
// Spec: https://developer.mozilla.org/docs/Web/CSS/grid-auto-rows.

import type { CSSProperties } from 'react';
import type { GridAutoRowsConfig } from './GridAutoRowsConfig';

export type GridAutoRowsStyles = Pick<CSSProperties, 'gridAutoRows'>;

export function applyGridAutoRows(config: GridAutoRowsConfig): GridAutoRowsStyles {
  if (config.value === undefined) return {};
  return { gridAutoRows: config.value } as GridAutoRowsStyles;
}
