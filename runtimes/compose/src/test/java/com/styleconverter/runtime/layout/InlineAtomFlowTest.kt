package com.styleconverter.runtime.layout

// Wave-20 lane W3 — JVM pins for the pure inline-atom-flow twins
// (InlineAtomFlow.kt ↔ iOS InlineAtomFlow.swift) and the shared UA
// widget geometry table (UAWidgetIntrinsics.kt ↔ .swift). Every pinned
// value is mirrored VERBATIM in InlineAtomFlowTests.swift; the shared
// table is the lane pin table (P15-P18). Corpus geometry comes from
// css-ui appearance-auto-001 (and its alias siblings, all sharing
// appearance-auto-ref.html): sixteen UA widgets inline-wrapped in a
// 500px container — ref rows (container-relative tops):
//   row 1 y0:  a, button, input-text, input-search        (pitch 21)
//   row 2 y21: textarea, input-button, input-submit, input-reset
//   row 3 y63: range, checkbox, radio, color, select, select-multiple,
//              meter                                       (pitch 42)
//   row 4 y135: progress                                   (pitch 72)
// (ref bands y17/38/80/152 with the container's own y17 origin; the
// pitches are the P17 baseline math + the P17b §10.8.1 strut, exact —
// fix 4 re-solved rows 3/4 against the white-black-ink ref: baseline
// y143 from checkbox top 133, row-4 top 152 from color bottom 152.)

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.widgets.UAControlFontMetrics
import com.styleconverter.runtime.widgets.UAWidgetsGeometry
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineAtomFlowTest {

    // ── P15 — the atom predicate ──

    @Test
    fun `widget tags are atoms regardless of children`() {
        // The widget-identity set: opaque atoms even with children
        // (a select's options are its CONTENT, not flow siblings).
        for (tag in listOf("button", "input", "textarea", "select", "meter", "progress")) {
            assertTrue(tag, InlineAtomFlow.isAtom(tag, null, hasElementChildren = true, hasText = false))
            assertTrue(tag, InlineAtomFlow.isAtom(tag, null, hasElementChildren = false, hasText = true))
        }
    }

    @Test
    fun `anchors are atoms only in the text-only shape`() {
        // <a>text</a> — the appearance-auto-001 first row member.
        assertTrue(InlineAtomFlow.isAtom("a", null, hasElementChildren = false, hasText = true))
        // An anchor wrapping elements is a real inline subtree (out of
        // scope), and an empty anchor renders nothing to pack.
        assertFalse(InlineAtomFlow.isAtom("a", null, hasElementChildren = true, hasText = true))
        assertFalse(InlineAtomFlow.isAtom("a", null, hasElementChildren = false, hasText = false))
    }

    @Test
    fun `non-widgets and display-overridden widgets are not atoms`() {
        // Generic containers stay on the frozen block path.
        assertFalse(InlineAtomFlow.isAtom("div", null, false, hasText = true))
        assertFalse(InlineAtomFlow.isAtom(null, null, false, hasText = true))
        // css-display-3 §2: a declared block-level display leaves the
        // inline flow; INLINE-family keywords keep the atom.
        assertFalse(InlineAtomFlow.isAtom("button", "BLOCK", false, false))
        assertFalse(InlineAtomFlow.isAtom("input", "FLEX", false, false))
        assertFalse(InlineAtomFlow.isAtom("input", "NONE", false, false))
        assertTrue(InlineAtomFlow.isAtom("input", "INLINE_BLOCK", false, false))
        assertTrue(InlineAtomFlow.isAtom("input", "INLINE", false, false))
    }

    // ── P15 — segmentation ──

    @Test
    fun `appearance-auto children segment into one 16-atom run`() {
        // All sixteen appearance-auto-001 children are atoms.
        val segs = InlineAtomFlow.segment(List(16) { true })
        assertEquals(1, segs.size)
        assertTrue(segs[0].isRun)
        assertEquals((0..15).toList(), segs[0].indices)
    }

    @Test
    fun `non-atoms split runs and lone atoms stay singles`() {
        // A block sibling splits the streak; ≥2 rule holds per streak.
        val segs = InlineAtomFlow.segment(listOf(true, true, false, true))
        assertEquals(3, segs.size)
        assertTrue(segs[0].isRun)
        assertEquals(listOf(0, 1), segs[0].indices)
        assertFalse(segs[1].isRun)
        // The trailing lone atom keeps the frozen block path.
        assertFalse(segs[2].isRun)
    }

    // ── P18 — widget-kind resolution (the wave-20 identity contract) ──

    @Test
    fun `kinds resolve from tag plus the attrs channel`() {
        val k = UAWidgetIntrinsics::kind
        // Buttons: the element and the input aliases.
        assertEquals(UAWidgetIntrinsics.Kind.BUTTON_LIKE, k("button", null, false))
        assertEquals(UAWidgetIntrinsics.Kind.BUTTON_LIKE, k("input", "submit", false))
        // Inputs fold unknown/absent types to the text field (HTML
        // §4.10.5 invalid-value default — also the pre-attrs-decode
        // degradation the renderer seam documents).
        assertEquals(UAWidgetIntrinsics.Kind.TEXT_FIELD, k("input", null, false))
        assertEquals(UAWidgetIntrinsics.Kind.TEXT_FIELD, k("input", "search", false))
        assertEquals(UAWidgetIntrinsics.Kind.RANGE, k("input", "range", false))
        assertEquals(UAWidgetIntrinsics.Kind.CHECKBOX, k("input", "checkbox", false))
        assertEquals(UAWidgetIntrinsics.Kind.COLOR, k("input", "color", false))
        // multiple flips the select family to the listbox.
        assertEquals(UAWidgetIntrinsics.Kind.SELECT, k("select", null, false))
        assertEquals(UAWidgetIntrinsics.Kind.LISTBOX, k("select", null, true))
        assertEquals(UAWidgetIntrinsics.Kind.METER, k("meter", null, false))
        assertEquals(UAWidgetIntrinsics.Kind.PROGRESS, k("progress", null, false))
    }

    // ── P16/P17 — the appearance-auto-001 flow pin ──

    // The sixteen atoms in wire order with their resolved geometry:
    // fixed sizes from the shared table; label-driven widths (a,
    // button, input-button/submit/reset, select, select-multiple) use
    // the ref-measured values the replicas converge on.
    private data class Atom(val w: Double, val h: Double, val d: Double,
                            val mS: Double = 0.0, val mE: Double = 0.0)
    // A-RC2 (wave 22): the label-driven widths are no longer eyeballed
    // off the ref — they are computed from the SAME Arial-metric pin the
    // replica painter measures with, so this flow test and the widget
    // pin table can never disagree about how wide a control is.
    private fun ctrl(label: String): Double =
        UAControlFontMetrics.advance(label, UAWidgetsGeometry.FONT).toDouble()
    private val atoms = listOf(
        // 'a' in the block's INTER 16px face (hmtx 1150/2048 × 16): the
        // anchor is prose, not a control, so it keeps the ref's Inter.
        Atom(8.984375, 20.0, 4.0),                     //  0 a
        Atom(ctrl("button") + 16.0, 21.0, 6.0),        //  1 button
        Atom(153.0, 21.0, 6.0),                        //  2 input-text
        Atom(153.0, 21.0, 6.0),                        //  3 input-search
        Atom(183.0, 36.0, 0.0),                        //  4 textarea
        Atom(ctrl("input-button") + 16.0, 21.0, 6.0),  //  5 input-button
        Atom(ctrl("input-submit") + 16.0, 21.0, 6.0),  //  6 input-submit
        Atom(ctrl("input-reset") + 16.0, 21.0, 6.0),   //  7 input-reset
        Atom(129.0, 21.0, 6.0, 2.0, 2.0),              //  8 range
        Atom(13.0, 13.0, 3.0, 4.5, 3.0),               //  9 checkbox
        Atom(13.0, 13.0, 3.0, 4.5, 3.0),               // 10 radio
        Atom(50.0, 27.0, 9.0),                         // 11 color
        Atom(ctrl("select") + 5.0 + 17.42, 19.0, 8.0), // 12 select
        Atom(ctrl("select-multiple") + 6.0, 70.0, 7.0),// 13 select-multiple
        Atom(80.0, 16.0, 3.0),                         // 14 meter
        Atom(160.0, 16.0, 3.0),                        // 15 progress
    )

    private fun plan() = InlineAtomFlow.layout(
        widths = atoms.map { it.w },
        heights = atoms.map { it.h },
        descents = atoms.map { it.d },
        marginStarts = atoms.map { it.mS },
        marginEnds = atoms.map { it.mE },
        // The refs' `#container { width: 500px }` content width.
        availableWidth = 500.0,
        gapPx = UAWidgetIntrinsics.ATOM_GAP_PX,
        // P17b (fix 4) — the §10.8.1 strut, exactly as the adapter
        // (InlineFlowLayout) passes it.
        strutAscentPx = UAWidgetIntrinsics.STRUT_ASCENT_PX,
        strutDescentPx = UAWidgetIntrinsics.STRUT_DESCENT_PX,
    )

    @Test
    fun `appearance-auto rows split 4-4-7-1 like the ref`() {
        val p = plan()
        // Wave-22 vertical solve. Row tops are 0 / 22 / 64 / 136 (rel to
        // the container content origin abs y16), i.e. ABS 16 / 38 / 80 /
        // 152 — every one of those is a measured ref band. Pitches come
        // from the corrected strut (ascent 16, descent 4).
        // Row 1: a+button+input-text+input-search share the first line;
        // the ANCHOR keeps the row top (ascent 16 = the strut) while the
        // 21px controls hang 1px lower — abs y17, the ref band.
        assertEquals(0.0, p.y[0], 1e-6)                     // a (abs 16)
        assertEquals(1.0, p.y[1], 1e-6)                     // button (abs 17)
        assertEquals(1.0, p.y[2], 1e-6)                     // input-text
        assertEquals(1.0, p.y[3], 1e-6)                     // input-search
        // Row 2 top = 16A + 6D = 22 → abs 38, the ref textarea border row.
        assertEquals(22.0, p.y[4], 1e-6)                    // textarea
        // Buttons hang from the shared baseline: 22 + 36A − 15a = 43
        // (abs 59 — the ref input-button border row).
        assertEquals(43.0, p.y[5], 1e-6)                    // input-button
        assertEquals(43.0, p.y[7], 1e-6)                    // input-reset
        // Row 3 top = 22 + (36+6) = 64 → abs 80: the listbox is the
        // ascent giant (70 − 7 descent = 63) and tops the row (ref y80).
        assertEquals(64.0, p.y[13], 1e-6)                   // select-multiple
        // Row-3 baseline = 64 + 63A = 127 → abs 143. Each atom below is
        // baseline − (h − d) and matches its ref band EXACTLY:
        assertEquals(112.0, p.y[8], 1e-6)                   // range   (abs 128)
        assertEquals(117.0, p.y[9], 1e-6)                   // checkbox(abs 133)
        assertEquals(117.0, p.y[10], 1e-6)                  // radio   (abs 133)
        assertEquals(109.0, p.y[11], 1e-6)                  // color   (abs 125)
        assertEquals(116.0, p.y[12], 1e-6)                  // select  (abs 132)
        // meter rides the same baseline: 127 − 13a = 114 (off-canvas).
        assertEquals(114.0, p.y[14], 1e-6)                  // meter
        // Row 4: progress alone at 64 + (63A + 9D) = 136 top (abs 152);
        // the strut holds the baseline at 136 + 16, so the 16px bar sits
        // at 152 − 13a = 139 → abs 155, whose 4px ink inset is the ref's
        // measured bar band y159..167.
        assertEquals(139.0, p.y[15], 1e-6)                  // progress
        assertEquals(0.0, p.x[15], 1e-6)
    }

    @Test
    fun `appearance-auto x positions match the ref bands`() {
        val p = plan()
        // Wave-22 horizontal solve (gap 4.5 = Inter's 16px space
        // advance). Every expectation below is quoted with the ABS
        // column (+16) and the ref's PAINTED border column, which
        // Chromium pixel-snaps — so a residual under 0.5 is exact.
        // Row 1: button abs 29.48 (ref 29), field abs 87.05 (ref 87),
        // search abs 244.55 (ref 245).
        assertEquals(0.0, p.x[0], 1e-6)
        assertEquals(13.4844, p.x[1], 1e-3)
        assertEquals(71.0546, p.x[2], 1e-3)
        assertEquals(228.5546, p.x[3], 1e-3)
        // Row 2: textarea flush left; input-button abs 203.5 (ref 204),
        // input-submit abs 294.42 (ref 294), input-reset abs 387.55
        // (ref 388).
        assertEquals(0.0, p.x[4], 1e-6)
        assertEquals(187.5, p.x[5], 1e-3)
        assertEquals(278.423, p.x[6], 1e-3)
        assertEquals(371.5465, p.x[7], 1e-3)
        // Row 3, the band wave 21 missed by 5-7px. Range carries its UA
        // 2px margin (abs 18 → track ink abs 19, the ref probe); the
        // checkbox and radio land on their ref columns EXACTLY.
        assertEquals(2.0, p.x[8], 1e-6)                     // range   (abs 18)
        assertEquals(142.0, p.x[9], 1e-6)                   // checkbox(abs 158 = ref)
        assertEquals(167.0, p.x[10], 1e-6)                  // radio   (abs 183 = ref)
        assertEquals(187.5, p.x[11], 1e-3)                  // color   (abs 203.5, ref 204)
        assertEquals(242.0, p.x[12], 1e-6)                  // select  (abs 258 = ref)
        assertEquals(303.7506, p.x[13], 1e-3)               // listbox (abs 319.75, ref 320)
        // Meter still fits row 3 (ends 479.47 ≤ 500) — the ref clips it
        // past the 390px viewport but it shares the row.
        assertEquals(399.4653, p.x[14], 1e-3)
        // Flow extent: widest row (row 3); total height = row-4 top 136
        // + the strut line (16 ascent + 4 descent) = 156.
        assertEquals(479.4653, p.width, 1e-3)
        assertEquals(156.0, p.height, 1e-6)
    }

    @Test
    fun `first atom in a row never wraps and unbounded never wraps`() {
        // A 600-wide atom in the 500 container holds row 1 (the loose
        // first-never-wraps rule, mirroring the float packer's rule 7).
        val p = InlineAtomFlow.layout(
            widths = listOf(600.0, 50.0),
            heights = listOf(10.0, 10.0),
            descents = listOf(0.0, 0.0),
            marginStarts = listOf(0.0, 0.0),
            marginEnds = listOf(0.0, 0.0),
            availableWidth = 500.0,
            gapPx = 4.0,
        )
        assertEquals(listOf(0.0, 0.0), p.x)
        assertEquals(listOf(0.0, 10.0), p.y)
        // Unbounded width → a single row, gaps between atoms.
        val q = InlineAtomFlow.layout(
            widths = listOf(600.0, 50.0),
            heights = listOf(10.0, 10.0),
            descents = listOf(0.0, 0.0),
            marginStarts = listOf(0.0, 0.0),
            marginEnds = listOf(0.0, 0.0),
            availableWidth = Double.POSITIVE_INFINITY,
            gapPx = 4.0,
        )
        assertEquals(listOf(0.0, 604.0), q.x)
        assertEquals(listOf(0.0, 0.0), q.y)
    }

    // ── renderer plan gate (blockInlineAtomSegments) ──

    // IR keyword wire shape — bare JSON string primitive.
    private fun kw(type: String, keyword: String) =
        IRProperty(type, Json.parseToJsonElement("\"$keyword\""))

    // Minimal IRComponent shells matching the appearance-auto-001 wire
    // (meta.sourceTag decodes onto _tag; Appearance rides properties).
    private fun widget(id: String, tag: String, text: String? = null) = IRComponent(
        id = id, name = id, properties = emptyList(), _text = text, _tag = tag,
    )
    private fun plainChild(id: String) = IRComponent(id = id, name = id)

    @Test
    fun `renderer plan exists only when an atom run exists`() {
        // The a+button pair (row-1 head) produces a run plan…
        val plan = ComponentRenderer.blockInlineAtomSegments(
            listOf(widget("a0", "a", text = "a"), widget("b1", "button", text = "button"))
        )
        assertEquals(1, plan!!.size)
        assertTrue(plan[0].isRun)
        // …while atom-free (or lone-atom) children keep the frozen loop.
        assertNull(
            ComponentRenderer.blockInlineAtomSegments(
                listOf(plainChild("d"), widget("i", "input"), plainChild("e"))
            )
        )
        // A display:block override on a widget suppresses its atom-ness
        // (css-display-3 §2) — the pair below never forms a run.
        assertNull(
            ComponentRenderer.blockInlineAtomSegments(
                listOf(
                    IRComponent("i1", "i1", listOf(kw("Display", "BLOCK")), _tag = "input"),
                    widget("i2", "input"),
                )
            )
        )
    }

    @Test
    fun `renderer kind seam reads the decoded attrs capsule`() {
        // Absent capsule (pre-wave-20 wires): every <input> folds to
        // TEXT_FIELD and <select> to the menulist — the HTML parser's
        // missing-attribute states.
        assertEquals(
            UAWidgetIntrinsics.Kind.TEXT_FIELD,
            ComponentRenderer.atomKindOf(widget("i", "input"))
        )
        assertEquals(
            UAWidgetIntrinsics.Kind.SELECT,
            ComponentRenderer.atomKindOf(widget("s", "select"))
        )
        // Present capsule (the wave-20 widget-identity wire, decoded by
        // lane W2's IRAttrs): type/multiple select the exact family.
        assertEquals(
            UAWidgetIntrinsics.Kind.CHECKBOX,
            ComponentRenderer.atomKindOf(
                IRComponent("c", "c", _tag = "input",
                    attrs = com.styleconverter.runtime.core.ir.IRAttrs(type = "checkbox"))
            )
        )
        assertEquals(
            UAWidgetIntrinsics.Kind.LISTBOX,
            ComponentRenderer.atomKindOf(
                IRComponent("m", "m", _tag = "select",
                    attrs = com.styleconverter.runtime.core.ir.IRAttrs(multiple = true))
            )
        )
    }

    // ── Wave-43 V1 — the replaced-image atom family ──

    @Test
    fun `replaced images join the inline flow only with a delivered raster`() {
        // Default (no attestation) is the frozen pre-wave-43 answer: an
        // <img> is NOT an atom — the byte-compat pin for every caller
        // that does not pass the new fact.
        assertFalse(InlineAtomFlow.isAtom("img", null, false, hasText = false))
        // The caller-attested delivered raster admits the whole replaced
        // family (the paint channel's tag table).
        for (tag in listOf("img", "embed", "object", "video")) {
            assertTrue(tag, InlineAtomFlow.isAtom(tag, null, false, false, hasDeliveredRaster = true))
        }
        // The flag NEVER admits a non-replaced tag: an attestation for a
        // <div> is a renderer bug the gate refuses rather than packs.
        assertFalse(InlineAtomFlow.isAtom("div", null, false, false, hasDeliveredRaster = true))
        // css-display-3 §2 still runs first: a declared block-level
        // display takes even a delivered img out of the inline flow,
        // while INLINE-family keywords keep it.
        assertFalse(InlineAtomFlow.isAtom("img", "BLOCK", false, false, hasDeliveredRaster = true))
        assertTrue(InlineAtomFlow.isAtom("img", "INLINE_BLOCK", false, false, hasDeliveredRaster = true))
    }

    // The VERBATIM wave42-final per-test IR of css-ui box-sizing-010/-016
    // (tools/titan/runs/wave42-final/sections/css-ui/per-test-ir/): a <p>,
    // a 70×70 inline-block div (margin-bottom 30 "for alignement"), and an
    // <img src=…svg> under `box-sizing: border-box; padding-bottom: 30px;
    // max-height: 100px`. Only the img's src differs between the two.
    private fun boxSizingWire(n: String, src: String) = """
        {"irVersion":2,"minReaderVersion":2,"components":[
        {"id":"wpt__css-ui__box-sizing-${n}__0-328","name":"wpt__css-ui__box-sizing-${n}__0","properties":[],
         "text":"Test passes if there are 2 filled green squares and they are the same size.",
         "meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css-ui__box-sizing-${n}__1-329","name":"wpt__css-ui__box-sizing-${n}__1","properties":[
         {"type":"Display","data":"INLINE_BLOCK"},{"type":"MarginBottom","data":{"px":30}},
         {"type":"Width","data":{"type":"length","px":70}},{"type":"Height","data":{"type":"length","px":70}},
         {"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],
         "meta":{"role":"ws-after"}},
        {"id":"wpt__css-ui__box-sizing-${n}__2-330","name":"wpt__css-ui__box-sizing-${n}__2","properties":[
         {"type":"BoxSizing","data":"BORDER_BOX"},{"type":"Width","data":"auto"},{"type":"Height","data":"auto"},
         {"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},
         {"type":"PaddingBottom","data":{"px":30}},{"type":"MaxHeight","data":{"type":"length","px":100}}],
         "meta":{"sourceTag":"img","attrs":{"src":"$src"}}}]}
    """.trimIndent()

    /** The per-sibling facts EXACTLY as blockInlineAtomSegments derives
     *  them from the decoded wire, plus the wave-43 attestation. */
    private fun atomFact(c: IRComponent, rasterDelivered: Boolean) = InlineAtomFlow.isAtom(
        tag = c._tag,
        displayKeyword = c.properties.firstOrNull { it.type == "Display" }
            ?.let { com.styleconverter.runtime.core.types.ValueExtractors.extractKeyword(it.data)?.uppercase() },
        hasElementChildren = !c.children.isNullOrEmpty(),
        hasText = !c._text.isNullOrEmpty(),
        // Both halves of the paint decision: the wire candidacy is read
        // off the decoded component; the registry-resolve half has no
        // JVM bitmap store, so the test injects it — exactly the seam
        // the renderer wiring will fill with DocumentImageRegistry.
        hasDeliveredRaster = rasterDelivered &&
            com.styleconverter.runtime.images.ReplacedImageContent.isCandidate(c),
    )

    @Test
    fun `box-sizing-010 and 016 verbatim wire facts gate on the delivered raster`() {
        for ((n, src) in listOf("010" to "css/css-ui/support/w100_h100.svg",
                                "016" to "css/css-ui/support/r1-1.svg")) {
            val comps = com.styleconverter.runtime.core.ir.IRDocumentDecoder
                .decode(boxSizingWire(n, src)).components
            // The wire candidacy (replaced sourceTag + non-blank src) is
            // true for the img alone — the <p> has text, the div has no
            // sourceTag at all on this wire.
            assertEquals(listOf(false, false, true),
                comps.map { com.styleconverter.runtime.images.ReplacedImageContent.isCandidate(it) })
            // Undelivered (the pre-raster-OFF arm): nothing is an atom —
            // the frozen stacking stays byte-identical.
            assertEquals(listOf(false, false, false), comps.map { atomFact(it, rasterDelivered = false) })
            // Delivered (the pre-raster-ON arm): the img is admitted…
            assertEquals(listOf(false, false, true), comps.map { atomFact(it, rasterDelivered = true) })
            // …but the pair still has NO ≥2 run: the div's inline-block
            // admission rides the InlineBlockAtom family (B1–B7), whose
            // B5 margin gate refuses its 30px margin-bottom today. Pinned
            // so the residual stacking is a named number, not a surprise:
            // packing 010/016 needs the margin-aware inline-block
            // follow-up, not more img admission.
            val segs = InlineAtomFlow.segment(comps.map { atomFact(it, rasterDelivered = true) })
            assertTrue(segs.none { it.isRun })
            assertNull(InlineBlockAtom.spec(comps[1].properties,
                hasOwnText = false, hasOwnRuns = false, containerDeclaresLineHeight = false))
        }
    }

    @Test
    fun `five consecutive delivered imgs form one packable run`() {
        // The box-sizing-008/-009 sibling shape (text root, <p>, the
        // inline-block ref div, then FIVE <img> roots): admitting the
        // delivered imgs makes exactly one 5-atom run — the div stays a
        // single (B5, above) and never breaks the img streak.
        val flags = listOf(false, false, false, true, true, true, true, true)
        val segs = InlineAtomFlow.segment(flags)
        assertEquals(4, segs.size)
        assertTrue(segs[3].isRun)
        assertEquals(listOf(3, 4, 5, 6, 7), segs[3].indices)
    }

    @Test
    fun `a replaced atom measures with the sec 10-4 constrained content size`() {
        // The admitted atom has NO fixed UA spec: InlineFlowLayout
        // measures it free, and the rendered box under width/height auto
        // is ReplacedImageContent's INTRINSIC mode — the wave-42 §10.4
        // table over the wire bounds. On box-sizing-010's verbatim
        // properties (border-box maxH 100 − 30px pad band = content maxH
        // 70) the 100×100 pre-raster resolves to the ref's 70×70 content…
        val img010 = com.styleconverter.runtime.core.ir.IRDocumentDecoder
            .decode(boxSizingWire("010", "css/css-ui/support/w100_h100.svg")).components[2]
        val used010 = com.styleconverter.runtime.images.ReplacedImageContent.constrainedAutoContentSize(
            properties = img010.properties.map { it.type to it.data },
            // The composed capture runs WPT-gated; BoxSizing is DECLARED
            // border-box on this wire, so the tri-state is explicit.
            wptCaptureMode = true,
            intrinsicWidthPx = 100f, intrinsicHeightPx = 100f, aspectRatio = 1f,
        )
        assertEquals(70f, used010.widthPx, 1e-3f)
        assertEquals(70f, used010.heightPx, 1e-3f)
        // …and 016's r1-1 pre-raster (150×150, the asserted 1:1 ratio)
        // resolves to the SAME 70×70. With the 30px pad band back on, the
        // atom's measured border box is 70×100 — baseline at its bottom
        // margin edge (§10.8.1 replaced ⇒ descent 0), matching the ref's
        // side-by-side geometry with the 70×70(+30 margin) div.
        val img016 = com.styleconverter.runtime.core.ir.IRDocumentDecoder
            .decode(boxSizingWire("016", "css/css-ui/support/r1-1.svg")).components[2]
        val used016 = com.styleconverter.runtime.images.ReplacedImageContent.constrainedAutoContentSize(
            properties = img016.properties.map { it.type to it.data },
            wptCaptureMode = true,
            intrinsicWidthPx = 150f, intrinsicHeightPx = 150f, aspectRatio = 1f,
        )
        assertEquals(70f, used016.widthPx, 1e-3f)
        assertEquals(70f, used016.heightPx, 1e-3f)
    }
}
