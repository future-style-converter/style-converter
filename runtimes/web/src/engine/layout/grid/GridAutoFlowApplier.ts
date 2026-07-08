// GridAutoFlowApplier.ts — emits `grid-auto-flow`.  Native.
// Spec: https://developer.mozilla.org/docs/Web/CSS/grid-auto-flow.

import type { CSSProperties } from 'react';
import type { GridAutoFlowConfig } from './GridAutoFlowConfig';

export type GridAutoFlowStyles = Pick<CSSProperties, 'gridAutoFlow'>;

export function applyGridAutoFlow(config: GridAutoFlowConfig): GridAutoFlowStyles {
  if (config.value === undefined) return {};
  return { gridAutoFlow: config.value } as GridAutoFlowStyles;
}
