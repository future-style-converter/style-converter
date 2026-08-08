// ZoomApplier.ts — emits the CSS `zoom` declaration.
//
// Platform API: the `zoom` CSS property (css-viewport-1 §"The zoom
// property"), which every Chromium/WebKit-based UA implements. React's
// `CSSProperties` carries it as a first-class key, so the applier is a
// straight passthrough of the token ZoomExtractor built.
//
// WAVE-36 LANE M2 — WHY THIS STOPPED BEING A NO-OP.
// This applier used to `return {}` on purpose. The rationale was
// cross-platform PARITY: neither SwiftUI nor Compose has a modifier that
// scales an element's LAYOUT slot (`.scaleEffect` / `Modifier.scale`
// scale the paint only), so web was the one platform where the
// `Zoom_75 / Zoom_150 / Zoom_200` components of fixtures/visual-test.json
// actually changed size, and the iOS-web / Android-web SSIM for those
// three cells sank accordingly. Dropping the declaration on web made the
// three-way comparison agree — by making all three platforms wrong.
//
// That trade is the exact "silent fallthrough" CLAUDE.md forbids: it hid
// a native gap instead of reporting it, and it cost real correctness.
// Measured on the WPT corpus (tools/titan/results/webmap-v1.json rank 10):
// css/css-viewport/zoom scores 1 PASS / 41 FAIL against the frozen
// Chromium reference, and every one of the 41 is this no-op — border-
// spacing 0.334, zoom-pseudo-image 0.554, text-stroke-width 0.633,
// border-width 0.653 … all "a length the ref multiplied by the effective
// zoom and we did not".
//
// So web now renders `zoom` truthfully.
//
// WAVE-37 — THE DIVERGENCE THIS BANNER USED TO REPORT IS CLOSED.
// Lane M2 left the natives on an honest no-op + TODO, which made the
// three `Zoom_*` cells of fixtures/visual-test.json diverge
// iOS/Android-vs-web. The fix was convergence, not reverting this file:
// both natives now ship a real dedicated triplet —
//   runtimes/compose/.../rendering/ZoomApplier.kt
//   runtimes/swiftui/.../StyleEngine/rendering/ZoomApplier.swift
// — implementing the same bounded css-viewport semantics by
// MEASURE-THEN-SCALE (measure the subtree against constraints/zoom,
// report size×zoom so the LAYOUT SLOT moves, then scale the paint from
// the top-left origin). That is exactly the "used-value scale threaded
// through layout, not a paint-time transform" the old TODO called for.
// All three runtimes render `zoom` now, and the visual-test baselines
// for the three cells were refreshed to the zoomed render.

import type { CSSProperties } from 'react';
import type { ZoomConfig } from './ZoomConfig';

export function applyZoom(c: ZoomConfig): CSSProperties {
  // No `Zoom` in the IR (or a payload the extractor declined to model,
  // which it already tracked) → emit nothing, so the cascade is untouched.
  if (!c.value) return {};
  // Straight declaration. `zoom` scales used values AND the layout slot,
  // which is why it cannot be approximated with `transform: scale()`.
  return { zoom: c.value };
}
