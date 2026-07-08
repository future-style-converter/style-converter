// StressApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/stress.
import type { CSSProperties } from 'react';
import type { StressConfig } from './StressConfig';
export function applyStress(c: StressConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ stress: c.value } as unknown as CSSProperties) as Record<string, string>;
}
