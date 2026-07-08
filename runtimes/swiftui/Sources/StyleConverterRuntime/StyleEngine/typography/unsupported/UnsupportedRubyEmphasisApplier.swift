//
//  UnsupportedRubyEmphasisApplier.swift
//  StyleEngine/typography/unsupported — Phase 6.
//
//  Identity contribution — SwiftUI has no rendering path for the
//  UnsupportedRubyEmphasis family. The Config payload is retained for audit.
//  TODO(phase-6+): revisit when SwiftUI exposes relevant APIs.
//

import Foundation

enum UnsupportedRubyEmphasisApplier {
    static func contribute(_ cfg: UnsupportedRubyEmphasisConfig?, into agg: inout TypographyAggregate) {
        _ = cfg; _ = agg
    }
}
