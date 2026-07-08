// FillOpacityApplier.ts — emits { fillOpacity }.  MDN: fill-opacity.
import type { CSSProperties } from 'react';
import type { FillOpacityConfig } from './FillOpacityConfig';
export function applyFillOpacity(c: FillOpacityConfig): CSSProperties {
  return c.value === undefined ? {} : { fillOpacity: c.value } as CSSProperties;
}
