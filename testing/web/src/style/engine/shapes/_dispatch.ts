// _dispatch.ts — Phase-10 shapes long-tail dispatch (5 properties).
import type { CSSProperties } from 'react';
import { extractShapeOutside } from './ShapeOutsideExtractor';
import { applyShapeOutside } from './ShapeOutsideApplier';
import { extractShapeMargin } from './ShapeMarginExtractor';
import { applyShapeMargin } from './ShapeMarginApplier';
import { extractShapePadding } from './ShapePaddingExtractor';
import { applyShapePadding } from './ShapePaddingApplier';
import { extractShapeImageThreshold } from './ShapeImageThresholdExtractor';
import { applyShapeImageThreshold } from './ShapeImageThresholdApplier';
import { extractShapeInside } from './ShapeInsideExtractor';
import { applyShapeInside } from './ShapeInsideApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyShapesPhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyShapeOutside(extractShapeOutside(properties)));
  Object.assign(out, applyShapeMargin(extractShapeMargin(properties)));
  Object.assign(out, applyShapePadding(extractShapePadding(properties)));
  Object.assign(out, applyShapeImageThreshold(extractShapeImageThreshold(properties)));
  Object.assign(out, applyShapeInside(extractShapeInside(properties)));
  return out;
}
