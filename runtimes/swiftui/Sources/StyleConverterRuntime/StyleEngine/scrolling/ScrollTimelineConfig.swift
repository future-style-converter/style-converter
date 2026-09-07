//
//  ScrollTimelineConfig.swift
//  StyleEngine/scrolling — Phase 9.
//
//  `scroll-timeline` + its two longhands (`scroll-timeline-name`,
//  `scroll-timeline-axis`). These name a scroll container that an
//  animation-timeline can later reference. This file captures the
//  configuration and NOTHING consumes it: there is nothing to emit at the
//  container itself, and scroll-driven playback (iOS 17+
//  `scrollTransition`) is not modelled by the wave-8 property-space
//  animation driver either. Retro P2b (A6#10) deleted the identity
//  `ScrollTimelineApplier` modifier this note used to point at — it had no
//  call site: its `.engineScrollTimeline` chain helper was never attached.
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
