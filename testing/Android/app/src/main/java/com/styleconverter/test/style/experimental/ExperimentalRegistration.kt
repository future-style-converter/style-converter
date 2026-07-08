package com.styleconverter.test.style.experimental

// Phase 10 facade — proprietary / vendor-experimental properties. None
// have a Compose analogue. Parse-only.
//
// Parser-gap notes:
//   * StringSet stores (name, rest-of-string) — no structural parsing.
//   * Running / PresentationLevel are opaque idents.

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers 3 experimental IR properties under the `experimental` owner.
 * All parse-only on every target.
 */
object ExperimentalRegistration {

    init {
        PropertyRegistry.migrated(
            "PresentationLevel",
            "Running",
            "StringSet",
            owner = "experimental"
        )
    }
}
