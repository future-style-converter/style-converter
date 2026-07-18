//
//  BackgroundImageConfig.swift
//  StyleEngine/background — Phase 4.
//
//  CSS `background-image` is an *ordered list of layers*; the first layer
//  paints on top (opposite of CSS source order is a common confusion
//  worth noting). Each layer can be a gradient, a URL (raster), or the
//  literal `"none"`. We store a parsed, platform-neutral representation
//  so the applier can compose layers with SwiftUI gradient types.
//

import Foundation
import SwiftUI

// One entry in the background-image layer stack. Source-ordered:
// `layers[0]` is CSS source order, which means it paints on top.
enum BackgroundImageLayer: Equatable {

    // Single equatable for every layer kind; SwiftUI uses value types
    // throughout so Equatable is cheap.
    static func == (lhs: BackgroundImageLayer, rhs: BackgroundImageLayer) -> Bool {
        switch (lhs, rhs) {
        case (.none, .none): return true
        case (.url(let a), .url(let b)): return a == b
        case let (.linear(la, ls), .linear(ra, rs)):
            return la == ra && ls == rs
        case let (.radial(lk, ls, lcx, lcy), .radial(rk, rs, rcx, rcy)):
            return lk == rk && ls == rs && lcx == rcx && lcy == rcy
        case let (.conic(la, ls, lcx, lcy), .conic(ra, rs, rcx, rcy)):
            return la == ra && ls == rs && lcx == rcx && lcy == rcy
        case let (.repeating(lk, la, ls), .repeating(rk, ra, rs)):
            return lk == rk && la == ra && ls == rs
        default: return false
        }
    }

    // CSS `none` — paint nothing for this layer.
    case none
    // URL (including data URIs). SwiftUI can't render arbitrary network
    // URLs without AsyncImage (remote url() is a defined no-op — see
    // BackgroundURLImage.swift); data/file URIs decode and paint. The
    // converter preserves url() bytes since wave 8 (base64 arrives
    // intact); the case keeps the raw string so the stack indexing and
    // the resolver's scheme dispatch stay correct.
    case url(String)
    // Linear gradient. `angleDeg` nil means "default 180deg" per CSS.
    case linear(angleDeg: Double?, stops: [BackgroundImageStop])
    // Radial gradient. `shape` keeps the IR keyword ("circle"/"ellipse")
    // or nil when unspecified; `cx/cy` are 0..1 fractions for the CSS
    // `at <position>` clause (default 0.5 / 0.5 = centre).
    case radial(shape: String?, stops: [BackgroundImageStop], cx: Double, cy: Double)
    // Conic gradient. `fromDeg` nil = start from top (CSS default).
    // `cx/cy` carry the optional `at <pos>` clause as 0..1 fractions.
    case conic(fromDeg: Double?, stops: [BackgroundImageStop], cx: Double, cy: Double)
    // Repeating variant — SwiftUI has no native API so the applier
    // renders a stub via the non-repeating branch with a documentation
    // note.
    case repeating(kind: RepeatingKind, angleDeg: Double?, stops: [BackgroundImageStop])

    enum RepeatingKind: Equatable { case linear, radial, conic }
}

// Single gradient stop. `position` is 0..1 (normalised from 0..100
// percentage in the IR) or nil when CSS didn't specify one (SwiftUI
// will interpolate evenly across nil positions).
struct BackgroundImageStop: Equatable {
    // Parsed colour. May be `.dynamic(...)` or `.unknown` — applier
    // falls back to clear when the colour can't be resolved.
    var color: ColorValue
    // Normalised position 0..1, or nil for "auto".
    var position: Double?
}

// Top-level container. `layers` is empty when no BackgroundImage property
// was present; an empty config is treated as "no-op" by the applier.
struct BackgroundImageConfig: Equatable {
    // Ordered per CSS source — index 0 paints on top.
    var layers: [BackgroundImageLayer] = []

    // Applier short-circuit check.
    var hasAny: Bool { !layers.isEmpty }
}
