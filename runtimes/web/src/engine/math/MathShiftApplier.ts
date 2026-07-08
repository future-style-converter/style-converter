// MathShiftApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/math-shift.
import type { CSSProperties } from 'react';
import type { MathShiftConfig } from './MathShiftConfig';
export function applyMathShift(c: MathShiftConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ mathShift: c.value } as unknown as CSSProperties) as Record<string, string>;
}
