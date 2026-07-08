//
//  UnsupportedFontMetaApplier.swift
//  StyleEngine/typography/unsupported — Phase 6.
//
//  Identity contribution — SwiftUI has no rendering path for the
//  UnsupportedFontMeta family. The Config payload is retained for audit.
//  TODO(phase-6+): revisit when SwiftUI exposes relevant APIs.
//

import Foundation

enum UnsupportedFontMetaApplier {
    static func contribute(_ cfg: UnsupportedFontMetaConfig?, into agg: inout TypographyAggregate) {
        _ = cfg; _ = agg
    }
}
