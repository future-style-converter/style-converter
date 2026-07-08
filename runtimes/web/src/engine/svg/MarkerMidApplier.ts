// MarkerMidApplier.ts — emits { markerMid }.  MDN: marker-mid.
import type { CSSProperties } from 'react';
import type { MarkerMidConfig } from './MarkerMidConfig';
export function applyMarkerMid(c: MarkerMidConfig): CSSProperties {
  return c.value === undefined ? {} : { markerMid: c.value } as CSSProperties;
}
