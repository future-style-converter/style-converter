// PauseAfterApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/pause-after.
import type { CSSProperties } from 'react';
import type { PauseAfterConfig } from './PauseAfterConfig';
export function applyPauseAfter(c: PauseAfterConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ pauseAfter: c.value } as unknown as CSSProperties) as Record<string, string>;
}
