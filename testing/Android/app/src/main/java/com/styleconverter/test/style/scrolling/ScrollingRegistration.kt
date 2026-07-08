package com.styleconverter.test.style.scrolling

// Phase 10 facade — claims every remaining scrolling-category IR property on
// the PropertyRegistry. Runtime extraction already lives in:
//   - OverflowExtractor    (overflow-* family, overflow-anchor, overflow-clip-margin)
//   - ScrollExtractor      (scroll-behavior / snap / margin / padding / overscroll / scrollbar)
// Phase 9 claimed scroll-timeline-{,name,axis} via ScrollTimelineRegistration.
// This facade covers the long-tail scroll-start / scroll-target / marker-group
// / scrollbar-color / reading-order-adjacent properties that the parser emits
// but the extractors above do not yet wire up for rendering — they're
// registered here so PropertyRegistry.allRegistered() reports honest
// coverage and the legacy StyleApplier switch skips them.
//
// Parser-gap notes:
//   * ScrollTargetGroup never returns null (wraps non-"none" in Named(...)).
//     Appliers must validate the name themselves if they ever render it.
//   * ScrollMarkerGroup only accepts `none | before | after` — spec-listed
//     `<name>` tokens are rejected by the parser.
//   * ScrollStart* accept keywords OR length/percentage; percentages route
//     through IRPercentage (runtime-dependent, so null is valid).
//   * OverflowClipMargin single-token form is lowercase-only; mixing case
//     falls through to length parser and returns null.
//
// See examples/properties/scrolling/longtail.json (Phase 10 fixture, 60
// components) and examples/properties/README-phase10.md for the full
// variant matrix.

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers 42 Phase 10 scrolling-family IR property names under the
 * `scrolling` owner on the PropertyRegistry.
 *
 * ## Property-family breakdown
 * - **overflow-\*** (7) — already wired via OverflowExtractor.
 *   Overflow, OverflowX, OverflowY, OverflowBlock, OverflowInline plus
 *   OverflowAnchor and OverflowClipMargin. The first five are claimed by
 *   [com.styleconverter.test.style.visibility.VisibilityRegistration];
 *   we re-claim here is a no-op thanks to first-write-wins semantics.
 * - **scroll-behavior / snap** (4) — wired via ScrollExtractor.
 * - **scroll-margin-\*** (8) — wired via ScrollExtractor.
 * - **scroll-padding-\*** (8) — wired via ScrollExtractor.
 * - **overscroll-behavior-\*** (5) — wired via ScrollExtractor.
 * - **scrollbar-\*** (3) — partially wired (ScrollbarWidth +
 *   ScrollbarColor + ScrollbarGutter). Compose has no first-class
 *   scrollbar API on Android mobile, so applier is TODO.
 * - **scroll-start / scroll-start-target** (10) — parse-only. Compose has
 *   no direct analogue for "initial scroll offset on layout" — the
 *   plumbing would need LazyListState.scrollToItem in a SideEffect.
 * - **scroll-marker-group / scroll-target-group / scroll-initial-target**
 *   (3) — parse-only on Compose.
 *
 * ## TODO applier work (not covered by Phase 10)
 * 1. `scroll-snap-*` → Modifier.scrollable + snapFlingBehavior on
 *    LazyListState. Scaffolded in ScrollApplier but not wired into
 *    ComponentRenderer.
 * 2. `overscroll-behavior` → Modifier.overscroll on Compose 1.7+. Needs
 *    platform-version gating.
 * 3. `scrollbar-color` → no Compose mobile analogue. Desktop Compose has
 *    VerticalScrollbar, but Android mobile does not expose styling.
 * 4. `scroll-start*` → SideEffect + LazyListState.scrollToItem on first
 *    composition. Not yet wired.
 *
 * Touching [ScrollingRegistration] from any test primes this init block via
 * Kotlin's lazy object initialization rules.
 */
object ScrollingRegistration {

    init {
        PropertyRegistry.migrated(
            // ---- overflow-anchor + overflow-clip-margin (2) ----
            // The base five overflow longhands are owned by Phase 8
            // visibility/VisibilityRegistration; we deliberately do NOT
            // re-claim them here (the `visibility` owner string must win).
            "OverflowAnchor",
            "OverflowClipMargin",
            // ---- scroll-behavior / snap (4) ----
            "ScrollBehavior",
            "ScrollSnapType", "ScrollSnapAlign", "ScrollSnapStop",
            // ---- scroll-margin longhands (8) ----
            // Parsed via LengthParser; IRLength normalized to px like any
            // other margin longhand.
            "ScrollMarginTop", "ScrollMarginRight",
            "ScrollMarginBottom", "ScrollMarginLeft",
            "ScrollMarginBlockStart", "ScrollMarginBlockEnd",
            "ScrollMarginInlineStart", "ScrollMarginInlineEnd",
            // ---- scroll-padding longhands (8) ----
            "ScrollPaddingTop", "ScrollPaddingRight",
            "ScrollPaddingBottom", "ScrollPaddingLeft",
            "ScrollPaddingBlockStart", "ScrollPaddingBlockEnd",
            "ScrollPaddingInlineStart", "ScrollPaddingInlineEnd",
            // ---- overscroll-behavior family (5) ----
            "OverscrollBehavior",
            "OverscrollBehaviorX", "OverscrollBehaviorY",
            "OverscrollBehaviorBlock", "OverscrollBehaviorInline",
            // ---- scrollbar styling (3) ----
            "ScrollbarWidth", "ScrollbarColor", "ScrollbarGutter",
            // ---- scroll-start (5) ----
            // Parser emits ScrollStart + per-axis (X/Y/Block/Inline)
            // longhands. All accept `auto|start|end|center|top|bottom|
            //   left|right|<length-percentage>`.
            "ScrollStart",
            "ScrollStartX", "ScrollStartY",
            "ScrollStartBlock", "ScrollStartInline",
            // ---- scroll-start-target (5) ----
            "ScrollStartTarget",
            "ScrollStartTargetX", "ScrollStartTargetY",
            "ScrollStartTargetBlock", "ScrollStartTargetInline",
            // ---- marker + target groups (2) ----
            "ScrollMarkerGroup",
            "ScrollTargetGroup",
            owner = "scrolling"
        )
    }
}
