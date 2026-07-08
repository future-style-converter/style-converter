// MathStyleApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/math-style.
import type { CSSProperties } from 'react';
import type { MathStyleConfig } from './MathStyleConfig';
export function applyMathStyle(c: MathStyleConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ mathStyle: c.value } as unknown as CSSProperties) as Record<string, string>;
}
