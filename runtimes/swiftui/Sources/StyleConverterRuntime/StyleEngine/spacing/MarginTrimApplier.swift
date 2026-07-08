//
//  MarginTrimApplier.swift
//  StyleEngine/spacing — Phase 2.
//
//  CSS `margin-trim` has no SwiftUI analogue today — it belongs to the
//  Paged Media module which targets print layouts. We keep the applier
//  scaffold so later phases can wire it into a custom container layout
//  if one ever materialises. For now it's a no-op ViewModifier that
//  records the mode on an `.accessibilityIdentifier` for debuggability.
//

// SwiftUI for ViewModifier.
import SwiftUI

struct MarginTrimApplier: ViewModifier {
    // Config threaded from StyleBuilder. Nil means the property was not
    // observed at all — we still attach but bail immediately.
    let config: MarginTrimConfig?

    func body(content: Content) -> some View {
        // Stamp the mode into an accessibility identifier purely for
        // screenshot-diagnostic purposes. The renderer output itself is
        // unchanged. Null config skips the stamp too.
        if let cfg = config, cfg.mode != .none {
            content.accessibilityIdentifier("margin-trim:\(cfg.mode.rawValue)")
        } else {
            content
        }
    }
}

// View helper for StyleBuilder call-sites.
extension View {
    func engineSpacingMarginTrim(_ config: MarginTrimConfig?) -> some View {
        modifier(MarginTrimApplier(config: config))
    }
}
