package com.styleconverter.runtime.animations

// Pinning for the transition driver's PURE core (spec 07 §4): which
// wave-7 bucket flips start flights, and what value a flight presents at
// each clock instant. Scenario shapes mirror
// fixtures/fidelity/motion/transitions.json (MT_BgFade / MT_WidthGrow /
// MT_AllChannels / MT_Delayed).

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionDriverTest {

    private val EPS = 1e-9
    private fun el(s: String) = Json.parseToJsonElement(s)

    private fun linearConfig(
        properties: List<String>,
        durationMs: Long = 1000L,
        delayMs: Long = 0L
    ) = TransitionConfig(
        properties = properties,
        durations = listOf(durationMs),
        delays = if (delayMs != 0L) listOf(delayMs) else emptyList(),
        timingFunctions = listOf(TimingFunctionConfig.LINEAR),
        hasTransitions = true
    )

    // ── wire-shape extraction (TransitionPropertyProperty variants) ─────

    @Test
    fun `transition-property extracts the class-discriminated wire shape`() {
        // VERBATIM converter output for MT_Delayed (motion/transitions.json):
        // sealed-variant tag "property-name" + the name field.
        val config = AnimationExtractor.extractTransitionConfig(listOf(
            "TransitionProperty" to el("""[{"type":"property-name","name":"background-color"}]"""),
            "TransitionDuration" to el("""[{"ms":1000.0,"original":{"v":1.0,"u":"S"}}]""")
        ))
        assertEquals(listOf("background-color"), config.properties)
        assertTrue(TransitionDriver.covers(config, "background-color"))
        org.junit.Assert.assertFalse(TransitionDriver.covers(config, "width"))
        // The `all` keyword variant (MT_AllChannels).
        val all = AnimationExtractor.extractTransitionConfig(listOf(
            "TransitionProperty" to el("""[{"type":"all","unit":{}}]""")
        ))
        assertTrue(TransitionDriver.covers(all, "opacity"))
    }

    @Test
    fun `transition-property none disables every transition`() {
        val none = AnimationExtractor.extractTransitionConfig(listOf(
            "TransitionProperty" to el("""[{"type":"none","unit":{}}]"""),
            "TransitionDuration" to el("""[{"ms":1000.0}]""")
        ))
        org.junit.Assert.assertFalse(TransitionDriver.covers(none, "width"))
        org.junit.Assert.assertFalse(TransitionDriver.covers(none, "background-color"))
    }

    // ── coverage matching (transition-property list semantics) ──────────

    @Test
    fun `covers matches explicit names and the all keyword`() {
        assertTrue(TransitionDriver.covers(linearConfig(listOf("background-color")), "background-color"))
        assertTrue(TransitionDriver.covers(linearConfig(listOf("all")), "width"))
        // Bare durations with no transition-property = shorthand all.
        assertTrue(TransitionDriver.covers(linearConfig(emptyList()), "opacity"))
        org.junit.Assert.assertFalse(
            TransitionDriver.covers(linearConfig(listOf("width")), "background-color"))
    }

    // ── flight start on a bucket flip (MT_WidthGrow :active) ────────────

    @Test
    fun `a covered changed property starts one flight`() {
        val before = listOf(IRProperty("Width", el("""{"type":"length","px":80.0}""")))
        val after = listOf(IRProperty("Width", el("""{"type":"length","px":160.0}""")))
        val flights = TransitionDriver.startFlights(before, after, linearConfig(listOf("width")), 0.0)
        assertEquals(1, flights.size)
        assertEquals("Width", flights[0].type)
    }

    @Test
    fun `uncovered and unchanged properties never fly`() {
        val before = listOf(
            IRProperty("Width", el("""{"type":"length","px":80.0}""")),
            IRProperty("Height", el("""{"type":"length","px":40.0}"""))
        )
        val after = listOf(
            IRProperty("Width", el("""{"type":"length","px":160.0}""")), // changed, NOT covered
            IRProperty("Height", el("""{"type":"length","px":40.0}"""))  // covered, unchanged
        )
        val flights = TransitionDriver.startFlights(
            before, after, linearConfig(listOf("height")), 0.0)
        assertTrue(flights.isEmpty())
    }

    // ── presented values along the clock (MT_BgFade / MT_Delayed) ───────

    @Test
    fun `flight presents old during delay, lerp mid-flight, target after`() {
        val before = listOf(IRProperty("BackgroundColor", el("""{"srgb":{"r":0.0,"g":0.0,"b":0.0}}""")))
        val after = listOf(IRProperty("BackgroundColor", el("""{"srgb":{"r":1.0,"g":1.0,"b":1.0}}""")))
        // MT_Delayed shape: 1s duration + 0.5s delay.
        val config = linearConfig(listOf("background-color"), delayMs = 500L)
        val flight = TransitionDriver.startFlights(before, after, config, 0.0).single()

        fun rAt(nowMs: Double): Double {
            val v = TransitionDriver.presentedValue(flight, nowMs) as JsonObject
            return v["srgb"]!!.jsonObject["r"]!!.jsonPrimitive.doubleOrNull!!
        }
        assertEquals(0.0, rAt(250.0), EPS)   // in the delay window → old
        assertEquals(0.25, rAt(750.0), EPS)  // 0.25 into the run
        assertEquals(1.0, rAt(1500.0), EPS)  // completed → target
        assertEquals(1.0, rAt(9999.0), EPS)  // stays at target
    }

    @Test
    fun `presented overlays only in-flight types onto the target list`() {
        val before = listOf(
            IRProperty("Width", el("""{"type":"length","px":80.0}""")),
            IRProperty("BackgroundColor", el("""{"srgb":{"r":0.0,"g":0.0,"b":0.0}}"""))
        )
        val after = listOf(
            IRProperty("Width", el("""{"type":"length","px":160.0}""")),
            IRProperty("BackgroundColor", el("""{"srgb":{"r":0.0,"g":0.0,"b":0.0}}"""))
        )
        val config = linearConfig(listOf("all"))
        val flights = TransitionDriver.startFlights(before, after, config, 0.0)
        val mid = TransitionDriver.presented(after, flights, 500.0)
        val px = (mid.first { it.type == "Width" }.data as JsonObject)["px"]!!
            .jsonPrimitive.doubleOrNull!!
        assertEquals(120.0, px, EPS)
        // The unchanged color kept its exact target payload.
        assertEquals(after[1].data, mid.first { it.type == "BackgroundColor" }.data)
    }

    @Test
    fun `zero duration transitions switch instantly - no flight`() {
        val before = listOf(IRProperty("Opacity", el("""{"alpha":1.0}""")))
        val after = listOf(IRProperty("Opacity", el("""{"alpha":0.4}""")))
        val flights = TransitionDriver.startFlights(
            before, after, linearConfig(listOf("opacity"), durationMs = 0L), 0.0)
        assertTrue(flights.isEmpty())
    }
}
