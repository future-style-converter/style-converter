// GridColumnEndApplier.ts — emits `grid-column-end`.  Native.
// Spec: https://developer.mozilla.org/docs/Web/CSS/grid-column-end.

import type { CSSProperties } from 'react';
import type { GridColumnEndConfig } from './GridColumnEndConfig';

export type GridColumnEndStyles = Pick<CSSProperties, 'gridColumnEnd'>;

export function applyGridColumnEnd(config: GridColumnEndConfig): GridColumnEndStyles {
  if (config.value === undefined) return {};
  return { gridColumnEnd: config.value } as GridColumnEndStyles;
}
