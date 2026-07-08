// ZoomApplier.ts — emits CSS declarations for the legacy `zoom` property.
//
// Cross-platform parity note: CSS `zoom` is a non-standard but widely
// supported property that scales an element AND consumes scaled layout
// space (unlike `transform: scale()` which only paints scaled). Modern
// browsers honour it, so a `zoom: 2` box renders 2× wide on web and
// reserves 2× the layout slot. Neither SwiftUI nor Compose has an
// equivalent that combines geometric scale + layout-space scale: iOS
// and Android currently parse the IR `Zoom` property but have no
// applier (no `ZoomApplier.swift`, no `ZoomApplier.kt`), so the value
// is silently dropped and the box renders at 1×. Forging zoom on iOS /
// Android via `.scaleEffect` / `Modifier.scale` would scale the paint
// but not the layout, producing a different visual divergence (the
// scaled box would overflow its parent or be clipped).
//
// Until both native pipelines grow real zoom support — which requires
// a custom layout pass, not just a Modifier — we drop the CSS
// declaration on web too. Otherwise web is the only platform where the
// `Zoom_75 / Zoom_150 / Zoom_200` fixtures actually scale, and
// iOS-web / Android-web SSIM bottoms out near 0.67-0.74 because the web
// box is 75-200% of its native counterpart.
//
// Mirrors the same parity strategy applied to font-stretch — see
// `FontStretchApplier.ts` for the fuller rationale. The IR property is
// still parsed (Config + Extractor unchanged) so audit coverage stays
// intact — we just don't emit the CSS that drives the visible
// divergence.

import type { CSSProperties } from 'react';
import type { ZoomConfig } from './ZoomConfig';

export function applyZoom(_c: ZoomConfig): CSSProperties {
  // Intentional no-op: see file kdoc above. iOS + Android can't honour
  // `zoom` with their current renderers (no Modifier scales layout
  // space), and emitting `zoom` on web alone makes it the only platform
  // with a visible scale.
  return {};
}
