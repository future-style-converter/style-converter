//
//  AccentColorConfig.swift
//  StyleEngine/color — Phase 4.
//
//  CSS `accent-color` tints native form controls (checkboxes, radios,
//  progress bars). SwiftUI exposes the equivalent via `.tint(_:)` (or
//  the deprecated `.accentColor(_:)` on iOS < 16). The IR flavours are:
//    { "type": "auto" }  → use the platform default, applier is a no-op
//    { "type": "color", "srgb": {r,g,b,a?}, "original": "..." }
//         → a real colour; store ColorValue.srgb.
//

// Foundation for plain types.
import Foundation

// Tri-state config. `.inherit` means "property not in IR", `.auto` means
// "IR said auto, use platform default", `.color(ColorValue)` carries a
// concrete colour.
enum AccentColorConfig: Equatable {
    // Not present in the property list — applier is identity.
    case inherit
    // Present but `auto` — SwiftUI's default tint is also the platform
    // accent colour, so the applier is still identity but we keep the
    // state so the self-test can distinguish the two.
    case auto
    // Concrete colour. Dynamic variants (color-mix, light-dark) are
    // currently unsupported for accent — see the applier comment.
    case color(ColorValue)

    // True when the applier should touch the view hierarchy. Only the
    // `.color` case changes output; `.auto` defers to the environment.
    var hasEffect: Bool {
        if case .color = self { return true }
        return false
    }
}
