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

    // True when the side is paintable: a DECLARED visible style AND a
    // positive used width. Matches Android's `hasBorder` derived property
    // (BorderSideConfig.kt: `style != null && style != NONE && style !=
    // HIDDEN`), and every band consumer (bandInsets, backgroundClipInsets,
    // ContainingBlockBasis, MarginCollapse) gates on it — so "no border"
    // means no paint AND no inset, consistently.
    var hasBorder: Bool {
        // CSS 2.1 §8.5.3 / css-backgrounds-3 §3.2: the initial value of
        // `border-style` is `none`, and `none`/`hidden` make the used
        // border-width ZERO ("Color and width are ignored"). An ABSENT
        // style therefore IS `none`: a width-only longhand
        // (`border-block-start-width: 6px` with no style) paints nothing
        // and insets nothing, exactly like the web reference. The old
        // `if let s = style, s == .none || s == .hidden` gate let a nil
        // style through, and BorderSideApplier's `style ?? .solid` then
        // painted width-only sides as a solid currentColor band on iOS
        // alone (retro A11#5: pairs-02 PW_Borders_Color_01 iOS
        // (238,238,238) top/right bands vs web/Android fill (167,139,250)).
        guard let s = style, s != .none, s != .hidden else { return false }
        // Effective width: explicit wins; absent width with a visible
        // declared style falls back to the CSS initial `medium` (3px —
        // CSS Backgrounds 3 §3.3). Web paints `border-*-style: dotted`
        // with no width as a 3px dotted band; iOS previously dropped
        // the side entirely (borders/016_Decorated's missing dotted
        // border-block-end).
        guard let w = effectiveWidth, w > 0 else { return false }
        return w > 0
    }

    // Paint width in points: explicit width, else the `medium` (3px)
    // default when a visible style was declared, else nil (nothing to
    // paint — CSS initial border-style is `none`).
    var effectiveWidth: CGFloat? {
        if let w = width { return w }
        if let s = style, s != .none, s != .hidden { return 3 }
        return nil
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
