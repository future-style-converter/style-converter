// AzimuthApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/azimuth.
import type { CSSProperties } from 'react';
import type { AzimuthConfig } from './AzimuthConfig';
export function applyAzimuth(c: AzimuthConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ azimuth: c.value } as unknown as CSSProperties) as Record<string, string>;
}
