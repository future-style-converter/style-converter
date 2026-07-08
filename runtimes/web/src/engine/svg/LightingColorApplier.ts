// LightingColorApplier.ts — emits { lightingColor }.  MDN: lighting-color.
import type { CSSProperties } from 'react';
import type { LightingColorConfig } from './LightingColorConfig';
export function applyLightingColor(c: LightingColorConfig): CSSProperties {
  return c.value === undefined ? {} : { lightingColor: c.value } as CSSProperties;
}
