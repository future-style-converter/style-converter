package com.styleconverter.runtime.performance

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave-36 lane M4 — pins for the `contain` WIRE SHAPE.
 *
 * `ContainProperty` is `data class ContainProperty(val values: List<ContainValue>)`,
 * and kotlinx.serialization flattens a single-field data class over a list to a
 * BARE JSON ARRAY. Measured on the wave-35 full-corpus css-contain IR
 * (tools/titan/runs/wave35-webmap/sections/css-contain/out/tmpOutput.json):
 *
 *     {"type":"Contain","data":["STRICT"]}
 *     {"type":"Contain","data":["LAYOUT","PAINT"]}
 *     {"type":"Contain","data":["INLINE_SIZE"]}
 *
 * Before this wave the shorthand expansion (`strict`, `content`) lived only in
 * the JsonPrimitive branch — a shape the converter never emits — so `["STRICT"]`
 * yielded the raw set {"STRICT"}, every ContainConfig flag stayed false and
 * `hasContainment` was false. The web twin had the same class of defect in
 * `keywordOrRaw` (no array branch at all); both are fixed this wave.
 *
 * These pins feed the ARRAY form deliberately, so a fictional payload can never
 * make them pass again.
 */
class ContainExtractorTest {

    private fun cfg(json: String): ContainConfig {
        val data: JsonElement = Json.parseToJsonElement(json)
        return PerformanceExtractor
            .extractPerformanceConfig(listOf("Contain" to data))
            .contain
    }

    @Test
    fun `strict as an array expands to all four primitives`() {
        val c = cfg("""["STRICT"]""")
        assertTrue("layout", c.layout)
        assertTrue("paint", c.paint)
        assertTrue("size", c.size)
        assertTrue("style", c.style)
        assertTrue("isStrict", c.isStrict)
        assertTrue("hasContainment", c.hasContainment)
    }

    @Test
    fun `content as an array expands to layout + paint + style but not size`() {
        val c = cfg("""["CONTENT"]""")
        assertTrue(c.layout && c.paint && c.style)
        assertFalse("size must stay off for `content`", c.size)
        assertTrue("isContent", c.isContent)
    }

    @Test
    fun `a multi-token array sets exactly those primitives`() {
        val c = cfg("""["LAYOUT","PAINT"]""")
        assertEquals(ContainConfig(layout = true, paint = true), c)
    }

    @Test
    fun `INLINE_SIZE arrives underscored from the enum`() {
        val c = cfg("""["INLINE_SIZE"]""")
        assertTrue("inlineSize", c.inlineSize)
        assertTrue("hasContainment", c.hasContainment)
    }

    @Test
    fun `none as an array yields no containment`() {
        assertEquals(ContainConfig.None, cfg("""["NONE"]"""))
        assertFalse(cfg("""["NONE"]""").hasContainment)
    }

    @Test
    fun `the space-separated string form still expands the same way`() {
        // Defensive shape (hand-authored fixtures / conformance goldens).
        assertTrue(cfg(""""strict"""").isStrict)
        assertEquals(ContainConfig(layout = true, paint = true), cfg(""""layout paint""""))
        assertTrue(cfg(""""inline-size"""").inlineSize)
    }

    @Test
    fun `a non-primitive array element cannot tear down extraction`() {
        // `jsonPrimitive` THROWS on an object element — the exact trap that
        // collapsed every WillChange fixture before wave-19. The valid token
        // beside it must still land.
        val c = cfg("""["LAYOUT",{"type":"auto"}]""")
        assertTrue(c.layout)
    }

    @Test
    fun `an empty or unknown payload yields no containment`() {
        assertEquals(ContainConfig.None, cfg("""[]"""))
        assertEquals(ContainConfig.None, cfg("""["BOGUS"]"""))
        assertEquals(ContainConfig.None, cfg("""123"""))
    }
}
