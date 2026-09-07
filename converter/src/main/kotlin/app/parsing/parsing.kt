package app.parsing

import app.irmodels.IRDocument
import app.parsing.css.cssParsing
import kotlinx.serialization.json.*

/**
 * Main parsing entry point that routes based on explicit --from input value.
 * This is the entry-point "parsing" transformation: input styles → IR.
 *
 * CSS is the ONLY reader. The `compose` / `swiftui` branches that used to sit
 * beside it (ComposeParsing.kt / SwiftUIParsing.kt) were placeholders that
 * printed "… not yet implemented" and called `exitProcess(0)` before any
 * artifact was written — an invocation that produced nothing yet looked like
 * success to every calling script, the exact silent-success class the CLEANUP
 * reboot removed on the OUTPUT side (`--to compose` fails fast in Main.kt).
 * Retrospective finding A6#2 deleted the input twins. Main.kt's `allowedFrom`
 * now rejects anything but `css` with a usage error (exit 2) before this
 * function runs, so the `else` arm is a defensive guard for a caller that
 * bypasses the CLI — it throws instead of exiting, so it can never be
 * mistaken for a successful conversion either.
 */
fun parsing(doc: JsonObject, from: String): IRDocument {
    return when (from.lowercase()) {
        // The one real reader: JSON envelope of CSS declarations → typed IR.
        "css" -> cssParsing(doc)
        // Unreachable through the CLI (Main.kt filters first); loud if reached.
        else -> throw IllegalArgumentException("Unsupported input format: $from — only 'css' has a reader")
    }
}
