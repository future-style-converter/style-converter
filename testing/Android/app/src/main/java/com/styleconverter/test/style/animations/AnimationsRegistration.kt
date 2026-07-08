package com.styleconverter.test.style.animations

// Phase 9 facade — the runtime config-extraction logic for the animation /
// transition / view-timeline / view-transition / timeline-scope families
// already lives in pre-canonical modules under this same folder and the
// sibling `style/scrolling/` folder (AnimationExtractor, ViewTimelineExtractor,
// ScrollTimelineExtractor). Those files pre-date the canonical category-per-
// folder triplet pattern and bundle several properties apiece. Rather than
// splitting them into 26 single-property triplets (which would also fragment
// the `AnimationConfig` shared types), Phase 9 takes the same approach as
// Phase 8's VisibilityRegistration: a thin **registration facade** that
// claims every Phase 9 property on the PropertyRegistry under the
// `animations` owner so the legacy PropertyApplier switch can never silently
// re-dispatch these IR types, and so the coverage audit
// (PropertyRegistry.allRegistered()) correctly reports the phase as done.
//
// The actual IR → Config pipeline is still run by the pre-canonical
// extractors at style-apply time (see StyleApplier.applyConfig which already
// calls AnimationExtractor.extractAnimationConfig + .extractTransitionConfig
// and ScrollTimelineExtractor.extractScrollTimelineConfig). Static rendering
// is unchanged; runtime animation execution (Compose Animatable / infinite
// transitions / scroll-linked timelines) is tracked as a separate multi-
// session effort and noted below as TODO.
//
// Property-name source of truth: every name matches a file under
// src/main/kotlin/app/irmodels/properties/animations/ so the IR/parser
// guarantees the camelcase IRProperty.type strings in use here.

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers the 26 animation-family IR property names under the
 * `animations` owner. The two remaining Phase 9 properties that live in
 * the `scrolling/` IR folder (ScrollTimeline, ScrollTimelineName,
 * ScrollTimelineAxis) are claimed by [com.styleconverter.test.style.scrolling.ScrollTimelineRegistration]
 * under the `scrolling` owner to keep owner strings aligned with the
 * IR/parser folder layout.
 *
 * ## Property-family breakdown
 * - **animation-\*** (13): name, duration, delay, iteration-count, direction,
 *   fill-mode, play-state, timing-function, composition, timeline, range,
 *   range-start, range-end.
 * - **transition-\*** (5): property, duration, delay, timing-function,
 *   behavior.
 * - **view-timeline-\*** (4) + **view-timeline** shorthand: name, axis,
 *   inset + the grouped `ViewTimeline` IR node.
 * - **view-transition-\*** (3): name, class, group.
 * - **timeline-scope** (1).
 *
 * Totals 26 IR property types. See `examples/properties/animations/README.md`
 * for the fixture coverage map of every value variant.
 *
 * ## TODOs not covered by Phase 9
 * 1. **Runtime animation execution.** Compose's `Animatable` /
 *    `rememberInfiniteTransition` / `animate*AsState` APIs are not wired
 *    into ComponentRenderer yet — config is extracted but CSS @keyframes
 *    are not interpreted into state-driven Compose animations.
 * 2. **Scroll-linked timelines.** `animation-timeline: scroll(...)` requires
 *    a LazyListState / ScrollState progress derivation pipeline. The
 *    plumbing exists in [com.styleconverter.test.style.scrolling.ScrollTimelineApplier]
 *    but it is not connected to the animation driver from #1.
 * 3. **View timelines.** `animation-timeline: view(...)` needs Compose
 *    `onGloballyPositioned` + viewport intersection math to derive
 *    visibility progress. Scaffolded in ViewTimelineApplier, not wired.
 *
 * Touching [AnimationsRegistration] from any test primes this init block via
 * Kotlin's lazy `object` initialization rules; see the `@Before` hook in
 * `AnimationsRegistryTest`.
 */
object AnimationsRegistration {

    init {
        // ---- animation-* longhands (13) ----
        // Source: src/main/kotlin/app/irmodels/properties/animations/Animation*Property.kt
        // Parser: src/main/kotlin/app/parsing/css/properties/longhands/animations/
        //   Animation{Name,Duration,Delay,IterationCount,Direction,FillMode,
        //     PlayState,TimingFunction,Composition,Timeline,Range,RangeStart,
        //     RangeEnd}PropertyParser.kt
        // The Config extraction happens in AnimationExtractor (names/durations/
        // delays/iteration-counts/directions/fill-modes/play-states/timing-
        // functions) and ScrollTimelineExtractor (timeline/range/range-start/
        // range-end). Composition is currently a parse-only record on the
        // ScrollTimelineConfig side (AnimationCompositionValue enum). Each
        // property below is claimed here exactly once; the registry is
        // idempotent so this stays safe even if a sibling extractor adds its
        // own init block later.
        PropertyRegistry.migrated(
            "AnimationName",
            "AnimationDuration",
            "AnimationDelay",
            "AnimationIterationCount",
            "AnimationDirection",
            "AnimationFillMode",
            "AnimationPlayState",
            "AnimationTimingFunction",
            "AnimationComposition",
            "AnimationTimeline",
            "AnimationRange",
            "AnimationRangeStart",
            "AnimationRangeEnd",
            // ---- transition-* longhands (5) ----
            // Source: src/main/kotlin/app/irmodels/properties/animations/Transition*Property.kt
            // TransitionTimingFunction re-uses the AnimationTimingFunction
            // parser family except for `linear(stops)` (see parser-gaps note
            // in examples/properties/animations/README.md). TransitionBehavior
            // is a two-value enum (normal | allow-discrete) parsed by
            // TransitionBehaviorPropertyParser.kt.
            "TransitionProperty",
            "TransitionDuration",
            "TransitionDelay",
            "TransitionTimingFunction",
            "TransitionBehavior",
            // ---- view-timeline-* longhands + shorthand (4 + 1) ----
            // Source: src/main/kotlin/app/irmodels/properties/animations/
            //   ViewTimeline{,Name,Axis,Inset}Property.kt
            // Extraction: style.scrolling.ViewTimelineExtractor. The
            // `ViewTimeline` node is the shorthand IR output (name + axis
            // grouped) emitted by ViewTimelinePropertyParser.
            "ViewTimeline",
            "ViewTimelineName",
            "ViewTimelineAxis",
            "ViewTimelineInset",
            // ---- view-transition-* longhands (3) ----
            // Source: src/main/kotlin/app/irmodels/properties/animations/
            //   ViewTransition{Name,Class,Group}Property.kt
            // These are parse-only on Compose today — there is no analogue
            // for the CSS View Transitions API on Android. Claiming them
            // here prevents the legacy dispatch from re-handling the IDs
            // and shows them as covered on the Phase 9 audit.
            "ViewTransitionName",
            "ViewTransitionClass",
            "ViewTransitionGroup",
            // ---- timeline-scope (1) ----
            // Source: src/main/kotlin/app/irmodels/properties/animations/TimelineScopeProperty.kt
            // The IR stores None / All / Names(list); parse-only on Compose.
            "TimelineScope",
            owner = "animations"
        )
    }
}
