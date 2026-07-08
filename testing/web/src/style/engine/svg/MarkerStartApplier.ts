// MarkerStartApplier.ts — emits { markerStart }.  MDN: marker-start.
import type { CSSProperties } from 'react';
import type { MarkerStartConfig } from './MarkerStartConfig';
export function applyMarkerStart(c: MarkerStartConfig): CSSProperties {
  return c.value === undefined ? {} : { markerStart: c.value } as CSSProperties;
}
