// PitchRangeApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/pitch-range.
import type { CSSProperties } from 'react';
import type { PitchRangeConfig } from './PitchRangeConfig';
export function applyPitchRange(c: PitchRangeConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ pitchRange: c.value } as unknown as CSSProperties) as Record<string, string>;
}
