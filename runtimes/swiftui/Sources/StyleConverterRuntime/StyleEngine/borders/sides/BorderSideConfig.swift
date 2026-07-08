//
//  BorderSideConfig.swift
//  StyleEngine/borders/sides — Phase 5.
//
//  Value struct for CSS `border-*-{width,color,style}` on all four physical
//  sides plus the eight logical-flow siblings (block-start/end,
//  inline-start/end × width/color/style).
//
//  Mirrors Android's `testing/Android/.../borders/sides/BorderSideConfig.kt`
//  so the coverage matrix is auditable cross-platform. iOS-specific
//  concerns (sRGB Color, `CGFloat` points) live in this file; the
//  platform-agnostic style keyword set lives in `BorderStyleValue.swift`
//  next to the extractor + applier.
//

// SwiftUI for `Color`, CoreGraphics for `CGFloat`. Foundation is pulled in
// transitively — `Equatable` is stdlib so no extra import.
import SwiftUI

// Per-side payload. Optional width/color/style distinguish "not set in
// IR" (nil) from "explicitly zero" (e.g. `border-top-width: 0`) — the
// applier treats nil as "inherit defaults", zero as "draw nothing".
// Matches parser file:
//   src/main/kotlin/app/parsing/css/properties/longhands/borders/sides/*
struct BorderSideConfig: Equatable {
    // CSS `border-<side>-width`. Pre-resolved to points when possible
    // (`thin`/`medium`/`thick` keywords arrive as `{px: N, original: "..."}`).
    // Nil when the IR entry is absent — lets the shorthand-driven
    // BorderShorthand applier short-circuit cleanly.
    var width: CGFloat? = nil

    // CSS `border-<side>-color`. Honours the `currentColor` keyword by
    // leaving this nil (the applier then falls back to the environment
    // foregroundColor — matches CSS 2.1 §8.5.2 default).
    var color: Color? = nil

    // CSS `border-<side>-style`. Covers all 10 keywords; see the
    // `BorderStyleValue` enum for the exhaustive list.
    var style: BorderStyleValue? = nil

    // True when the side is paintable: positive width, non-`none`
    // non-`hidden` style, and a resolvable (or defaultable) colour.
    // Matches Android's `hasBorder` derived property.
    var hasBorder: Bool {
        guard let w = width, w > 0 else { return false }
        // `none` + `hidden` paint nothing per CSS 2.1 §8.5.3.
        if let s = style, s == .none || s == .hidden { return false }
        return true
    }
}

// Four-sided logical configuration. The extractor populates top/end/
// bottom/start using LTR-physical mapping (matches Android behaviour);
// a future layoutDirection-aware applier can swap end↔start for RTL if
// we ever exercise RTL fixtures.
struct AllBordersConfig: Equatable {
    // Physical `top` (and `border-block-start` in LTR).
    var top: BorderSideConfig = BorderSideConfig()
    // Physical `right` / logical `border-inline-end`.
    var end: BorderSideConfig = BorderSideConfig()
    // Physical `bottom` / logical `border-block-end`.
    var bottom: BorderSideConfig = BorderSideConfig()
    // Physical `left` / logical `border-inline-start`.
    var start: BorderSideConfig = BorderSideConfig()

    // Fast-path predicate: at least one side is paintable.
    var hasAny: Bool {
        top.hasBorder || end.hasBorder || bottom.hasBorder || start.hasBorder
    }

    // Uniform borders let the applier take the cheap `.border()` path —
    // identical width, color, and style on every side. Width/color/style
    // equality uses the optional's `Equatable` synthesis.
    var isUniform: Bool {
        top.width == end.width && end.width == bottom.width && bottom.width == start.width
            && top.color == end.color && end.color == bottom.color && bottom.color == start.color
            && top.style == end.style && end.style == bottom.style && bottom.style == start.style
    }
}
