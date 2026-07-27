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

// One gradient-center axis (`at <position>`, css-images-3 §3.5 /
// css-images-4 §3.4.4). CSS allows a full <length-percentage> per axis;
// the IR wire carries percents as raw numbers and lengths as objects
// (IRLengthPercentageSerializer). The extractor resolves runtime-dependent
// units (lh/em/rem) to PX at extract time — font metrics live in the
// component's own FontSize/LineHeight properties there — so the applier
// only ever sees FRACTION or PX. Byte-parallel twin of the Compose
// `GradientCoord` (ColorConfig.kt): identical kinds, identical resolve.
struct GradientCoord: Equatable {
    enum Kind: Equatable {
        /// 0..1 fraction of the gradient box axis (percent / keyword).
        case fraction
        /// Absolute CSS pixels from the box origin (length centers).
        case px
    }
    var kind: Kind
    var value: Double

    /// Resolve to a 0..1 fraction of the given axis size in px. PX on a
    /// degenerate (≤0) axis falls back to center — the CSS default — so a
    /// zero-sized box can never divide by zero.
    func fraction(_ axisPx: Double) -> Double {
        switch kind {
        case .fraction: return value
        case .px: return axisPx > 0 ? value / axisPx : 0.5
        }
    }

    /// CSS default center (50%).
    static let center = GradientCoord(kind: .fraction, value: 0.5)
    /// Fraction constructor (percent ÷ 100 done by the caller).
    static func fraction(_ f: Double) -> GradientCoord { .init(kind: .fraction, value: f) }
    /// Absolute-pixel constructor.
    static func px(_ v: Double) -> GradientCoord { .init(kind: .px, value: v) }
}

// One weighted cross-fade() argument (css-images-4 §2.6.2): EFFECTIVE
// weight as a 0..1 fraction (the extractor runs CrossFadeMath's shared
// normalization on the authored wire weights) + the sub-image layer.
// Recursion through the enum is fine — the array boxes the storage.
struct CrossFadeArg: Equatable {
    var weight: Double
    var layer: BackgroundImageLayer
}

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
        // <color>-as-image (cross-fade argument, css-images-4 §2.6.2).
        case let (.color(a), .color(b)): return a == b
        // cross-fade() — arg lists compare element-wise.
        case let (.crossFade(a), .crossFade(b)): return a == b
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
    // or nil when unspecified; `cx/cy` carry the CSS `at <position>`
    // clause per-axis as GradientCoord (FRACTION or PX — A-RC8; default
    // `.center` = the CSS 50% 50%).
    case radial(shape: String?, stops: [BackgroundImageStop], cx: GradientCoord, cy: GradientCoord)
    // Conic gradient. `fromDeg` nil = start from top (CSS default).
    // `cx/cy` carry the optional `at <pos>` clause (same coord contract).
    case conic(fromDeg: Double?, stops: [BackgroundImageStop], cx: GradientCoord, cy: GradientCoord)
    // Repeating variant — SwiftUI has no native API so the applier
    // renders a stub via the non-repeating branch with a documentation
    // note.
    case repeating(kind: RepeatingKind, angleDeg: Double?, stops: [BackgroundImageStop])
    // A bare `<color>` used AS an image — only produced as a cross-fade()
    // argument (css-images-4 §2.6.2 `<cf-image>` allows `<color>`).
    // Renders as a solid fill of the layer box.
    case color(ColorValue)
    // cross-fade() (css-images-4 §2.6.2, A-RC2): weighted premultiplied
    // SUM of the argument images — the applier composites via opacity ×
    // .plusLighter inside a compositing group (see GradientApplier).
    case crossFade([CrossFadeArg])

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
