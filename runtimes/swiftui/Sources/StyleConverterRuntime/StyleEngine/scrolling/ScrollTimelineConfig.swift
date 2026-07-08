//
//  ScrollTimelineConfig.swift
//  StyleEngine/scrolling — Phase 9.
//
//  `scroll-timeline` + its two longhands (`scroll-timeline-name`,
//  `scroll-timeline-axis`). These name a scroll container that an
//  animation-timeline can later reference. Phase 9 captures the
//  configuration; the actual scroll-driven playback lives with the
//  animations applier (iOS 17+ `scrollTransition`).
//
//  README note 14 — ScrollTimelinePropertyParser defaults the name to
//  the literal string `"none"` when no non-axis token is seen, so the
//  config surfaces that verbatim and leaves sentinel interpretation to
//  the caller.
//

import Foundation

/// `scroll-timeline` shorthand-as-object. Mirrors the IR shape
/// `{ name: { name: "…" }, axis: "BLOCK"|"INLINE"|"X"|"Y" }`.
struct ScrollTimelineDeclaration: Equatable {
    /// The name CSS ident. Parser stores the literal string `"none"`
    /// here when no non-axis token was seen (README note 14), so a
    /// platform applier must compare the string explicitly if it cares.
    let name: String?
    /// Axis keyword — nil when absent.
    let axis: TimelineAxisKind?
}

/// Rolled-up scroll-timeline state produced by `ScrollTimelineExtractor`.
struct ScrollTimelineConfig: Equatable {
    /// Full `scroll-timeline` declaration.
    var timeline: ScrollTimelineDeclaration? = nil
    /// Longhand name — raw string, including literal "none".
    var name: String? = nil
    /// Longhand axis.
    var axis: TimelineAxisKind? = nil
    /// Touched flag — the applier short-circuits on `false`.
    var touched: Bool = false
}
