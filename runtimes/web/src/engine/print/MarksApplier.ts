// MarksApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/marks.
import type { CSSProperties } from 'react';
import type { MarksConfig } from './MarksConfig';
export function applyMarks(c: MarksConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ marks: c.value } as unknown as CSSProperties) as Record<string, string>;
}
