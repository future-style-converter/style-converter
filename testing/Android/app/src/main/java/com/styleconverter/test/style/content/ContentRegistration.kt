package com.styleconverter.test.style.content

// Phase 10 facade — ContentExtractor already produces ContentConfig
// covering `content`, `quotes`, and the three counter-* properties. The
// applier only renders `content` for `:before` / `:after` pseudo-elements
// (which ComponentRenderer does not currently implement), so the
// effective runtime impact today is no-op.
//
// Parser-gap note:
//   * Content is huge — 10+ value kinds, quote-aware tokenizer, `url(...) /
//     "alt"` syntax, multi-part lists.

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers 5 content / counter / quotes IR properties under the
 * `content` owner.
 */
object ContentRegistration {

    init {
        PropertyRegistry.migrated(
            "Content",
            "Quotes",
            "CounterReset", "CounterIncrement", "CounterSet",
            owner = "content"
        )
    }
}
