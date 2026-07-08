//
//  ColorConfig.swift
//  StyleEngine/color — Phase 4.
//
//  Config struct holding the two CSS "paint" colours that are still
//  properties-on-the-component (not containers): `background-color` and
//  `color` (the text foreground). Both travel as Phase-1 ColorValue enums
//  so dynamic flavours (color-mix / light-dark / var()) survive round-trip
//  and can be paint-resolved by the applier with platform-appropriate
//  fallbacks.
//

// Foundation is the only dependency — the SwiftUI bridge lives on
// ColorValue itself via `toSwiftUIColor()`.
import Foundation

// Phase 4 extractor output. A nil on either side means "property was not
// present in the IR" — the applier then leaves the environment default
// untouched instead of painting transparent.
struct ColorConfig: Equatable {
    // `background-color` — paints the component box behind border+content.
    var background: ColorValue? = nil
    // `color` — the CSS text foreground. Consumed by the text renderer
    // path; also used as the resolution target for `currentColor`.
    var foreground: ColorValue? = nil

    // True when the extractor saw at least one of the two properties.
    // Lets appliers short-circuit to the identity transform.
    var hasAny: Bool {
        background != nil || foreground != nil
    }
}
