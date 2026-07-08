// ShapeRenderingApplier.ts — emits { shapeRendering }.  MDN: shape-rendering.
import type { CSSProperties } from 'react';
import type { ShapeRenderingConfig } from './ShapeRenderingConfig';
export function applyShapeRendering(c: ShapeRenderingConfig): CSSProperties {
  return c.value === undefined ? {} : { shapeRendering: c.value } as CSSProperties;
}
