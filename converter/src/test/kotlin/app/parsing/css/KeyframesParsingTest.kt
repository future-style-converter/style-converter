package app.parsing.css

// Keyframes authoring-block tests (wave 8 — schema/spec/07-animations.md).
//
// Pins the converter half of the animation contract:
//   1. the document-level `keyframes` authoring block parses into
//      IRDocument.keyframes with css-animations-1 §4.2 offset resolution
//      (`from` → 0, `to` → 1, `N%` → N/100),
//   2. stops are SORTED ascending by resolved offset (stable for equals),
//   3. declarations run through the same PropertiesParser pipeline as
//      component base properties (typed values, shorthand expansion),
//   4. invalid offsets / empty sets are dropped loudly, never emitted,
//   5. the v2 wire carries the additive `keyframes` envelope key
//      (omit-when-empty) and the deprecated v1 emission NEVER does.

import app.irmodels.IRDocument
import app.irmodels.IRWireV2
import app.parsing.IRFlattener
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyframesParsingTest {

    private val json = Json

    /** Parse an input-envelope JSON string straight through cssParsing. */
    private fun parse(input: String): IRDocument =
        cssParsing(json.parseToJsonElement(input).jsonObject)

    // ---- authoring block → typed IR ----

    @Test
    fun `keyframes block parses with offsets resolved and stops sorted`() {
        // Offsets authored DELIBERATELY out of order (to, 50%, from) —
        // the converter must emit 0.0, 0.5, 1.0.
        val doc = parse(
            """
            {
              "components": { "Box": { "properties": { "width": "40px" } } },
              "keyframes": {
                "fade": [
                  { "offset": "to",   "declarations": { "opacity": "1" } },
                  { "offset": "50%",  "declarations": { "opacity": "0.25" } },
                  { "offset": "from", "declarations": { "opacity": "0" } }
                ]
              }
            }
            """
        )
        val fade = assertNotNull(doc.keyframes?.get("fade"))
        assertEquals(listOf(0.0, 0.5, 1.0), fade.map { it.offset })
        // Every stop's declarations became typed IR properties.
        fade.forEach { stop ->
            assertEquals(listOf("opacity"), stop.properties.map { it.propertyName })
        }
    }

    @Test
    fun `keyframe declarations reuse the base property pipeline including shorthands`() {
        val doc = parse(
            """
            {
              "components": { "Box": { "properties": {} } },
              "keyframes": {
                "grow": [
                  { "offset": "0%",   "declarations": { "background-color": "#ff0000", "width": "10px" } },
                  { "offset": "100%", "declarations": { "padding": "4px 8px" } }
                ]
              }
            }
            """
        )
        val grow = assertNotNull(doc.keyframes?.get("grow"))
        // Typed parsers claimed the longhands (not GenericProperty).
        assertEquals(
            listOf("background-color", "width"),
            grow[0].properties.map { it.propertyName }.sorted()
        )
        // The `padding` shorthand expanded into its four longhands exactly
        // as it does for component base properties.
        assertEquals(
            listOf("padding-bottom", "padding-left", "padding-right", "padding-top"),
            grow[1].properties.map { it.propertyName }.sorted()
        )
    }

    @Test
    fun `invalid offsets are dropped and fully-invalid sets vanish`() {
        val doc = parse(
            """
            {
              "components": { "Box": { "properties": {} } },
              "keyframes": {
                "partial": [
                  { "offset": "0%",     "declarations": { "opacity": "0" } },
                  { "offset": "150%",   "declarations": { "opacity": "1" } },
                  { "offset": "middle", "declarations": { "opacity": "0.5" } }
                ],
                "broken": [
                  { "offset": "nope", "declarations": { "opacity": "1" } }
                ],
                "empty": []
              }
            }
            """
        )
        // `partial` keeps only its valid stop (out-of-range % and unknown
        // keywords are invalid keyframe rules per css-animations-1 §4.2).
        assertEquals(listOf(0.0), doc.keyframes?.get("partial")?.map { it.offset })
        // Sets with zero valid stops are dropped whole (schema minItems 1).
        assertNull(doc.keyframes?.get("broken"))
        assertNull(doc.keyframes?.get("empty"))
    }

    @Test
    fun `documents without a keyframes block carry null`() {
        val doc = parse("""{ "components": { "Box": { "properties": {} } } }""")
        assertNull(doc.keyframes)
    }

    @Test
    fun `equal offsets keep authoring order - stable sort`() {
        val doc = parse(
            """
            {
              "components": { "Box": { "properties": {} } },
              "keyframes": {
                "dup": [
                  { "offset": "50%", "declarations": { "opacity": "0.1" } },
                  { "offset": "50%", "declarations": { "width": "10px" } }
                ]
              }
            }
            """
        )
        val dup = assertNotNull(doc.keyframes?.get("dup"))
        // Both survive, in authoring order (the runtimes apply the
        // last-wins cascade; the wire never reorders equals).
        assertEquals(listOf("opacity", "width"), dup.map { it.properties.single().propertyName })
    }

    // ---- wire emission (v2 additive key; v1 untouched) ----

    @Test
    fun `v2 wire emits offset-sorted keyframes and v1 emission never does`() {
        val doc = parse(
            """
            {
              "components": { "Box": { "properties": { "animation-name": "fade" } } },
              "keyframes": {
                "fade": [
                  { "offset": "to",   "declarations": { "opacity": "1" } },
                  { "offset": "from", "declarations": { "opacity": "0" } }
                ]
              }
            }
            """
        )
        // Flatten preserves the document-level keyframes map.
        val flat = IRFlattener.flatten(doc)
        assertNotNull(flat.keyframes)
        val wire = IRWireV2.encodeDocument(flat)
        // Envelope gains exactly the additive key (spec 05 minor revision).
        assertEquals(setOf("irVersion", "minReaderVersion", "components", "keyframes"), wire.keys)
        val fade = wire["keyframes"]!!.jsonObject["fade"]!!.jsonArray
        assertEquals(listOf(0.0, 1.0), fade.map { it.jsonObject["offset"]!!.jsonPrimitive.double })
        // Stops carry the standard {type,data} property envelopes with the
        // typed Opacity payload (alpha float), not a Generic passthrough.
        val stop0 = fade[0].jsonObject["properties"]!!.jsonArray.single().jsonObject
        assertEquals("Opacity", stop0["type"]!!.jsonPrimitive.content)
        assertTrue("alpha" in stop0["data"]!!.jsonObject.keys)
        // The DEPRECATED v1 plugin-serializer path must never leak the
        // field (v1 is a descriptive freeze — @Transient enforces this).
        val v1Text = Json { prettyPrint = true }.encodeToString(doc)
        assertFalse(v1Text.contains("keyframes"))
    }

    @Test
    fun `v2 wire omits the keyframes key when the document has none`() {
        val doc = parse("""{ "components": { "Box": { "properties": {} } } }""")
        val wire = IRWireV2.encodeDocument(IRFlattener.flatten(doc))
        assertEquals(setOf("irVersion", "minReaderVersion", "components"), wire.keys)
    }

    @Test
    fun `v2 decode round-trips set names and offsets`() {
        val doc = parse(
            """
            {
              "components": { "Box": { "properties": {} } },
              "keyframes": {
                "slide": [
                  { "offset": "0%",   "declarations": { "width": "10px" } },
                  { "offset": "100%", "declarations": { "width": "90px" } }
                ]
              }
            }
            """
        )
        val decoded = IRWireV2.decodeDocument(IRWireV2.encodeDocument(IRFlattener.flatten(doc)))
        // Structural round-trip: names + offsets (property payloads stay
        // the documented decode stub, same as component properties).
        assertEquals(listOf(0.0, 1.0), decoded.keyframes?.get("slide")?.map { it.offset })
    }
}
