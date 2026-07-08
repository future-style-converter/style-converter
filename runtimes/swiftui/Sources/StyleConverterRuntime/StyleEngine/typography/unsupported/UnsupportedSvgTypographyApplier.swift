//
//  UnsupportedSvgTypographyApplier.swift
//  StyleEngine/typography/unsupported — Phase 6.
//
//  Identity contribution — SwiftUI has no rendering path for the
//  UnsupportedSvgTypography family. The Config payload is retained for audit.
//  TODO(phase-6+): revisit when SwiftUI exposes relevant APIs.
//

import Foundation

enum UnsupportedSvgTypographyApplier {
    static func contribute(_ cfg: UnsupportedSvgTypographyConfig?, into agg: inout TypographyAggregate) {
        _ = cfg; _ = agg
    }
}
