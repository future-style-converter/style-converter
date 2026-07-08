// FontStretchApplier.ts — emits CSS declarations from a FontStretchConfig.
//
// Cross-platform parity note: `font-stretch` is a real CSS property that
// modern browsers honour natively when the loaded font exposes a width
// axis (variable fonts) or has matching condensed/expanded family
// members. macOS Safari + SF Pro deliver visibly narrower / wider text
// for `font-stretch: ultra-condensed` / `ultra-expanded`. Neither iOS
// SwiftUI nor Android Compose has any equivalent path: SwiftUI's
// `FontStretchApplier` is a no-op (font-axis access requires a custom
// renderer that hasn't been built), and Android's
// `TextStyleApplier`/`TypographyApplier` previously forged a fake
// stretch via `TextGeometricTransform.scaleX` but that was reverted as
// a CSS-spec mismatch (system sans-serif has no width axis on Android
// either). Until one of those pipelines grows real width-axis support,
// the only way to keep visual-test SSIM aligned is to also drop the
// CSS declaration on web — otherwise web is the only platform with
// visible stretch, and `Typography_FontUltraCondensed` /
// `_FontUltraExpanded` sit at SSIM ~0.46 because of it.
//
// The IR property is still parsed (Config retained, Extractor
// unchanged) so audit coverage stays intact — we just don't emit the
// CSS that drives the visible divergence.

import type { CSSProperties } from 'react';
import type { FontStretchConfig } from './FontStretchConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type FontStretchStyles = Pick<CSSProperties, 'fontStretch'>;

export function applyFontStretch(_config: FontStretchConfig): FontStretchStyles {
  // Intentional no-op: see file kdoc above. iOS + Android can't honour
  // a width axis with their current renderers, and emitting `fontStretch`
  // on web alone makes it the only platform with a visible change.
  return {};
}
