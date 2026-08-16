package com.styleconverter.runtime.layout

// Wave 33 (lane C, C2) — JVM pins for the declared-`display:inline-block`
// atom predicate (InlineBlockAtom.spec) and for the §9.4.2 row it feeds
// through the wave-20 packer. Every assertion here is mirrored VERBATIM in
// InlineBlockAtomTests.swift so the two natives cannot drift.
//
// Measured defect (see InlineBlockAtom's header): declared inline-block
// siblings that belong on one line box were block-STACKED by both natives'
// block child loop. On device the gate admits exactly ONE corpus site —
// filter-effects/backdrop-filter-boundary, Android 0.6130 → 0.8962 — and
// CSS2/abspos/static-inside-inline-block stays UNFIXED because its three
// boxes are document ROOTS, stacked by the harness, not by this loop.

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InlineBlockAtomTest {

    // ───────────────────────── B1 — the admission ticket ─────────────────

    @Test fun `B1 - a declared inline-block with a definite size is an atom`() {
        val spec = InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0), false, false, false)
        assertNotNull(spec)
        assertEquals(100.0, spec!!.fixedWpx!!, 0.0)
        assertEquals(100.0, spec.fixedHpx!!, 0.0)
    }

    @Test fun `B1 - block inline flex grid and table displays are not atoms`() {
        for (kw in listOf("BLOCK", "INLINE", "FLEX", "GRID", "TABLE", "LIST_ITEM", "NONE")) {
            assertNull(kw, InlineBlockAtom.spec(box(kw, 100.0, 100.0), false, false, false))
        }
    }

    @Test fun `B1 - an UNDECLARED display is the wave-20 widget lane not this one`() {
        // A bare <input> is inline-block by UA default; UAWidgetIntrinsics
        // owns its geometry, and racing that table with a wire size the
        // component does not carry is exactly what B1 prevents.
        assertNull(InlineBlockAtom.spec(sized(100.0, 100.0), false, false, false))
    }

    // ───────────────────────── B2 — definite border box ──────────────────

    @Test fun `B2 - an auto or percentage or missing axis refuses`() {
        // Shrink-to-fit (§10.3.9) and content height (§10.6.6) are measure
        // questions this pure rule cannot answer.
        assertNull("no size", InlineBlockAtom.spec(prop("Display", "\"INLINE_BLOCK\""), false, false, false))
        assertNull(
            "percent width",
            InlineBlockAtom.spec(
                prop("Display", "\"INLINE_BLOCK\"") +
                    prop("Width", """{"type":"percentage","value":50}""") +
                    len("Height", 100.0),
                false, false, false,
            )
        )
        assertNull(
            "auto height",
            InlineBlockAtom.spec(
                prop("Display", "\"INLINE_BLOCK\"") +
                    len("Width", 100.0) +
                    prop("Height", "\"auto\""),
                false, false, false,
            )
        )
    }

    @Test fun `B2 - the logical InlineSize and BlockSize spellings resolve too`() {
        val spec = InlineBlockAtom.spec(
            prop("Display", "\"INLINE_BLOCK\"") +
                len("InlineSize", 60.0) +
                len("BlockSize", 30.0),
            false, false, false,
        )
        assertNotNull(spec)
        assertEquals(60.0, spec!!.fixedWpx!!, 0.0)
        assertEquals(30.0, spec.fixedHpx!!, 0.0)
    }

    // ───────────────────────── B3/B4 — flow membership ───────────────────

    @Test fun `B3 - an absolutely positioned or fixed box is not on the line`() {
        for (kw in listOf("ABSOLUTE", "FIXED")) {
            assertNull(
                kw,
                InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0) + prop("Position", "\"$kw\""), false, false, false)
            )
        }
    }

    @Test fun `B3 - relative and sticky stay in flow and remain atoms`() {
        for (kw in listOf("RELATIVE", "STICKY", "STATIC")) {
            assertNotNull(
                kw,
                InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0) + prop("Position", "\"$kw\""), false, false, false)
            )
        }
    }

    @Test fun `B4 - a float belongs to the wave-19 packer`() {
        assertNull(
            InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0) + prop("Float", "\"LEFT\""), false, false, false)
        )
        // An explicit `float: none` is the initial value — still an atom.
        assertNotNull(
            InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0) + prop("Float", "\"NONE\""), false, false, false)
        )
    }

    // ───────────────────────── B5 — margins zero, bands box-sized ────────

    @Test fun `B5 - a non-zero margin refuses on any spelling`() {
        for (t in listOf("MarginLeft", "MarginTop", "MarginInlineStart", "MarginBlockEnd")) {
            assertNull(
                t,
                InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0) + len(t, 4.0), false, false, false)
            )
        }
    }

    @Test fun `B5 - explicit zero bands are fine - the extracted corpus shape`() {
        // backdrop-filter-clip-rect-2's boxes carry an explicit `padding:
        // 0` from the UA reset the extractor bakes in.
        val props = box("INLINE_BLOCK", 100.0, 100.0) +
            len("PaddingTop", 0.0) + len("PaddingLeft", 0.0) + len("MarginTop", 0.0)
        assertNotNull(InlineBlockAtom.spec(props, false, false, false))
    }

    @Test fun `B5 - wave33's all-zero-band sites are byte-identical under H2`() {
        // The wave-33 gate forced every band to zero. Both wave-34 branches
        // ADD 0 on such a box, so every site wave 33 admitted keeps its
        // exact AtomSpec — which is what makes backdrop-filter-boundary and
        // appearance-auto-non-html-namespace-001 unmoved.
        for (bs in listOf(null, "CONTENT_BOX", "BORDER_BOX")) {
            val props = box("INLINE_BLOCK", 160.0, 90.0) +
                len("PaddingLeft", 0.0) + len("BorderTopWidth", 0.0) +
                (bs?.let { prop("BoxSizing", "\"$it\"") } ?: emptyList())
            val spec = InlineBlockAtom.spec(props, false, false, false)
            assertNotNull("box-sizing=$bs", spec)
            assertEquals(160.0, spec!!.fixedWpx!!, 0.0)
            assertEquals(90.0, spec.fixedHpx!!, 0.0)
        }
    }

    // ── H2 (wave 34): the box-sizing-aware band read ─────────────────────

    @Test fun `H2 - border-box keeps the wire px as the OUTER border box`() {
        // backdrop-filter-clip-rect-2's exact shape: `box-sizing:
        // border-box; width/height: 100px; border: 10px`. css-sizing-3 §3
        // puts the bands INSIDE the declared size, so the atom is 100x100
        // and the three boxes pack 100 + 4.5 + 100 + 4.5 + 100 = 309.
        val props = box("INLINE_BLOCK", 100.0, 100.0) +
            prop("BoxSizing", "\"BORDER_BOX\"") +
            len("BorderTopWidth", 10.0) + len("BorderRightWidth", 10.0) +
            len("BorderBottomWidth", 10.0) + len("BorderLeftWidth", 10.0)
        val spec = InlineBlockAtom.spec(props, false, false, false)
        assertNotNull(spec)
        assertEquals(100.0, spec!!.fixedWpx!!, 0.0)
        assertEquals(100.0, spec.fixedHpx!!, 0.0)
    }

    @Test fun `H2 - border-box tolerates a band shape this rule cannot read`() {
        // Under border-box the outer box is the declared px WHATEVER the
        // band is, so a % padding does not need to be resolvable.
        val props = box("INLINE_BLOCK", 100.0, 100.0) +
            prop("BoxSizing", "\"BORDER_BOX\"") +
            prop("PaddingLeft", """{"type":"percentage","value":25}""")
        assertNotNull(InlineBlockAtom.spec(props, false, false, false))
    }

    @Test fun `H2 - content-box ADDS the padding and border bands`() {
        // css-sizing-3 §3: the declared size is the CONTENT box, so the
        // atom's outer border box is 100 + 4 + 6 wide and 100 + 2 + 8 tall.
        val props = box("INLINE_BLOCK", 100.0, 100.0) +
            prop("BoxSizing", "\"CONTENT_BOX\"") +
            len("PaddingLeft", 4.0) + len("PaddingTop", 2.0) +
            len("BorderRightWidth", 6.0) + prop("BorderRightStyle", "\"SOLID\"") +
            len("BorderBottomWidth", 8.0) + prop("BorderBottomStyle", "\"SOLID\"")
        val spec = InlineBlockAtom.spec(props, false, false, false)
        assertNotNull(spec)
        assertEquals(110.0, spec!!.fixedWpx!!, 0.0)
        assertEquals(110.0, spec.fixedHpx!!, 0.0)
    }

    @Test fun `H2 - an UNSET box-sizing is content-box under the WPT gate`() {
        // This predicate is composed-WPT-only (P19), where
        // SizingApplier.effectiveBoxSizing(null, true) == CONTENT_BOX.
        val props = box("INLINE_BLOCK", 100.0, 50.0) + len("PaddingRight", 7.0)
        val spec = InlineBlockAtom.spec(props, false, false, false)
        assertNotNull(spec)
        assertEquals(107.0, spec!!.fixedWpx!!, 0.0)
        assertEquals(50.0, spec.fixedHpx!!, 0.0)
    }

    @Test fun `H2 - a none or hidden border side bands at zero`() {
        // CSS 2.1 §8.5.3 — used width 0, matching BorderSideConfig.hasBorder
        // on both natives. clip-rect-2's `border-style: none` box is this.
        for (kw in listOf("NONE", "HIDDEN")) {
            val props = box("INLINE_BLOCK", 100.0, 100.0) +
                len("BorderLeftWidth", 10.0) + prop("BorderLeftStyle", "\"$kw\"")
            val spec = InlineBlockAtom.spec(props, false, false, false)
            assertNotNull(kw, spec)
            assertEquals(kw, 100.0, spec!!.fixedWpx!!, 0.0)
        }
    }

    @Test fun `H2 - content-box refuses the three unknowable band shapes`() {
        // 1. a non-exact band value (the % basis is a measure question).
        assertNull(
            "percent padding",
            InlineBlockAtom.spec(
                box("INLINE_BLOCK", 100.0, 100.0) +
                    prop("PaddingLeft", """{"type":"percentage","value":25}"""),
                false, false, false,
            )
        )
        // 2. a non-zero LOGICAL band (physical mapping is writing-mode aware).
        assertNull(
            "logical padding",
            InlineBlockAtom.spec(
                box("INLINE_BLOCK", 100.0, 100.0) + len("PaddingInlineStart", 5.0),
                false, false, false,
            )
        )
        // 3. a visible border-style with NO width — the `medium` initial the
        //    two natives resolve differently (Compose 0 / iOS 3).
        assertNull(
            "styled width-less border",
            InlineBlockAtom.spec(
                box("INLINE_BLOCK", 100.0, 100.0) + prop("BorderTopStyle", "\"SOLID\""),
                false, false, false,
            )
        )
    }

    @Test fun `H2 - an unreadable box-sizing keyword refuses rather than guesses`() {
        assertNull(
            InlineBlockAtom.spec(
                box("INLINE_BLOCK", 100.0, 100.0) + prop("BoxSizing", "\"WHAT_BOX\""),
                false, false, false,
            )
        )
    }

    // ───────────────────────── B6 — the baseline ─────────────────────────

    @Test fun `B6 - own text or runs move the baseline so the box refuses`() {
        assertNull("text", InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0), true, false, false))
        assertNull("runs", InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0), false, true, false))
    }

    @Test fun `B6 - a childless or abspos-child box baselines at its bottom edge`() {
        // §10.8.1: no in-flow line boxes ⇒ baseline == bottom margin edge
        // ⇒ descent 0, which is what leaves the ref's 4px strut descent
        // visible below a row of tall inline-blocks.
        assertEquals(0.0, InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0), false, false, false)!!.descentPx, 0.0)
    }

    // ───────────────────────── B7 — the container's line box ────────────

    @Test fun `B7 - a container that declares its own line-height refuses`() {
        // MEASURED on device: absolute-tables-013/-014/-015 put two 100x50
        // spans in a `line-height: 0` <td>, where the browser's line boxes
        // are 50 tall. Feeding them the harness's 16/4 strut moved the
        // second span 4px down and cost 0.0115/0.0163/0.0165 SSIM against
        // the frozen wave32-final Android baseline; refusing restores all
        // three byte-for-byte.
        assertNull(
            InlineBlockAtom.spec(
                box("INLINE_BLOCK", 100.0, 50.0), false, false,
                containerDeclaresLineHeight = true,
            )
        )
    }

    // ───────────────────────── the two corpus geometries ─────────────────

    @Test fun `three same-size boxes pack on one line inside the composed width`() {
        // The static-inside-inline-block geometry: three 100×100
        // inline-blocks, one collapsed space between each, in the 358px
        // composed content width. This pins the PACKER's answer; that test
        // itself is root-level and never reaches this lane (see the file
        // header's measured-outcome section).
        val spec = InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0), false, false, false)!!
        val plan = InlineAtomFlow.layout(
            widths = List(3) { spec.fixedWpx!! },
            heights = List(3) { spec.fixedHpx!! },
            descents = List(3) { spec.descentPx },
            marginStarts = List(3) { 0.0 },
            marginEnds = List(3) { 0.0 },
            availableWidth = 358.0,
            gapPx = UAWidgetIntrinsics.ATOM_GAP_PX,
            strutAscentPx = UAWidgetIntrinsics.STRUT_ASCENT_PX,
            strutDescentPx = UAWidgetIntrinsics.STRUT_DESCENT_PX,
        )
        // One row: 3×100 + 2×4.5 = 309 ≤ 358.
        assertEquals(listOf(0.0, 104.5, 209.0), plan.x)
        assertEquals(listOf(0.0, 0.0, 0.0), plan.y)
        assertEquals(309.0, plan.width, 0.0)
        // Row height = ascent 100 (the boxes) + descent 4 (the §10.8.1
        // strut) — the browser's classic below-inline-block gap.
        assertEquals(104.0, plan.height, 0.0)
    }

    @Test fun `the absolute-tables-013 pair WRAPS inside its 100px cell`() {
        // Two 100×50 spans in a 100px-wide <td>: 100 + 4.5 + 100 > 100,
        // so the second wraps — the same visual stacking the frozen
        // baseline already had, one strut descent lower per line box.
        val spec = InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 50.0), false, false, false)!!
        val plan = InlineAtomFlow.layout(
            widths = List(2) { spec.fixedWpx!! },
            heights = List(2) { spec.fixedHpx!! },
            descents = List(2) { spec.descentPx },
            marginStarts = List(2) { 0.0 },
            marginEnds = List(2) { 0.0 },
            availableWidth = 100.0,
            gapPx = UAWidgetIntrinsics.ATOM_GAP_PX,
            strutAscentPx = UAWidgetIntrinsics.STRUT_ASCENT_PX,
            strutDescentPx = UAWidgetIntrinsics.STRUT_DESCENT_PX,
        )
        assertEquals(listOf(0.0, 0.0), plan.x)
        // Line box 1 = ascent 50 + strut descent 4 ⇒ row 2 top at 54.
        assertEquals(listOf(0.0, 54.0), plan.y)
        assertEquals(108.0, plan.height, 0.0)
    }

    // ── H1 (wave 34): the composed-canvas ROOT flow facade ───────────────

    @Test fun `H1 - rootBox is spec projected to scalars`() {
        val b = InlineBlockAtom.rootBox(box("INLINE_BLOCK", 100.0, 100.0), false, false, false)
        assertEquals(InlineBlockAtom.RootBox(100.0, 100.0), b)
        // A non-atom root keeps the harness's frozen block stack.
        assertNull(InlineBlockAtom.rootBox(box("BLOCK", 100.0, 100.0), false, false, false))
    }

    @Test fun `H1 - the target document segments into p then a 3-box run`() {
        // CSS2/abspos/static-inside-inline-block: root 0 is the prose <p>
        // (no Display, so not an atom), roots 1-3 the inline-blocks.
        val boxes = listOf(
            null,
            InlineBlockAtom.RootBox(100.0, 100.0),
            InlineBlockAtom.RootBox(100.0, 100.0),
            InlineBlockAtom.RootBox(100.0, 100.0),
        )
        // rootGaps in the harness are [16 above <p>, 16 above root 1, 0, 0].
        val segs = InlineBlockAtom.rootSegments(boxes, listOf(16.0, 16.0, 0.0, 0.0))
        assertNotNull(segs)
        assertEquals(
            listOf(
                InlineBlockAtom.RootSegment(listOf(0), false),
                InlineBlockAtom.RootSegment(listOf(1, 2, 3), true),
            ),
            segs,
        )
    }

    @Test fun `H1 - a run-free document gets NO plan so the harness stays frozen`() {
        // Every composed section but two is this case — the null return is
        // what keeps their captures byte-identical.
        assertNull(InlineBlockAtom.rootSegments(listOf(null, null, null), listOf(0.0, 0.0, 0.0)))
        // A LONE atom is not a run either (the ≥2 rule the segmenter pins).
        assertNull(
            InlineBlockAtom.rootSegments(
                listOf(null, InlineBlockAtom.RootBox(10.0, 10.0), null),
                listOf(0.0, 0.0, 0.0),
            )
        )
    }

    @Test fun `H3 - a run with a real block gap inside it refuses and stacks`() {
        // Dropping that gap would silently lose layout, so the whole run
        // degrades to singles and the harness stacks it exactly as before.
        val boxes = listOf(
            InlineBlockAtom.RootBox(100.0, 100.0),
            InlineBlockAtom.RootBox(100.0, 100.0),
        )
        assertNull(InlineBlockAtom.rootSegments(boxes, listOf(0.0, 16.0)))
        // The gap ABOVE the first member is the caller's and never gates.
        assertNotNull(InlineBlockAtom.rootSegments(boxes, listOf(16.0, 0.0)))
    }

    @Test fun `H1 - the target run packs to the ref's 121x88 green box`() {
        // Three 100x100 roots in the composed canvas's 358px content width.
        val plan = InlineBlockAtom.rootRowPlan(
            widthsPx = List(3) { 100.0 },
            heightsPx = List(3) { 100.0 },
            availableWidthPx = 358.0,
            gapPx = InlineBlockAtom.ROOT_ATOM_GAP_PX,
        )
        // One row: 3x100 + 2x4.5 = 309 <= 358. The MIDDLE box (the red one
        // holding the green abspos child) starts at run-relative 104.5, so
        // the harness's 16px frame puts it at image x = 120.5 -> the 121
        // column the browser-ref rasterises.
        assertEquals(listOf(0.0, 104.5, 209.0), plan.xPx)
        assertEquals(listOf(0.0, 0.0, 0.0), plan.yPx)
        assertEquals(309.0, plan.widthPx, 0.0)
        // Row height = ascent 100 (the boxes) + descent 4 (the §10.8.1
        // strut) — the classic below-inline-block gap. 16 pad + 16 gap +
        // a 40px two-line <p> + 16 gap = the ref's y = 88.
        assertEquals(104.0, plan.heightPx, 0.0)
    }

    @Test fun `H1 - anchor-center-overflow-005's four roots wrap 3 plus 1`() {
        // 4x100 + 3x4.5 = 413.5 > 358, so the 4th box starts row 2 at the
        // first row's full height (100 + the 4px strut descent).
        val plan = InlineBlockAtom.rootRowPlan(
            widthsPx = List(4) { 100.0 },
            heightsPx = List(4) { 100.0 },
            availableWidthPx = 358.0,
            gapPx = InlineBlockAtom.ROOT_ATOM_GAP_PX,
        )
        assertEquals(listOf(0.0, 104.5, 209.0, 0.0), plan.xPx)
        assertEquals(listOf(0.0, 0.0, 0.0, 104.0), plan.yPx)
        assertEquals(208.0, plan.heightPx, 0.0)
    }

    @Test fun `H1 - the root row plan is the nested row plan`() {
        // The facade must be a pure re-expression of InlineAtomFlow.layout
        // with the inline-block family's fixed answers, never a second
        // packer — this pins the two against each other.
        val direct = InlineAtomFlow.layout(
            widths = List(3) { 100.0 },
            heights = List(3) { 100.0 },
            descents = List(3) { 0.0 },
            marginStarts = List(3) { 0.0 },
            marginEnds = List(3) { 0.0 },
            availableWidth = 358.0,
            gapPx = UAWidgetIntrinsics.ATOM_GAP_PX,
            strutAscentPx = UAWidgetIntrinsics.STRUT_ASCENT_PX,
            strutDescentPx = UAWidgetIntrinsics.STRUT_DESCENT_PX,
        )
        val viaFacade = InlineBlockAtom.rootRowPlan(
            widthsPx = List(3) { 100.0 },
            heightsPx = List(3) { 100.0 },
            availableWidthPx = 358.0,
            gapPx = InlineBlockAtom.ROOT_ATOM_GAP_PX,
        )
        assertEquals(direct.x, viaFacade.xPx)
        assertEquals(direct.y, viaFacade.yPx)
        assertEquals(direct.width, viaFacade.widthPx, 0.0)
        assertEquals(direct.height, viaFacade.heightPx, 0.0)
    }

    // ── wave 44 (lane U3): the ROOT facade's margin + replaced channels ──

    @Test fun `U3 - rootBox equals the spec projection on margin-free inline-blocks`() {
        // The anti-drift pin: rootBox is no longer a spec delegate, so the
        // shapes BOTH admit must produce the identical box. Plain, unset-
        // box-sizing banded, and border-box banded shapes all agree.
        val shapes = listOf(
            box("INLINE_BLOCK", 100.0, 100.0),
            box("INLINE_BLOCK", 100.0, 50.0) + len("PaddingRight", 7.0),
            box("INLINE_BLOCK", 100.0, 100.0) + prop("BoxSizing", "\"BORDER_BOX\"") +
                len("BorderTopWidth", 10.0),
        )
        for (props in shapes) {
            val s = InlineBlockAtom.spec(props, false, false, false)!!
            assertEquals(
                InlineBlockAtom.RootBox(s.fixedWpx!!, s.fixedHpx!!),
                InlineBlockAtom.rootBox(props, false, false, false),
            )
        }
    }

    @Test fun `U3a - the box-sizing-010 ref div rides its margin-bottom onto the box`() {
        // The `#ref` div, verbatim: `display:inline-block; margin-bottom:
        // 30px /*for alignement*/; width/height: 70px`. The margin now
        // lives on the RootBox (the packer owns it) …
        val props = box("INLINE_BLOCK", 70.0, 70.0) + len("MarginBottom", 30.0)
        assertEquals(
            InlineBlockAtom.RootBox(70.0, 70.0, marginBottomPx = 30.0),
            InlineBlockAtom.rootBox(props, false, false, false),
        )
        // …while the NESTED lane's spec keeps refusing it — its AtomSpec
        // has no margin channel and its adapters pack border boxes.
        assertNull(InlineBlockAtom.spec(props, false, false, false))
        // Negative margins are legal exact px (box-sizing-007's t01 kind).
        assertEquals(
            InlineBlockAtom.RootBox(70.0, 70.0, marginLeftPx = -10.0),
            InlineBlockAtom.rootBox(
                box("INLINE_BLOCK", 70.0, 70.0) + len("MarginLeft", -10.0),
                false, false, false,
            ),
        )
    }

    @Test fun `U3a - unreadable or logical margins still refuse the root box`() {
        val base = box("INLINE_BLOCK", 70.0, 70.0)
        // `auto` needs §10.3.3's line-layout resolution — refuse.
        assertNull(InlineBlockAtom.rootBox(base + prop("MarginTop", "\"auto\""), false, false, false))
        // A % margin's basis is a containing-block question — refuse.
        assertNull(
            InlineBlockAtom.rootBox(
                base + prop("MarginLeft", """{"type":"percentage","value":10}"""),
                false, false, false,
            )
        )
        // A non-zero LOGICAL margin is writing-mode dependent — refuse.
        assertNull(InlineBlockAtom.rootBox(base + len("MarginInlineStart", 5.0), false, false, false))
        // …but an explicit logical ZERO is fine (the extractor's UA reset).
        assertNotNull(InlineBlockAtom.rootBox(base + len("MarginInlineStart", 0.0), false, false, false))
    }

    @Test fun `B8 - vertical-align gates the margin channel but not the frozen family`() {
        val va = prop("VerticalAlign", """{"type":"keyword","value":"TOP"}""")
        // css-position/position-absolute-semi-replaced-stretch-input's
        // shape: a MARGINED inline-block declaring `vertical-align: top`
        // refuses — the packer models §10.8.1 baseline rows only, and
        // without this gate those two tests were the only OFF-arm change.
        assertNull(
            InlineBlockAtom.rootBox(
                box("INLINE_BLOCK", 150.0, 100.0) + len("MarginTop", 5.0) + va,
                false, false, false,
            )
        )
        // css-cascade/scope-pseudo-element's shape: the same declaration
        // on a margin-FREE inline-block keeps its frozen wave-34
        // admission (admitted today, Android 0.9742 PASS).
        assertNotNull(
            InlineBlockAtom.rootBox(box("INLINE_BLOCK", 100.0, 100.0) + va, false, false, false)
        )
        // An explicit `baseline` is the modelled value — admitted.
        assertNotNull(
            InlineBlockAtom.rootBox(
                box("INLINE_BLOCK", 150.0, 100.0) + len("MarginTop", 5.0) +
                    prop("VerticalAlign", """{"type":"keyword","value":"BASELINE"}"""),
                false, false, false,
            )
        )
    }

    /** The box-sizing-010 img, verbatim wire shape: `box-sizing:
     *  border-box; width/height: auto; padding-bottom: 30px; max-height:
     *  100px` (the white background is not read by the gate). */
    private fun imgProps010(): List<IRProperty> =
        prop("BoxSizing", "\"BORDER_BOX\"") +
            prop("Width", "\"auto\"") + prop("Height", "\"auto\"") +
            len("PaddingBottom", 30.0) + len("MaxHeight", 100.0)

    /** Delivered-raster facts for one support vector, per the pre-raster
     *  scale contract's probe table (svg-preraster.mjs). */
    private fun facts(w: Double, h: Double, tag: String = "img") =
        InlineBlockAtom.ReplacedRootFacts(tag, w, h, w / h)

    @Test fun `U3b - a delivered img root is sized by 10-3-2 and 10-4`() {
        // box-sizing-010: w100_h100 (100×100 intrinsic), max-height 100
        // BORDER-box over a 30px bottom band ⇒ content max 70 ⇒ §10.4 row
        // "h > max-height" re-solves width through the 1:1 ratio: content
        // 70×70, OUTER border box 70×100 (the 30px white band below).
        assertEquals(
            InlineBlockAtom.RootBox(70.0, 100.0),
            InlineBlockAtom.rootBox(imgProps010(), false, false, false, facts(100.0, 100.0)),
        )
        // box-sizing-016: r1-1 pre-rasterises at 150×150 (ratio survives,
        // the size is the raster's assertion) — same §10.4 row, same box.
        assertEquals(
            InlineBlockAtom.RootBox(70.0, 100.0),
            InlineBlockAtom.rootBox(imgProps010(), false, false, false, facts(150.0, 150.0)),
        )
        // UNDELIVERED (facts null — the pre-raster-OFF arm): refused, the
        // frozen block stack renders. THE byte-compat pin for arm A.
        assertNull(InlineBlockAtom.rootBox(imgProps010(), false, false, false))
        // Document-level: [<p>, #ref div, img] segments into the <p>
        // single plus ONE 2-member run — the ref's side-by-side squares.
        // The declared-neutral probe gaps carry only the <p>'s UA margins.
        val boxes = listOf(
            null,
            InlineBlockAtom.rootBox(
                box("INLINE_BLOCK", 70.0, 70.0) + len("MarginBottom", 30.0),
                false, false, false,
            ),
            InlineBlockAtom.rootBox(imgProps010(), false, false, false, facts(100.0, 100.0)),
        )
        val segs = InlineBlockAtom.rootSegments(boxes, listOf(16.0, 16.0, 0.0))
        assertEquals(listOf(1, 2), segs!!.single { it.isRun }.indices)
    }

    @Test fun `U3b - the family and display gates hold for replaced roots`() {
        // Facts attached to a non-replaced tag are a harness bug the gate
        // refuses rather than packs (isAtom's discipline, mirrored).
        assertNull(
            InlineBlockAtom.rootBox(imgProps010(), false, false, false, facts(100.0, 100.0, tag = "div"))
        )
        // css-display-3 §2: a declared block-level display leaves the
        // inline flow even with a delivered raster; INLINE keeps it.
        assertNull(
            InlineBlockAtom.rootBox(
                imgProps010() + prop("Display", "\"BLOCK\""),
                false, false, false, facts(100.0, 100.0),
            )
        )
        assertNotNull(
            InlineBlockAtom.rootBox(
                imgProps010() + prop("Display", "\"INLINE\""),
                false, false, false, facts(100.0, 100.0),
            )
        )
        // B8 — a replaced root declaring a non-baseline vertical-align
        // refuses (the packer's baseline-row model).
        assertNull(
            InlineBlockAtom.rootBox(
                imgProps010() + prop("VerticalAlign", """{"type":"keyword","value":"TOP"}"""),
                false, false, false, facts(100.0, 100.0),
            )
        )
    }

    @Test fun `U3b - a definite axis derives the free one through the ratio`() {
        // box-sizing-007 t03: `width: 120px; padding-left: 20px;
        // box-sizing: border-box; margin: 10px; margin-left: -10px` over
        // the 100×100 raster ⇒ content 100 wide, height 100 via the 1:1
        // ratio ⇒ border box 120×100 with the margins riding the box.
        val t03 = prop("BoxSizing", "\"BORDER_BOX\"") +
            len("Width", 120.0) + prop("Height", "\"auto\"") +
            len("PaddingLeft", 20.0) +
            len("MarginTop", 10.0) + len("MarginRight", 10.0) +
            len("MarginBottom", 10.0) + len("MarginLeft", -10.0)
        assertEquals(
            InlineBlockAtom.RootBox(120.0, 100.0, 10.0, 10.0, 10.0, -10.0),
            InlineBlockAtom.rootBox(t03, false, false, false, facts(100.0, 100.0)),
        )
        // t04, the mirror: `height: 120px; padding-bottom: 20px` ⇒ content
        // 100 tall, width 100 via the ratio ⇒ border box 100×120.
        val t04 = prop("BoxSizing", "\"BORDER_BOX\"") +
            prop("Width", "\"auto\"") + len("Height", 120.0) +
            len("PaddingBottom", 20.0) +
            len("MarginTop", 10.0) + len("MarginRight", 10.0) +
            len("MarginBottom", -10.0) + len("MarginLeft", 10.0)
        assertEquals(
            InlineBlockAtom.RootBox(100.0, 120.0, 10.0, 10.0, -10.0, 10.0),
            InlineBlockAtom.rootBox(t04, false, false, false, facts(100.0, 100.0)),
        )
    }

    @Test fun `U3a - rootRowPlan packs margin boxes and returns border-box origins`() {
        // The box-sizing-010 pair, ref-verified geometry: div 70×70 with
        // margin-bottom 30 (margin box 70×100) beside the img's 70×100
        // border box. One row — both margin boxes 100 tall — with both
        // border tops at y 0, the img at x 70 + 4.5 = 74.5 (image x 91).
        val plan = InlineBlockAtom.rootRowPlan(
            widthsPx = listOf(70.0, 70.0),
            heightsPx = listOf(70.0, 100.0),
            availableWidthPx = 358.0,
            gapPx = InlineBlockAtom.ROOT_ATOM_GAP_PX,
            marginBottomsPx = listOf(30.0, 0.0),
        )
        assertEquals(listOf(0.0, 74.5), plan.xPx)
        assertEquals(listOf(0.0, 0.0), plan.yPx)
        // Row = ascent 100 (the margin boxes) + the §10.8.1 strut's 4.
        assertEquals(104.0, plan.heightPx, 0.0)
        // Margin-free lists are the wave-34 plan byte-for-byte.
        val frozen = InlineBlockAtom.rootRowPlan(
            widthsPx = List(3) { 100.0 }, heightsPx = List(3) { 100.0 },
            availableWidthPx = 358.0, gapPx = InlineBlockAtom.ROOT_ATOM_GAP_PX,
        )
        assertEquals(listOf(0.0, 104.5, 209.0), frozen.xPx)
    }

    @Test fun `U3 - the verbatim box-sizing-007 twenty-img run wraps two per row`() {
        // The twenty imgs of css-ui/box-sizing-007, property lists
        // verbatim from the frozen wave43-final per-test IR: four support
        // vectors × five sizing shapes, every margin box exactly 120×120
        // (the test's "same size" design). At the composed canvas's 358px
        // the packer must put TWO per row — 120 + 4.5 + 120 = 244.5 fits,
        // a third (369) does not — for the ref's ten rows of two.
        val margins = len("MarginTop", 10.0) + len("MarginRight", 10.0)
        fun img(shape: Int, iw: Double, ih: Double): InlineBlockAtom.RootBox? {
            val sizing = when (shape) {
                // t*0 — both auto, plain margins.
                0 -> prop("Width", "\"auto\"") + prop("Height", "\"auto\"") +
                    len("MarginBottom", 10.0) + len("MarginLeft", 10.0)
                // t*1 — both auto, padding-left 20, margin-left -10.
                1 -> prop("Width", "\"auto\"") + prop("Height", "\"auto\"") +
                    len("MarginBottom", 10.0) + len("MarginLeft", -10.0) + len("PaddingLeft", 20.0)
                // t*2 — both auto, padding-bottom 20, margin-bottom -10.
                2 -> prop("Width", "\"auto\"") + prop("Height", "\"auto\"") +
                    len("MarginBottom", -10.0) + len("MarginLeft", 10.0) + len("PaddingBottom", 20.0)
                // t*3 — width 120 definite, padding-left 20, margin-left -10.
                3 -> len("Width", 120.0) + prop("Height", "\"auto\"") +
                    len("MarginBottom", 10.0) + len("MarginLeft", -10.0) + len("PaddingLeft", 20.0)
                // t*4 — height 120 definite, padding-bottom 20, margin-bottom -10.
                else -> prop("Width", "\"auto\"") + len("Height", 120.0) +
                    len("MarginBottom", -10.0) + len("MarginLeft", 10.0) + len("PaddingBottom", 20.0)
            }
            return InlineBlockAtom.rootBox(
                prop("BoxSizing", "\"BORDER_BOX\"") + sizing + margins,
                false, false, false, facts(iw, ih),
            )
        }
        // The bare-r1-1 row (t30..t34) declares one definite axis per img
        // in the SOURCE (its vector has no intrinsic size), so its five
        // property lists differ from the other three rows' — verbatim:
        fun r11(shape: Int): InlineBlockAtom.RootBox? {
            val sizing = when (shape) {
                // t30 — width 100, plain margins.
                0 -> len("Width", 100.0) + prop("Height", "\"auto\"") +
                    len("MarginBottom", 10.0) + len("MarginLeft", 10.0)
                // t31 — height 100, padding-left 20, margin-left -10.
                1 -> prop("Width", "\"auto\"") + len("Height", 100.0) +
                    len("MarginBottom", 10.0) + len("MarginLeft", -10.0) + len("PaddingLeft", 20.0)
                // t32 — width 100, padding-bottom 20, margin-bottom -10.
                2 -> len("Width", 100.0) + prop("Height", "\"auto\"") +
                    len("MarginBottom", -10.0) + len("MarginLeft", 10.0) + len("PaddingBottom", 20.0)
                // t33 — width 120, padding-left 20, margin-left -10.
                3 -> len("Width", 120.0) + prop("Height", "\"auto\"") +
                    len("MarginBottom", 10.0) + len("MarginLeft", -10.0) + len("PaddingLeft", 20.0)
                // t34 — height 120, padding-bottom 20, margin-bottom -10.
                else -> prop("Width", "\"auto\"") + len("Height", 120.0) +
                    len("MarginBottom", -10.0) + len("MarginLeft", 10.0) + len("PaddingBottom", 20.0)
            }
            // The bare vector's pre-raster asserts 150×150 (the scale
            // contract's limitation 2) — but every t3x declares an axis,
            // so only the surviving 1:1 RATIO is consumed here.
            return InlineBlockAtom.rootBox(
                prop("BoxSizing", "\"BORDER_BOX\"") + sizing + margins,
                false, false, false, facts(150.0, 150.0),
            )
        }
        // Rows t0x/t1x/t2x: the three sized vectors all pre-raster at
        // 100×100 (the probe table), shapes 0..4 verbatim.
        val boxes = (0 until 3).flatMap { (0..4).map { s -> img(s, 100.0, 100.0) } } +
            (0..4).map { r11(it) }
        // Every one of the twenty is admitted…
        assertEquals(20, boxes.filterNotNull().size)
        // …but the bare-r1-1 row's t30 declares `width: 100px`, so its
        // border box is 100×100 like every other (margin box 120×120).
        for (b in boxes.filterNotNull()) {
            assertEquals(120.0, b.marginLeftPx + b.widthPx + b.marginRightPx, 0.0)
            assertEquals(120.0, b.marginTopPx + b.heightPx + b.marginBottomPx, 0.0)
        }
        // The packed run: ten rows of two at the canvas width.
        val bs = boxes.filterNotNull()
        val plan = InlineBlockAtom.rootRowPlan(
            widthsPx = bs.map { it.widthPx },
            heightsPx = bs.map { it.heightPx },
            availableWidthPx = 358.0,
            gapPx = InlineBlockAtom.ROOT_ATOM_GAP_PX,
            marginTopsPx = bs.map { it.marginTopPx },
            marginRightsPx = bs.map { it.marginRightPx },
            marginBottomsPx = bs.map { it.marginBottomPx },
            marginLeftsPx = bs.map { it.marginLeftPx },
        )
        // Row pitch 124 (margin-box ascent 120 + strut descent 4), ten
        // rows — the ref's green rows at image y 98, 222, … 1214.
        assertEquals(1240.0, plan.heightPx, 0.0)
        // t00's border box at x 10 (its +10 margin-left), t01's at 114.5
        // (slot 124.5 minus its -10 margin-left; its green starts +20
        // padding later — the ref's 151 column).
        assertEquals(10.0, plan.xPx[0], 0.0)
        assertEquals(114.5, plan.xPx[1], 0.0)
        // Border tops sit +10 (margin-top) into each 124px row.
        assertEquals(10.0, plan.yPx[0], 0.0)
        assertEquals(10.0, plan.yPx[1], 0.0)
        assertEquals(9 * 124.0 + 10.0, plan.yPx[18], 0.0)
        assertEquals(9 * 124.0 + 10.0, plan.yPx[19], 0.0)
    }

    // ───────────────────────── wire helpers ──────────────────────────────

    private fun prop(type: String, json: String) = listOf(IRProperty(type, Json.parseToJsonElement(json)))

    private fun len(type: String, px: Double) =
        listOf(IRProperty(type, Json.parseToJsonElement("""{"type":"length","px":$px}""")))

    /** A declared-display box with an exact px width/height. */
    private fun box(display: String, w: Double, h: Double): List<IRProperty> =
        prop("Display", "\"$display\"") + len("Width", w) + len("Height", h)

    /** The same box WITHOUT any Display declaration (B1's UA-default case). */
    private fun sized(w: Double, h: Double): List<IRProperty> = len("Width", w) + len("Height", h)
}
