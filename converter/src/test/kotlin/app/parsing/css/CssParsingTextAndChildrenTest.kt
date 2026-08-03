package app.parsing.css

// FIX-A2 regression suite: locks in the round-trip of `_text` (element text)
// and `children` (nested-component map) from WPT-extractor input through the
// CSS parser → IRDocument → tmpOutput.json pipeline. Before FIX-A2 the IR
// serializer dropped both fields; the W3 swarm rerun smoke saw `no-data: 5`
// on every css-color test because of that drop. These tests exist so the
// regression can never reland silently — anyone editing IRComponent,
// CssComponent, or the IR serializer will see them fail before merging.
//
// Why JUnit 5 + kotlin.test instead of golden-file diffing: each behaviour
// (text preserved, children flattened, deep nesting kept, backward compat)
// is independently meaningful, so we want one failing red dot per broken
// invariant rather than one giant diff that buries the cause.

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CssParsingTextAndChildrenTest {

    // Shared Json instance — production code uses Json {} with no special
    // config for parseToJsonElement, so mirror that here. Pretty-printing
    // would change byte counts in the round-trip assertions below.
    private val json = Json

    /**
     * (a) `_text` on a leaf component is preserved end-to-end.
     *
     * Mirrors fixtures/wpt/css-color/color-001.json — the simplest WPT case
     * and the one that broke first in the swarm-2b smoke. If this test
     * fails, the W3 renderer will see no-data on every css-color test.
     */
    @Test
    fun `text on leaf component flows from input to IR output`() {
        // Input shape exactly matches what tools/titan/extract-fixture.mjs
        // emits for a single-element WPT fixture (a <p> with text inside).
        val input = json.parseToJsonElement(
            """
            {
              "components": {
                "color-001__0": {
                  "properties": { "color": "green" },
                  "_text": "Test passes if this text is green"
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        // Run the full css-parse pipeline (this is what Main.kt invokes for
        // `--from css`).
        val ir = cssParsing(input)

        // Round-trip through the actual production serializer so we test
        // not just the in-memory model but the JSON write path the
        // renderer ultimately reads.
        val outString = Json { prettyPrint = true }.encodeToString(ir)
        val outObj = json.parseToJsonElement(outString).jsonObject
        val component = outObj["components"]!!.jsonArray[0].jsonObject

        // The wire field is `_text` (leading underscore = renderer-only
        // metadata convention; see IRComponentSerializer KDoc and
        // runtimes/web/src/core/ir/IRModels.ts).
        val textNode = component["_text"]
        assertNotNull(textNode, "Expected _text field on serialized IRComponent")
        assertEquals(
            "Test passes if this text is green",
            textNode.jsonPrimitive.content,
            "Element text payload must round-trip verbatim"
        )

        // And the IR-side IRComponent should expose it as `text` (no
        // underscore — Kotlin identifier convention; the underscore is
        // added/stripped at the JSON boundary only).
        assertEquals("Test passes if this text is green", ir.components[0].text)
    }

    /**
     * (b) `children` (input map) → IR `children` (output array).
     *
     * The WPT extractor ships nested children as a name-keyed map so each
     * child keeps its stable extractor id (`__0`, `__0__1`, …). The
     * renderer-facing IR needs an array (matches `IRComponent[]` in
     * runtimes/web/src/core/ir/IRModels.ts). This test pins the
     * map→array flattening at the IR boundary.
     */
    @Test
    fun `children map on input becomes children array on output`() {
        val input = json.parseToJsonElement(
            """
            {
              "components": {
                "wrapper": {
                  "properties": { "background": "blue" },
                  "children": {
                    "wrapper__0": {
                      "properties": { "color": "red" }
                    },
                    "wrapper__1": {
                      "properties": { "color": "green" }
                    }
                  }
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        val ir = cssParsing(input)

        // Two children, in insertion order. JsonObject preserves source
        // order in kotlinx-serialization, so this is deterministic.
        val wrapper = ir.components[0]
        val children = wrapper.children
        assertNotNull(children, "Expected non-null children list")
        assertEquals(2, children.size)
        assertEquals("wrapper__0", children[0].name)
        assertEquals("wrapper__1", children[1].name)

        // Round-trip the JSON shape: `children` MUST serialize as a JSON
        // array (NOT an object), because the web renderer iterates with
        // `.map(...)`. A regression to map-shape would crash at runtime.
        val outString = Json { prettyPrint = false }.encodeToString(ir)
        val outObj = json.parseToJsonElement(outString).jsonObject
        val wrapperOut = outObj["components"]!!.jsonArray[0].jsonObject
        assertTrue(
            wrapperOut["children"] is JsonArray,
            "Serialized children must be a JSON array (the web renderer requires IRComponent[])"
        )
        assertEquals(2, (wrapperOut["children"] as JsonArray).size)
    }

    /**
     * (c) Deep nesting (3 levels) preserved end-to-end.
     *
     * Mirrors fixtures/wpt/css-break/abspos-in-opacity-000.json — three
     * levels of `children` plus a leaf `_text` at the top. Catches any
     * regression where recursion bottoms out early or the serializer fails
     * to recurse on the array shape it just wrote.
     */
    @Test
    fun `three level deep nesting preserved through serialization`() {
        val input = json.parseToJsonElement(
            """
            {
              "components": {
                "L0": {
                  "_text": "outer label",
                  "properties": {},
                  "children": {
                    "L1": {
                      "properties": { "opacity": "0.5" },
                      "children": {
                        "L2": {
                          "properties": { "background": "red" },
                          "children": {
                            "L3": {
                              "properties": { "color": "blue" },
                              "_text": "deepest"
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        val ir = cssParsing(input)
        val outString = Json { prettyPrint = false }.encodeToString(ir)
        val outObj = json.parseToJsonElement(outString).jsonObject

        // Walk the array spine all the way down and confirm every level
        // survived BOTH the in-memory IR conversion AND the JSON write.
        val l0 = outObj["components"]!!.jsonArray[0].jsonObject
        assertEquals("outer label", l0["_text"]!!.jsonPrimitive.content)

        val l1 = l0["children"]!!.jsonArray[0].jsonObject
        assertEquals("L1", l1["name"]!!.jsonPrimitive.content)

        val l2 = l1["children"]!!.jsonArray[0].jsonObject
        assertEquals("L2", l2["name"]!!.jsonPrimitive.content)

        val l3 = l2["children"]!!.jsonArray[0].jsonObject
        assertEquals("L3", l3["name"]!!.jsonPrimitive.content)
        // The deepest leaf's `_text` must also survive — recursion bugs
        // typically swallow this last value.
        assertEquals("deepest", l3["_text"]!!.jsonPrimitive.content)

        // Sanity: deepest level has no further children → `children` field
        // must be omitted entirely (not serialized as `null` or `[]`),
        // matching the existing IRComponentSerializer omit-when-empty
        // contract.
        assertNull(l3["children"])
    }

    /**
     * Backward-compat: a fixture WITHOUT `_text` and WITHOUT `children`
     * (e.g. fixtures/visual-test.json) must produce IR JSON that does not
     * contain either field. Any spurious key would shift bytes on the
     * 327-pair BASELINE=1 regression and break that gate.
     */
    @Test
    fun `non-WPT fixture omits text and children fields entirely`() {
        val input = json.parseToJsonElement(
            """
            {
              "components": {
                "PlainBox": {
                  "properties": { "width": "100px", "background-color": "#3498db" }
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        val ir = cssParsing(input)
        // Confirm the IR side is null too — important so the serializer's
        // omit-when-null branch is the reason the field is missing
        // (rather than e.g. the field being present but empty-string).
        assertNull(ir.components[0].text, "text must be null when input has no _text")
        assertNull(ir.components[0].children, "children must be null when input has none")
        // Same nullity invariant for _role — non-WPT fixtures never carry
        // a role marker, so the serializer must omit the key entirely.
        assertNull(ir.components[0].role, "role must be null when input has no _role")

        val outString = Json { prettyPrint = true }.encodeToString(ir)
        // Use literal substring search rather than parsing the JSON tree so
        // the test would catch even an accidental `"_text": null` or
        // `"children": []` (both of which would be JSON-null-ish but bytes
        // still differ from the pre-FIX-A2 baseline).
        assertTrue(
            !outString.contains("\"_text\""),
            "Backward-compat: non-WPT fixture output must not contain _text key"
        )
        assertTrue(
            !outString.contains("\"children\""),
            "Backward-compat: non-WPT fixture output must not contain children key"
        )
        // Same byte-stability gate for _role — its very presence would
        // shift bytes on the 327-pair BASELINE=1 regression.
        assertTrue(
            !outString.contains("\"_role\""),
            "Backward-compat: non-WPT fixture output must not contain _role key"
        )
    }

    /**
     * Bug 1 / swarm-003 backdrop-filter-root-element regression test.
     *
     * The WPT extractor (tools/titan/extract-fixture.mjs:1417) stamps
     * `_role: "body-root"` on synthetic body-root components so the
     * platform renderers can branch on root-element paint semantics.
     * Before this fix the Kotlin convert pipeline silently dropped the
     * field — confirmed by `grep '_role' tmpOutput.json` returning zero
     * hits. The renderer-side fix (viewport-paint hoisting) is blocked
     * until the marker survives convert; this test pins that survival.
     */
    @Test
    fun `_role body-root marker preserved end-to-end`() {
        val input = json.parseToJsonElement(
            """
            {
              "components": {
                "backdrop-filter-root-element__body": {
                  "properties": {
                    "background": "green",
                    "backdrop-filter": "invert(1)"
                  },
                  "_role": "body-root"
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        val ir = cssParsing(input)
        // IR-side: identifier-style field (no underscore prefix — that's
        // a JSON-boundary convention; in-memory the Kotlin idiom wins).
        assertEquals("body-root", ir.components[0].role)

        val outString = Json { prettyPrint = false }.encodeToString(ir)
        val outObj = json.parseToJsonElement(outString).jsonObject
        val component = outObj["components"]!!.jsonArray[0].jsonObject

        // On the wire the leading underscore is back — that's what the
        // platform renderers grep for.
        val roleNode = component["_role"]
        assertNotNull(roleNode, "Expected _role field on serialized IRComponent")
        assertEquals("body-root", roleNode.jsonPrimitive.content)
    }

    /**
     * Bug 1 round-trip with the other renderer-only metadata fields
     * present — verifies _role doesn't disrupt _text and children. The
     * shape mirrors a hypothetical body-root that wraps inner styled
     * children (a future WPT extractor feature: today body-root has no
     * children, but the field shouldn't conflict if it grows them).
     */
    @Test
    fun `_role coexists with _text and children`() {
        val input = json.parseToJsonElement(
            """
            {
              "components": {
                "root": {
                  "_role": "body-root",
                  "_text": "viewport text",
                  "properties": { "background": "green" },
                  "children": {
                    "root__0": {
                      "properties": { "color": "red" }
                    }
                  }
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        val ir = cssParsing(input)
        val root = ir.components[0]
        assertEquals("body-root", root.role)
        assertEquals("viewport text", root.text)
        assertEquals(1, root.children?.size)

        // All three optional metadata fields survive the serializer in
        // one pass — _role + _text + children.
        val outString = Json { prettyPrint = false }.encodeToString(ir)
        val outObj = json.parseToJsonElement(outString).jsonObject
        val component = outObj["components"]!!.jsonArray[0].jsonObject
        assertEquals("body-root", component["_role"]!!.jsonPrimitive.content)
        assertEquals("viewport text", component["_text"]!!.jsonPrimitive.content)
        assertTrue(component["children"] is JsonArray, "children must serialize as array")
    }

    /**
     * Defensive: an explicit JSON `null` at `_role` round-trips to a
     * Kotlin `null` (no field on output). Matches the same convention
     * already in place for `_text` — see CssParsing.parseComponent.
     */
    @Test
    fun `explicit null _role is treated as absent`() {
        val input = json.parseToJsonElement(
            """
            {
              "components": {
                "PlainBox": {
                  "properties": { "color": "red" },
                  "_role": null
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        val ir = cssParsing(input)
        assertNull(ir.components[0].role, "explicit null _role → Kotlin null")

        val outString = Json { prettyPrint = false }.encodeToString(ir)
        assertTrue(
            !outString.contains("\"_role\""),
            "explicit null _role must not appear in serialized output"
        )
    }

    /**
     * wave-20 W1: `_attrs` (widget-identity attributes) is parsed into the
     * IRComponent's `attrs` field VERBATIM — the converter never interprets
     * the extractor-owned payload (strings/booleans/numbers per the
     * extract-fixture.mjs widgetAttrsFor wire contract). The deprecated v1
     * serializer must keep IGNORING it so `--emit-ir v1` bytes stay frozen
     * (the same rule `_tag`/`_pseudo` follow; IR v2 emission is pinned in
     * IRWireV2Test).
     */
    @Test
    fun `_attrs is parsed verbatim and ignored by the v1 serializer`() {
        // The appearance-checkbox-001 shape: an attributed <input> widget.
        val input = json.parseToJsonElement(
            """
            {
              "components": {
                "chk__0": {
                  "properties": { "appearance": "checkbox" },
                  "_tag": "input",
                  "_attrs": { "type": "checkbox", "checked": true, "min": 0.5 }
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        val ir = cssParsing(input)
        // In-memory model carries the payload byte-verbatim (opaque object).
        val attrs = ir.components[0].attrs
        assertNotNull(attrs, "Expected attrs forwarded onto IRComponent")
        assertEquals("checkbox", attrs["type"]!!.jsonPrimitive.content)
        assertEquals("true", attrs["checked"]!!.jsonPrimitive.content)
        assertEquals("0.5", attrs["min"]!!.jsonPrimitive.content)

        // The frozen v1 wire never learns about attrs (historical-drop rule).
        val outString = Json { prettyPrint = false }.encodeToString(ir)
        assertTrue(
            !outString.contains("_attrs") && !outString.contains("\"attrs\""),
            "v1 serializer must not emit attrs in any spelling"
        )
    }

    /**
     * wave-22 lane DECOR: `_decorations` (the collapsed inline run's ordered
     * per-line decoration list) is parsed into the IRComponent's
     * `decorations` field VERBATIM — entry ORDER, `line` keyword spelling
     * and the AUTHORED colour token all survive untouched, because the
     * converter must not normalize what the runtimes' own CSS token parsers
     * resolve (schema/spec/04-metadata-fields.md). Same v1 freeze rule as
     * `_attrs`: the deprecated serializer ignores the field entirely.
     */
    @Test
    fun `_decorations is parsed verbatim and ignored by the v1 serializer`() {
        // The live text-decoration-color__7 shape: three nested decorating
        // boxes collapsed onto one run, outermost-first.
        val input = json.parseToJsonElement(
            """
            {
              "components": {
                "run__0": {
                  "properties": { "text-decoration": "underline" },
                  "_text": "collapsed run",
                  "_decorations": [
                    { "line": "underline", "color": "blue" },
                    { "line": "overline", "color": "gray" },
                    { "line": "line-through" }
                  ]
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        val ir = cssParsing(input)
        // In-memory model carries the array byte-verbatim (opaque payload).
        val decorations = ir.components[0].decorations
        assertNotNull(decorations, "Expected decorations forwarded onto IRComponent")
        assertEquals(3, decorations.size, "every entry survives — no filtering here")
        // ORDER is outermost-first and must not be re-sorted.
        assertEquals("underline", decorations[0].jsonObject["line"]!!.jsonPrimitive.content)
        assertEquals("overline", decorations[1].jsonObject["line"]!!.jsonPrimitive.content)
        assertEquals("line-through", decorations[2].jsonObject["line"]!!.jsonPrimitive.content)
        // Colour tokens stay AUTHORED (no sRGB leaf, no re-spelling)…
        assertEquals("blue", decorations[0].jsonObject["color"]!!.jsonPrimitive.content)
        assertEquals("gray", decorations[1].jsonObject["color"]!!.jsonPrimitive.content)
        // …and an omitted colour stays omitted (currentColor, §2.2 initial).
        assertTrue(
            "color" !in decorations[2].jsonObject,
            "an uncoloured entry must not gain a synthesized colour"
        )

        // The frozen v1 wire never learns about decorations (historical-drop).
        val outString = Json { prettyPrint = false }.encodeToString(ir)
        assertTrue(
            !outString.contains("_decorations") && !outString.contains("\"decorations\""),
            "v1 serializer must not emit decorations in any spelling"
        )
    }

    /**
     * wave-27 lane CBAKE: `_markerText` (the RESOLVED list-marker string one
     * `<li>` renders — representation + suffix per css-counter-styles-3 §6,
     * already resolved against `<ol start>` and the item's ordinal) is parsed
     * onto the IRComponent's `markerText` field VERBATIM. The converter must
     * never re-derive or re-spell it: only the extractor has the counter
     * origin, and only it walked the §6 table. Same v1 freeze rule as
     * `_attrs`/`_decorations` — the deprecated serializer ignores the field.
     */
    @Test
    fun `_markerText is parsed verbatim and ignored by the v1 serializer`() {
        // The live css3-counter-styles-159 shape: <ol start='1860'> whose one
        // item resolves to the cambodian representation of 1860 plus ".".
        val input = json.parseToJsonElement(
            """
            {
              "components": {
                "list__0": {
                  "properties": { "list-style-position": "inside" },
                  "_tag": "ol",
                  "_attrs": { "start": "1860" },
                  "children": {
                    "list__0__0": {
                      "properties": { "list-style-type": "cambodian" },
                      "_tag": "li",
                      "_markerText": "\u17e1\u17e8\u17e6\u17e0."
                    }
                  }
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        val ir = cssParsing(input)
        // The <ol> keeps the ordinal attribute (the disjoint list attr lane).
        assertEquals("1860", ir.components[0].attrs!!["start"]!!.jsonPrimitive.content)
        // The child <li> carries the baked marker string byte-verbatim.
        val item = ir.components[0].children!![0]
        assertEquals("\u17e1\u17e8\u17e6\u17e0.", item.markerText)
        // A component with no `_markerText` must not gain one.
        assertNull(ir.components[0].markerText)

        // The frozen v1 wire never learns about markerText (historical-drop).
        val outString = Json { prettyPrint = false }.encodeToString(ir)
        assertTrue(
            !outString.contains("_markerText") && !outString.contains("\"markerText\""),
            "v1 serializer must not emit markerText in any spelling"
        )
    }
}
