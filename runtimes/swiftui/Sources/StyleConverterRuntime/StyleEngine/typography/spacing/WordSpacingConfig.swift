//
//  WordSpacingConfig.swift
//  StyleEngine/typography/spacing — Phase 6.
//
//  `word-spacing`: adds extra space between words. SwiftUI Text has no
//  direct API, so PlaceholderLabel renders it via an AttributedString
//  `.kern` on each space character (lane IOS-TEXT fix 2 — the value used
//  to be captured into the aggregate and never consumed).
//

import CoreGraphics

struct WordSpacingConfig: Equatable {
    /// Absolute extra space per word gap in px, when pre-resolved.
    var px: CGFloat? = nil
    /// Lane IOS-TEXT fix 3 — unresolved em/rem value (same bogus-px-0
    /// wire shape as letter-spacing, see RelativeFontLength.parse);
    /// resolved in TypographyExtractor against the element font size.
    var relative: RelativeFontLength? = nil
}
