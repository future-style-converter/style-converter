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
    private val atoms = listOf(
        Atom(8.0, 20.0, 5.0),        //  0 a         (measured text)
        Atom(54.0, 21.0, 6.0),       //  1 button    (measured label)
        Atom(153.0, 21.0, 6.0),      //  2 input-text     (table)
        Atom(153.0, 21.0, 6.0),      //  3 input-search   (table)
        Atom(184.0, 36.0, 0.0),      //  4 textarea       (table)
        Atom(86.0, 21.0, 6.0),       //  5 input-button  (measured)
        Atom(89.0, 21.0, 6.0),       //  6 input-submit  (measured)
        Atom(82.0, 21.0, 6.0),       //  7 input-reset   (measured)
        Atom(129.0, 21.0, 6.0),      //  8 range          (table)
        Atom(13.0, 13.0, 3.0, 4.0, 3.0), // 9 checkbox    (table+margins)
        Atom(13.0, 13.0, 3.0, 4.0, 3.0), // 10 radio      (table+margins)
        Atom(50.0, 27.0, 9.0),       // 11 color          (table, fix 4)
        Atom(55.0, 19.0, 8.0),       // 12 select        (measured w, fix 4)
        Atom(87.0, 70.0, 7.0),       // 13 select-multiple (measured w, fix 4)
        Atom(80.0, 16.0, 4.5),       // 14 meter          (table, fix 4)
        Atom(160.0, 16.0, 4.5),      // 15 progress       (table, fix 4)
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
        // Row membership read off the y coordinates: row tops are 0 /
        // 21 / 63 / 135 (P17 pitches 21, 42, 72 — see the pitch pin).
        // Row 1: a+button+input-text+input-search share the first line
        // (the lane's y17-37 ref band, container-relative y0).
        assertEquals(0.0, p.y[0], 1e-9)                     // a
        assertEquals(0.0, p.y[1], 1e-9)                     // button
        assertEquals(0.0, p.y[2], 1e-9)                     // input-text
        assertEquals(0.0, p.y[3], 1e-9)                     // input-search
        // Row 2: the textarea tops the second line (ref y38 → rel 21).
        assertEquals(21.0, p.y[4], 1e-9)                    // textarea
        // Buttons hang from the shared baseline: 21 + 36A − 15a = 42.
        assertEquals(42.0, p.y[5], 1e-9)                    // input-button
        assertEquals(42.0, p.y[7], 1e-9)                    // input-reset
        // Row 3 top = 21 + (36+6) = 63 (ref y80 → rel 63): the listbox
        // is the ascent giant (70 − 7 descent = 63) and tops the row.
        assertEquals(63.0, p.y[13], 1e-9)                   // select-multiple
        // Row-3 baseline = 63 + 63A = 126 (abs y143 — checkbox top 133):
        // range 126 − 15a = 111 (ref box y127.5 → rel 110.5, ±0.5).
        assertEquals(111.0, p.y[8], 1e-9)                   // range
        // checkbox/radio: 126 − 10a = 116 (ref y133 → rel 116, exact).
        assertEquals(116.0, p.y[9], 1e-9)                   // checkbox
        assertEquals(116.0, p.y[10], 1e-9)                  // radio
        // color: 126 − 18a = 108 (ref y125 → rel 108, exact).
        assertEquals(108.0, p.y[11], 1e-9)                  // color
        // select: 126 − 11a = 115 (ref slab y132 → rel 115, exact).
        assertEquals(115.0, p.y[12], 1e-9)                  // select
        // meter rides the same baseline: 126 − 11.5a = 114.5.
        assertEquals(114.5, p.y[14], 1e-9)                  // meter
        // Row 4: progress alone at 63 + (63A + 9D) = 135 top; the P17b
        // strut holds the baseline at 135 + 15, so the 16px bar sits at
        // 150 − 11.5a = 138.5 (ref box y155.5 → rel 138.5, exact — the
        // ink band y159.5-166.5 the fix-4 probe measured).
        assertEquals(138.5, p.y[15], 1e-9)                  // progress
        assertEquals(0.0, p.x[15], 1e-9)
    }

    @Test
    fun `appearance-auto x positions match the ref bands`() {
        val p = plan()
        // Row 1 cursor walk (gap 4.16): ref input-text at x87−16=71,
        // input-search at 245−16=229 — the pure walk lands within a px.
        assertEquals(0.0, p.x[0], 1e-9)
        assertEquals(12.16, p.x[1], 1e-9)
        assertEquals(70.32, p.x[2], 1e-9)
        assertEquals(227.48, p.x[3], 1e-9)
        // Row 2: textarea flush left; input-button at 188.16 (ref
        // x204−16=188 — exact to the sub-px).
        assertEquals(0.0, p.x[4], 1e-9)
        assertEquals(188.16, p.x[5], 1e-9)
        assertEquals(278.32, p.x[6], 1e-9)
        assertEquals(371.48, p.x[7], 1e-9)
        // Row 3: checkbox origin includes its 4px UA margin-left
        // (129 range + 4.16 gap + 4 margin).
        assertEquals(0.0, p.x[8], 1e-9)
        assertEquals(137.16, p.x[9], 1e-9)
        // Meter still fits row 3 (ends 465.96 ≤ 500) — the ref clips it
        // past the 390px viewport but it shares the row.
        assertEquals(385.96, p.x[14], 1e-9)
        // Flow extent: widest row (row 3); total height = row-4 top 135
        // + the P17b strut line (15 ascent + 5 descent) = 155.
        assertEquals(465.96, p.width, 1e-9)
        assertEquals(155.0, p.height, 1e-9)
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
}
