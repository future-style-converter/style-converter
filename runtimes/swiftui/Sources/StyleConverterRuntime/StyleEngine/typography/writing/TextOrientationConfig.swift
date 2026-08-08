//
//  TextOrientationConfig.swift
//  StyleEngine/typography/writing — Phase 6.
//
//  CSS text-orientation (vertical text glyph rotation). SwiftUI has no vertical text; captured.
//

import Foundation

struct TextOrientationConfig: Equatable {
    var keyword: String? = nil

    /// Wave 35 (lane B5) — the keyword as the §5.1 enum the vertical-flow
    /// decision consumes. Absent / unrecognised ⇒ `.mixed`, which is the
    /// property's initial value, so a component that never declared
    /// `text-orientation` classifies exactly as the spec says it should.
    /// Twin of the Compose `WritingModeConfig.textOrientation` field.
    var value: TextOrientationValue { TextOrientationValue.from(keyword: keyword) }
}
