// PauseApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/pause.
import type { CSSProperties } from 'react';
import type { PauseConfig } from './PauseConfig';
export function applyPause(c: PauseConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ pause: c.value } as unknown as CSSProperties) as Record<string, string>;
}
