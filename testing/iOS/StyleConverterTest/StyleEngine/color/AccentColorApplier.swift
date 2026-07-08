//
//  AccentColorApplier.swift
//  StyleEngine/color — Phase 4.
//
//  ViewModifier mapping AccentColorConfig to `.tint(_:)`. On iOS 16+
//  `.tint` replaces `.accentColor`, which is deprecated but still
//  functional. We deployment-target iOS 16 (see project.yml), so using
//  `.tint` is safe — no availability check needed.
//
//  Dynamic ColorValue (color-mix / light-dark / var()) falls through to
//  the identity branch. A later phase can resolve these against the
//  environment; documented limitation in the Phase 4 report.
//

import SwiftUI

struct AccentColorApplier: ViewModifier {
    // Config from AccentColorExtractor. Nil means "property not present";
    // `.auto` means "defer to platform"; both render identity.
    let config: AccentColorConfig?

    func body(content: Content) -> some View {
        // Fast path — no IR entry.
        guard let cfg = config else { return AnyView(content) }

        // Only `.color(ColorValue)` actually tints. `.auto` and
        // `.inherit` both mean "leave the environment alone".
        guard case .color(let cv) = cfg else { return AnyView(content) }

        // Bridge through the Phase-1 SwiftUI converter — nil for dynamic
        // flavours. We skip painting rather than guess a fallback.
        guard let swiftColor = cv.toSwiftUIColor() else { return AnyView(content) }

        // `.tint` applies to children that honour it (Toggle, ProgressView,
        // Picker, etc.). Our fixtures don't include interactive controls,
        // so visible output is identical to identity there — the mapping
        // is still correct and will show up the moment a Toggle appears.
        return AnyView(content.tint(swiftColor))
    }
}

// View extension to chain alongside the other engine modifiers.
extension View {
    func engineAccentColor(_ config: AccentColorConfig?) -> some View {
        modifier(AccentColorApplier(config: config))
    }
}
