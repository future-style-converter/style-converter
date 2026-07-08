// GridTemplateAreasApplier.ts — emits `grid-template-areas`.  Native.
// Spec: https://developer.mozilla.org/docs/Web/CSS/grid-template-areas.

import type { CSSProperties } from 'react';
import type { GridTemplateAreasConfig } from './GridTemplateAreasConfig';

export type GridTemplateAreasStyles = Pick<CSSProperties, 'gridTemplateAreas'>;

export function applyGridTemplateAreas(config: GridTemplateAreasConfig): GridTemplateAreasStyles {
  if (config.value === undefined) return {};                                      // unset
  return { gridTemplateAreas: config.value } as GridTemplateAreasStyles;          // browser-native
}
