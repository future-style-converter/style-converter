// ShapeOutsideApplier.ts — emits { shapeOutside }.  MDN: shape-outside.
import type { CSSProperties } from 'react';
import type { ShapeOutsideConfig } from './ShapeOutsideConfig';
export function applyShapeOutside(c: ShapeOutsideConfig): CSSProperties {
  return c.value === undefined ? {} : { shapeOutside: c.value } as CSSProperties;
}
