// CueApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/cue.
import type { CSSProperties } from 'react';
import type { CueConfig } from './CueConfig';
export function applyCue(c: CueConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ cue: c.value } as unknown as CSSProperties) as Record<string, string>;
}
