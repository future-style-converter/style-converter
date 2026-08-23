//
//  LineClampConfig.swift
//  StyleEngine/typography/line — Phase 6.
//
//  `line-clamp` truncates multi-line text after N lines. Accepts a
//  positive integer or `none`. Maps to SwiftUI's `.lineLimit(_:)` with
//  `.truncationMode(.tail)` to mimic CSS ellipsis behaviour.
//
//  Wave 46 (lane Y1) adds the two-value grammar's marker component
//  (css-overflow-4 §5.1 `<integer> || <'block-ellipsis'>`): whether the
//  author FORBADE a marker on the last shown line.
//

import Foundation

struct LineClampConfig: Equatable {
    /// Line cap. Nil → `none` (no clamp). Values < 1 are normalised to nil.
    var lines: Int? = nil
    /// Wave 46 (lane Y1) — true when the wire's `ellipsis` component is
    /// `no-ellipsis` or the EMPTY string (`line-clamp: 4 ""`): the clamp
    /// still discards lines past the Nth, but no marker may be drawn and
    /// no content may be displaced to make room for one (css-overflow-4
    /// §block-ellipsis; WPT block-ellipsis-023/-024 assert exactly this).
    /// False for an absent marker, the explicit `ellipsis`/`auto`
    /// keywords and any non-empty string — every one of those draws a
    /// marker, which SwiftUI's `.lineLimit` tail truncation already does.
    var markerSuppressed: Bool = false
}
