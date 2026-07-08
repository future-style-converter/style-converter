// GridTemplateRowsApplier.ts — emits `grid-template-rows`.
// Native CSS Grid 1 — https://developer.mozilla.org/docs/Web/CSS/grid-template-rows.

import type { CSSProperties } from 'react';
import type { GridTemplateRowsConfig } from './GridTemplateRowsConfig';

export type GridTemplateRowsStyles = Pick<CSSProperties, 'gridTemplateRows'>;

export function applyGridTemplateRows(config: GridTemplateRowsConfig): GridTemplateRowsStyles {
  if (config.value === undefined) return {};                                      // unset
  return { gridTemplateRows: config.value } as GridTemplateRowsStyles;            // passthrough
}
