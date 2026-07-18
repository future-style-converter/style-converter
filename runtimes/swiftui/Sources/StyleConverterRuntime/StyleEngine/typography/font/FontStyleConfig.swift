//
//  FontStyleConfig.swift
//  StyleEngine/typography/font — Phase 6.
//
//  CSS `font-style`: `normal | italic | oblique [ <angle> ]`. The render
//  vehicle is boolean (a fixed 14deg synthetic shear — see
//  syntheticObliqueUIFont in Renderer/ComponentRenderer.swift), so the
//  extractor collapses the value to one flag: italic / bare oblique /
//  `oblique <angle>` with angle ≥ 14deg → true; normal / sub-threshold
//  angles → false. That mirrors Chromium's fixed-skew synthesis on the
//  roman-only harness Inter exactly (wave-6 capture evidence in
//  FontStyleExtractor.swift's header) — per-angle slants would diverge.
//

import Foundation

/// Italic flag. `nil` → inherit; `false` → explicit `normal`.
struct FontStyleConfig: Equatable {
    /// True when italic or oblique was declared.
    var italic: Bool? = nil
}
