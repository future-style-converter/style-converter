// MarkerEndApplier.ts — emits { markerEnd }.  MDN: marker-end.
import type { CSSProperties } from 'react';
import type { MarkerEndConfig } from './MarkerEndConfig';
export function applyMarkerEnd(c: MarkerEndConfig): CSSProperties {
  return c.value === undefined ? {} : { markerEnd: c.value } as CSSProperties;
}
