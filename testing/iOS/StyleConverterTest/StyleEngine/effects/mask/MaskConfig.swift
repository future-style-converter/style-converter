//
//  MaskConfig.swift
//  StyleEngine/effects/mask — Phase 8.
//
//  CSS `mask-*` family: image source(s), mode, repeat, position, size,
//  origin, clip, composite, type, plus the 6 mask-border-* companions.
//  Maps onto SwiftUI's `.mask { … }` modifier for the common cases;
//  several corners (multi-layer composition, alpha-vs-luminance modes,
//  mask-border-image) have no direct SwiftUI analog and are best-effort.
//

import SwiftUI

// A single mask-image layer. `type` tokens:
//   "none"          → nothing to mask with (identity)
//   "url"           → opaque reference we stash as an id
//   "linear-gradient" / "radial-gradient" / "conic-gradient"
//                   → fold into a LinearGradient / RadialGradient /
//                     AngularGradient in the applier
enum MaskLayer: Equatable {
    case none
    case url(href: String)
    case linearGradient(angleDeg: Double, stops: [Gradient.Stop])
    case radialGradient(stops: [Gradient.Stop])
    case conicGradient(stops: [Gradient.Stop])
}

// `mask-mode`: alpha vs luminance. SwiftUI always uses alpha; we record
// luminance-mode so the applier can note the TODO.
enum MaskMode: Equatable { case alpha, luminance, matchSource }

// `mask-repeat` — six CSS keywords. SwiftUI lacks per-axis mask tiling
// so we document best-effort mapping in the applier.
enum MaskRepeat: Equatable { case repeatBoth, noRepeat, repeatX, repeatY, round, space }

// `mask-size` — keyword or explicit two-axis length/percent.
enum MaskSize: Equatable {
    case auto
    case cover
    case contain
    case explicit(width: CGFloat?, height: CGFloat?,
                  widthPercent: CGFloat?, heightPercent: CGFloat?)
}

// `mask-position` — unit-point (0…1) pair, with an optional points fallback.
struct MaskPositionValue: Equatable {
    var x: CGFloat = 0.5
    var y: CGFloat = 0.5
}

// `mask-origin` / `mask-clip` — reference box for mask geometry.
enum MaskBoxRef: String, Equatable {
    case contentBox, paddingBox, borderBox, fillBox, strokeBox, viewBox, noClip
}

// `mask-composite` — PorterDuff-style combiner between adjacent layers.
enum MaskComposite: Equatable { case add, subtract, intersect, exclude }

// `mask-type` — SVG mask element shape; irrelevant on iOS gallery items.
enum MaskType: Equatable { case alpha, luminance }

struct MaskConfig: Equatable {
    // Primary (foreground) mask image list. CSS allows comma-separated
    // multi-mask; SwiftUI `.mask` only takes one view so we stack with
    // ZStack in the applier.
    var images: [MaskLayer] = []
    // All ancillary fields default to CSS initial values / empty so an
    // incomplete aggregate still paints something sensible.
    var mode: MaskMode = .matchSource
    var repeatMode: MaskRepeat = .repeatBoth
    var position: MaskPositionValue = MaskPositionValue()
    var size: MaskSize = .auto
    var origin: MaskBoxRef = .borderBox
    var clip: MaskBoxRef = .borderBox
    var composite: MaskComposite = .add
    var type: MaskType = .luminance

    // Mask-border-* fields — captured so the self-test can observe them
    // even if the applier drops them (no SwiftUI analog). We keep them
    // as plain strings for now.
    var borderSource: String? = nil
    var borderSlice: String? = nil
    var borderWidth: String? = nil
    var borderOutset: String? = nil
    var borderRepeat: String? = nil
    var borderMode: MaskType? = nil

    // Touched flag — set when the extractor wrote any field.
    var touched: Bool = false
}
