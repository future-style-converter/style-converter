//
//  BorderStyleValue.swift
//  StyleEngine/borders/sides — Phase 5.
//
//  All 10 CSS `border-*-style` keywords. Parser emits these uppercased
//  (see src/main/kotlin/app/parsing/css/properties/longhands/borders/sides/
//  BorderTopStylePropertyParser.kt — accepts the 10 keywords below, rejects
//  everything else). We store them as a Swift enum so the applier can
//  exhaustively switch.
//

// No SwiftUI import needed — this is a pure data enum.
import Foundation

// One per CSS line-style keyword. Ordering matches CSS 2.1 §8.5.3 so reviews
// read top-to-bottom the same way the spec does.
enum BorderStyleValue: String, Equatable {
    // `none` — width resets to 0 per the spec. We surface it so shorthand
    // paths can tell "not set" from "explicitly none".
    case none
    // `hidden` — same visual as `none` but wins in table-collapse ties.
    // We render it identically to `.none`.
    case hidden
    // Straight solid line — cheapest path. Maps to SwiftUI `.border()`.
    case solid
    // Series of dots. Requires a `StrokeStyle(dash:)` overlay since
    // `.border()` is solid-only.
    case dotted
    // Series of short line segments. Same overlay approach as `dotted`
    // with a longer dash pattern.
    case dashed
    // Two parallel solid lines with a gap — classic "double border".
    // SwiftUI has no primitive for this; applier draws two concentric
    // rectangles inside the border box.
    case double
    // 3D carved-in appearance. Approximated by darken-on-top/left
    // + lighten-on-bottom/right. Degrades to solid on iOS since we
    // don't derive luminance-adjusted pairs yet.
    case groove
    // Inverse of `groove`.
    case ridge
    // Embedded-looking border. CSS spec uses darker shades on top/left.
    case inset
    // Inverse of `inset`.
    case outset
}

// Parse from the IR keyword shape. CSS parser emits uppercase strings
// ("SOLID", "DASHED") either bare or wrapped in `{ type/keyword/value: ... }`.
// `.none` resolves keyword-or-nil → keyword-string so we handle both.
func extractBorderStyle(_ value: IRValue?) -> BorderStyleValue? {
    // ValueExtractors.extractKeyword handles every wrapper shape.
    guard let raw = ValueExtractors.extractKeyword(value) else { return nil }
    // Case-insensitive match. rawValue is lowercase; normalise input.
    return BorderStyleValue(rawValue: raw.lowercased())
}
