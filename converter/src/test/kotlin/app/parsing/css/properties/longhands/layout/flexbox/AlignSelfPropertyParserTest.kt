package app.parsing.css.properties.longhands.layout.flexbox

// Wave 52 (lane L7, static-position T3) — the `align-self` value classes the
// parser used to hand to the Generic passthrough.
//
// Verbatim source: the wave51-fix per-test IR carried these as
//   {"type":"Generic","data":{"propertyName":"align-self",
//     "rawValue":"last baseline","_unmapped":true}}
// on 12 css-grid/abspos/*last-baseline* docs (and "normal" ×12, "left" ×10,
// "right" ×10 — tools/titan/results/wave52-static-position census). The web
// runtime drops static Generic values, so the browser fell back to `auto`
// and painted the mark 14 px high (rtl-last-baseline-002 web P 0.9979).
//
// Pins (css-align-3 §6.1 grammar, §4.2 baseline positions):
//   P3a `last baseline` → typed LAST_BASELINE, both token orders;
//   P3b `first baseline` → BASELINE (same value as bare `baseline`);
//   P3c `normal` → typed NORMAL;
//   P3d `left` / `right` → InvalidDeclaration (dropped — not align-self CSS);
//   P3e `safe center` stays on the Generic wire (null) — the enum cannot
//       carry the overflow keyword and both mobile readers parse it there;
//   P3f the wire bytes: {"type":"AlignSelf","data":"LAST_BASELINE"};
//   P3g end-to-end: PropertiesParser drops `align-self: left` (no Generic).
// MUTATIONS EXECUTED (tools/titan/results/wave52-static-position/mutations.log):
//   M-C1 disable the `last baseline` arm → P3a, P3f fail (null, Generic again);
//   M-C2 return null instead of InvalidDeclaration for left/right → P3d, P3g fail.

import app.irmodels.IRProperty
import app.irmodels.IRPropertySerializer
import app.irmodels.properties.layout.flexbox.AlignSelfProperty
import app.parsing.css.CssPropertyValue
import app.parsing.css.properties.InvalidDeclaration
import app.parsing.css.properties.PropertiesParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AlignSelfPropertyParserTest {

    // The typed keyword a value parses to (fails the test if not typed).
    private fun typed(css: String): AlignSelfProperty.AlignSelf =
        assertIs<AlignSelfProperty>(AlignSelfPropertyParser.parse(css)).value

    @Test
    fun `P3a last baseline is typed in both token orders`() {
        // The verbatim rawValue of every *-last-baseline-* abspos child.
        assertEquals(AlignSelfProperty.AlignSelf.LAST_BASELINE, typed("last baseline"))
        // `&&` combinator (§4.2): order-free.
        assertEquals(AlignSelfProperty.AlignSelf.LAST_BASELINE, typed("baseline last"))
        // ASCII case-insensitive keywords, surrounding whitespace ignored.
        assertEquals(AlignSelfProperty.AlignSelf.LAST_BASELINE, typed("  LAST   Baseline "))
    }

    @Test
    fun `P3b first baseline is the same value as baseline`() {
        // §4.2: `first baseline` ≡ `baseline`.
        assertEquals(AlignSelfProperty.AlignSelf.BASELINE, typed("first baseline"))
        // The pre-wave-52 single keyword is unchanged.
        assertEquals(AlignSelfProperty.AlignSelf.BASELINE, typed("baseline"))
    }

    @Test
    fun `P3c normal is typed`() {
        // Verbatim rawValue "normal" (12 css-grid abspos docs).
        assertEquals(AlignSelfProperty.AlignSelf.NORMAL, typed("normal"))
    }

    @Test
    fun `P3d left and right are invalid align-self values`() {
        // §6.1: physical keywords belong to justify-self only.
        assertSame(InvalidDeclaration, AlignSelfPropertyParser.parse("left"))
        assertSame(InvalidDeclaration, AlignSelfPropertyParser.parse("right"))
        // An overflow keyword does not make them valid.
        assertSame(InvalidDeclaration, AlignSelfPropertyParser.parse("safe right"))
    }

    @Test
    fun `P3e overflow-prefixed positions stay on the Generic wire`() {
        // Verbatim rawValue of the three safe-center carriers.
        assertNull(AlignSelfPropertyParser.parse("safe center"))
        // And of flex-abspos-staticpos-align-self-safe-003.
        assertNull(AlignSelfPropertyParser.parse("safe end"))
        // The existing single keywords are untouched.
        assertEquals(AlignSelfProperty.AlignSelf.SELF_END, typed("self-end"))
        assertEquals(AlignSelfProperty.AlignSelf.ANCHOR_CENTER, typed("anchor-center"))
    }

    @Test
    fun `P3f the wire carries the bare enum name`() {
        // The production Json configuration (IRWireV2 shares the default).
        val wire = Json.encodeToJsonElement(
            IRPropertySerializer,
            AlignSelfPropertyParser.parse("last baseline") as IRProperty
        ).jsonObject
        // {type, data} envelope (schema/spec/01) with the bare keyword.
        assertEquals("AlignSelf", wire["type"]!!.jsonPrimitive.content)
        assertEquals("LAST_BASELINE", wire["data"]!!.jsonPrimitive.content)
    }

    @Test
    fun `P3g PropertiesParser drops align-self left instead of passing it through`() {
        // End-to-end through the longhand registry + the InvalidDeclaration filter.
        val out = PropertiesParser.parse(
            mapOf("align-self" to CssPropertyValue("left"), "width" to CssPropertyValue("8px"))
        )
        // No align-self of any shape reaches the wire (typed or Generic).
        assertTrue(out.none { it.propertyName == "align-self" }, "got $out")
        // The sibling declaration survives — only the invalid one is dropped.
        assertTrue(out.any { it.propertyName == "width" }, "got $out")
    }
}
