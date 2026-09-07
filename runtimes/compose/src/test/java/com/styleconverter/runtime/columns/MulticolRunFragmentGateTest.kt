package com.styleconverter.runtime.columns

// WAVE-42 SKEPTIC S2 — the two container-level gates the multi-child RUN
// pass shipped without.
//
// (2) VERTICAL WRITING MODES. MulticolRunFragmentMeasure.measureRunFragment
//     had no `fragmentationAllowed` parameter, and MultiColumnApplier calls
//     it INSIDE the `definiteBlockSize && inlineSizeBounded` branch, ABOVE
//     that branch's own `overflowing > 0 && !fragmentationAllowed` bail — so
//     a vertical-writing-mode container reached the run model unguarded and
//     would have been sliced along the wrong axis. The spanner-flow twin has
//     carried the parameter since wave-21. Evidence shape: WPT
//     css-anchor-position/anchor-position-multicol-007 declares
//     `writing-mode: vertical-rl` on a definite-height `column-fill: auto`
//     multicol container with plain flow children — it passes every other
//     run-pass gate today.
//
// (3) N == 1. FragmentGeometry caps the fragment list at N, so with a single
//     used column an overflowing run becomes ONE fragment clipped to H and
//     the tail is never drawn: the pass would turn "content painted below"
//     (css-overflow-3 §2 overflow, what a one-column box really does) into
//     "content discarded" (which only `continue: discard` may do).
//
// No Robolectric in this suite (standing contract), so the pins are: the IR
// → config → flag pipeline the renderer really runs, the PURE consequence of
// running at N == 1, and a source scan of the wiring — the same three shapes
// MultiColumnFragmentationGateTest uses.

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MulticolRunFragmentGateTest {

    /** IR wire shape: keyword properties are bare JSON string primitives. */
    private fun prop(t: String, j: String) = IRProperty(t, Json.parseToJsonElement(j))

    /**
     * The anchor-position-multicol-007 container's declarations: a vertical
     * writing mode on a definite-height `column-fill: auto` multicol box.
     * (The height itself is a Sizing property the applier reads through the
     * constraints, not through MultiColumnConfig — the gate under test is
     * the writing-mode flag, so only the multicol half is modeled here.)
     */
    private val verticalMulticol = listOf(
        prop("WritingMode", "\"VERTICAL_RL\""),
        prop("ColumnCount", "2"),
        prop("ColumnFill", "\"AUTO\"")
    )

    // ── 1. The IR shape really produces fragmentationAllowed = false ──────

    @Test
    fun `the vertical-rl multicol IR shape flags the bail and keeps fill auto`() {
        val config = MultiColumnExtractor.extractMultiColumnConfig(
            verticalMulticol.map { it.type to it.data }
        )
        assertTrue("vertical-rl must flag the bail", config.verticalWritingMode)
        // MultiColumnLayout computes `fragmentationAllowed =
        // !config.verticalWritingMode` — model that exact hop, because it
        // is the false the run pass now receives (and previously never saw).
        val fragmentationAllowed = !config.verticalWritingMode
        assertFalse("the run pass must receive fragmentationAllowed = false", fragmentationAllowed)
        // Every OTHER run-pass gate stays open on this shape — that is why
        // the missing parameter was reachable and not academic: fill:auto
        // (the fill gate passes) and a 2-column container (the N gate too).
        assertEquals(ColumnFill.AUTO, config.fill)
        assertEquals(2, config.columnCount)
    }

    @Test
    fun `an inherited vertical writing mode reaches the same flag`() {
        // css-writing-modes-4 §3.2: writing-mode inherits, and the wave-12
        // channel carries it — a multicol child declaring only column
        // properties under a vertical parent must ALSO bail the run pass.
        val parentChannel = listOf(prop("WritingMode", "\"VERTICAL_RL\""))
            .filter { it.type in ComponentRenderer.INHERITED_PROPERTY_TYPES }
        val merged = ComponentRenderer.mergeInherited(
            listOf(prop("ColumnCount", "2"), prop("ColumnFill", "\"AUTO\"")),
            parentChannel
        )
        val config = MultiColumnExtractor.extractMultiColumnConfig(
            merged.map { it.type to it.data }
        )
        assertTrue("inherited vertical-rl must flag the bail", config.verticalWritingMode)
    }

    // ── 2. What running at N == 1 would actually do (the pure evidence) ───

    @Test
    fun `at one used column the run plan clips the tail away instead of overflowing`() {
        // Two 100px children in a 120px-tall single-column container: the
        // run is 200 tall, so §7.2 would want a second column — there is
        // none. FragmentGeometry's N cap therefore emits ONE fragment,
        // 120 tall, and the remaining 80px of run paint is never drawn.
        val plan = MulticolRunFragment.runPlan(
            childHeightsPx = listOf(100, 100),
            columnBlockSizePx = 120,
            columnWidthPx = 300,
            columnGapPx = 16,
            columnCount = 1
        )
        assertEquals(200, plan.totalBlockSizePx)
        assertEquals("the N cap leaves exactly one fragment", 1, plan.fragments.size)
        assertEquals("…which shows only the first H of the run", 120, plan.fragments[0].clipHeight)
        // That missing 80px is the discard the gate prevents: with the gate
        // the run pass never engages here, and the legacy path lets the
        // column overflow (css-overflow-3 §2) the way Chromium paints it.
        assertEquals(0, plan.fragments[0].translateY)
        // Sanity: at N == 2 the same run DOES have somewhere to continue —
        // proof the clipping above is the single-column shape, not the run
        // model being wrong in general.
        assertEquals(
            2,
            MulticolRunFragment.runPlan(
                childHeightsPx = listOf(100, 100),
                columnBlockSizePx = 120,
                columnWidthPx = 300,
                columnGapPx = 16,
                columnCount = 2
            ).fragments.size
        )
    }

    // ── 3. Source scan: both gates are wired into the real measure pass ───

    /** A repo file's text, located by walking up from the test working dir. */
    private fun source(rel: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
        return File(requireNotNull(dir) { "repo root ($rel) not found" }, rel).readText()
    }

    private val measureSource: String by lazy {
        source("runtimes/compose/src/main/java/com/styleconverter/runtime/columns/MulticolRunFragmentMeasure.kt")
    }
    private val applierSource: String by lazy {
        source("runtimes/compose/src/main/java/com/styleconverter/runtime/columns/MultiColumnApplier.kt")
    }

    @Test
    fun `the run pass declares and honours fragmentationAllowed`() {
        // The parameter exists (the spanner-flow twin's spelling)…
        assertTrue(
            "measureRunFragment must take fragmentationAllowed",
            measureSource.contains("fragmentationAllowed: Boolean"))
        // …and bails on it, loudly (no silent fallthrough).
        assertTrue(
            "the vertical bail must return null",
            measureSource.contains("if (!fragmentationAllowed) {"))
        assertTrue(
            "the vertical bail must log its reason once",
            measureSource.contains("run fragmentation: vertical writing-mode (blocked-platform)"))
    }

    @Test
    fun `the run pass bails at a single used column`() {
        assertTrue(
            "the N == 1 containment gate must exist",
            measureSource.contains("if (usedCount <= 1) {"))
        assertTrue(
            "the N == 1 bail must log its reason once",
            measureSource.contains("run fragmentation: single used column"))
    }

    // ── 4. Wave-43 lane V6: the N == 1 `continue: discard` caveat ─────────
    //
    // The N == 1 gate exists to keep "content painted below" from becoming
    // "content discarded" — but css-overflow-4 §5.3 `continue: discard` is
    // exactly the declaration that makes the drop CORRECT. For a definite-
    // height fill:auto multi-child container with `column-count: 1` and
    // `continue: discard`, the bail therefore keeps the WRONG render (the
    // overflow paints below instead of being discarded): the run pass takes
    // no discard parameter at all, so the config flag cannot reach the gate.
    //
    // This is a DOCUMENTED caveat, not a fix, because no corpus shape can
    // measure the fix: every `Continue: DISCARD` container in the wave-42
    // gate (tools/titan/runs/wave42-final — the discard-multicol-001…004
    // IRs, the corpus' complete discard family) declares ColumnCount 3, and
    // three of the four carry raw text (no measurables), which the run pass
    // never handles anyway. Building the N == 1 discard branch would be
    // unmeasurable code; this pin keeps the bail's shape honest until a
    // corpus shape exists to prove a fix against.

    @Test
    fun `at one used column the bail also un-discards continue-discard content - documented caveat`() {
        // The IR shape that WOULD hit the caveat, through the real config
        // pipeline: a single-column fill:auto container declaring discard.
        val config = MultiColumnExtractor.extractMultiColumnConfig(
            listOf(
                prop("ColumnCount", "1"),
                prop("ColumnFill", "\"AUTO\""),
                prop("Continue", "\"DISCARD\"")
            ).map { it.type to it.data }
        )
        // Both facts survive extraction — the caveat is in the WIRING, not
        // the config: the flag exists and is true, the count is one.
        assertEquals(1, config.columnCount)
        assertTrue("the discard flag must extract", config.continueDiscard)
        assertEquals(ColumnFill.AUTO, config.fill)
        // The pure half: at N == 1 the run plan's single H-clipped fragment
        // (pinned in the test above) is EXACTLY the render §3 discard wants
        // — proof the machinery could serve the shape; only the gate stands
        // between them, and only for the reason documented here.
        val plan = MulticolRunFragment.runPlan(
            childHeightsPx = listOf(100, 100),
            columnBlockSizePx = 120,
            columnWidthPx = 300,
            columnGapPx = 16,
            columnCount = 1
        )
        assertEquals("one fragment, clipped to H — the discard-correct paint",
            120, plan.fragments.single().clipHeight)
        // The wiring half (source scan, same shape as the pins above): the
        // run pass declares NO discard parameter, so the bail cannot
        // distinguish a discard container from a plain one…
        assertFalse(
            "measureRunFragment must not silently grow a discard parameter " +
                "without retiring this caveat pin",
            measureSource.contains("discardOverflow"))
        // …and the N == 1 bail's own comment owns the tradeoff by naming
        // `continue: discard` as the exception it knowingly swallows.
        assertTrue(
            "the N == 1 bail must document the discard exception",
            measureSource.contains("only `continue: discard` may drop"))
    }

    // ── 5. Wave-43 lane G3: the `continue` cascade fold is last-write-wins ─
    //
    // The extractor's `when` branch REASSIGNS `continueDiscard` per
    // declaration (MultiColumnExtractor lines 40-41), so a duplicated
    // `continue` resolves by order of appearance — css-cascade-5 §6.4.4's
    // final tiebreak. iOS's ColumnsExtractor folded the same property with
    // an any-DISCARD-wins `properties.contains { … }` scan until wave-43 G3,
    // so the wire [DISCARD, AUTO] extracted true there and false here. These
    // two pins are the Compose half of the cross-platform pin pair (the twin
    // lives in swiftui MulticolDiscardThreadingTests); Compose already
    // behaved — the pins keep it from drifting to any-wins in either
    // direction, which is why BOTH orders are pinned (an any-keyword-wins
    // fold would pass one of them by accident).

    @Test
    fun `duplicate continue declarations take the last one - discard then auto`() {
        val config = MultiColumnExtractor.extractMultiColumnConfig(
            listOf(
                prop("ColumnCount", "3"),
                prop("Continue", "\"DISCARD\""),
                prop("Continue", "\"AUTO\"")
            ).map { it.type to it.data }
        )
        assertFalse(
            "[DISCARD, AUTO]: the later `auto` overrides the earlier discard",
            config.continueDiscard)
    }

    @Test
    fun `duplicate continue declarations take the last one - auto then discard`() {
        val config = MultiColumnExtractor.extractMultiColumnConfig(
            listOf(
                prop("ColumnCount", "3"),
                prop("Continue", "\"AUTO\""),
                prop("Continue", "\"DISCARD\"")
            ).map { it.type to it.data }
        )
        assertTrue(
            "[AUTO, DISCARD]: the later `discard` overrides the earlier auto",
            config.continueDiscard)
    }

    @Test
    fun `the applier threads the flag into the run pass it calls first`() {
        // The hand-off itself…
        assertTrue(
            "MultiColumnApplier must pass fragmentationAllowed to the run pass",
            applierSource.contains("fragmentationAllowed = fragmentationAllowed"))
        // …and the ORDERING that made the missing parameter a defect: the
        // run-pass call site sits ABOVE the sole-child branch's own
        // `!fragmentationAllowed` bail, so the run pass cannot rely on it.
        val runCall = applierSource.indexOf("measureRunFragment(")
        val soleChildVerticalBail =
            applierSource.indexOf("overflowing > 0 && !fragmentationAllowed")
        assertTrue("the run-pass call site must exist", runCall > 0)
        assertTrue("the sole-child vertical bail must exist", soleChildVerticalBail > 0)
        assertTrue(
            "the run pass runs BEFORE the sole-child vertical bail — it must own its own gate",
            runCall < soleChildVerticalBail)
    }
}
