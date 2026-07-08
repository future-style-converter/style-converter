//
//  CaretColorConfig.swift
//  StyleEngine/color — Phase 4.
//
//  CSS `caret-color` sets the text insertion caret tint inside editable
//  fields. SwiftUI does not expose this directly on TextField (as of
//  iOS 17) — the caret inherits the field's `.tint`. This config is
//  therefore a data-only record: we carry the parsed value for
//  diagnostics and future platforms, but the applier is a no-op.
//

import Foundation

struct CaretColorConfig: Equatable {
    // Resolved color value. May be `.unknown` for unrecognised IR,
    // `.dynamic(...)` for color-mix/light-dark, or `.srgb(...)` for a
    // concrete colour. Nil in the parent optional means "property absent".
    var color: ColorValue? = nil

    // True when the IR contained a `caret-color` property. Currently
    // unused by the applier but exposed for the self-test and for
    // future SwiftUI support (FB: no dedicated caret-color API).
    var hasAny: Bool { color != nil }
}
