// GridRowStartApplier.ts — emits `grid-row-start`.  Native.
// Spec: https://developer.mozilla.org/docs/Web/CSS/grid-row-start.

import type { CSSProperties } from 'react';
import type { GridRowStartConfig } from './GridRowStartConfig';

export type GridRowStartStyles = Pick<CSSProperties, 'gridRowStart'>;

export function applyGridRowStart(config: GridRowStartConfig): GridRowStartStyles {
  if (config.value === undefined) return {};
  return { gridRowStart: config.value } as GridRowStartStyles;
}
