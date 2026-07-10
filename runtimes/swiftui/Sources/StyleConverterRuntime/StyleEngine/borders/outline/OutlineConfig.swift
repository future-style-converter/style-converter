//
//  OutlineConfig.swift
//  StyleEngine/borders/outline — Phase 5.
//
//  CSS `outline-*` family. Differs from border in three ways:
//    1. Drawn OUTSIDE the border box (by `offset + width / 2`).
//    2. Doesn't consume layout space.
//    3. Exactly one value per property — no per-side logical variants.
//
//  Mirrors Android's `outline/OutlineConfig.kt`. Keeps the same four
//  fields so the cross-platform coverage matrix lines up.
//

// SwiftUI for Color, CoreGraphics via SwiftUI for CGFloat.
import SwiftUI

// Typed bag. Defaults match the CSS spec initial values
// (outline-width: medium → 3pt, style: none, colour: currentColor, offset: 0).
struct OutlineConfig: Equatable {
    // `outline-width`. Keywords (`thin|medium|thick`) pre-resolve to px
    // in the CSS parser so this always ends up numeric.
    // Wave 5: the initial value is MEDIUM = 3px (css-ui-4 §4.2), not 0 —
    // `outline-style: groove` with no declared width must paint a 3px
    // frame exactly like the browser (PW_Borders_Sizing_04: iOS painted
    // NOTHING because width stayed 0 and hasOutline gated the applier).
    // `outline-style: none` (the style initial) still suppresses painting
    // via hasOutline, so width-less/style-less components are unaffected.
    var width: CGFloat = 3
    // `outline-style`. Reuses the border style enum — outline and border
    // share the ten-keyword set per CSS Box 3 §2.4.
    var style: BorderStyleValue = .none
    // `outline-color`. Nil = the CSS `currentColor` default — the applier
    // then falls back to the environment foreground.
    var color: Color? = nil
    // `outline-offset`. Spacer distance between the border box and the
    // outline stroke. Can be negative (CSS spec allows it — outline is
    // then drawn inside the element).
    var offset: CGFloat = 0

    // True when the outline paints anything — mirrors the Android
    // `hasOutline` derived property.
    var hasOutline: Bool {
        width > 0 && style != .none && style != .hidden
    }
}
