package com.styleconverter.test.screenshot

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.layout.InlineBlockAtom
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * wave-34 lane H (H1) — unit pins for [composedRootInlineBoxes], the composed
 * canvas's per-ROOT read of the runtime's inline-block atom facade.
 *
 * WHY it exists: CSS2/abspos/static-inside-inline-block's three
 * `display: inline-block` boxes are document ROOTS. The composed canvas
 * stacks roots in the harness's own Column, which is not a runtime block box,
 * so the wave-33 nested inline-atom lane never saw them and the boxes were
 * block-STACKED — Android 0.8965 / iOS 0.9088 against a web at 0.9803.
 *
 * WHAT THIS FILE PINS: only the harness's half — which wire facts each root
 * contributes (B6's text/runs, B7's body-root line-height) and that a
 * run-free document gets NO plan, so the frozen stacking loop keeps running
 * verbatim. The gate table itself and the §9.4.2 packing are pinned in the
 * runtime's InlineBlockAtomTest / InlineBlockAtomTests.swift, which is where
 * they live — this test must never re-derive them.
 *
 * Plain junit:4.13.2 — the resolver is a pure function over the decoded IR.
 */
class ComposedRootInlineFlowTest {

    // ── the target document ──────────────────────────────────────────────

    @Test fun `the target document's three inline-block roots are atoms`() {
        // CSS2/abspos/static-inside-inline-block, verbatim: a prose <p> root
        // then three 100x100 declared inline-blocks (the middle one holding
        // the abspos green square as a CHILD, which generates no line box).
        val boxes = composedRootInlineBoxes(
            listOf(
                prose("Test passes if there is a filled green square and no red."),
                inlineBlock(100.0, 100.0),
                inlineBlock(100.0, 100.0),
                inlineBlock(100.0, 100.0),
            )
        )
        assertNull("the prose root is not an atom", boxes[0])
        for (i in 1..3) {
            assertEquals("root $i", InlineBlockAtom.RootBox(100.0, 100.0), boxes[i])
        }
        // …and they segment into [<p>] + one 3-member run.
        val segs = InlineBlockAtom.rootSegments(boxes, listOf(16.0, 16.0, 0.0, 0.0))
        assertNotNull(segs)
        assertEquals(listOf(1, 2, 3), segs!!.single { it.isRun }.indices)
    }

    // ── B6: own line-box content leaves the lane ─────────────────────────

    @Test fun `a root with its own text is not an atom`() {
        // §10.8.1 — own line boxes move the baseline off the bottom margin
        // edge, which is the whole reason B6 exists.
        val boxes = composedRootInlineBoxes(
            listOf(
                inlineBlock(100.0, 100.0).copy(_text = "hello"),
                inlineBlock(100.0, 100.0),
            )
        )
        assertNull(boxes[0])
        assertNotNull(boxes[1])
        // One atom is not a run, so the harness keeps the frozen stack.
        assertNull(InlineBlockAtom.rootSegments(boxes, listOf(0.0, 0.0)))
    }

    // ── B7: the document body's own line box ─────────────────────────────

    @Test fun `a body-root line-height refuses every root atom`() {
        // The packer's 16/4 strut pins are solved for the ref's INJECTED
        // body line box; an author override invalidates them, exactly like
        // absolute-tables-013's `line-height: 0` <td> in the nested lane.
        val body = IRComponent(
            id = "body", name = "body",
            properties = listOf(prop("LineHeight", """{"type":"length","px":0}""")),
            role = "body-root",
        )
        val boxes = composedRootInlineBoxes(
            listOf(body, inlineBlock(100.0, 100.0), inlineBlock(100.0, 100.0))
        )
        assertNull(boxes[1])
        assertNull(boxes[2])
        // Without the body-root declaration the same two roots ARE atoms —
        // this is the control that proves the gate is the line-height, not
        // the extra leading root.
        val control = composedRootInlineBoxes(
            listOf(
                IRComponent(id = "body", name = "body", role = "body-root"),
                inlineBlock(100.0, 100.0), inlineBlock(100.0, 100.0),
            )
        )
        assertNotNull(control[1])
        assertNotNull(control[2])
    }

    // ── the frozen path ──────────────────────────────────────────────────

    @Test fun `an ordinary document produces no boxes and therefore no plan`() {
        // The overwhelming majority of the composed corpus: nothing declares
        // inline-block at root level, so every entry is null, rootSegments
        // returns null, and ComposedCaptureCanvas runs its frozen loop.
        val boxes = composedRootInlineBoxes(
            listOf(prose("a"), block(100.0, 100.0), prose("b"))
        )
        assertEquals(listOf(null, null, null), boxes)
        assertNull(InlineBlockAtom.rootSegments(boxes, listOf(16.0, 0.0, 16.0)))
    }

