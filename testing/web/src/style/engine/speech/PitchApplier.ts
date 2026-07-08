// PitchApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/pitch.
import type { CSSProperties } from 'react';
import type { PitchConfig } from './PitchConfig';
export function applyPitch(c: PitchConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ pitch: c.value } as unknown as CSSProperties) as Record<string, string>;
}
