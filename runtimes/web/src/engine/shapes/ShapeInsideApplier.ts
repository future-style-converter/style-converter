// ShapeInsideApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/shape-inside.
import type { CSSProperties } from 'react';
import type { ShapeInsideConfig } from './ShapeInsideConfig';
export function applyShapeInside(c: ShapeInsideConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ shapeInside: c.value } as unknown as CSSProperties) as Record<string, string>;
}
