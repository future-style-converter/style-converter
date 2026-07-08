// MathDepthApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/math-depth.
import type { CSSProperties } from 'react';
import type { MathDepthConfig } from './MathDepthConfig';
export function applyMathDepth(c: MathDepthConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ mathDepth: c.value } as unknown as CSSProperties) as Record<string, string>;
}
