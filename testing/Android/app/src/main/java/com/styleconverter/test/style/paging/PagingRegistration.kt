package com.styleconverter.test.style.paging

// Phase 10 facade — CSS Fragmentation (Paged Media level 3) page-break +
// break-* properties are parse-only on every mobile target. The parser
// emits them from the `paging` IR folder (break-before / break-after /
// break-inside + legacy page-break-*), plus `margin-break`. PrintExtractor
// already processes these via PrintConfig and PrintRegistration has
// claimed them under the `print` owner; this facade exists so the
// canonical style/paging/ folder mirror exists (CLAUDE.md requires one-
// to-one folder parity with irmodels/properties/paging/).
//
// Registrations here are idempotent — the first-write-wins rule means
// these IDs keep their `print` owner after PrintRegistration loads first;
// if PagingRegistration loads first they become owned by `paging`. That
// ambiguity is acceptable for coverage auditing purposes.
//
// Parser-gap note:
//   * PageBreak{Before,After,Inside} have a Raw catch-all + Keyword
//     global-keyword path.
//   * MarginBreak is a strict enum.

import com.styleconverter.test.style.PropertyRegistry

/** Registers 7 paging-category IR properties under the `paging` owner. */
object PagingRegistration {

    init {
        PropertyRegistry.migrated(
            "BreakBefore", "BreakAfter", "BreakInside",
            "PageBreakBefore", "PageBreakAfter", "PageBreakInside",
            "MarginBreak",
            owner = "paging"
        )
    }
}
