package com.styleconverter.test.style.global

// Phase 10 facade — the CSS `all` shorthand resets every other property
// to `initial | inherit | unset | revert | revert-layer`. On Compose
// there is no single mechanism to reset every Modifier at runtime, so
// we treat `all` as parse-only: the parser records it and we drop it
// rather than trying to synthesize per-property reset logic.

import com.styleconverter.test.style.PropertyRegistry

/** Registers the `all` shorthand under the `global` owner. */
object GlobalRegistration {

    init {
        PropertyRegistry.migrated(
            "All",
            owner = "global"
        )
    }
}
