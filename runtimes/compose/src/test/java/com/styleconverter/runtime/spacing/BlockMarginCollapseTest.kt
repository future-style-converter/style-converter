package com.styleconverter.runtime.spacing

// Pinning suite for the CSS2 §8.3.1 margin-collapse emulation
// (BlockMarginCollapse): the sibling max() rule, the edge-hoist math and
// its parent-own-margin composition, the value-flavor eligibility guards,
// the MarginConfig override substitution, and the hoist gates evaluated
// against VERBATIM live-converter wire shapes (`--to ir` on
// fixtures/properties/spacing/margin-trim.json — wave-5 lesson: pin to
// live output, never assumed shapes).

import com.styleconverter.runtime.core.types.LengthUnit
import com.styleconverter.runtime.core.types.LengthValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockMarginCollapseTest {

    // Shorthand: parse a JSON literal into the wire JsonElement shape.
    private fun j(json: String): JsonElement = Json.parseToJsonElement(json)

    // Shorthand: an exact-px block-axis margin pair.
    private fun m(top: Float, bottom: Float) = CollapsedMargin(top, bottom)

    // Open gates — the fixture parent's padding-0/border-0/height-auto case.
    private val openGates = BlockMarginCollapse.HoistGates(top = true, bottom = true)

    // ── computePlan: the §8.3.1 max rule + hoisting ─────────────────────

    /** The campaign fixture: three 20px-margined siblings, hoisting parent.
     *  Web (Chromium probe): parent 300x160, bars flush at y 0/60/120 inside
     *  the parent, 20px escaped above AND below the parent box. */
    @Test
    fun `fixture geometry - three siblings collapse to 160px with 20px hoists`() {
        val plan = BlockMarginCollapse.computePlan(
            children = listOf(m(20f, 20f), m(20f, 20f), m(20f, 20f)),
            parentOwn = m(0f, 0f),
            gates = openGates,
        )
        // Edge margins hoist entirely (parent own margins are 0).
        assertEquals(20f, plan.hoistTopPx, 0.001f)
        assertEquals(20f, plan.hoistBottomPx, 0.001f)
        // First child flush; interior gaps carry max(20,20)=20 exactly once.
        assertEquals(listOf(m(0f, 0f), m(20f, 0f), m(20f, 0f)), plan.perChild)
        // Geometry pin: with 40px-tall children, in-parent y offsets are
        // 0/60/120 and the parent content height sums to 160 — web's box.
        var y = 0f
        val offsets = plan.perChild.map { c -> (y + c.topPx).also { y = it + 40f + c.bottomPx } }
        assertEquals(listOf(0f, 60f, 120f), offsets)
        assertEquals(160f, y, 0.001f)
    }

    /** §8.3.1: adjoining sibling margins collapse to the MAX, not the sum. */
    @Test
    fun `asymmetric sibling margins take the max`() {
        val plan = BlockMarginCollapse.computePlan(
            children = listOf(m(10f, 30f), m(20f, 5f)),
            parentOwn = m(0f, 0f),
            gates = BlockMarginCollapse.HoistGates(top = false, bottom = false),
        )
        // Closed gates: first keeps its own top, last keeps its own bottom.
        // The interior gap is max(prev bottom 30, own top 20) = 30.
        assertEquals(listOf(m(10f, 0f), m(30f, 5f)), plan.perChild)
        // Nothing hoists through closed gates.
        assertEquals(0f, plan.hoistTopPx, 0.001f)
        assertEquals(0f, plan.hoistBottomPx, 0.001f)
    }

    /** A closed top gate (padded/bordered parent) keeps the first child's
     *  margin INSIDE the parent — the pre-collapse behavior for that edge. */
    @Test
    fun `closed top gate keeps first margin inside`() {
        val plan = BlockMarginCollapse.computePlan(
            children = listOf(m(20f, 20f), m(20f, 20f)),
            parentOwn = m(0f, 0f),
            gates = BlockMarginCollapse.HoistGates(top = false, bottom = true),
        )
        // First child's 20px stays applied; sibling gap still collapses.
        assertEquals(listOf(m(20f, 0f), m(20f, 0f)), plan.perChild)
        assertEquals(0f, plan.hoistTopPx, 0.001f)
        // Bottom edge still hoists.
        assertEquals(20f, plan.hoistBottomPx, 0.001f)
    }

    /** §8.3.1 collapse-through composes with the parent's OWN margin via
     *  max(): only the excess beyond the parent margin is hoisted (the
     *  parent's MarginApplier already renders its own share). */
    @Test
    fun `hoist is max-composed with the parent own margin`() {
        val plan = BlockMarginCollapse.computePlan(
            children = listOf(m(20f, 20f)),
            parentOwn = m(30f, 10f),
            gates = openGates,
        )
        // Top: max(30, 20) = 30 — fully covered by the parent's own margin.
        assertEquals(0f, plan.hoistTopPx, 0.001f)
        // Bottom: max(10, 20) = 20 → 10px excess hoists.
        assertEquals(10f, plan.hoistBottomPx, 0.001f)
        // The single child's margins are hoisted on both edges.
        assertEquals(listOf(m(0f, 0f)), plan.perChild)
    }

    // ── blockMarginsOrNull: value-flavor eligibility ─────────────────────

    /** Plain exact px margins (the fixture wire {"px":20.0}) are in scope;
     *  unset sides read as the CSS initial 0. */
    @Test
    fun `exact px margins resolve and unset sides are zero`() {
        val cfg = MarginConfig(
            top = MarginValue.Length(LengthValue.Exact(20.0)),
            // bottom deliberately unset → 0.
        )
        assertEquals(m(20f, 0f), BlockMarginCollapse.blockMarginsOrNull(cfg))
    }

    /** Logical block sides feed the physical axis (MarginConfig.resolve). */
    @Test
    fun `logical block margins resolve to the physical axis`() {
        val cfg = MarginConfig(
            blockStart = MarginValue.Length(LengthValue.Exact(12.0)),
            blockEnd = MarginValue.Length(LengthValue.Exact(8.0)),
        )
        assertEquals(m(12f, 8f), BlockMarginCollapse.blockMarginsOrNull(cfg))
    }

    /** Auto in the block axis bails (MarginApplier's centering path owns it). */
    @Test
    fun `auto block margin is out of scope`() {
        assertNull(BlockMarginCollapse.blockMarginsOrNull(MarginConfig(top = MarginValue.Auto)))
    }

    /** Negative margins bail — §8.3.1's negative rules are not emulated. */
    @Test
    fun `negative margin is out of scope`() {
        assertNull(
            BlockMarginCollapse.blockMarginsOrNull(
                MarginConfig(bottom = MarginValue.Length(LengthValue.Exact(-10.0)))
            )
        )
    }

    /** Relative and calc margins need live context a pure plan can't see. */
    @Test
    fun `relative and calc margins are out of scope`() {
        assertNull(
            BlockMarginCollapse.blockMarginsOrNull(
                MarginConfig(top = MarginValue.Length(LengthValue.Relative(2.0, LengthUnit.EM, null)))
            )
        )
        assertNull(
            BlockMarginCollapse.blockMarginsOrNull(
                MarginConfig(top = MarginValue.Length(LengthValue.Calc("calc(10px + 2em)")))
            )
        )
    }

    /** wave-45 lane X4 — an EM margin CARRYING a prebaked pxFallback (the
     *  shape MarginExtractor now emits for DynamicValueResolver-prebaked
     *  em) classifies through that fallback, keeping every §8.3.1 plan
     *  value byte-identical to the pre-wave-45 Exact(prebake) reading. */
    @Test
    fun `em with a prebaked fallback classifies via the fallback px`() {
        val cfg = MarginConfig(
            top = MarginValue.Length(LengthValue.Relative(1.0, LengthUnit.EM, 16.0)),
            bottom = MarginValue.Length(LengthValue.Relative(0.5, LengthUnit.EM, 46.0)),
        )
        assertEquals(m(16f, 46f), BlockMarginCollapse.blockMarginsOrNull(cfg))
    }

    /** Same non-negativity floor as the Exact lane (§8.3.1 negative rules
     *  are not emulated), and non-EM relatives never ride the fallback. */
    @Test
    fun `fallback lane keeps the negative floor and the EM-only gate`() {
        // Negative prebake bails — identical to Exact(-10) bailing.
        assertNull(
            BlockMarginCollapse.blockMarginsOrNull(
                MarginConfig(top = MarginValue.Length(LengthValue.Relative(-1.0, LengthUnit.EM, -16.0)))
            )
        )
        // A %-flavored fallback carrier stays out of scope (its base is
        // the containing block, never a font size — the fallback shortcut
        // is an EM-lane contract only).
        assertNull(
            BlockMarginCollapse.blockMarginsOrNull(
                MarginConfig(top = MarginValue.Length(LengthValue.Relative(10.0, LengthUnit.PERCENT, 39.0)))
            )
        )
    }

    /** An all-null config (no margin declared) is a valid zero pair — the
     *  plan builder separately skips all-zero containers. */
    @Test
    fun `empty config is a zero pair`() {
        assertEquals(m(0f, 0f), BlockMarginCollapse.blockMarginsOrNull(MarginConfig()))
    }

    // ── applyOverride: the MarginConfig substitution ─────────────────────

    /** The override replaces the block axis, clears the logical block sides
     *  (resolve() would otherwise never see them anyway), and leaves the
     *  inline axis untouched (§8.3.1 is vertical-only). */
    @Test
    fun `override replaces block axis and preserves inline axis`() {
        val original = MarginConfig(
            top = MarginValue.Length(LengthValue.Exact(20.0)),
            bottom = MarginValue.Length(LengthValue.Exact(20.0)),
            left = MarginValue.Length(LengthValue.Exact(20.0)),
            right = MarginValue.Auto,
            blockStart = MarginValue.Length(LengthValue.Exact(99.0)),
        )
        val out = BlockMarginCollapse.applyOverride(original, m(0f, 7f))
        assertEquals(MarginValue.Length(LengthValue.Exact(0.0)), out.top)
        assertEquals(MarginValue.Length(LengthValue.Exact(7.0)), out.bottom)
        assertNull(out.blockStart)
        assertNull(out.blockEnd)
        // Inline sides — including a horizontal auto — pass through.
        assertEquals(MarginValue.Length(LengthValue.Exact(20.0)), out.left)
        assertEquals(MarginValue.Auto, out.right)
    }

    /** The override can INTRODUCE margin on a margin-less child (a sibling's
     *  bottom margin carried as this child's applied top) — hasMargin must
     *  flip true so MarginApplier doesn't early-return. */
    @Test
    fun `override on a margin-less config still has margin`() {
        val out = BlockMarginCollapse.applyOverride(MarginConfig(), m(20f, 0f))
        assertTrue(out.hasMargin)
    }

    // ── hoistGates: §8.3.1 adjoining conditions from live wire pairs ─────

    // The MarginTrim_None parent's pairs, verbatim from the live converter
    // (padding: 0 emits explicit {"px":0.0} per side — the gate must treat
    // present-but-zero as open, which is why the check is value-based).
    private fun fixtureParentPairs(): List<Pair<String, JsonElement?>> = listOf(
        "Width" to j("""{"type":"length","px":300.0}"""),
        "MarginTrim" to j("\"NONE\""),
        "PaddingTop" to j("""{"px":0.0}"""),
        "PaddingRight" to j("""{"px":0.0}"""),
        "PaddingBottom" to j("""{"px":0.0}"""),
        "PaddingLeft" to j("""{"px":0.0}"""),
        "BackgroundColor" to j("""{"srgb":{"r":0.2,"g":0.2,"b":0.2},"original":"#333333"}"""),
    )

    /** The fixture parent (explicit zero padding, no border, auto height,
     *  visible overflow) opens both gates — Width does NOT pin the bottom. */
    @Test
    fun `fixture parent opens both gates`() {
        assertEquals(openGates, BlockMarginCollapse.hoistGates(fixtureParentPairs()))
    }

    /** §8.3.1: top padding separates the parent/first-child margins. */
    @Test
    fun `top padding closes only the top gate`() {
        val gates = BlockMarginCollapse.hoistGates(
            fixtureParentPairs().map { (t, d) ->
                if (t == "PaddingTop") t to j("""{"px":8.0}""") else t to d
            }
        )
        assertFalse(gates.top)
        assertTrue(gates.bottom)
    }

    /** §8.3.1: a top border separates the margins; radius corners do not
     *  (BorderTopLeftRadius shares the BorderTop prefix but adds no band). */
    @Test
    fun `top border closes the top gate but radius does not`() {
        val bordered = BlockMarginCollapse.hoistGates(
            fixtureParentPairs() + ("BorderTopWidth" to j("""{"px":2.0}"""))
        )
        assertFalse(bordered.top)
        assertTrue(bordered.bottom)
        val rounded = BlockMarginCollapse.hoistGates(
            fixtureParentPairs() + ("BorderTopLeftRadius" to j("""{"px":8.0}"""))
        )
        assertTrue(rounded.top)
    }

    /** An all-side border shorthand family entry closes both gates. */
    @Test
    fun `all-side border closes both gates`() {
        val gates = BlockMarginCollapse.hoistGates(
            fixtureParentPairs() + ("BorderWidth" to j("""{"px":1.0}"""))
        )
        assertFalse(gates.top)
        assertFalse(gates.bottom)
    }

    /** §8.3.1: bottom collapse-through needs height:auto — an explicit
     *  Height pins the bottom edge; the top edge is unaffected. */
    @Test
    fun `explicit height closes only the bottom gate`() {
        val gates = BlockMarginCollapse.hoistGates(
            fixtureParentPairs() + ("Height" to j("""{"type":"length","px":100.0}"""))
        )
        assertTrue(gates.top)
        assertFalse(gates.bottom)
    }

    /** CSS2 §9.4.1: non-visible overflow makes the parent a BFC root whose
     *  margins never collapse with its children — both gates close. */
    @Test
    fun `hidden overflow closes both gates`() {
        val gates = BlockMarginCollapse.hoistGates(
            fixtureParentPairs() + ("Overflow" to j("\"hidden\""))
        )
        assertFalse(gates.top)
        assertFalse(gates.bottom)
    }

    // ── SHARED S1–S12 PIN TABLE ──────────────────────────────────────────
    // The UNIFIED COLLAPSE GATE CONTRACT's scenario pins. Expected values
    // are IDENTICAL to the iOS lane (MarginCollapseTests.swift) so the two
    // native implementations cannot drift silently — divergence between them
    // is exactly the bug class this contract exists to kill. S1–S4 pin the
    // pure §8.3.1 fold math and S5–S8 the parent hoist gates HERE; the
    // IR-level container bails S9–S12 (and the full-plan integration of
    // S5–S8) live in BlockCollapsePlanForTest.

    /** S1 [A(0,20), B(20,0)] parent (0,0), open gates → interior gap
     *  max(A.bottom 20, B.top 20) = 20 carried once; both edge margins are
     *  0, so nothing hoists (hoists 0/0). */
    @Test
    fun `S1 interior gap takes the max, zero edges do not hoist`() {
        val plan = BlockMarginCollapse.computePlan(
            children = listOf(m(0f, 20f), m(20f, 0f)),
            parentOwn = m(0f, 0f), gates = openGates,
        )
        assertEquals(listOf(m(0f, 0f), m(20f, 0f)), plan.perChild)
        assertEquals(0f, plan.hoistTopPx, 0.001f)
        assertEquals(0f, plan.hoistBottomPx, 0.001f)
    }

    /** S2 [A,B,C] each (20,20), open gates → applied tops [hoisted 0, 20,
     *  20], both hoists 20/20, in-parent content offsets 0/60/120. */
    @Test
    fun `S2 three equal siblings collapse with both edges hoisted`() {
        val plan = BlockMarginCollapse.computePlan(
            children = listOf(m(20f, 20f), m(20f, 20f), m(20f, 20f)),
            parentOwn = m(0f, 0f), gates = openGates,
        )
        assertEquals(listOf(m(0f, 0f), m(20f, 0f), m(20f, 0f)), plan.perChild)
        assertEquals(20f, plan.hoistTopPx, 0.001f)
        assertEquals(20f, plan.hoistBottomPx, 0.001f)
        // Offsets with 40px-tall children: 0 / 60 / 120 (web geometry).
        var y = 0f
        val offsets = plan.perChild.map { c -> (y + c.topPx).also { y = it + 40f + c.bottomPx } }
        assertEquals(listOf(0f, 60f, 120f), offsets)
    }

    /** S3 [A(30,0), B(0,10)] parent (0,0), open gates → adjoining interior
     *  margins are both 0 so the gap is 0; the outer edges hoist their full
     *  declared margins (hoists 30/10). */
    @Test
    fun `S3 zero interior gap, full edge margins hoist`() {
        val plan = BlockMarginCollapse.computePlan(
            children = listOf(m(30f, 0f), m(0f, 10f)),
            parentOwn = m(0f, 0f), gates = openGates,
        )
        assertEquals(listOf(m(0f, 0f), m(0f, 0f)), plan.perChild)
        assertEquals(30f, plan.hoistTopPx, 0.001f)
        assertEquals(10f, plan.hoistBottomPx, 0.001f)
    }

    /** S4 parent own margin (20,0), first child top 30 → hoistTop band =
     *  max(20, 30) − 20 = 10 (the parent's own 20px is already painted by
     *  its MarginApplier; only the 10px excess escapes). */
    @Test
    fun `S4 hoist band is the excess over the parent own margin`() {
        val plan = BlockMarginCollapse.computePlan(
            children = listOf(m(30f, 0f)),
            parentOwn = m(20f, 0f), gates = openGates,
        )
        assertEquals(10f, plan.hoistTopPx, 0.001f)
        assertEquals(0f, plan.hoistBottomPx, 0.001f)
    }

    /** S5 parent padding-top 8 → the TOP gate closes (§8.3.1 needs "no top
     *  padding"), the bottom gate stays open. */
    @Test
    fun `S5 top padding closes the top gate only`() {
        val gates = BlockMarginCollapse.hoistGates(
            fixtureParentPairs().map { (t, d) ->
                if (t == "PaddingTop") t to j("""{"px":8.0}""") else t to d
            }
        )
        assertFalse(gates.top)
        assertTrue(gates.bottom)
    }

    /** S6 parent overflow-x hidden → a single non-visible axis already
     *  establishes a BFC (css-overflow-3 §2.1: overflow-x:hidden computes
     *  overflow-y to auto), so BOTH gates close (G3). */
    @Test
    fun `S6 overflow-x hidden closes both gates`() {
        val gates = BlockMarginCollapse.hoistGates(
            fixtureParentPairs() + ("OverflowX" to j("\"hidden\""))
        )
        assertFalse(gates.top)
        assertFalse(gates.bottom)
    }

    /** S7 parent position:absolute → out-of-flow parent establishes a BFC-
     *  like context (G4), closing BOTH gates. */
    @Test
    fun `S7 absolute parent closes both gates`() {
        val gates = BlockMarginCollapse.hoistGates(
            fixtureParentPairs() + ("Position" to j("\"ABSOLUTE\""))
        )
        assertFalse(gates.top)
        assertFalse(gates.bottom)
    }

    /** S8 parent max-height 500px → the bottom gate stays OPEN: max-height
     *  leaves the used height auto-derived, so collapse-through still
     *  happens (G5 — CSS2 §8.3.1 names only height/min-height). */
    @Test
    fun `S8 max-height leaves both gates open`() {
        val gates = BlockMarginCollapse.hoistGates(
            fixtureParentPairs() + ("MaxHeight" to j("""{"type":"length","px":500.0}"""))
        )
        assertTrue(gates.top)
        assertTrue(gates.bottom)
    }
}
