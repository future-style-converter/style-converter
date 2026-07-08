package com.styleconverter.test.style.counters

// Phase 10 facade — counter-reset / counter-increment / counter-set
// already extract into ContentConfig via ContentExtractor. This facade
// exists so the canonical style/counters/ folder mirrors the parser's
// irmodels/properties/counters/ folder. Applier is no-op unless
// rendered via a `content: counter(...)` pseudo-element, which
// ComponentRenderer does not implement.
//
// Parser-gap note:
//   * Counter{Increment,Reset} pair tokens greedily: `a 1 b 2` ->
//     [(a,1),(b,2)], `a b` -> [(a,1),(b,1)].
//   * CounterSet requires every name be followed by an integer, else
//     null.

import com.styleconverter.test.style.PropertyRegistry

/** Registers 3 CSS Lists-3 counter-* IR properties under the `counters` owner. */
object CountersRegistration {

    init {
        PropertyRegistry.migrated(
            "CounterReset", "CounterIncrement", "CounterSet",
            owner = "counters"
        )
    }
}
