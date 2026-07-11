package com.styleconverter.runtime.core.media

// Wave-7 pinning tests for the dynamic-styling runtime v1 media evaluation
// (schema/spec/06-dynamic-styling.md §4):
//   - min/max-width compare against the RENDER-SURFACE width the caller
//     supplies (the capture canvas: 390 default / 250 second run —
//     docs/DYNAMIC_CAPTURE.md §2), boundaries inclusive per
//     mediaqueries-5 §4.2,
//   - prefers-color-scheme maps to the platform dark-mode flag,
//   - the full 390/250 truth table of fixtures/fidelity/dynamic/
//     media-width.json,
//   - EVERYTHING outside the v1 grammar is conservatively inactive.

import com.styleconverter.runtime.core.ir.IRMedia
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaBucketEvaluatorTest {

    // Evaluate at the default capture width, light scheme.
    private fun at390(q: String) = MediaBucketEvaluator.evaluate(q, 390f, isDarkScheme = false)

    // Evaluate at the second capture width, light scheme.
    private fun at250(q: String) = MediaBucketEvaluator.evaluate(q, 250f, isDarkScheme = false)

    // ── width features against the render surface ───────────────────────

    @Test
    fun `min-width boundary is inclusive - 390px matches a 390px surface`() {
        // mediaqueries-5 §4.2 pin, quoted verbatim in spec 06 §4.
        assertTrue(at390("(min-width: 390px)"))
        assertFalse(at250("(min-width: 390px)"))
        // max-width is inclusive too.
        assertTrue(MediaBucketEvaluator.evaluate("(max-width: 390px)", 390f, false))
    }

    @Test
    fun `the media-width fixture truth table holds at 390 and 250`() {
        // docs/DYNAMIC_CAPTURE.md §2 — the two-width design of
        // fixtures/fidelity/dynamic/media-width.json:
        // 390: min 200 / max 500 / min 300 match; min 500 / max 300 do not.
        assertTrue(at390("(min-width: 200px)"))
        assertTrue(at390("(max-width: 500px)"))
        assertTrue(at390("(min-width: 300px)"))
        assertFalse(at390("(min-width: 500px)"))
        assertFalse(at390("(max-width: 300px)"))
        // 250: max 300 flips ON, min 300 flips OFF; the rest keep answers.
        assertTrue(at250("(min-width: 200px)"))
        assertTrue(at250("(max-width: 500px)"))
        assertFalse(at250("(min-width: 300px)"))
        assertFalse(at250("(min-width: 500px)"))
        assertTrue(at250("(max-width: 300px)"))
    }

    @Test
    fun `evaluation uses the surface width the caller supplies - not a device constant`() {
        // The render-surface semantics pin (spec 06 §4: "never the device
        // screen width"): the SAME query answers differently purely as a
        // function of the supplied surface width.
        val q = "(min-width: 500px)"
        assertFalse(MediaBucketEvaluator.evaluate(q, 390f, false))  // capture canvas
        assertTrue(MediaBucketEvaluator.evaluate(q, 1080f, false))  // a hypothetical device width
    }

    // ── conjunctions ─────────────────────────────────────────────────────

    @Test
    fun `and-conjunctions require every term to hold`() {
        assertTrue(at390("(min-width: 200px) and (max-width: 500px)"))
        assertFalse(at390("(min-width: 200px) and (max-width: 300px)"))
        // Three terms, mixing width and scheme.
        assertTrue(
            MediaBucketEvaluator.evaluate(
                "(min-width: 200px) and (max-width: 500px) and (prefers-color-scheme: dark)",
                390f, isDarkScheme = true
            )
        )
    }

    // ── prefers-color-scheme ─────────────────────────────────────────────

    @Test
    fun `prefers-color-scheme maps to the platform dark-mode flag`() {
        // Dark bucket: active only when the platform reports dark.
        assertTrue(MediaBucketEvaluator.evaluate("(prefers-color-scheme: dark)", 390f, true))
        assertFalse(MediaBucketEvaluator.evaluate("(prefers-color-scheme: dark)", 390f, false))
        // Light bucket: the inverse.
        assertTrue(MediaBucketEvaluator.evaluate("(prefers-color-scheme: light)", 390f, false))
        assertFalse(MediaBucketEvaluator.evaluate("(prefers-color-scheme: light)", 390f, true))
    }

    @Test
    fun `dark buckets must not apply in the default light capture environment`() {
        // docs/DYNAMIC_CAPTURE.md §3: the standard run is LIGHT scheme —
        // dark-mode.json's buckets must stay inactive there.
        assertFalse(at390("(prefers-color-scheme: dark)"))
    }

    // ── conservative inactivity (the no-apply-by-guess rule) ────────────

    @Test
    fun `everything outside the v1 grammar is conservatively inactive`() {
        // not/only prefixes.
        assertFalse(at390("not (min-width: 200px)"))
        assertFalse(at390("only screen and (min-width: 200px)"))
        // Media-type prefixes (v1 grammar is bare feature terms only).
        assertFalse(at390("screen and (min-width: 200px)"))
        // Comma-separated query lists.
        assertFalse(at390("(min-width: 200px), (max-width: 500px)"))
        // Range syntax.
        assertFalse(at390("(200px <= width)"))
        assertFalse(at390("(width >= 200px)"))
        // Features outside v1 — even ones the surface would trivially pass.
        assertFalse(at390("(orientation: portrait)"))
        assertFalse(at390("(hover: hover)"))
        assertFalse(at390("(min-height: 100px)"))
        // Non-px width units (spec 06 §4: px only at v1).
        assertFalse(at390("(min-width: 20em)"))
        assertFalse(at390("(min-width: 50%)"))
        // Malformed strings never crash, never apply.
        assertFalse(at390("(min-width 200px)"))
        assertFalse(at390("min-width: 200px"))
        assertFalse(at390(""))
    }

    @Test
    fun `an unsupported term poisons the whole conjunction`() {
        // spec 06 §4: "the WHOLE bucket is conservatively inactive" — a
        // supported passing term cannot rescue an unsupported sibling.
        assertFalse(at390("(min-width: 200px) and (orientation: portrait)"))
    }

    // ── tolerance within the grammar ─────────────────────────────────────

    @Test
    fun `case and whitespace variance inside the v1 grammar is tolerated`() {
        assertTrue(at390("( MIN-WIDTH : 200px )"))
        assertTrue(at390("(min-width:200px)"))
        assertTrue(at390("(min-width: 200px)  AND  (max-width: 500px)"))
    }

    // ── bucket filtering ─────────────────────────────────────────────────

    @Test
    fun `activeBuckets filters by activity and preserves array order`() {
        val p = IRProperty("BackgroundColor", JsonPrimitive("x"))
        val media = listOf(
            IRMedia("(min-width: 200px)", listOf(p)),   // active at 390
            IRMedia("(min-width: 500px)", listOf(p)),   // inactive at 390
            IRMedia("(max-width: 500px)", listOf(p)),   // active at 390
            IRMedia("(orientation: portrait)", listOf(p)) // unsupported → inactive
        )
        val active = MediaBucketEvaluator.activeBuckets(media, 390f, false)
        // Wire array order preserved — §3 layering depends on it.
        assertEquals(listOf("(min-width: 200px)", "(max-width: 500px)"), active.map { it.query })
    }
}
