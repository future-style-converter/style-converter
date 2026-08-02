package app.parsing.css.properties.longhands.columns

// Wave-24 wire pin for the CSS Gap Decorations Level 1 family. The whole
// point of this family is that its eight leaves are BYTE-IDENTICAL to the
// three column-rule leaves that already ship — the three native runtime
// lanes decode both through the same extractors. So every assertion below
// compares the new bytes against the existing ColumnRule* bytes produced by
// the SAME pipeline run, rather than against a hand-copied literal.

import app.irmodels.PropertyScope
import app.parsing.IRFlattener
import app.parsing.css.cssParsing
import app.parsing.css.properties.CssPropertyValidator
import app.irmodels.IRWireV2
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GapDecorationsWireTest {

    // Run the REAL pipeline (cssParsing → IRFlattener → IRWireV2) and index
    // the first component's properties by their `type` tag, so a test can ask
    // for "RowRuleWidth" without depending on declaration order.
    private fun emit(css: String): Map<String, JsonElement> {
        val input = """{"components":{"C":{"properties":{$css}}}}"""
        val doc = IRWireV2.encodeDocument(IRFlattener.flatten(cssParsing(Json.parseToJsonElement(input).jsonObject)))
        val props = doc["components"]!!.jsonArray[0].jsonObject["properties"]!!.jsonArray
        return props.associate { p ->
            val o = p.jsonObject
            o["type"]!!.jsonPrimitive.content to o["data"]!!
        }
    }

    // ---- validator/scope wiring ----

    @Test
    fun `every gap-decoration name survives the validator whitelist`() {
        // The validator runs BEFORE parsing; a missing name is a silent drop.
        val names = listOf(
            "row-rule", "row-rule-width", "row-rule-style", "row-rule-color",
            "column-rule-break", "row-rule-break",
            "column-rule-inset", "row-rule-inset", "rule-overlap"
        )
        val rejected = names.filterNot { CssPropertyValidator.isValidProperty(it) }
        assertTrue(rejected.isEmpty(), "silently dropped by CssPropertyValidator: $rejected")
    }

    @Test
    fun `the whole family is CONTAINER-scoped like row-gap`() {
        // Gap decorations are declared on the container and paint in its own
        // gutters — they are never child-carried parent-data.
        val names = listOf(
            "row-rule", "row-rule-width", "row-rule-style", "row-rule-color",
            "column-rule-break", "row-rule-break",
            "column-rule-inset", "row-rule-inset", "rule-overlap"
        )
        names.forEach { assertEquals(PropertyScope.CONTAINER, PropertyScope.of(it), "scope of $it") }
    }

    // ---- byte-identity with the column-rule twins ----

    @Test
    fun `row-rule longhands emit byte-identical data to their column twins`() {
        // Same three values on both axes → the three data payloads must match
        // pairwise, which is exactly what the wave-24 wire contract promises.
        val d = emit(
            """"column-rule-width":"10px","column-rule-style":"solid","column-rule-color":"green",
               "row-rule-width":"10px","row-rule-style":"solid","row-rule-color":"green""""
        )
        assertEquals(d["ColumnRuleWidth"], d["RowRuleWidth"], "width payloads must be identical")
        assertEquals(d["ColumnRuleStyle"], d["RowRuleStyle"], "style payloads must be identical")
        assertEquals(d["ColumnRuleColor"], d["RowRuleColor"], "colour payloads must be identical")
        // And the concrete shapes, pinned against the live wave23 css-gaps IR.
        assertEquals("SOLID", d["RowRuleStyle"]!!.jsonPrimitive.content)
        assertEquals("length", d["RowRuleWidth"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(10.0, d["RowRuleWidth"]!!.jsonObject["px"]!!.jsonPrimitive.content.toDouble())
        assertEquals("green", d["RowRuleColor"]!!.jsonObject["original"]!!.jsonPrimitive.content)
    }

    @Test
    fun `row-rule-width keyword form matches the column-rule-width keyword form`() {
        val d = emit(""""column-rule-width":"thin","row-rule-width":"thin"""")
        assertEquals(d["ColumnRuleWidth"], d["RowRuleWidth"])
        // {"type":"keyword","value":"THIN"} — the deep-flattened sealed variant.
        assertEquals("keyword", d["RowRuleWidth"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("THIN", d["RowRuleWidth"]!!.jsonObject["value"]!!.jsonPrimitive.content)
    }

    // ---- the four axis knobs ----

    @Test
    fun `break keywords emit UPPERCASE idents on both axes`() {
        val d = emit(
            """"column-rule-break":"intersection","row-rule-break":"spanning-item""""
        )
        assertEquals("INTERSECTION", d["ColumnRuleBreak"]!!.jsonPrimitive.content)
        assertEquals("SPANNING_ITEM", d["RowRuleBreak"]!!.jsonPrimitive.content)
        // `normal` is the initial value and must still round-trip explicitly.
        val n = emit(""""row-rule-break":"normal"""")
        assertEquals("NORMAL", n["RowRuleBreak"]!!.jsonPrimitive.content)
    }

    @Test
    fun `insets emit bare length objects and keep negatives`() {
        val d = emit(""""column-rule-inset":"-2px","row-rule-inset":"0"""")
        // Bare {px:N} — no {"type":"length"} wrapper, matching MarginTop.
        assertEquals(setOf("px"), (d["ColumnRuleInset"] as JsonObject).keys)
        assertEquals(-2.0, d["ColumnRuleInset"]!!.jsonObject["px"]!!.jsonPrimitive.content.toDouble())
        // Unitless zero normalises to 0px (LengthParser special case).
        assertEquals(0.0, d["RowRuleInset"]!!.jsonObject["px"]!!.jsonPrimitive.content.toDouble())
    }

    @Test
    fun `percentage inset stays runtime-dependent with no px key`() {
        // Percentages resolve against the gap, which the converter cannot see —
        // spec 02: pixels stays null and only `original` is written.
        val d = emit(""""row-rule-inset":"-25%"""")
        val o = d["RowRuleInset"]!!.jsonObject
        assertNull(o["px"], "percentage inset must not claim a pixel value")
        assertEquals(-25.0, o["original"]!!.jsonObject["v"]!!.jsonPrimitive.content.toDouble())
        assertEquals("PERCENT", o["original"]!!.jsonObject["u"]!!.jsonPrimitive.content)
    }

    @Test
    fun `rule-overlap emits the two paint-order idents`() {
        assertEquals(
            "COLUMN_OVER_ROW",
            emit(""""rule-overlap":"column-over-row"""")["RuleOverlap"]!!.jsonPrimitive.content
        )
        assertEquals(
            "ROW_OVER_COLUMN",
            emit(""""rule-overlap":"row-over-column"""")["RuleOverlap"]!!.jsonPrimitive.content
        )
    }

    // ---- shorthand ----

    @Test
    fun `row-rule shorthand expands to the same three longhands column-rule does`() {
        // Order-independent per css-gaps-1 §4.4; the rgba() token must survive
        // the paren-aware tokeniser as one unit.
        val d = emit(""""row-rule":"5px solid rgba(255, 0, 0, 0.5)"""")
        assertEquals(5.0, d["RowRuleWidth"]!!.jsonObject["px"]!!.jsonPrimitive.content.toDouble())
        assertEquals("SOLID", d["RowRuleStyle"]!!.jsonPrimitive.content)
        assertEquals(0.5, d["RowRuleColor"]!!.jsonObject["srgb"]!!.jsonObject["a"]!!.jsonPrimitive.content.toDouble())
        // Swapped-token form used by the WPT corpus ("5px red solid").
        val swapped = emit(""""row-rule":"5px red solid"""")
        assertEquals("SOLID", swapped["RowRuleStyle"]!!.jsonPrimitive.content)
        assertEquals("red", swapped["RowRuleColor"]!!.jsonObject["original"]!!.jsonPrimitive.content)
    }

    @Test
    fun `unrecognised idents stay unmapped instead of defaulting`() {
        // `overlap-join` is a draft ident this wave deliberately does NOT map;
        // it must surface as a Generic (_unmapped) property, never as 0px.
        val d = emit(""""row-rule-inset":"overlap-join"""")
        assertNull(d["RowRuleInset"], "overlap-join must not be coerced to a length")
        assertTrue(d.containsKey("Generic"), "unmapped value must surface as GenericProperty")
    }
}
