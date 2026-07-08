// RunningApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/running.
import type { CSSProperties } from 'react';
import type { RunningConfig } from './RunningConfig';
export function applyRunning(c: RunningConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ running: c.value } as unknown as CSSProperties) as Record<string, string>;
}
