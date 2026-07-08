// _dispatch.ts — Phase-8 transforms dispatch (10 properties).
// Mirrors engine/layout/_dispatch.ts.  One explicit import per extractor and
// per applier so `tsc --noEmit` catches drift.

import type { CSSProperties } from 'react';

import { extractTransform } from './TransformExtractor';
import { applyTransform } from './TransformApplier';
import { extractRotate } from './RotateExtractor';
import { applyRotate } from './RotateApplier';
import { extractScale } from './ScaleExtractor';
import { applyScale } from './ScaleApplier';
import { extractTranslate } from './TranslateExtractor';
import { applyTranslate } from './TranslateApplier';
import { extractTransformOrigin } from './TransformOriginExtractor';
import { applyTransformOrigin } from './TransformOriginApplier';
import { extractTransformBox } from './TransformBoxExtractor';
import { applyTransformBox } from './TransformBoxApplier';
import { extractTransformStyle } from './TransformStyleExtractor';
import { applyTransformStyle } from './TransformStyleApplier';
import { extractPerspective } from './PerspectiveExtractor';
import { applyPerspective } from './PerspectiveApplier';
import { extractPerspectiveOrigin } from './PerspectiveOriginExtractor';
import { applyPerspectiveOrigin } from './PerspectiveOriginApplier';
import { extractBackfaceVisibility } from './BackfaceVisibilityExtractor';
import { applyBackfaceVisibility } from './BackfaceVisibilityApplier';

interface IRPropertyLike { type: string; data: unknown }

// Fold every transforms property into a merged CSS object.  Appliers that
// touch draft-level properties (rotate, scale, translate) return
// Record<string,string>; both shapes spread-merge into CSSProperties.
export function applyTransformsPhase8(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyTransform(extractTransform(properties)));                  // transform fn list
  Object.assign(out, applyRotate(extractRotate(properties)));                        // `rotate` longhand
  Object.assign(out, applyScale(extractScale(properties)));                          // `scale` longhand
  Object.assign(out, applyTranslate(extractTranslate(properties)));                  // `translate` longhand
  Object.assign(out, applyTransformOrigin(extractTransformOrigin(properties)));      // origin point
  Object.assign(out, applyTransformBox(extractTransformBox(properties)));            // reference box
  Object.assign(out, applyTransformStyle(extractTransformStyle(properties)));        // flat vs 3d
  Object.assign(out, applyPerspective(extractPerspective(properties)));              // 3d depth
  Object.assign(out, applyPerspectiveOrigin(extractPerspectiveOrigin(properties)));  // vanishing point
  Object.assign(out, applyBackfaceVisibility(extractBackfaceVisibility(properties)));// card flip

  // CSS spec: `perspective: <length>` on an element only affects the
  // 3D rendering of its CHILDREN — not the element itself. iOS and
  // Android, however, interpret a co-located `perspective` + 3D
  // `transform` (rotateY, rotateX, …) as "apply this perspective to
  // the rotation" — which matches the visible intent of every
  // perspective fixture in the suite.  To converge web with
  // iOS/Android, fold the `perspective` length into the transform
  // string itself via the `perspective(<length>)` function. This is
  // the canonical spec-compliant way to apply perspective to the
  // SAME element's transform.  Keep the standalone `perspective` key
  // for the (rare) genuine parent-of-children use case — both
  // declarations are valid simultaneously.
  const persp = extractPerspective(properties);
  const xform = extractTransform(properties);
  if (persp.value !== undefined && xform.value !== undefined &&
      // Only prepend when the transform string actually contains a
      // 3D function. Otherwise the perspective() prefix would just
      // tax the GPU for zero visual difference.
      /(rotate[XYZ3]|translate[XYZ3]|matrix3d|scale[XYZ3])/i.test(String(xform.value)) &&
      // Don't double-add when the author already prefixed perspective().
      !/^\s*perspective\s*\(/.test(String(xform.value))) {
    out.transform = `perspective(${persp.value}) ${xform.value}`;
  }
  return out;
}
