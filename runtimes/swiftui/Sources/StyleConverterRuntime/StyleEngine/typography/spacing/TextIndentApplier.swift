//
//  TextIndentApplier.swift
//  StyleEngine/typography/spacing — Phase 6.
//

import Foundation

enum TextIndentApplier {
    static func contribute(_ cfg: TextIndentConfig?, into agg: inout TypographyAggregate) {
        guard let px = cfg?.px else { return }
        // PlaceholderLabel renders this as leading padding on the glyph
        // wrapper (all lines - SwiftUI has no first-line-only inset;
        // negative values outdent left per css-text-3 s8.2.1).
        agg.textIndentPx = px
        // Fidelity wave 2: without the touched flag a component whose
        // ONLY typography declaration was text-indent lost its whole
        // aggregate (extract() returns nil on untouched) - the
        // Typography_C17 -100px outdent silently no-op'd.
        agg.touched = true
    }
}
