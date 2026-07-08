// CueAfterApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/cue-after.
import type { CSSProperties } from 'react';
import type { CueAfterConfig } from './CueAfterConfig';
export function applyCueAfter(c: CueAfterConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ cueAfter: c.value } as unknown as CSSProperties) as Record<string, string>;
}
