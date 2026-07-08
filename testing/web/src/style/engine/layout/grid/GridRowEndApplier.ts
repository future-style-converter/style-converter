// GridRowEndApplier.ts — emits `grid-row-end`.  Native.
// Spec: https://developer.mozilla.org/docs/Web/CSS/grid-row-end.

import type { CSSProperties } from 'react';
import type { GridRowEndConfig } from './GridRowEndConfig';

export type GridRowEndStyles = Pick<CSSProperties, 'gridRowEnd'>;

export function applyGridRowEnd(config: GridRowEndConfig): GridRowEndStyles {
  if (config.value === undefined) return {};
  return { gridRowEnd: config.value } as GridRowEndStyles;
}
