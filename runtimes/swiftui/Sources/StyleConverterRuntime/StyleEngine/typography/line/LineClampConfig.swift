//
//  LineClampConfig.swift
//  StyleEngine/typography/line — Phase 6.
//
//  `line-clamp` truncates multi-line text after N lines. Accepts a
//  positive integer or `none`. Maps to SwiftUI's `.lineLimit(_:)` with
//  `.truncationMode(.tail)` to mimic CSS ellipsis behaviour.
//

import Foundation

struct LineClampConfig: Equatable {
    /// Line cap. Nil → `none` (no clamp). Values < 1 are normalised to nil.
    var lines: Int? = nil
}
