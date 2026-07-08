// CaretShapeApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/caret-shape.
import type { CSSProperties } from 'react';
import type { CaretShapeConfig } from './CaretShapeConfig';
export function applyCaretShape(c: CaretShapeConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ caretShape: c.value } as unknown as CSSProperties) as Record<string, string>;
}
