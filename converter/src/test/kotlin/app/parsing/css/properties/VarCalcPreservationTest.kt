package app.parsing.css.properties

// The null+original preservation contract for dynamic values
// (schema/spec/02-values.md, CLAUDE.md "null means runtime-dependent"):
// when a declaration value contains var() or calc(), the converter cannot
// pre-compute it — the EXACT raw expression must survive into the emitted
// IR bytes so the runtimes can resolve it (custom-property names inside
// var() are case-SENSITIVE per css-variables-1 §2, so even a lowercase
// fold is data corruption).
//
// Strategy: run each (property, value) pair through the REAL pipeline
// (cssParsing → IRFlattener → IRWireV2) and assert the raw value appears
// VERBATIM somewhere in the serialized component JSON. This is
// deliberately shape-agnostic — {"expr": …}, {"original": …}, raw string
// leaves are all acceptable carriers; what is pinned is that no parser
// mangles (lowercases, truncates, re-tokenizes) the expression.

import app.irmodels.IRWireV2
import app.parsing.IRFlattener
import app.parsing.css.cssParsing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertTrue

class VarCalcPreservationTest {

    // Serialize the whole first component to a JSON string for the
    // verbatim-substring assertion.
    private fun emittedComponentJson(property: String, value: String): String {
        val input = """{"components":{"C":{"properties":{"$property":"$value"}}}}"""
        val doc = cssParsing(Json.parseToJsonElement(input).jsonObject)
        return IRWireV2.encodeDocument(IRFlattener.flatten(doc))["components"]!!
            .jsonArray[0].jsonObject.toString()
    }

    // Assert the raw declaration survives byte-for-byte in the emitted IR.
    private fun assertPreserved(property: String, value: String) {
        val json = emittedComponentJson(property, value)
        assertTrue(
            json.contains(value),
            "$property: raw expression '$value' was mangled — emitted component: $json"
        )
    }

    // ---- var() references (incl. case-sensitive names + fallbacks) ----

    @Test
    fun `var references survive verbatim across the core visual properties`() {
        // Colors: dynamic → srgb omitted, original carries the reference.
        assertPreserved("color", "var(--Brand-Fg)")
        assertPreserved("background-color", "var(--bg, rgb(1, 2, 3))")
        assertPreserved("border-top-color", "var(--edge)")
        // Box model: PaddingValue/MarginValue/InsetValue Expression escapes.
        assertPreserved("padding-left", "var(--space-2)")
        assertPreserved("margin-left", "var(--space-2, 8px)")
        assertPreserved("top", "var(--Y)")
        // Sizing: WidthValue.Expression keeps the trimmed original.
        assertPreserved("width", "var(--w)")
        assertPreserved("height", "var(--H, 48px)")
        // Border width: pinned the fromKeyword() lowercase-mangle fix —
        // the mixed-case custom-property name must NOT fold to --bw.
        assertPreserved("border-top-width", "var(--BW)")
        // Typography + gaps.
        assertPreserved("font-size", "var(--type-scale-1)")
        assertPreserved("row-gap", "var(--gap)")
    }

    @Test
    fun `nested var fallback chains survive verbatim`() {
        // css-variables-1 §3: fallbacks may nest arbitrarily — the whole
        // unresolved chain is one raw token stream to the converter.
        assertPreserved("color", "var(--missing, var(--fallback, #00ff00))")
        assertPreserved("padding-top", "var(--a, var(--b, var(--c, 4px)))")
    }

    // ---- calc() expressions (incl. relative-unit mixes + nesting) ----

    @Test
    fun `calc expressions survive verbatim including unit mixes`() {
        // %-of-parent minus absolute px: unresolvable before layout.
        assertPreserved("width", "calc(100% - 24px)")
        // Font-relative plus absolute: unresolvable before font sizing.
        assertPreserved("padding-top", "calc(2em + 4px)")
        assertPreserved("font-size", "calc(1em + 2px)")
        // Nested calc trees stay one verbatim expression.
        assertPreserved("height", "calc(calc(100% - 10px) / 2)")
        // calc mixing var() — both dynamic layers in one token stream.
        assertPreserved("margin-top", "calc(var(--space-2) * 2)")
    }
}
