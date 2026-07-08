// _dispatch.ts — Phase-10 rendering long-tail dispatch (9 properties).
import type { CSSProperties } from 'react';
import { extractContentVisibility } from './ContentVisibilityExtractor';
import { applyContentVisibility } from './ContentVisibilityApplier';
import { extractFieldSizing } from './FieldSizingExtractor';
import { applyFieldSizing } from './FieldSizingApplier';
import { extractForcedColorAdjust } from './ForcedColorAdjustExtractor';
import { applyForcedColorAdjust } from './ForcedColorAdjustApplier';
import { extractPrintColorAdjust } from './PrintColorAdjustExtractor';
import { applyPrintColorAdjust } from './PrintColorAdjustApplier';
import { extractImageOrientation } from './ImageOrientationExtractor';
import { applyImageOrientation } from './ImageOrientationApplier';
import { extractImageResolution } from './ImageResolutionExtractor';
import { applyImageResolution } from './ImageResolutionApplier';
import { extractInputSecurity } from './InputSecurityExtractor';
import { applyInputSecurity } from './InputSecurityApplier';
import { extractInterpolateSize } from './InterpolateSizeExtractor';
import { applyInterpolateSize } from './InterpolateSizeApplier';
import { extractZoom } from './ZoomExtractor';
import { applyZoom } from './ZoomApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyRenderingPhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyContentVisibility(extractContentVisibility(properties)));
  Object.assign(out, applyFieldSizing(extractFieldSizing(properties)));
  Object.assign(out, applyForcedColorAdjust(extractForcedColorAdjust(properties)));
  Object.assign(out, applyPrintColorAdjust(extractPrintColorAdjust(properties)));
  Object.assign(out, applyImageOrientation(extractImageOrientation(properties)));
  Object.assign(out, applyImageResolution(extractImageResolution(properties)));
  Object.assign(out, applyInputSecurity(extractInputSecurity(properties)));
  Object.assign(out, applyInterpolateSize(extractInterpolateSize(properties)));
  Object.assign(out, applyZoom(extractZoom(properties)));
  return out;
}
