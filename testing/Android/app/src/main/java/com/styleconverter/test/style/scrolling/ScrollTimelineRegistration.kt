package com.styleconverter.test.style.scrolling

// Phase 9 facade — claims the three scroll-timeline longhand IR property
// names under the `scrolling` owner on the PropertyRegistry. See
// [com.styleconverter.test.style.animations.AnimationsRegistration] for the
// rationale behind using a thin registration facade rather than a new
// triplet (the extractor logic already exists in ScrollTimelineExtractor,
// which bundles `scroll-timeline-*` + the cross-cutting
// `animation-timeline` / `animation-range*` properties that route through
// the same scroll-progress pipeline).
//
// The three properties here live in
// src/main/kotlin/app/irmodels/properties/scrolling/ (alongside scroll-*
// snapping / padding / margin properties), not in the `animations/` IR
// folder, so the owner string matches the IR folder name for audit
// consistency.

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers the three `scroll-timeline-*` CSS properties under the
 * `scrolling` owner.
 *
 * ## Properties
 * - `scroll-timeline` — shorthand (name + optional axis) parsed by
 *   ScrollTimelinePropertyParser. See the parser-gap note in
 *   examples/properties/animations/README.md: the parser stores the literal
 *   string `"none"` as the name when no token is seen (no sentinel).
 * - `scroll-timeline-name` — `<dashed-ident>` | `none`. Parser also stores
 *   `"none"` as a literal string.
 * - `scroll-timeline-axis` — `block` | `inline` | `x` | `y`.
 *
 * Extraction lives in [ScrollTimelineExtractor] (shared with view-timeline
 * and animation-timeline). Static rendering is a no-op — scroll-linked
 * animation progress plumbing exists in [ScrollTimelineApplier] but is not
 * wired into ComponentRenderer yet (see the Phase 9 TODO list in
 * AnimationsRegistration).
 */
object ScrollTimelineRegistration {

    init {
        // Only these three are claimed here. The cross-cutting
        // `AnimationTimeline` / `AnimationRange*` properties are claimed by
        // AnimationsRegistration under owner="animations" because the IR
        // parser emits them from the animations/ IR folder even though the
        // runtime logic overlaps.
        PropertyRegistry.migrated(
            "ScrollTimeline",
            "ScrollTimelineName",
            "ScrollTimelineAxis",
            owner = "scrolling"
        )
    }
}
