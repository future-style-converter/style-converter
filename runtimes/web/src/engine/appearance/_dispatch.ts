// _dispatch.ts — Phase-10 appearance long-tail dispatch (4 properties).
import type { CSSProperties } from 'react';
import { extractAppearance } from './AppearanceExtractor';
import { applyAppearance } from './AppearanceApplier';
import { extractAppearanceVariant } from './AppearanceVariantExtractor';
import { applyAppearanceVariant } from './AppearanceVariantApplier';
import { extractColorAdjust } from './ColorAdjustExtractor';
import { applyColorAdjust } from './ColorAdjustApplier';
import { extractImageRenderingQuality } from './ImageRenderingQualityExtractor';
import { applyImageRenderingQuality } from './ImageRenderingQualityApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyAppearancePhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyAppearance(extractAppearance(properties)));
  Object.assign(out, applyAppearanceVariant(extractAppearanceVariant(properties)));
  Object.assign(out, applyColorAdjust(extractColorAdjust(properties)));
  Object.assign(out, applyImageRenderingQuality(extractImageRenderingQuality(properties)));
  return out;
}
