//
//  BoxShadowConfig.swift
//  StyleEngine/effects/shadow — Phase 5.
//
//  Path mirrors irmodels/properties/effects/shadow/BoxShadow.kt.
//
//  CSS `box-shadow` is a comma-separated list of shadow layers. Each
//  layer has up to five fields:
//    offset-x, offset-y, blur-radius (optional), spread-radius (optional),
//    colour (optional, defaults to currentColor), inset (optional keyword).
//
//  SwiftUI's built-in `.shadow(...)` is outset-only and supports exactly
//  one layer per call; we therefore keep an array of layers so the
//  applier can stack multiple `.shadow()` modifiers. Inset shadows are
//  drawn through a masked overlay inside the applier.
//

// SwiftUI for Color, CoreGraphics for CGFloat.
import SwiftUI

// One shadow in the box-shadow list. `blur` and `spread` are optional so
// we can tell a zero-value shadow from a shadow that omitted those args.
struct BoxShadowLayer: Equatable {
    // Horizontal offset in points — positive shifts right.
    var x: CGFloat = 0
    // Vertical offset in points — positive shifts down (CSS convention).
    var y: CGFloat = 0
    // Blur radius in points. Zero = crisp shadow. SwiftUI treats the
    // `.shadow(radius:)` arg as roughly CSS blur / 2 visually, so we
    // divide at apply time.
    var blur: CGFloat = 0
    // Spread in points — grows/shrinks the shadow rectangle before blur.
    // SwiftUI has no spread primitive; applier approximates via a
    // scaled Shape overlay.
    var spread: CGFloat = 0
    // Nil → CSS `currentColor` fallback; applier uses `.primary`.
    var color: Color? = nil
    // `inset` keyword — renders the shadow inside the border box.
    var inset: Bool = false
}

// Full box-shadow config — list of layers plus a helper predicate.
struct BoxShadowConfig: Equatable {
    // Paint order follows CSS spec: first layer is drawn topmost; list
    // order matches the IR array order.
    var layers: [BoxShadowLayer] = []

    // True when there's anything to paint.
    var hasAny: Bool { !layers.isEmpty }
}
