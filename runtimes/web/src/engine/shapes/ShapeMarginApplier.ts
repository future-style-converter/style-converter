// ShapeMarginApplier.ts — emits { shapeMargin }.  MDN: shape-margin.
import type { CSSProperties } from 'react';
import type { ShapeMarginConfig } from './ShapeMarginConfig';
export function applyShapeMargin(c: ShapeMarginConfig): CSSProperties {
  return c.value === undefined ? {} : { shapeMargin: c.value } as CSSProperties;
}
