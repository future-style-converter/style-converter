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

    func body(content: Content) -> some View {
        // Fast path: nothing to paint at all.
        guard let cfg = config, let bg = cfg.background else {
            return AnyView(content)
        }

        // Resolve the ColorValue through the Phase 1 bridge. `.dynamic`
        // and `.unknown` map to nil here — we leave the environment alone
        // rather than paint a wrong colour.
        guard let swiftColor = bg.toSwiftUIColor() else {
            return AnyView(content)
        }

        // Rounded-corner aware painting — uses the same BorderRadiusShape
        // the Phase 5 radius applier clips to, so the fill aligns pixel-
        // for-pixel with the stroke.
        if let r = radius, r.hasAny {
            return AnyView(
                content.background(BorderRadiusShape(radius: r).fill(swiftColor))
            )
        }
        // Plain rectangle path.
        return AnyView(content.background(swiftColor))
    }
}

// View extension — public call surface. `config` may be nil because
// StyleBuilder forwards ComponentStyle's optional config unconditionally
// so the render chain stays flat.
extension View {
    // Chain helper. Mirrors `.engineSpacingPadding` in the spacing module.
    // `radius` is the engine-side corner config so the background paints
    // inside rounded corners without duplicating the Shape.
    func engineBackgroundColor(_ config: ColorConfig?,
                               radius: BorderRadiusConfig? = nil) -> some View {
        modifier(ColorApplier(config: config, radius: radius))
    }
}
