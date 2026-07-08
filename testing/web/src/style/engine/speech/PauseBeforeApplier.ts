// PauseBeforeApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/pause-before.
import type { CSSProperties } from 'react';
import type { PauseBeforeConfig } from './PauseBeforeConfig';
export function applyPauseBefore(c: PauseBeforeConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ pauseBefore: c.value } as unknown as CSSProperties) as Record<string, string>;
}
