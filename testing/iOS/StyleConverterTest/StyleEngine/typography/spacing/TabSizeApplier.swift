//
//  TabSizeApplier.swift
//  StyleEngine/typography/spacing — Phase 6.
//

import Foundation

enum TabSizeApplier {
    static func contribute(_ cfg: TabSizeConfig?, into agg: inout TypographyAggregate) {
        guard let n = cfg?.count else { return }
        // Preserve for the audit. TODO(phase-6+): paragraph-style tab stops.
        agg.tabSize = n
    }
}
