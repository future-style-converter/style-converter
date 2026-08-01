// DecorationWire.kt — the `meta.decorations` WIRE → paint-request bridge
// (applier campaign wave 22, lane DECOR).
//
// BYTE-PARALLEL TWIN of the iOS runtime's
// runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/
// decoration/DecorationWire.swift — same filter order, same drop rule,
// same currentColor fallback, same tracker key. Change one, change both.
//
// WHY A SEPARATE FILE: DecorationColorOps is deliberately dependency-free
// (no IR types, no Compose types) so the JVM pin suite compiles it
// standalone. This bridge is where the two dependencies it refuses meet:
// the decoded IR model ([IRDecoration]) on one side and the runtime's CSS
// colour-token parser on the other. Keeping it here means BOTH painter
// call sites in ComponentRenderer share one conversion instead of two.
//
// WHY THE COLOUR IS RESOLVED HERE AND NOT IN THE CONVERTER:
// `meta.decorations` carries the colour token AS AUTHORED ("blue", "#00f",
// "rgb(0,0,255)") — see schema/spec/04-metadata-fields.md. `meta` members
// are extractor-owned payloads the converter forwards verbatim (the
// wave-20 `meta.attrs` precedent); normalizing this one would make the
// converter interpret a hint it is contractually opaque to. Every runtime
// already owns a token parser for exactly this shape — the one that reads
// substituted `var()` values — so resolution lands there, ONCE, at decode
// time. The honest cost is documented on [toDecorationLines].
package com.styleconverter.runtime.typography

import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRDecoration
import com.styleconverter.runtime.core.types.ValueExtractors

object DecorationWire {

    /**
     * The wire list → the ordered request list [DecorationColorOps.resolve]
     * consumes. ORDER IS PRESERVED (outermost-first, css-text-decor-3 §2.1)
     * because paint order is the whole point of the list.
     *
     * TWO drop rules, both LOUD (recorded in PropertyTracker's unhandled
     * set, which the harness dumps — never a silent fallthrough):
     *
     *  1. An unrecognised `line` keyword (a future §2.1 keyword, `blink`,
     *     junk) drops the ENTRY. Painting a guessed line would be worse
     *     than painting none, and the caller still sees an authoritative
     *     — possibly EMPTY — list, which by contract paints nothing.
     *  2. An unresolvable `color` token drops the COLOUR ONLY, not the
     *     line: the entry keeps its line and falls back to `null`, which
     *     is what an absent colour means anyway. NOTE what `null`
     *     actually paints — NOT plain `currentColor`: ComponentRenderer
     *     substitutes `ownedDecorationColor` = the run's merged
     *     `text-decoration-color` leaf, else the text colour (the iOS
     *     twin's `textConfig.decorationColor ?? resolvedColor` does the
     *     same). For the OUTERMOST entry that is exactly right; for any
     *     other entry it is the root's colour, not §2.2's initial.
     *
     *     Color tokens resolve through CssVariableResolver.parseColorValue —
     *     the platform's FULL CSS color parser (#hex 3/4/6/8, rgb()/rgba(),
     *     hsl()/hsla(), `transparent`, all 147 css-color-4 §6.1 names,
     *     CSS-correct gray/darkgray/lightgray values). The iOS twin
     *     delegates to CSSTokenParser.color over a table GENERATED from
     *     this one (wave 22), so named-token parity is structural, not
     *     ledger-maintained. Pinned by the seam probe suites on both
     *     natives (coral/skyblue/crimson now resolve identically).
     *
     * A null/empty input maps to null/empty output: the EMPTY list is a
     * meaningful state ("authoritative, and it says no lines"), never
     * silently upgraded to "absent".
     */
    fun toDecorationLines(wire: List<IRDecoration>?): List<DecorationColorOps.DecorationLine>? {
        // Absent stays absent — the painter then synthesizes the
        // component's own flags (the pre-wave-22 legacy path).
        if (wire == null) return null
        return wire.mapNotNull { entry ->
            // Rule 1: the line keyword decides whether the entry survives.
            val kind = DecorationColorOps.lineKindFrom(entry.line)
            if (kind == null) {
                PropertyTracker.markUnhandled("TextDecorationLine(meta.decorations line='${entry.line}')")
                return@mapNotNull null
            }
            // Rule 2: the colour is best-effort; a failure degrades to
            // currentColor rather than dropping the line.
            val color = entry.color?.let { token ->
                val parsed = com.styleconverter.runtime.core.variables.CssVariableResolver
                    // Wave 22 — the FULL CSS color parser (hex, rgb()/rgba(),
                    // hsl()/hsla(), all 147 css-color-4 §6.1 names): the
                    // reduced var-substitution reader missed coral/skyblue
                    // (live in text-decoration-style-multiple) — skeptic find.
                    .parseColorValue(token)
                if (parsed == null) {
                    PropertyTracker.markUnhandled("TextDecorationColor(meta.decorations color='$token')")
                    null
                } else {
                    // IR/CSS sRGB 0..1 is exactly Compose's Color space —
                    // straight component copy, no conversion.
                    DecorationColorOps.Rgba(parsed.red, parsed.green, parsed.blue, parsed.alpha)
                }
            }
            DecorationColorOps.DecorationLine(kind, color)
        }
    }
}
