package com.styleconverter.test.style.print

// Phase 10 facade — claims every print / paged-media IR property.
// PrintExtractor already handles page-break-{before,after,inside},
// break-{before,after,inside}, orphans, widows via PrintConfig. The
// remaining print properties (page, size, bleed, marks, bookmark-*,
// footnote-*, leader, margin-break) are parse-only on mobile — no
// printing pipeline, no paged-media layout engine.
//
// Parser-gap notes:
//   * Page always succeeds — `auto` or anything else becomes Named(value).
//   * BookmarkLabel always succeeds — `attr(x)` → Attr, else Content.
//   * BookmarkTarget strict — only `self | url(...) | attr(...)`.
//   * Size: named page sizes (a3-a5, b4-b5, jis-b4/5, letter, legal,
//     ledger) + optional orientation, `portrait`/`landscape` alone, or 1-2
//     lengths.
//   * Leader: `dotted | solid | space` or any string (quoted or not).
//   * MarginBreak / FootnoteDisplay / FootnotePolicy are strict enums.
//   * PageBreak{Before,After,Inside} have a Raw catch-all + Keyword
//     (global-keyword) path.

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers 14 print/paged-media IR property names under the `print`
 * owner. Applier is no-op on every mobile platform target.
 */
object PrintRegistration {

    init {
        PropertyRegistry.migrated(
            // ---- break-* (3) — wired via PrintExtractor -> PrintConfig ----
            "BreakBefore", "BreakAfter", "BreakInside",
            // ---- page-break-* (3) — CSS 2.1 legacy; wired ----
            "PageBreakBefore", "PageBreakAfter", "PageBreakInside",
            // Orphans + Widows are owned by typography/ per the Phase 6
            // tripwire; not re-claimed here.
            // ---- paged-media layout (parse-only) ----
            "Page",
            "Size",
            "Bleed",
            "Marks",
            "MarginBreak",
            // ---- bookmarks (parse-only) ----
            "BookmarkLabel", "BookmarkLevel",
            "BookmarkState", "BookmarkTarget",
            // ---- footnotes (parse-only) ----
            "FootnoteDisplay", "FootnotePolicy",
            // ---- leader (parse-only) ----
            "Leader",
            owner = "print"
        )
    }
}
