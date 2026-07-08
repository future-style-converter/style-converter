//
//  BorderImageConfig.swift
//  StyleEngine/borders/image — Phase 5.
//
//  CSS `border-image-*` family. Full spec is a 9-slice image applied
//  around the border box. iOS has no native `border-image` primitive,
//  so the Applier renders what it can (solid-colour placeholder for
//  `url(...)` sources, nothing for `none`, best-effort tiling for
//  stretch/repeat/round/space). Data is still captured faithfully so
//  a future phase can swap in a real NineSlice drawer without
//  re-touching the extractor.
//
//  Mirrors Android's `image/BorderImageConfig.kt`.
//

// SwiftUI transitively for CGFloat / CGSize types.
import SwiftUI

// Source discriminator. Mirrors the Kotlin sealed class.
enum BorderImageSource: Equatable {
    // `border-image-source: none` — initial value.
    case none
    // `url("...")` — we capture the URL but load it async in the
    // applier. Failure leaves the element borderless.
    case url(String)
    // `linear-gradient(...)` / `radial-gradient(...)` — captured as the
    // raw CSS expression since the CSS-parser emits it that way; future
    // phase will plumb it through the Phase 4 GradientApplier.
    case gradient(String)
}

// `border-image-slice` — one per edge, plus the fill flag.
struct BorderImageSliceEdge: Equatable {
    // Raw numeric slice value.
    var value: CGFloat
    // True when the value came from a "<number>%" form.
    var isPercent: Bool
}

// `border-image-width` / `border-image-outset`. Same four shapes as Android.
enum BorderImageDimension: Equatable {
    // `auto` — match the slice size at render time.
    case auto
    // Length in points.
    case length(CGFloat)
    // "N%" relative to the border area.
    case percent(CGFloat)
    // Plain number — multiplier of the computed border-width.
    case number(CGFloat)
}

// `border-image-repeat` keyword set (per edge axis).
enum BorderImageRepeat: String, Equatable {
    // Stretch to fill — initial value.
    case stretch
    // Tile whole copies.
    case round
    // Tile and resize so whole copies fit.
    case repeatTile = "repeat"
    // Tile leaving even gaps so whole copies fit.
    case space
}

// Aggregate — fields are 1:1 with the Android struct for parity.
struct BorderImageConfig: Equatable {
    var source: BorderImageSource = .none
    // Four slice edges + fill.
    var sliceTop: BorderImageSliceEdge? = nil
    var sliceRight: BorderImageSliceEdge? = nil
    var sliceBottom: BorderImageSliceEdge? = nil
    var sliceLeft: BorderImageSliceEdge? = nil
    var sliceFill: Bool = false
    // Four width entries.
    var widthTop: BorderImageDimension? = nil
    var widthRight: BorderImageDimension? = nil
    var widthBottom: BorderImageDimension? = nil
    var widthLeft: BorderImageDimension? = nil
    // Four outset entries.
    var outsetTop: BorderImageDimension? = nil
    var outsetRight: BorderImageDimension? = nil
    var outsetBottom: BorderImageDimension? = nil
    var outsetLeft: BorderImageDimension? = nil
    // Two-axis repeat.
    var repeatHorizontal: BorderImageRepeat = .stretch
    var repeatVertical: BorderImageRepeat = .stretch

    // `true` when any paint would result — saves the applier from
    // chaining a real modifier when only `source: none` was set.
    var hasBorderImage: Bool {
        if case .none = source { return false }
        return true
    }
}
