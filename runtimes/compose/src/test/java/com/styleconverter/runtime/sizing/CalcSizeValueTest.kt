package com.styleconverter.runtime.sizing

// Wave 42 (lane W3) — css-values-5 calc-size() pure-decision pins.
//
// The modifiers (CalcSizeLayout / FitContentSqueeze) need a Compose layout
// pass, which this suite deliberately does not run (no Robolectric) — so
// every decision they make lives in CalcSizeValue / CalcSizeMath /
// FitContentSqueeze.squeezed and is pinned HERE, the same "extract the
// decision, pin it on the JVM" shape as ExactWidthOverflow / IntrinsicChannel
// / FlexAutoMinSize.
//
// The wire payload is VERBATIM converter output (checked against
// `:converter:run` on fixtures/wpt/css-values/calc-size__calc-size-flex-002:
//   {"type":"calc-size","basis":"auto","factor":1,"offsetPx":40,
//    "original":"calc-size(auto, size + 40px)"}
// ) and the arithmetic table is the WPT corpus's own:
//   flex-001  min-width calc-size(auto, size + 20px), content 80   → 100
//   flex-002  min-width calc-size(auto, size + 40px), w60/content80 → 100
//   flex-003  min-width calc-size(auto, size + 40px), w80/content60 → 100
//   flex-009  min-width calc-size(auto, size * 2),    w80/content50 → 100
//   min-max-001 width calc-size(auto, size + 80px), spans min 20    → 100

