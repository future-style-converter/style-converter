// _dispatch.ts — Phase-10 images long-tail dispatch (4 properties).
import type { CSSProperties } from 'react';
import { extractImageRendering } from './ImageRenderingExtractor';
import { applyImageRendering } from './ImageRenderingApplier';
import { extractObjectFit } from './ObjectFitExtractor';
import { applyObjectFit } from './ObjectFitApplier';
import { extractObjectPosition } from './ObjectPositionExtractor';
import { applyObjectPosition } from './ObjectPositionApplier';
import { extractObjectViewBox } from './ObjectViewBoxExtractor';
import { applyObjectViewBox } from './ObjectViewBoxApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyImagesPhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyImageRendering(extractImageRendering(properties)));
  Object.assign(out, applyObjectFit(extractObjectFit(properties)));
  Object.assign(out, applyObjectPosition(extractObjectPosition(properties)));
  Object.assign(out, applyObjectViewBox(extractObjectViewBox(properties)));
  return out;
}
