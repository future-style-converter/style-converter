package com.styleconverter.runtime.animations

// Cross-runtime easing conformance (Wave 2).
//
// An animation is a function from time to a value, and most of what can go
// wrong in that function — a wrong curve, a wrong step boundary, a wrong
// endpoint — is pure arithmetic. This test checks that arithmetic against
// schema/conformance/easing/easing-reference.json, the SAME table the SwiftUI
// and web suites assert against, so a divergence is attributable to a named
// easing function at a named input with no emulator, no capture and no SSIM.
//
// The reference is generated from css-easing-1 by
// schema/conformance/easing/gen-easing-reference.mjs — deliberately NOT by
// running any runtime, because a table generated from an implementation would
// enshrine that implementation's bugs as the standard.

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class EasingReferenceTest {

    // ── Locating the shared table ───────────────────────────────────────────
    // The compose runtime is consumed as `:runtime` by apps/android-harness,
    // so the JVM working directory during a unit-test run depends on which
    // Gradle build invoked it. Walk up to the repo root instead of hardcoding
    // a relative depth that silently breaks when the invoker changes.
    private fun referenceFile(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val candidate = File(dir, "schema/conformance/easing/easing-reference.json")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        fail("easing-reference.json not found walking up from ${System.getProperty("user.dir")}")
        error("unreachable")
    }

    private val table: JsonObject by lazy {
        Json.parseToJsonElement(referenceFile().readText()).jsonObject
    }

    private val cases get() = table["cases"]!!.jsonArray.map { it.jsonObject }
    private val bezierTolerance
        get() = table["tolerance"]!!.jsonObject["cubicBezier"]!!.jsonPrimitive.content.toDouble()
    private val exactTolerance
        get() = table["tolerance"]!!.jsonObject["stepsAndLinear"]!!.jsonPrimitive.content.toDouble()

    /** Build the runtime's config object for one reference case. */
    private fun configFor(case: JsonObject): TimingFunctionConfig? = when (
        case["kind"]!!.jsonPrimitive.content
    ) {
        "cubic-bezier" -> {
            val cb = case["cubicBezier"]!!.jsonObject
            TimingFunctionConfig(
                cubicBezier = listOf("x1", "y1", "x2", "y2").map { cb[it]!!.jsonPrimitive.content.toDouble() },
                stepsCount = null,
                stepsPosition = null,
                original = case["css"]!!.jsonPrimitive.content,
            )
        }
        "steps" -> {
            val st = case["steps"]!!.jsonObject
            TimingFunctionConfig(
                cubicBezier = null,
                stepsCount = st["count"]!!.jsonPrimitive.content.toInt(),
                stepsPosition = st["position"]!!.jsonPrimitive.content,
                original = case["css"]!!.jsonPrimitive.content,
            )
        }
        // linear() is not representable: TimingFunctionConfig has no
        // linearStops field, so the runtime cannot carry the stop list at
        // all. Reported explicitly by the coverage test below rather than
        // silently skipped — the repo's no-silent-fallthrough rule applies
        // to tests as much as to appliers.
        else -> null
    }

    // ── The table itself must be intact ─────────────────────────────────────

    @Test
    fun `reference table loads and is non-trivial`() {
        assertEquals(1, table["version"]!!.jsonPrimitive.content.toInt())
        assertTrue("expected a substantial case list", cases.size >= 30)
        val samples = cases.sumOf { it["samples"]!!.jsonArray.size }
        assertEquals(
            table["sampleCount"]!!.jsonPrimitive.content.toInt(), samples
        )
    }

    @Test
    fun `keyword control points match the CSS spec constants`() {
        // css-easing-1 §3.1. If the runtime's companion constants drift from
        // the spec, every curve built from a keyword is quietly wrong — and
        // no pixel comparison would attribute it to the keyword mapping.
        val expected = mapOf(
            "keyword-linear" to TimingFunctionConfig.LINEAR,
            "keyword-ease" to TimingFunctionConfig.EASE,
            "keyword-ease-in" to TimingFunctionConfig.EASE_IN,
            "keyword-ease-out" to TimingFunctionConfig.EASE_OUT,
            "keyword-ease-in-out" to TimingFunctionConfig.EASE_IN_OUT,
        )
        for ((id, config) in expected) {
            val case = cases.first { it["id"]!!.jsonPrimitive.content == id }
            val cb = case["cubicBezier"]!!.jsonObject
            val fromSpec = listOf("x1", "y1", "x2", "y2").map { cb[it]!!.jsonPrimitive.content.toDouble() }
            assertEquals("$id control points", fromSpec, config.cubicBezier)
        }
    }

    // ── The conformance sweep ───────────────────────────────────────────────

    @Test
    fun `every representable easing case matches the reference`() {
        val failures = mutableListOf<String>()
        var checked = 0

        for (case in cases) {
            val config = configFor(case) ?: continue
            val id = case["id"]!!.jsonPrimitive.content
            val kind = case["kind"]!!.jsonPrimitive.content
            // steps() and linear() are exact rational arithmetic; only the
            // bezier solver is allowed float slack (Compose evaluates through
            // androidx CubicBezierEasing in Float).
            val tol = if (kind == "cubic-bezier") bezierTolerance else exactTolerance

            for (sample in case["samples"]!!.jsonArray.map { it.jsonObject }) {
                val t = sample["t"]!!.jsonPrimitive.content.toDouble()
                val expected = sample["expected"]!!.jsonPrimitive.content.toDouble()
                val actual = KeyframeTimeline.ease(config, t)
                checked += 1
                if (kotlin.math.abs(actual - expected) > tol) {
                    failures += "$id  t=$t  expected=$expected  actual=$actual  (Δ=${actual - expected}, tol=$tol)"
                }
            }
        }

        assertTrue("expected to check a meaningful number of samples, got $checked", checked > 300)
        if (failures.isNotEmpty()) {
            fail(
                "${failures.size} of $checked easing samples diverge from " +
                    "schema/conformance/easing/easing-reference.json:\n" +
                    failures.joinToString("\n").take(4000)
            )
        }
    }

    // ── Honest coverage reporting ───────────────────────────────────────────

    @Test
    fun `unrepresentable cases are named, not silently skipped`() {
        // A conformance suite that quietly drops what it cannot express is
        // indistinguishable from one that passes. Assert the gap explicitly
        // so it shows up as a maintained fact rather than an absence.
        val skipped = cases.filter { configFor(it) == null }.map { it["id"]!!.jsonPrimitive.content }
        assertEquals(
            "Compose's TimingFunctionConfig has no linearStops field, so linear() easing " +
                "cannot be represented and falls through to the `ease` default. If linear() " +
                "support lands, delete this expectation — do not widen it.",
            listOf(
                "linear-identity", "linear-three-even", "linear-positioned", "linear-nonmonotonic"
            ),
            skipped,
        )
    }
}
