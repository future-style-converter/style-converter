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

  // css-transforms-2 §4.1.1 names two ways a perspective reaches an element:
  // a `perspective()` FUNCTION in its own transform list ("computes into
  // the element's current transformation matrix", §12.2) and the
  // `perspective` PROPERTY, which "influence[s] the rendering of its
  // 3d-transformed children" (§8). The property is therefore NOT folded
  // into this element's own `transform` string any more (retro F3, skeptic
  // S2): the previous `perspective(<len>)` prefix hack made
  // `perspective: 1000px; transform: rotateY(45deg)` keystone the element
  // itself, which the frozen WPT ref for css-transforms/backface-visibility-
  // hidden-001 does not do (100 rows in every column — orthographic), and it
  // is the shape Compose stopped drawing in the same retro. The standalone
  // `perspective` key applyPerspective emitted above is the spec's actual
  // channel — the browser projects the CHILDREN through it natively.
  return out;
}
