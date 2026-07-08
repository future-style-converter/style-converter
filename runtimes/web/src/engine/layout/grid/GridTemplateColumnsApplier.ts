// GridTemplateColumnsApplier.ts — emits `grid-template-columns`.
// Native CSS Grid 1 — https://developer.mozilla.org/docs/Web/CSS/grid-template-columns.

import type { CSSProperties } from 'react';
import type { GridTemplateColumnsConfig } from './GridTemplateColumnsConfig';

export type GridTemplateColumnsStyles = Pick<CSSProperties, 'gridTemplateColumns'>;

export function applyGridTemplateColumns(config: GridTemplateColumnsConfig): GridTemplateColumnsStyles {
  if (config.value === undefined) return {};                                      // unset → empty object
  return { gridTemplateColumns: config.value } as GridTemplateColumnsStyles;      // browser-native
}
