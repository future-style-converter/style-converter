// CueBeforeApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/cue-before.
import type { CSSProperties } from 'react';
import type { CueBeforeConfig } from './CueBeforeConfig';
export function applyCueBefore(c: CueBeforeConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ cueBefore: c.value } as unknown as CSSProperties) as Record<string, string>;
}