    // ── wave 44 (U3): the margin channel's harness half ─────────────────

    @Test fun `an undelivered img root is not an atom on the JVM`() {
        // The replaced family is delivered-raster gated; this JVM suite
        // never configures DocumentImageRegistry, so the seam answers null
        // and the img keeps the frozen block path — the arm-A byte-compat
        // pin at the HARNESS level (the delivered arm is pinned in the
        // runtime's InlineBlockAtomTest, where facts are constructed).
        val img = IRComponent(
            id = "img", name = "img", _tag = "img",
            attrs = com.styleconverter.runtime.core.ir.IRAttrs(src = "css/css-ui/support/w100_h100.svg.png"),
            properties = listOf(prop("BoxSizing", "\"BORDER_BOX\"")),
        )
        assertNull(composedRootInlineBoxes(listOf(img, img))[0])
    }

    @Test fun `the margined ref div IS an atom and carries its margin`() {
        // box-sizing-010's `#ref` div — refused before wave 44, admitted
        // now with the margin riding the RootBox for the packer.
        val div = IRComponent(
            id = "d", name = "d",
            properties = listOf(
                prop("Display", "\"INLINE_BLOCK\""), len("MarginBottom", 30.0),
                len("Width", 70.0), len("Height", 70.0),
            ),
        )
        assertEquals(
            InlineBlockAtom.RootBox(70.0, 70.0, marginBottomPx = 30.0),
            composedRootInlineBoxes(listOf(div))[0],
        )
    }

    @Test fun `atomNeutralPlan mutes declared block margins but keeps UA ones`() {
        // A margined atom-shaped div: declared sides contribute ZERO to
        // the stack fold (the packer owns them now)…
        val div = IRComponent(
            id = "d", name = "d",
            properties = listOf(
                prop("Display", "\"INLINE_BLOCK\""), len("MarginBottom", 30.0),
                len("Width", 70.0), len("Height", 70.0),
            ),
        )
        val neutral = atomNeutralPlan(div)
        assertEquals(0f, neutral.topPx, 0f)
        assertEquals(0f, neutral.bottomPx, 0f)
        // …while an undeclared side on a UA-margined tag keeps its UA
        // default, which is what lets the H3 probe refuse a UA-margined
        // member instead of silently dropping its gap.
        val p = IRComponent(id = "p", name = "p", _tag = "p")
        assertEquals(16f, atomNeutralPlan(p).topPx, 0f)
        assertEquals(16f, atomNeutralPlan(p).bottomPx, 0f)
    }

    @Test fun `neutralized gaps drop only the run members' declared margins`() {
        // [<p>, #ref div (margin-bottom 30), img] — the 010 root list.
        // The frozen plans fold the div's declared 30 into the gap above
        // the img; neutralizing the two ATOMS (div + img) leaves only the
        // <p>'s UA bottom margin above the run and ZERO inside it.
        val p = IRComponent(id = "p", name = "p", _tag = "p", _text = "t")
        val div = IRComponent(
            id = "d", name = "d",
            properties = listOf(
                prop("Display", "\"INLINE_BLOCK\""), len("MarginBottom", 30.0),
                len("Width", 70.0), len("Height", 70.0),
            ),
        )
        val img = IRComponent(id = "i", name = "i", _tag = "img")
        val roots = listOf(p, div, img)
        // The REAL plans, built exactly like the capture screen's fold
        // (declared statics resolved, stripDeclared set for the div).
        val plans = roots.map { root ->
            val declared = declaredMarginSides(root.properties.map { it.type })
            rootStackMargin(
                root._tag, "top" in declared, "bottom" in declared,
                StaticEmMargin.verticalEdges(root.properties),
            )
        }
        // Frozen: [16 above p, 16 above div, 30 between div and img, 0].
        assertEquals(listOf(16f, 16f, 30f, 0f), collapsedRootStackGapsPx(plans))
        // Neutralized over the two atoms: the div's 30 is gone (it rides
        // the packer), the <p>'s UA margins survive untouched.
        assertEquals(
            listOf(16f, 16f, 0f, 0f),
            neutralizedRootGapsPx(roots, plans, listOf(false, true, true)),
        )
        // Neutralizing NOTHING is the frozen fold verbatim — the run-free
        // document identity the walk relies on.
        assertEquals(
            collapsedRootStackGapsPx(plans),
            neutralizedRootGapsPx(roots, plans, listOf(false, false, false)),
        )
    }

