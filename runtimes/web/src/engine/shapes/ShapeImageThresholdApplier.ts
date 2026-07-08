// ShapeImageThresholdApplier.ts — emits { shapeImageThreshold }.  MDN: shape-image-threshold.
import type { CSSProperties } from 'react';
import type { ShapeImageThresholdConfig } from './ShapeImageThresholdConfig';
export function applyShapeImageThreshold(c: ShapeImageThresholdConfig): CSSProperties {
  return c.value === undefined ? {} : { shapeImageThreshold: c.value } as CSSProperties;
}
