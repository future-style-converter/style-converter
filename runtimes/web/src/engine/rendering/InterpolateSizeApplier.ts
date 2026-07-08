// InterpolateSizeApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/interpolate-size.
import type { CSSProperties } from 'react';
import type { InterpolateSizeConfig } from './InterpolateSizeConfig';
export function applyInterpolateSize(c: InterpolateSizeConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ interpolateSize: c.value } as unknown as CSSProperties) as Record<string, string>;
}