    // ── wave 44 (skeptic S2): the B8 pass-preservation proof ────────────

    @Test fun `B8 - scope-pseudo-element's verbatim wave43 IR keeps its passing root run`() {
        // ADVERSARIAL pin (wave-44 skeptic S2): css-cascade/scope-pseudo-
        // element is the ONE currently-PASSING composed document (Android
        // 0.9742, wave43-final frozen) whose three roots declare BOTH
        // `display: inline-block` AND `vertical-align: top`. An UNSCOPED
        // B8 gate would evict all three from the root run and revert the
        // canvas to the block stack — an unmeasured regression of a pass.
        // This test feeds the committed wave43-final per-test IR VERBATIM
        // (decoder → SlotComposer → the harness facade — the exact path
        // ComposedCaptureCanvas runs) and pins the run's survival.
        val rel = "tools/titan/runs/wave43-final/sections/css-cascade/" +
            "per-test-ir/wpt__css-cascade__scope-pseudo-element.json"
        // Repo-root walk-up — the ComposedFullHeightCaptureTest convention,
        // robust to Gradle running tests from module subdirs. The frozen
        // runs/ evidence is gitignored (materialised in campaign worktrees,
        // absent on CI), so absence SKIPS rather than fails: the gate table
        // itself stays pinned by the synthetic B8 test above either way.
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !java.io.File(dir, rel).exists()) dir = dir.parentFile
        org.junit.Assume.assumeTrue(
            "wave43-final evidence not materialised (gitignored runs/) — skipping the verbatim-IR pin",
            dir != null,
        )
        val json = java.io.File(dir!!, rel).readText()
        val roots = com.styleconverter.runtime.core.renderer.SlotComposer.compose(
            com.styleconverter.runtime.core.ir.IRDocumentDecoder.decode(json),
        )
        assertEquals("the document's three inline-block roots", 3, roots.size)
        // Guard the premise: every root really declares the B8-adverse
        // shape (a keyword-wrapped `vertical-align: top` off the wire) —
        // if a converter change ever drops it, this test must say so
        // rather than vacuously pass.
        for (root in roots) {
            val va = root.properties.last { it.type == "VerticalAlign" }.data.toString()
            assertEquals("""{"type":"keyword","value":"TOP"}""", va)
        }
        val boxes = composedRootInlineBoxes(roots)
        boxes.forEachIndexed { i, b ->
            assertNotNull(
                "root $i must keep its frozen wave-34 admission — B8 must stay scoped to the margin/replaced channels",
                b,
            )
            // 100px content + 1px borders per side, margin-free: the exact
            // pre-wave-44 box, so the committed capture cannot move.
            assertEquals(InlineBlockAtom.RootBox(102.0, 102.0), b)
        }
        // The canvas's H3 probe for an all-atom document: every plan entry
        // is atomNeutralPlan's (placeholder plans are never read when all
        // roots neutralize). The run must survive the probe too.
        val placeholder = roots.map { RootStackMargin(0f, 0f, stripDeclared = false) }
        val probeGaps = neutralizedRootGapsPx(roots, placeholder, roots.map { true })
        val segs = InlineBlockAtom.rootSegments(
            boxes,
            probeGaps.take(roots.size).map { it.toDouble() },
        )
        assertNotNull("the plan must exist — no plan means the block stack", segs)
        assertEquals(
            "the three roots must pack as ONE run, exactly the frozen passing layout",
            listOf(0, 1, 2),
            segs!!.single { it.isRun }.indices,
        )
    }

    // ── wire helpers ─────────────────────────────────────────────────────

    private fun prop(type: String, json: String) =
        IRProperty(type = type, data = Json.parseToJsonElement(json))

    private fun len(type: String, px: Double) =
        prop(type, """{"type":"length","px":$px}""")

    /** A `<p>`-shaped prose root: text, no declared display. */
    private fun prose(text: String) =
        IRComponent(id = "p", name = "p", _text = text, _tag = "p")

    /** A declared `display: inline-block` root with a definite border box. */
    private fun inlineBlock(w: Double, h: Double) = IRComponent(
        id = "ib", name = "ib",
        properties = listOf(prop("Display", "\"INLINE_BLOCK\""), len("Width", w), len("Height", h)),
    )

    /** The same box declared `display: block` — the B1 control. */
    private fun block(w: Double, h: Double) = IRComponent(
        id = "b", name = "b",
        properties = listOf(prop("Display", "\"BLOCK\""), len("Width", w), len("Height", h)),
    )
}
