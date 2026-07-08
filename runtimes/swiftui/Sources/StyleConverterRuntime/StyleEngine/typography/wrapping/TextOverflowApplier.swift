//
//  TextOverflowApplier.swift
//  StyleEngine/typography/wrapping — Phase 6.
//

import SwiftUI

enum TextOverflowApplier {
    static func contribute(_ cfg: TextOverflowConfig?, into agg: inout TypographyAggregate) {
        guard let m = cfg?.mode else { return }
        switch m {
        case .clip:
            // `.head` would drop leading chars; clip is closest to "no
            // ellipsis glyph" — SwiftUI will simply cut at the edge.
            agg.truncationMode = .middle   // closest reasonable fallback
        case .ellipsis, .fade, .customString:
            // All three render as a tail ellipsis in SwiftUI.
            agg.truncationMode = .tail
        }
        agg.touched = true
    }
}
