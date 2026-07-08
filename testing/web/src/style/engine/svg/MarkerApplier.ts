// MarkerApplier.ts — emits { marker }.  MDN: marker.
import type { CSSProperties } from 'react';
import type { MarkerConfig } from './MarkerConfig';
export function applyMarker(c: MarkerConfig): CSSProperties {
  return c.value === undefined ? {} : { marker: c.value } as CSSProperties;
}
