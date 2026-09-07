//
//  BorderImageConfig.swift
//  StyleEngine/borders/image — Phase 5.
//
//  CSS `border-image-*` family (css-backgrounds-3 §5). iOS has no native
//  `border-image` primitive, so the family is drawn by hand — and it IS
//  drawn: retro P2e replaced the Phase-5 sentence that stood here ("the
//  Applier renders what it can (solid-colour placeholder for `url(...)`
//  sources …) … a future phase can swap in a real NineSlice drawer").
//  That future phase landed: BorderImageApplier decodes a `url()` raster
//  through BackgroundURLImageResolver and paints all nine regions in a
//  Canvas (`BorderImageNineSliceView`) — §5.2 slices incl. the `fill`
//  keyword, §5.3 widths, §5.4 outsets (BorderImageMath) and the §5.5
//  stretch/repeat/round/space rules. Still identity, each with a
//  PropertyTracker breadcrumb rather than a silent placeholder: a
//  GRADIENT source (Compose has it, iOS does not) and a url() that fails
//  to decode.
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
    // The element's COMPUTED border widths (css-backgrounds-3 §4.3: a
    // side whose border-style is none/hidden/absent computes to 0) —
    // the §5.3 basis for `<number>` border-image-width values and the
    // initial `1`, and the §5.4 basis for `<number>` outsets. Populated
    // by the extractor from the same BorderSideExtractor lane the stroke
    // painter reads, mirroring Compose's computedBorder* config fields.
    var computedBorderTop: CGFloat = 0
    var computedBorderRight: CGFloat = 0
    var computedBorderBottom: CGFloat = 0
    var computedBorderLeft: CGFloat = 0

    // `true` when any paint would result — saves the applier from
    // chaining a real modifier when only `source: none` was set.
    var hasBorderImage: Bool {
        if case .none = source { return false }
        return true
    }
}
