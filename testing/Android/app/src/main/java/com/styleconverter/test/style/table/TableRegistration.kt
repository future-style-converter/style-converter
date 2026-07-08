package com.styleconverter.test.style.table

// Phase 10 facade — TableExtractor + TableApplier already handle all five
// CSS 2.1 table-model properties. This facade claims the names on
// PropertyRegistry.
//
// Parser-gap note:
//   * CaptionSide has a Raw catch-all — unknown keywords degrade to raw.
//   * BorderSpacing only accepts 1 or 2 tokens (not 3+).
//
// TODO applier note: table rendering is only triggered when a component is
// declared `display: table` / `display: table-row` etc. — most fixtures
// don't exercise this path, so the applier is effectively identity in
// visual regression.

import com.styleconverter.test.style.PropertyRegistry

/** Registers 5 CSS 2.1 table-model IR property names under the `table` owner. */
object TableRegistration {

    init {
        PropertyRegistry.migrated(
            "BorderCollapse", "BorderSpacing",
            "CaptionSide",
            "EmptyCells",
            "TableLayout",
            owner = "table"
        )
    }
}
