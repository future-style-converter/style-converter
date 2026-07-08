// GridColumnStartApplier.ts — emits `grid-column-start`.  Native.
// Spec: https://developer.mozilla.org/docs/Web/CSS/grid-column-start.

import type { CSSProperties } from 'react';
import type { GridColumnStartConfig } from './GridColumnStartConfig';

export type GridColumnStartStyles = Pick<CSSProperties, 'gridColumnStart'>;

export function applyGridColumnStart(config: GridColumnStartConfig): GridColumnStartStyles {
  if (config.value === undefined) return {};
  return { gridColumnStart: config.value } as GridColumnStartStyles;
}
