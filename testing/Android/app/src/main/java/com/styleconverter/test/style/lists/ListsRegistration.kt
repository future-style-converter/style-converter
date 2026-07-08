package com.styleconverter.test.style.lists

// Phase 10 facade — ListStyleExtractor + ListStyleApplier already wire
// all three list-style-* longhands. Applier emits bullet / ordinal prefix
// markers only when a component is declared `display: list-item`.
//
// Parser-gap note:
//   * ListStyleType has ~30 named keywords + `symbols(...)` function +
//     quoted strings + any lowercase ident (falls through to CustomString).

import com.styleconverter.test.style.PropertyRegistry

/** Registers 3 list-style-* IR properties under the `lists` owner. */
object ListsRegistration {

    init {
        PropertyRegistry.migrated(
            "ListStyleType",
            "ListStylePosition",
            "ListStyleImage",
            owner = "lists"
        )
    }
}
