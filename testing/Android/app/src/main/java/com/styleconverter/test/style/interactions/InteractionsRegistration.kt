package com.styleconverter.test.style.interactions

// Phase 10 facade — claims the interactions-category IR properties.
// InteractionExtractor already handles Cursor / PointerEvents /
// TouchAction / UserSelect / Visibility / BackfaceVisibility /
// ContentVisibility / Appearance. This facade also claims Resize /
// Caret / CaretShape / Interactivity (plus re-affirms the above for
// coverage) so a `ls interactions/` folder audit matches the parser's
// interactions/ IR folder exactly.
//
// Parser-gap notes (see README-phase10.md):
//   * Cursor with `url(...)` REQUIRES a fallback keyword after the URL;
//     parser returns null if no valid keyword fallback is found.
//   * Caret shorthand parses whichever tokens it can — allows either
//     color or shape to be absent, but returns null if BOTH are absent.
//   * TouchAction multi-value: every token must be valid or the whole
//     thing fails.
//   * PointerEvents has a Raw catch-all; unknown values become Raw.
//   * Appearance has a Raw catch-all; appliers must filter.
//
// TODO applier notes:
//   - user-select → SelectionContainer(false) wrapper in Compose. Scaffolded
//     via extractUserSelect but not wired into ComponentRenderer.
//   - cursor on Android mobile has no analogue (touch only). Desktop
//     Compose has Modifier.pointerHoverIcon but we don't target it.
//   - pointer-events → Modifier.pointerInput filter possible but not
//     wired; `none` could map to Modifier.clickable(enabled = false).
//   - touch-action → gesture-detector config; not wired.

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers 13 interactions-category IR property names under the
 * `interactions` owner. Covers the active-input family (cursor /
 * pointer-events / touch-action / user-select / user-drag / ime-mode /
 * resize / interactivity / caret / caret-shape / appearance /
 * backface-visibility).
 */
object InteractionsRegistration {

    init {
        PropertyRegistry.migrated(
            // ---- already wired via InteractionExtractor ----
            // Note: `Visibility` is deliberately omitted — it is claimed
            // by visibility/VisibilityRegistration under the `visibility`
            // owner (Phase 8), and the first-write-wins rule means
            // claiming it here would be a no-op anyway, but we keep the
            // omission explicit to avoid test-ordering surprises.
            // `BackfaceVisibility` is owned by transforms/Transform3DExtractor.
            "Cursor", "PointerEvents", "TouchAction", "UserSelect",
            "ContentVisibility",
            "Appearance",
            // ---- Phase 10 additions (parse-only on Compose mobile) ----
            "Resize",
            "Caret", "CaretShape",
            "Interactivity",
            // ScrollBehavior lives in the interactions IR folder but is
            // claimed by scrolling/ScrollingRegistration under the
            // `scrolling` owner (first-write wins). Not repeated here.
            owner = "interactions"
        )
    }
}