import androidx.compose.ui.unit.Constraints
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcSizeValueTest {

    // ── wire decode ──────────────────────────────────────────────────────

    @Test
    fun `decodes the verbatim converter wire`() {
        val v = CalcSizeValue.decode(Json.parseToJsonElement(
            """{"type":"calc-size","basis":"auto","factor":1,"offsetPx":40,
                "original":"calc-size(auto, size + 40px)"}"""))
        assertEquals(CalcSizeValue(CalcSizeBasis.AUTO, 1.0, 40.0), v)
    }

    @Test
    fun `decode is additive — every non-calc-size shape refuses`() {
        // The neighbouring sizing shapes must keep their existing routes.
        assertNull(CalcSizeValue.decode(Json.parseToJsonElement("""{"type":"length","px":100}""")))
        assertNull(CalcSizeValue.decode(Json.parseToJsonElement("\"auto\"")))
        assertNull(CalcSizeValue.decode(null))
        // Unknown basis keyword = wire drift from a newer converter — refuse.
        assertNull(CalcSizeValue.decode(Json.parseToJsonElement(
            """{"type":"calc-size","basis":"anchor","factor":1,"offsetPx":0}""")))
        // Discriminator without a basis is malformed — refuse, don't guess.
        assertNull(CalcSizeValue.decode(Json.parseToJsonElement("""{"type":"calc-size"}""")))
    }

    @Test
    fun `factor and offset default to the identity expression`() {
        // Same degradation the web twin applies: `calc-size(auto, size)`.
        val v = CalcSizeValue.decode(Json.parseToJsonElement(
            """{"type":"calc-size","basis":"min-content"}"""))
        assertEquals(CalcSizeValue(CalcSizeBasis.MIN_CONTENT, 1.0, 0.0), v)
    }

    // ── targetPx arithmetic ──────────────────────────────────────────────

    @Test
    fun `targetPx evaluates the corpus affine family`() {
        // flex-001: basis 80, size + 20px → 100.
        assertEquals(100, CalcSizeValue(CalcSizeBasis.AUTO, 1.0, 20.0).targetPx(80))
        // flex-009: basis 50, size * 2 → 100.
        assertEquals(100, CalcSizeValue(CalcSizeBasis.AUTO, 2.0, 0.0).targetPx(50))
        // flex-007: basis 80, size / 2 → 40.
        assertEquals(40, CalcSizeValue(CalcSizeBasis.AUTO, 0.5, 0.0).targetPx(80))
    }

    @Test
    fun `targetPx floors negative results at zero`() {
        // `size - 50px` over a basis smaller than the offset must clamp to 0
        // (css-values-4 §10 non-negative value space) instead of handing a
        // negative px to a Constraints call that would throw.
        //
        // NARRATIVE CORRECTION (wave-42 skeptic S5): an earlier revision of
        // this comment called these two rows "aspect-ratio-003/004 … a no-op
        // floor on currently-PASSING cells". Both halves were wrong, and the
        // measurement says so: 003/004 are NOT passing — they paint at half
        // size against the ref — and this zero-floor is not the branch their
        // corpus geometry takes. Their real path runs the other way: the
        // min lane resolves autoMinBasis(specified = null, contentMin = 150)
        // = 150, and targetPx(150) = 150 − 50 = 100, a POSITIVE result that
        // raises those cells toward the ref rather than floor them. So these
        // two assertions pin the defensive clamp only (a basis at or under
        // the offset), and the cells they used to be credited to are moved
        // by the arithmetic above this floor, not by the floor itself.
        assertEquals(0, CalcSizeValue(CalcSizeBasis.AUTO, 1.0, -50.0).targetPx(0))
        assertEquals(0, CalcSizeValue(CalcSizeBasis.AUTO, 1.0, -50.0).targetPx(30))
        // The corpus path 003/004 actually take, pinned so the correction
        // above stays checkable: basis 150, `size - 50px` → 100, un-floored.
        assertEquals(100, CalcSizeValue(CalcSizeBasis.AUTO, 1.0, -50.0)
            .targetPx(CalcSizeMath.autoMinBasis(null, 150)))
    }

    // ── §4.5 automatic minimum basis (min lane, auto) ────────────────────

    @Test
    fun `autoMinBasis pins the whole flex corpus table`() {
        // flex-001: no specified width, content 80 → 80.
        assertEquals(80, CalcSizeMath.autoMinBasis(null, 80))
        // flex-002: specified 60 beats content 80 → 60.
        assertEquals(60, CalcSizeMath.autoMinBasis(60, 80))
        // flex-003: content 60 beats specified 80 → 60.
        assertEquals(60, CalcSizeMath.autoMinBasis(80, 60))
        // flex-009: content 50 beats specified 80 → 50 (then ×2 → 100).
        assertEquals(50, CalcSizeMath.autoMinBasis(80, 50))
    }

    // ── preferred-lane basis resolution ──────────────────────────────────

    @Test
    fun `preferredBasis resolves auto as the content max-content size`() {
        // An empty div's max-content is 0, so an `auto` basis reads 0 — the
        // css-values-5 §5 rule that `auto` in the preferred lane resolves to
        // the max-content size, with nothing to measure.
        //
        // NARRATIVE CORRECTION (wave-42 skeptic S5): this row used to be
        // annotated "aspect-ratio-001's empty div … +50 = the 50px the
        // pre-typed render already painted — PASS preserved". The measured
        // corpus says otherwise: on aspect-ratio-001/002 the ref paints a
        // 100×100 green square (10000 green px) while BOTH natives paint it
        // half-width (5000 green px); web matches the ref. Those cells are
        // therefore not at ref level, and there is no PASS for this basis
        // arithmetic to preserve. This lane does not move them either —
        // it leaves 001/002 exactly where they were, and the assertion below
        // pins the basis rule itself, not a corpus outcome.
        assertEquals(0, CalcSizeMath.preferredBasis(CalcSizeBasis.AUTO, 0, 0, 358))
        // min-max-002's span flow: min 20 / max 40 → auto reads 40.
        assertEquals(40, CalcSizeMath.preferredBasis(CalcSizeBasis.AUTO, 20, 40, 200))
    }

    @Test
    fun `preferredBasis fit-content clamps between the intrinsics`() {
        // min-max-004: avail 0 → max(20, min(0, 40)) = 20 (+80 → the ref's
        // 100px square).
        assertEquals(20, CalcSizeMath.preferredBasis(CalcSizeBasis.FIT_CONTENT, 20, 40, 0))
        // min-max-005: avail 200 → max(20, min(200, 40)) = 40 (+60 → 100).
        assertEquals(40, CalcSizeMath.preferredBasis(CalcSizeBasis.FIT_CONTENT, 20, 40, 200))
        // Unbounded avail degrades to max-content per §5.1 shrink-to-fit.
        assertEquals(40, CalcSizeMath.preferredBasis(
            CalcSizeBasis.FIT_CONTENT, 20, 40, Constraints.Infinity))
    }

    @Test
    fun `preferredBasis propagates a refused channel as null`() {
        // A refusing subtree must fall back atomically — never half a basis.
        assertNull(CalcSizeMath.preferredBasis(CalcSizeBasis.AUTO, 20, null, 100))
        assertNull(CalcSizeMath.preferredBasis(CalcSizeBasis.FIT_CONTENT, null, 40, 100))
    }

    // ── intrinsic contribution substitution (css-values-5) ───────────────

    @Test
    fun `intrinsicBasis substitutes the queried intrinsic on contribution bases`() {
        // min-max-001's inner div (spans min 20 / max 40, auto basis):
        // min-content contribution reads 20, max-content reads 40 — what
        // makes the fit-content PARENT see 100/120 after +80.
        assertEquals(20, CalcSizeMath.intrinsicBasis(CalcSizeBasis.AUTO, true, 20, 40))
        assertEquals(40, CalcSizeMath.intrinsicBasis(CalcSizeBasis.AUTO, false, 20, 40))
        assertEquals(20, CalcSizeMath.intrinsicBasis(CalcSizeBasis.FIT_CONTENT, true, 20, 40))
    }

    @Test
    fun `intrinsicBasis keeps fixed-kind bases fixed`() {
        // A min-content BASIS answers the min intrinsic to BOTH queries —
        // the keyword names a size, not a contribution.
        assertEquals(20, CalcSizeMath.intrinsicBasis(CalcSizeBasis.MIN_CONTENT, false, 20, 40))
        assertEquals(40, CalcSizeMath.intrinsicBasis(CalcSizeBasis.MAX_CONTENT, true, 20, 40))
    }

    // ── min-lane floor band (§4.5 overflow-not-collapse) ─────────────────

    @Test
    fun `floorBand raises a squeezing band to the floor`() {
        // flex-001's item: container hands (0..0), floor 100 → (100..100):
        // the item overflows instead of collapsing to nothing.
        assertEquals(100 to 100, CalcSizeMath.floorBand(0, 0, 100))
    }

    @Test
    fun `floorBand leaves a roomy band's ceiling alone`() {
        // A 358-wide container with a 100 floor keeps its ceiling — content
        // larger than the floor may still size itself.
        assertEquals(100 to 358, CalcSizeMath.floorBand(0, 358, 100))
    }

    @Test
    fun `floorBand preserves an unbounded ceiling`() {
        // Infinity must not be folded into maxOf arithmetic.
        assertEquals(100 to Constraints.Infinity,
            CalcSizeMath.floorBand(0, Constraints.Infinity, 100))
    }

    // ── fit-content squeeze guard decision ───────────────────────────────

    @Test
    fun `squeezed only under a bounded max below the content minimum`() {
        // min-max-001's green box: avail 0, content min 100 → squeeze.
        assertTrue(FitContentSqueeze.squeezed(0, 100))
        // min-max-002's green box: avail 200, content min 100 → pass-through
        // (the path every existing fit-content capture keeps).
        assertFalse(FitContentSqueeze.squeezed(200, 100))
        // Unbounded avail can never squeeze.
        assertFalse(FitContentSqueeze.squeezed(Constraints.Infinity, 100))
    }
}
