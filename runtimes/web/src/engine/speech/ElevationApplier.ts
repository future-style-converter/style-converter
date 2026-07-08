// ElevationApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/elevation.
import type { CSSProperties } from 'react';
import type { ElevationConfig } from './ElevationConfig';
export function applyElevation(c: ElevationConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ elevation: c.value } as unknown as CSSProperties) as Record<string, string>;
}
