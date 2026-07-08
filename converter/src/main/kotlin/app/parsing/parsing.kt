package app.parsing

import app.irmodels.IRDocument
import app.parsing.css.cssParsing
import app.parsing.compose.composeParsing
import app.parsing.swiftUI.swiftUIParsing
import kotlinx.serialization.json.*

/**
 * Main parsing entry point that routes based on explicit --from input value.
 * This is the entry-point "parsing" transformation: input styles → IR.
 */

fun parsing(doc: JsonObject, from: String): IRDocument {
    return when (from.lowercase()) {
        "css" -> cssParsing(doc)
        "compose" -> composeParsing(doc)
        "swiftui" -> swiftUIParsing(doc)
        else -> throw IllegalArgumentException("Unsupported input format: $from")
    }
}