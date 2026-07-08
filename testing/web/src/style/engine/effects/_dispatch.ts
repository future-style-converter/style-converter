// _dispatch.ts — Phase-8 effects dispatch (clip + filter + mask = 22 props).
// Pattern mirrors engine/layout/_dispatch.ts: one explicit import per extractor
// and per applier so `tsc --noEmit` catches drift.

import type { CSSProperties } from 'react';

// ---- Clip (4) ----------------------------------------------------------
import { extractClipPath } from './clip/ClipPathExtractor';
import { applyClipPath } from './clip/ClipPathApplier';
import { extractClipPathGeometryBox } from './clip/ClipPathGeometryBoxExtractor';
import { applyClipPathGeometryBox } from './clip/ClipPathGeometryBoxApplier';
import { extractClipRule } from './clip/ClipRuleExtractor';
import { applyClipRule } from './clip/ClipRuleApplier';
import { extractClip } from './clip/ClipExtractor';
import { applyClip } from './clip/ClipApplier';

// ---- Filter (2) --------------------------------------------------------
import { extractFilter } from './filter/FilterExtractor';
import { applyFilter } from './filter/FilterApplier';
import { extractBackdropFilter } from './filter/BackdropFilterExtractor';
import { applyBackdropFilter } from './filter/BackdropFilterApplier';

// ---- Mask (16) ---------------------------------------------------------
import { extractMaskImage } from './mask/MaskImageExtractor';
import { applyMaskImage } from './mask/MaskImageApplier';
import { extractMaskMode } from './mask/MaskModeExtractor';
import { applyMaskMode } from './mask/MaskModeApplier';
import { extractMaskRepeat } from './mask/MaskRepeatExtractor';
import { applyMaskRepeat } from './mask/MaskRepeatApplier';
import { extractMaskPosition } from './mask/MaskPositionExtractor';
import { applyMaskPosition } from './mask/MaskPositionApplier';
import { extractMaskPositionX } from './mask/MaskPositionXExtractor';
import { applyMaskPositionX } from './mask/MaskPositionXApplier';
import { extractMaskPositionY } from './mask/MaskPositionYExtractor';
import { applyMaskPositionY } from './mask/MaskPositionYApplier';
import { extractMaskSize } from './mask/MaskSizeExtractor';
import { applyMaskSize } from './mask/MaskSizeApplier';
import { extractMaskOrigin } from './mask/MaskOriginExtractor';
import { applyMaskOrigin } from './mask/MaskOriginApplier';
import { extractMaskClip } from './mask/MaskClipExtractor';
import { applyMaskClip } from './mask/MaskClipApplier';
import { extractMaskComposite } from './mask/MaskCompositeExtractor';
import { applyMaskComposite } from './mask/MaskCompositeApplier';
import { extractMaskType } from './mask/MaskTypeExtractor';
import { applyMaskType } from './mask/MaskTypeApplier';
import { extractMaskBorderSource } from './mask/MaskBorderSourceExtractor';
import { applyMaskBorderSource } from './mask/MaskBorderSourceApplier';
import { extractMaskBorderSlice } from './mask/MaskBorderSliceExtractor';
import { applyMaskBorderSlice } from './mask/MaskBorderSliceApplier';
import { extractMaskBorderWidth } from './mask/MaskBorderWidthExtractor';
import { applyMaskBorderWidth } from './mask/MaskBorderWidthApplier';
import { extractMaskBorderOutset } from './mask/MaskBorderOutsetExtractor';
import { applyMaskBorderOutset } from './mask/MaskBorderOutsetApplier';
import { extractMaskBorderRepeat } from './mask/MaskBorderRepeatExtractor';
import { applyMaskBorderRepeat } from './mask/MaskBorderRepeatApplier';
import { extractMaskBorderMode } from './mask/MaskBorderModeExtractor';
import { applyMaskBorderMode } from './mask/MaskBorderModeApplier';

interface IRPropertyLike { type: string; data: unknown }

// Fold every effects property into a merged CSS object.
export function applyEffectsPhase8(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};

  // Clip.  ClipPathGeometryBox runs FIRST so that a full ClipPath declaration
  // (if present) overrides the standalone box keyword on the same `clipPath`
  // CSS key.  Clip (legacy) is unrelated to clip-path so order is irrelevant.
  Object.assign(out, applyClipPathGeometryBox(extractClipPathGeometryBox(properties)));
  Object.assign(out, applyClipPath(extractClipPath(properties)));
  Object.assign(out, applyClipRule(extractClipRule(properties)));
  Object.assign(out, applyClip(extractClip(properties)));

  // Filter + BackdropFilter.
  Object.assign(out, applyFilter(extractFilter(properties)));
  Object.assign(out, applyBackdropFilter(extractBackdropFilter(properties)));

  // Mask — order doesn't matter because each property owns a distinct CSS key.
  Object.assign(out, applyMaskImage(extractMaskImage(properties)));
  Object.assign(out, applyMaskMode(extractMaskMode(properties)));
  Object.assign(out, applyMaskRepeat(extractMaskRepeat(properties)));
  Object.assign(out, applyMaskPosition(extractMaskPosition(properties)));
  Object.assign(out, applyMaskPositionX(extractMaskPositionX(properties)));
  Object.assign(out, applyMaskPositionY(extractMaskPositionY(properties)));
  Object.assign(out, applyMaskSize(extractMaskSize(properties)));
  Object.assign(out, applyMaskOrigin(extractMaskOrigin(properties)));
  Object.assign(out, applyMaskClip(extractMaskClip(properties)));
  Object.assign(out, applyMaskComposite(extractMaskComposite(properties)));
  Object.assign(out, applyMaskType(extractMaskType(properties)));
  Object.assign(out, applyMaskBorderSource(extractMaskBorderSource(properties)));
  Object.assign(out, applyMaskBorderSlice(extractMaskBorderSlice(properties)));
  Object.assign(out, applyMaskBorderWidth(extractMaskBorderWidth(properties)));
  Object.assign(out, applyMaskBorderOutset(extractMaskBorderOutset(properties)));
  Object.assign(out, applyMaskBorderRepeat(extractMaskBorderRepeat(properties)));
  Object.assign(out, applyMaskBorderMode(extractMaskBorderMode(properties)));

  return out;
}
