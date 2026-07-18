//
//  ColorApplier.swift
//  StyleEngine/color — Phase 4.
//
//  ViewModifier that paints the `background-color` half of a ColorConfig.
//  The `foreground` half is consumed by the text renderer in
//  ComponentRenderer (via `ComponentStyle.text.color`) — painting it on
//  the container would tint every child view, which isn't what CSS
//  `color` means. So this applier is background-only.
//
//  Dynamic colour fallbacks: when ColorValue is `.dynamic` (e.g.
//  color-mix, light-dark) we currently fall through to `.unknown` and
//  skip the paint. A later phase can introduce an environment-resolving
//  pass that hands a resolved sRGB down via PreferenceKey. The
//  "degrade gracefully" rule says skip > crash.
//

// SwiftUI for Color + ViewModifier.
import SwiftUI

// Attach via the `.engineBackgroundColor(_:)` extension below so
// StyleBuilder.applyStyle reads as a single chained modifier per family.
struct ColorApplier: ViewModifier {
    // The config from ColorExtractor. Nil means "no color family entries"
    // — body hits the identity branch and the modifier is a zero-cost
    // wrapper.
    let config: ColorConfig?
    // Phase 5: corner radii come from the engine-side BorderRadiusConfig
    // (StyleEngine/borders/radius). Nil when the IR has no radius
    // property — background paints a plain rectangle in that case.
    let radius: BorderRadiusConfig?
    // CSS `background-clip: padding-box | content-box` shrink band
    // (CSS Backgrounds 3 §2.4) — the background colour paints only
    // inside the border band (padding-box) or inside border+padding
    // (content-box). Zero insets == default border-box paint.
    var clipInsets: EdgeInsets = EdgeInsets()

    func body(content: Content) -> some View {
        // Fast path: nothing to paint (absent / dynamic / unknown colour
        // — the fillView helper returns nil for all three, keeping the
        // "degrade gracefully: skip > crash" rule from the header).
        guard let fill = ColorApplier.fillView(config: config, radius: radius) else {
            return AnyView(content)
        }
        // `.padding(clipInsets)` shrinks the painted shape for
        // background-clip's non-default boxes (CSS Backgrounds 3 §2.4).
        return AnyView(content.background(fill.padding(clipInsets)))
    }

    /// Shape-aware fill builder — shared by this modifier's body and the
    /// background-blend compositor (IOS-BBM lane): CSS Compositing 1 §3.2
    /// makes `background-color` the BOTTOM layer of a blended background
    /// stack, so BackgroundImageApplier needs the exact fill view this
    /// applier paints (same colour resolution, same radius shape) to slot
    /// underneath the blended gradient layers. Extracting the builder —
    /// rather than duplicating it — keeps the two paint paths pixel-
    /// identical by construction. Returns nil when there is no paintable
    /// colour (`.dynamic` / `.unknown` resolve to nil via the Phase 1
    /// bridge — we never paint a guessed colour).
    static func fillView(config: ColorConfig?,
                         radius: BorderRadiusConfig?) -> AnyView? {
        // No background entry, or a value the bridge can't resolve → nil.
        guard let bg = config?.background,
              let swiftColor = bg.toSwiftUIColor() else { return nil }
        // Rounded-corner aware painting — uses the same BorderRadiusShape
        // the Phase 5 radius applier clips to, so the fill aligns pixel-
        // for-pixel with the stroke.
        if let r = radius, r.hasAny {
            return AnyView(BorderRadiusShape(radius: r).fill(swiftColor))
        }
        // Plain rectangle path — SwiftUI `Color` fills its proposal.
        return AnyView(swiftColor)
    }
}

// View extension — public call surface. `config` may be nil because
// StyleBuilder forwards ComponentStyle's optional config unconditionally
// so the render chain stays flat.
extension View {
    // Chain helper. Mirrors `.engineSpacingPadding` in the spacing module.
    // `radius` is the engine-side corner config so the background paints
    // inside rounded corners without duplicating the Shape. `clipInsets`
    // carries the resolved background-clip shrink band (default zero).
    func engineBackgroundColor(_ config: ColorConfig?,
                               radius: BorderRadiusConfig? = nil,
                               clipInsets: EdgeInsets = EdgeInsets()) -> some View {
        modifier(ColorApplier(config: config, radius: radius,
                              clipInsets: clipInsets))
    }
}
