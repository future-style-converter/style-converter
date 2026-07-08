//
//  BorderMiscConfig.swift
//  StyleEngine/borders/sides — Phase 5.
//
//  Catch-all for three short "keyword-only" border-family properties
//  whose triplets would otherwise be single-line-each:
//
//    - `box-decoration-break` — paginated rendering hint. Web fragments
//      a box across line/column/page breaks; `clone` restarts the
//      border, `slice` continues through. Irrelevant on iOS (no
//      fragmentation) — captured but not applied.
//    - `corner-shape` — CSS Backgrounds 4 draft. Lets authors pick
//      `round` (default), `bevel`, `scoop`, `notch` for each corner.
//      Not yet a shipping spec in any browser; captured for future work.
//    - `border-boundary` — experimental proposal for limiting border
//      propagation across flex/grid containers. Captured for parity.
//
//  All three appliers are identity today — they log the keyword to the
//  simulator console so fixture screenshots are reviewable without
//  silent drops. This mirrors Android's "config-only, degrades
//  gracefully" stance for these properties.
//

import Foundation

// `box-decoration-break` — CSS Backgrounds 3 §5.6.
enum BoxDecorationBreak: String, Equatable {
    // Splits border/padding/background at each fragment (CSS default).
    case slice
    // Redraws border/padding at every fragment — used for pill-shaped
    // inline highlights split across lines.
    case clone
}

// `corner-shape` — CSS Backgrounds 4 draft §3.1.
enum CornerShapeKind: String, Equatable {
    // Classic quarter-ellipse (default).
    case round
    // 45° flat corner — replaces the arc with a single line segment.
    case bevel
    // Concave arc — the draft's "inverted round".
    case scoop
    // V-shaped cut — hardest to approximate without a real Shape.
    case notch
}

// Miscellaneous border config bag. Each field is optional so the
// extractor only touches what it sees in the IR.
struct BorderMiscConfig: Equatable {
    var decorationBreak: BoxDecorationBreak? = nil
    var cornerShape: CornerShapeKind? = nil
    // `border-boundary` is one of `none | parent | display`; stored as a
    // raw keyword because iOS doesn't act on it.
    var borderBoundary: String? = nil

    // True when any miscellaneous border keyword was captured — lets the
    // applier log once per component instead of every frame.
    var hasAny: Bool {
        decorationBreak != nil || cornerShape != nil || borderBoundary != nil
    }
}
