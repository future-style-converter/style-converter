package com.styleconverter.runtime.typography.inline

// Wave 44 (lane U1) — the inline-run FOLD pins, on the REAL WIRE.
//
// The four Folded documents below are tools/titan/runs/wave43-final/
// sections/<section>/per-test-ir/<test>.json VERBATIM (minified only — no
// key or value was altered), i.e. the exact bytes the wave-43 Android
// gate consumed for the measured victims:
//   hyphens-manual-inline-010  android-ref 0.6570  (3 members → 3 LINES)
//   hyphens-manual-inline-011  android-ref 0.8480
//   hyphens-auto-inline-010    android-ref 0.9303
//   CSS2 inherit-computed-001  android-ref 0.8378
// The two Bailed documents are the same-run payloads of hyphens-out-of-
// flow-001 (abspos member — deferred by scope) and text-decoration-
// inset-014 (a <u> member whose UA underline never rides the wire),
// trimmed to their runs host (+ its child) with the host's dangling
// slot removed; every kept byte is verbatim.
//
// Each wire test decodes + composes through the PRODUCTION path
// (IRDocumentDecoder → SlotComposer → InlineRunPlan.resolve), exactly
// like the harness screens, so a fold that works on hand-built entries
// but dies on the wire cannot pass. Synthetic cases then pin each bail
// rule in isolation.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRDocumentDecoder
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.InlineRunPlan
import com.styleconverter.runtime.core.renderer.SlotComposer
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineRunFoldTest {

    /** Decode + compose a captured v2 document, return root [index]. */
    private fun root(doc: String, index: Int): IRComponent =
        SlotComposer.compose(IRDocumentDecoder.decode(doc))[index]

    /** Resolve the host's runs plan (production resolver), then fold. */
    private fun foldOf(host: IRComponent): InlineRunFold.Outcome {
        // The production plan the renderer seam hands the fold.
        val plan = InlineRunPlan.resolve(host.runs, host.children)
        assertNotNull("the wire must resolve a runs plan", plan)
        return InlineRunFold.fold(
            entries = plan!!.entries,
            children = host.children!!,
            hostProperties = host.properties,
            // The seam passes LocalContentLanguage.current — the host's
            // own decoded meta.lang is exactly that value here.
            hostEffectiveLang = host.lang,
        )
    }

    // ── the four measured victims, verbatim wire ─────────────────────────

    @Test
    fun `hyphens-manual-inline-010 - three members fold to one paragraph, span hyphens adopted`() {
        // Roots: [0] instruction <p>, [1] the runs host, [2] reference div.
        val outcome = foldOf(root(MANUAL_INLINE_010, 1))
        val folded = outcome as InlineRunFold.Outcome.Folded
        // "DNA " + span text + "." — ONE flowing paragraph, the exact
        // string of the reference div the ref renders identically.
        assertEquals("DNA means Deoxyribonucleic acid.", folded.text)
        // The host declares no Hyphens; the span's MANUAL governs the
        // paragraph and is appended LAST so the own-list-first read wins.
        assertTrue(folded.adoptedHyphens)
        assertEquals("Hyphens", folded.properties.last().type)
        assertEquals(JsonPrimitive("MANUAL"), folded.properties.last().data)
        // Exactly one Hyphens in the merged list (never a duplicate).
        assertEquals(1, folded.properties.count { it.type == "Hyphens" })
        assertEquals(0, folded.droppedEmptyMembers)
    }

    @Test
    fun `hyphens-manual-inline-011 - soft hyphens survive the fold verbatim`() {
        // Roots: [0] the runs host (this capture has no instruction <p>).
        val outcome = foldOf(root(MANUAL_INLINE_011, 0))
        val folded = outcome as InlineRunFold.Outcome.Folded
        // The span's two U+00AD stay in place — rule A / the manual-mode
        // hyphen materialization are downstream pipeline stages, not ours.
        assertEquals("DNA means Deoxy­ribo­nucleic acid.", folded.text)
        assertTrue(folded.adoptedHyphens)
        assertEquals(JsonPrimitive("MANUAL"), folded.properties.last().data)
    }

    @Test
    fun `hyphens-auto-inline-010 - AUTO adoption with a matching language`() {
        val outcome = foldOf(root(AUTO_INLINE_010, 0))
        val folded = outcome as InlineRunFold.Outcome.Folded
        // "There are " + "new engines now" + "." — one paragraph.
        assertEquals("There are new engines now.", folded.text)
        // Span `hyphens: auto` + lang "en" == host lang "en" → adopted;
        // the paragraph can select the English dictionary downstream.
        assertTrue(folded.adoptedHyphens)
        assertEquals(JsonPrimitive("AUTO"), folded.properties.last().data)
    }

    @Test
    fun `inherit-computed-001 - empty em drops and its flanking spaces collapse to one`() {
        val outcome = foldOf(root(INHERIT_COMPUTED_001, 0))
        val folded = outcome as InlineRunFold.Outcome.Folded
        // "… size " + <em></em> + " and …" — css-text-3 §4.1.1 collapses
        // the space AFTER the empty inline into the one before it, exactly
        // as the Chromium ref paints "size ▮and" with a single word space.
        assertEquals("This line is all in one font size and there is no red.", folded.text)
        // The empty <em> (its Color/border-color payload is paint-inert
        // without glyphs or border styles) is dropped — and REPORTED.
        assertEquals(1, folded.droppedEmptyMembers)
        // No member declared Hyphens → nothing adopted, nothing appended:
        // the paragraph property list is the host's own, unchanged.
        assertEquals(false, folded.adoptedHyphens)
        assertEquals(root(INHERIT_COMPUTED_001, 0).properties.size, folded.properties.size)
    }

    // ── the two deferred shapes, verbatim wire ───────────────────────────

    @Test
    fun `hyphens-out-of-flow-001 - an abspos member bails to the stacked fallback`() {
        val outcome = foldOf(root(OUT_OF_FLOW_001_HOST3, 0))
        // Position is not in the member property sets: out-of-flow members
        // are wave-scope-deferred (they need a static-position mount, not
        // a glyph contribution) — the bail names the offending type.
        assertEquals("member-prop:Position", (outcome as InlineRunFold.Outcome.Bailed).reason)
    }

    @Test
    fun `text-decoration-inset-014 - a u member bails on its UA-styled tag`() {
        val outcome = foldOf(root(INSET_014_HOST, 0))
        // <u>'s UA underline never rides the wire, so folding its text
        // flat would silently drop a visible decoration — tag-gated out.
        assertEquals("member-tag:u", (outcome as InlineRunFold.Outcome.Bailed).reason)
    }

    // ── synthetic pins for each remaining gate rule ──────────────────────

    /** Hand-built text-leaf member with [props] and optional [lang]. */
    private fun member(
        tag: String,
        text: String?,
        props: List<IRProperty> = emptyList(),
        lang: String? = null,
    ) = IRComponent(
        id = "m", name = "m", properties = props,
        _text = text, _tag = tag, lang = lang,
    )

    /** One text run + one child member, the minimal foldable shape. */
    private fun entries(runText: String) = listOf(
        InlineRunPlan.Entry.Text(runText),
        InlineRunPlan.Entry.Child(0),
    )

    @Test
    fun `cross-boundary space collapse - run trailing space eats the member's leading space`() {
        val outcome = InlineRunFold.fold(
            entries = entries("a "),
            children = listOf(member("span", " b")),
            hostProperties = emptyList(),
            hostEffectiveLang = null,
        )
        // "a " + " b" → "a b": one collapsible space survives (§4.1.1).
        assertEquals("a b", (outcome as InlineRunFold.Outcome.Folded).text)
    }

    @Test
    fun `hyphens conflict - a member contradicting the host's declaration bails`() {
        val outcome = InlineRunFold.fold(
            entries = entries("x "),
            children = listOf(member("span", "y", listOf(IRProperty("Hyphens", JsonPrimitive("MANUAL"))))),
            // Host declared NONE: adopting the member's MANUAL would strip
            // soft hyphens the member wants kept (or vice versa).
            hostProperties = listOf(IRProperty("Hyphens", JsonPrimitive("NONE"))),
            hostEffectiveLang = null,
        )
        assertEquals("hyphens-conflict", (outcome as InlineRunFold.Outcome.Bailed).reason)
    }

    @Test
    fun `hyphens conflict - two members disagreeing bail`() {
        val outcome = InlineRunFold.fold(
            entries = listOf(InlineRunPlan.Entry.Child(0), InlineRunPlan.Entry.Child(1)),
            children = listOf(
                member("span", "a", listOf(IRProperty("Hyphens", JsonPrimitive("MANUAL")))),
                member("span", "b", listOf(IRProperty("Hyphens", JsonPrimitive("AUTO"))), lang = null),
            ),
            hostProperties = emptyList(),
            hostEffectiveLang = null,
        )
        // MANUAL then AUTO: no single paragraph mode is faithful to both.
        assertEquals("hyphens-conflict", (outcome as InlineRunFold.Outcome.Bailed).reason)
    }

    @Test
    fun `auto adoption - a member language diverging from the paragraph's bails`() {
        val outcome = InlineRunFold.fold(
            entries = entries("x "),
            children = listOf(
                member("span", "y", listOf(IRProperty("Hyphens", JsonPrimitive("AUTO"))), lang = "fr"),
            ),
            // Paragraph language is English — a French dictionary cannot
            // be selected for the whole paragraph (§6.1 language-appropriate).
            hostProperties = emptyList(),
            hostEffectiveLang = "en",
        )
        assertEquals("hyphens-lang-divergence", (outcome as InlineRunFold.Outcome.Bailed).reason)
    }

    @Test
    fun `host gates - text-transform and vertical writing modes bail`() {
        // text-transform rewrites the merged string downstream.
        val tt = InlineRunFold.fold(
            entries = entries("x "),
            children = listOf(member("span", "y")),
            hostProperties = listOf(IRProperty("TextTransform", JsonPrimitive("UPPERCASE"))),
            hostEffectiveLang = null,
        )
        assertEquals("host-text-transform", (tt as InlineRunFold.Outcome.Bailed).reason)
        // A vertical host renders through the rotated branch — uncalibrated.
        val wm = InlineRunFold.fold(
            entries = entries("x "),
            children = listOf(member("span", "y")),
            hostProperties = listOf(IRProperty("WritingMode", JsonPrimitive("VERTICAL_RL"))),
            hostEffectiveLang = null,
        )
        assertEquals("host-vertical-writing-mode", (wm as InlineRunFold.Outcome.Bailed).reason)
    }

    @Test
    fun `member gates - strut-bearing empty member and tab content bail`() {
        // FontSize on an EMPTY inline still contributes its strut to the
        // line box (CSS 2.1 §10.8) — dropping it would shrink the line.
        val strut = InlineRunFold.fold(
            entries = entries("x "),
            children = listOf(member("em", null, listOf(IRProperty("FontSize", JsonPrimitive(32))))),
            hostProperties = emptyList(),
            hostEffectiveLang = null,
        )
        assertEquals("member-prop:FontSize", (strut as InlineRunFold.Outcome.Bailed).reason)
        // A tab would be rewritten by tab-size expansion downstream.
        val tab = InlineRunFold.fold(
            entries = entries("x\ty "),
            children = listOf(member("span", "z")),
            hostProperties = emptyList(),
            hostEffectiveLang = null,
        )
        assertEquals("contains-tab", (tab as InlineRunFold.Outcome.Bailed).reason)
    }

    @Test
    fun `member gates - a member carrying its own nested runs bails`() {
        val nested = member("span", "y").copy(
            // A member with its own inline interleave is a real subtree.
            runs = listOf(com.styleconverter.runtime.core.ir.IRRun(text = "y")),
        )
        val outcome = InlineRunFold.fold(
            entries = entries("x "),
            children = listOf(nested),
            hostProperties = emptyList(),
            hostEffectiveLang = null,
        )
        assertEquals("member-has-runs", (outcome as InlineRunFold.Outcome.Bailed).reason)
    }

    companion object {
        // tools/titan/runs/wave43-final/sections/css-text/per-test-ir/
        // wpt__css-text__hyphens__hyphens-manual-inline-010.json — verbatim.
        private val MANUAL_INLINE_010 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text__hyphens__hyphens-manual-inline-010__0-255","name":"wpt__css-text__hyphens__hyphens-manual-inline-010__0","properties":[],"text":"Test passes if the characters inside of each black-bordered rectangles are laid out identically. Only \"ucleic\" should be outside of the black-bordered rectangles.","meta":{"sourceTag":"p","lang":"en"}},{"id":"wpt__css-text__hyphens__hyphens-manual-inline-010__1-256","name":"wpt__css-text__hyphens__hyphens-manual-inline-010__1","properties":[{"type":"BorderTopWidth","data":{"px":2}},{"type":"BorderRightWidth","data":{"px":2}},{"type":"BorderBottomWidth","data":{"px":2}},{"type":"BorderLeftWidth","data":{"px":2}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"FontFamily","data":["monospace"]},{"type":"FontSize","data":{"px":32,"original":{"type":"length","px":32}}},{"type":"MarginBottom","data":{"original":{"v":0.25,"u":"EM"}}},{"type":"Width","data":{"type":"length","original":{"v":10,"u":"CH"}}}],"text":"DNA .","meta":{"role":"ws-after","lang":"en","runs":[{"text":"DNA "},{"child":"hyphens__hyphens-manual-inline-010__1__0"},{"text":"."}]}},{"id":"hyphens__hyphens-manual-inline-010__1__0-257","name":"hyphens__hyphens-manual-inline-010__1__0","properties":[{"type":"Hyphens","data":"MANUAL"}],"slot":{"parent":"wpt__css-text__hyphens__hyphens-manual-inline-010__1-256"},"text":"means Deoxyribonucleic acid","meta":{"sourceTag":"span","lang":"en"}},{"id":"wpt__css-text__hyphens__hyphens-manual-inline-010__2-258","name":"wpt__css-text__hyphens__hyphens-manual-inline-010__2","properties":[{"type":"BorderTopWidth","data":{"px":2}},{"type":"BorderRightWidth","data":{"px":2}},{"type":"BorderBottomWidth","data":{"px":2}},{"type":"BorderLeftWidth","data":{"px":2}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"FontFamily","data":["monospace"]},{"type":"FontSize","data":{"px":32,"original":{"type":"length","px":32}}},{"type":"MarginBottom","data":{"original":{"v":0.25,"u":"EM"}}},{"type":"Width","data":{"type":"length","original":{"v":10,"u":"CH"}}},{"type":"Hyphens","data":"NONE"}],"text":"DNA means Deoxyribonucleic acid.","meta":{"lang":"en"}}]}"""

        // …/wpt__css-text__hyphens__hyphens-manual-inline-011.json — verbatim.
        private val MANUAL_INLINE_011 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text__hyphens__hyphens-manual-inline-011__0-259","name":"wpt__css-text__hyphens__hyphens-manual-inline-011__0","properties":[{"type":"BorderTopWidth","data":{"px":2}},{"type":"BorderRightWidth","data":{"px":2}},{"type":"BorderBottomWidth","data":{"px":2}},{"type":"BorderLeftWidth","data":{"px":2}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"FontFamily","data":["monospace"]},{"type":"FontSize","data":{"px":32,"original":{"type":"length","px":32}}},{"type":"Width","data":{"type":"length","original":{"v":10,"u":"CH"}}}],"text":"DNA .","meta":{"runs":[{"text":"DNA "},{"child":"hyphens__hyphens-manual-inline-011__0__0"},{"text":"."}]}},{"id":"hyphens__hyphens-manual-inline-011__0__0-260","name":"hyphens__hyphens-manual-inline-011__0__0","properties":[{"type":"Hyphens","data":"MANUAL"}],"slot":{"parent":"wpt__css-text__hyphens__hyphens-manual-inline-011__0-259"},"text":"means Deoxy­ribo­nucleic acid","meta":{"sourceTag":"span"}}]}"""

        // …/wpt__css-text__hyphens__hyphens-auto-inline-010.json — verbatim.
        private val AUTO_INLINE_010 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text__hyphens__hyphens-auto-inline-010__0-240","name":"wpt__css-text__hyphens__hyphens-auto-inline-010__0","properties":[{"type":"BorderTopWidth","data":{"px":2}},{"type":"BorderRightWidth","data":{"px":2}},{"type":"BorderBottomWidth","data":{"px":2}},{"type":"BorderLeftWidth","data":{"px":2}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"FontFamily","data":["monospace"]},{"type":"FontSize","data":{"px":32,"original":{"type":"length","px":32}}},{"type":"Width","data":{"type":"length","original":{"v":6,"u":"CH"}}}],"text":"There are .","meta":{"lang":"en","runs":[{"text":"There are "},{"child":"hyphens__hyphens-auto-inline-010__0__0"},{"text":"."}]}},{"id":"hyphens__hyphens-auto-inline-010__0__0-241","name":"hyphens__hyphens-auto-inline-010__0__0","properties":[{"type":"Hyphens","data":"AUTO"}],"slot":{"parent":"wpt__css-text__hyphens__hyphens-auto-inline-010__0-240"},"text":"new engines now","meta":{"sourceTag":"span","lang":"en"}}]}"""

        // tools/titan/runs/wave43-final/sections/CSS2/per-test-ir/
        // wpt__CSS2__cascade__inherit-computed-001.json — verbatim.
        private val INHERIT_COMPUTED_001 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css2__cascade__inherit-computed-001__0-100","name":"wpt__CSS2__cascade__inherit-computed-001__0","properties":[{"type":"FontSize","data":{"original":{"type":"relative","keyword":"larger"}}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"original":"medium"}},{"type":"BorderRightColor","data":{"original":"medium"}},{"type":"BorderBottomColor","data":{"original":"medium"}},{"type":"BorderLeftColor","data":{"original":"medium"}}],"text":"This line is all size and there is no red.","meta":{"sourceTag":"p","runs":[{"text":"This line is all "},{"child":"cascade__inherit-computed-001__0__0"},{"text":" size "},{"child":"cascade__inherit-computed-001__0__1"},{"text":" and there is no red."}]}},{"id":"cascade__inherit-computed-001__0__0-101","name":"cascade__inherit-computed-001__0__0","properties":[],"slot":{"parent":"wpt__css2__cascade__inherit-computed-001__0-100"},"text":"in one font","meta":{"sourceTag":"span","role":"ws-after"}},{"id":"cascade__inherit-computed-001__0__1-102","name":"cascade__inherit-computed-001__0__1","properties":[{"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderTopColor","data":{"original":"inherit"}},{"type":"BorderRightColor","data":{"original":"inherit"}},{"type":"BorderBottomColor","data":{"original":"inherit"}},{"type":"BorderLeftColor","data":{"original":"inherit"}}],"slot":{"parent":"wpt__css2__cascade__inherit-computed-001__0-100"},"meta":{"sourceTag":"em"}}]}"""

        // tools/titan/runs/wave43-final/sections/css-text/per-test-ir/
        // wpt__css-text__hyphens__hyphens-out-of-flow-001.json — host __3 and
        // its abspos child, verbatim (siblings trimmed).
        private val OUT_OF_FLOW_001_HOST3 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text__hyphens__hyphens-out-of-flow-001__3-273","name":"wpt__css-text__hyphens__hyphens-out-of-flow-001__3","properties":[{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}},{"type":"BorderRightColor","data":{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}},{"type":"BorderBottomColor","data":{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}},{"type":"BorderLeftColor","data":{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}},{"type":"MarginTop","data":{"px":5}},{"type":"MarginRight","data":{"px":5}},{"type":"MarginBottom","data":{"px":5}},{"type":"MarginLeft","data":{"px":5}},{"type":"Width","data":{"type":"length","original":{"v":6,"u":"CH"}}},{"type":"Hyphens","data":"MANUAL"}],"text":"high­way","meta":{"role":"ws-after","runs":[{"text":"h"},{"child":"hyphens__hyphens-out-of-flow-001__3__0"},{"text":"igh­way"}]}},{"id":"hyphens__hyphens-out-of-flow-001__3__0-274","name":"hyphens__hyphens-out-of-flow-001__3__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":"transparent"}}],"slot":{"parent":"wpt__css-text__hyphens__hyphens-out-of-flow-001__3-273"},"text":"abspos","meta":{"sourceTag":"span"}}]}"""

        // tools/titan/runs/wave43-final/sections/css-text-decor/per-test-ir/
        // wpt__css-text-decor__text-decoration-inset-014.json — the runs host
        // and its <u> child, verbatim (host's dangling slot removed).
        private val INSET_014_HOST = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"text-decoration-inset-014__1__0-110","name":"text-decoration-inset-014__1__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"FontFamily","data":["monospace"]},{"type":"Width","data":{"type":"length","original":{"v":16,"u":"CH"}}}],"text":"the fox","meta":{"sourceTag":"h1","runs":[{"text":"the "},{"child":"text-decoration-inset-014__1__0__0"},{"text":" fox"}]}},{"id":"text-decoration-inset-014__1__0__0-111","name":"text-decoration-inset-014__1__0__0","properties":[{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BoxDecorationBreak","data":"CLONE"}],"slot":{"parent":"text-decoration-inset-014__1__0-110"},"text":"ultra-quick brown","meta":{"sourceTag":"u"}}]}"""
    }
}
