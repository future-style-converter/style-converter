// MarkerSideApplier.ts — csstype-widened (SVG 2 draft key; browsers ignore it).
import type { CSSProperties } from 'react';
import type { MarkerSideConfig } from './MarkerSideConfig';
// Pure function — emit only when the extractor produced a value.
export function applyMarkerSide(c: MarkerSideConfig): Record<string, string> {
  if (c.value === undefined) return {};                             // unset → no declaration
  return ({ markerSide: c.value } as unknown as CSSProperties) as Record<string, string>;
}
