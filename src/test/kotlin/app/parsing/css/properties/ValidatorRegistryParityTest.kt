package app.parsing.css.properties

// Pins validator/registry parity: every property with a registered parser
// in PropertyParserRegistry must also be accepted by CssPropertyValidator.
// The validator runs FIRST in the parsing pipeline, so any name it rejects
// never reaches its parser — the declaration is silently dropped with no
// diagnostic. This drift really happened: font-smooth, interactivity,
// margin-break, reading-order, ruby-overhang, scroll-marker-group and
// scroll-target-group all had parsers but were missing from the whitelist.

import app.parsing.css.properties.longhands.PropertyParserRegistry
import kotlin.test.Test
import kotlin.test.assertTrue

class ValidatorRegistryParityTest {

    @Test
    fun `every registered parser key is accepted by the validator`() {
        // Collect every registered name the validator would reject.
        val silentlyDropped = PropertyParserRegistry.registeredPropertyNames()
            .filterNot { CssPropertyValidator.isValidProperty(it) }
            .sorted()

        // Any entry here means a parser exists but its input is filtered out
        // before parsing — i.e. valid CSS is silently dropped. Fix by adding
        // the name to CssPropertyValidator's whitelist.
        assertTrue(
            silentlyDropped.isEmpty(),
            "Properties with registered parsers rejected by CssPropertyValidator " +
                "(valid CSS silently dropped): $silentlyDropped"
        )
    }

    @Test
    fun `the seven previously dropped properties are accepted`() {
        // Regression pin for the exact 7 names fixed in this cleanup, so the
        // failure message is precise if any of them regresses individually.
        val previouslyDropped = listOf(
            "font-smooth", "interactivity", "margin-break", "reading-order",
            "ruby-overhang", "scroll-marker-group", "scroll-target-group"
        )
        val stillRejected = previouslyDropped.filterNot { CssPropertyValidator.isValidProperty(it) }
        assertTrue(
            stillRejected.isEmpty(),
            "Whitelist regression — these names are registered parsers but rejected again: $stillRejected"
        )
    }
}
