package com.styleconverter.test.style.performance

// Phase 10 facade — claims the performance-category IR properties.
// PerformanceExtractor already handles Contain / WillChange / Zoom /
// ImageRendering / BoxSizing / BoxDecorationBreak. IsolationExtractor
// handles Isolation. This facade also claims the contain-intrinsic-*
// family the parser emits.
//
// Parser-gap notes:
//   * Contain rejects unknown keyword in a multi-value list (all-or-
//     nothing).
//   * WillChange treats any non-`scroll-position`/`contents` token as a
//     PropertyName — no validation that the name corresponds to an
//     animatable CSS property.
//   * ContainIntrinsic{Size,Width,Height,BlockSize,InlineSize} accept
//     `none | auto | auto <length> | <length>`.
//
// TODO applier notes:
//   - contain / content-visibility: Compose auto-composes. `content-
//     visibility: auto` could map to a visibility-ranged composition
//     optimization, but no direct analogue exists. No-op.
//   - will-change: no-op on Compose (the compiler decides rendering
//     hints).

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers 10 performance-category IR property names under the
 * `performance` owner.
 */
object PerformanceRegistration {

    init {
        PropertyRegistry.migrated(
            // ---- existing Phase 9/earlier extractors ----
            "Contain", "WillChange",
            "Isolation",
            "Zoom",
            "ImageRendering",
            "BoxSizing", "BoxDecorationBreak",
            // ---- contain-intrinsic-* (5) ----
            "ContainIntrinsicSize",
            "ContainIntrinsicWidth", "ContainIntrinsicHeight",
            "ContainIntrinsicBlockSize", "ContainIntrinsicInlineSize",
            // ---- content-visibility (parse-only) ----
            // Also claimed by interactions; first-write-wins.
            "ContentVisibility",
            owner = "performance"
        )
    }
}
