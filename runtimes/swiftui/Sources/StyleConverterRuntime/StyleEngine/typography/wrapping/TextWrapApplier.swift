//
//  TextWrapApplier.swift
//  StyleEngine/typography/wrapping — Phase 6.
//
//  Fidelity wave 2: `text-wrap: nowrap` (css-text-4 §5.1 — the text-wrap
//  shorthand's text-wrap-mode longhand) suppresses soft wrapping; the
//  PlaceholderLabel maps the flag to `.fixedSize(horizontal: true)`.
//  The style keywords (balance / pretty / stable) merely tune where
//  lines break and have no SwiftUI analogue — identity.
//

import Foundation

enum TextWrapApplier {
    static func contribute(_ cfg: TextWrapConfig?, into agg: inout TypographyAggregate) {
        // Only the mode keyword is renderable; extractor lowercased it.
        if cfg?.keyword == "nowrap" {
            // One-line layout, overflow to the right like the web ref.
            agg.noWrap = true
            agg.touched = true
        }
    }
}
