// StrokeOpacityApplier.ts — emits { strokeOpacity }.  MDN: stroke-opacity.
import type { CSSProperties } from 'react';
import type { StrokeOpacityConfig } from './StrokeOpacityConfig';
export function applyStrokeOpacity(c: StrokeOpacityConfig): CSSProperties {
  return c.value === undefined ? {} : { strokeOpacity: c.value } as CSSProperties;
}
