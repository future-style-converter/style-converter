package com.styleconverter.runtime.content

// Wave-50 lane B3 — pin table for PseudoTextFold, the component rewrite that
// folds an ordinary element's baked `::after` glyphs onto the END of its own
// text (CSS 2.1 §12.1 order: element content, then ::after).
//
// WHY THESE ROWS. Every `pseudos` payload below is VERBATIM from
// tools/titan/runs/wave49-final/sections/*/per-test-ir — the same bytes the
// Android capture consumed when it painted NOTHING for
// css-counter-styles/cjk-decimal/counter-cjk-decimal (android f 0.9407,
// semantic presence 0.000 against the ref's 0.946) and lost the "A5"/"A2"
// runs of css-lists/counter-reset-reversed-pseudo-001 (android f 0.9884 vs
// iOS P 0.9999). The surrounding IRComponent is built through the runtime's
// own constructor because the wire→component hop is IRDocumentDecoder's job
// (it flattens `meta.role` → `role`, `text` → `_text`), not this fold's.
//
// The last two rows pin the DOUBLE-RENDER guard: a folded bucket must
// disappear from ContentApplier's wrapper, or the same glyphs paint twice.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.ir.IRRun
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PseudoTextFoldTest {

    /** Parse one wire `pseudos` object exactly as the decoder retains it. */
    private fun pseudos(json: String): JsonObject =
        Json.parseToJsonElement(json) as JsonObject

    // ── The corpus payloads, verbatim ───────────────────────────────────

    /** counter-cjk-decimal component __15: an ::after-ONLY CJK counter row. */
    private val cjkAfterOnly = pseudos(
        """{"after": {"properties": {"content": "\"一二三四五\""}, "_text": "一二三四五", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}}"""
    )

    /** counter-reset-reversed-pseudo-001 component __0__0: before + after. */
    private val beforeAndAfter = pseudos(
        """{"before": {"properties": {"counter-increment": "foo -1", "content": "\"B\" \"7\""}, "_text": "B7", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}, "after": {"properties": {"counter-increment": "foo -2", "content": "\"A\" \"5\""}, "_text": "A5", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}}"""
    )

    /** counter-list-item component __1__0__0: the ::after trails item text. */
    private val listItemAfter = pseudos(
        """{"before": {"properties": {"content": "\"1\" \" \""}, "_text": "1 ", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}, "after": {"properties": {"content": "\" \" \"1\""}, "_text": " 1", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}}"""
    )

    /** contain-content-011 component __3: the one STYLED ::after in corpus. */
    private val styledAfter = pseudos(
        """{"after": {"properties": {"content": "\"25\"", "font-size": "3em"}, "_lossy": true, "_lossyReasons": ["em/rem", "generated-content-baked"], "_text": "25"}}"""
    )

    /** scope-pseudo-element component __1__0: a real inline-block BOX. */
    private val boxAfter = pseudos(
        """{"after": {"properties": {"content": "\"A\"", "width": "20px", "height": "20px", "display": "inline-block", "background-color": "tomato"}, "_text": "A", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}}"""
    )

    /**
     * A SYNTHETIC host carrying both bucket shapes at once: a box-shaped
     * `::before` (the declaration set of WPT css-pseudo/before-as-flex-
     * container, the corpus's only block-level generated box) and a
     * text-shaped `::after`. Synthetic deliberately — the census in
     * PseudoTextFold's KDoc found ZERO corpus documents with this pair, and
     * the guard exists so the ordering inversion cannot appear the moment
     * one does. (Wave-50 skeptic S3.)
     */
    private val boxBeforeTextAfter = pseudos(
        """{"before": {"properties": {"content": "\"A B\"", "display": "flex", "justify-content": "space-between", "width": "200px", "height": "100px", "background": "green"}, "_text": "A B"}, "after": {"properties": {"content": "\"25\""}, "_text": "25", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}}"""
    )

    /** attr-notype-fallback component __4: `content: ""`, no baked `_text`. */
    private val emptyContentAfter = pseudos(
        """{"after": {"properties": {"content": "\"\""}, "_lossy": true, "_lossyReasons": ["baked-attr"]}}"""
    )

    /** A bare host with the given bucket — the corpus shape: no properties. */
    private fun host(
        bucket: JsonObject?,
        text: String? = null,
        role: String? = "ws-after",
        children: List<IRComponent>? = null,
        runs: List<IRRun>? = null,
        properties: List<IRProperty> = emptyList(),
    ) = IRComponent(
        id = "host-1", name = "host", properties = properties,
        children = children, _text = text, runs = runs,
        pseudos = bucket, role = role,
    )

    // ── The fold ────────────────────────────────────────────────────────

    @Test
    fun `an after-only CJK bucket becomes the component's own text`() {
        // The counter-cjk-decimal shape: nothing but generated content, which
        // is exactly why the Android capture was blank.
        val folded = PseudoTextFold.resolve(host(cjkAfterOnly))
        assertTrue(folded.afterFolded)
        assertEquals("一二三四五", folded.component._text)
        // No styling declarations in this bucket → the host's typed list is
        // untouched, so nothing about its box changes.
        assertTrue(folded.component.properties.isEmpty())
    }

    @Test
    fun `the after run appends to the host's own text in CSS 2_1 section 12_1 order`() {
        // counter-list-item: marker "1." + ::before "1 " + text "a" +
        // ::after " 1" — iOS paints "1. 1 a 1", Android painted "1. 1 a".
        val folded = PseudoTextFold.resolve(host(listItemAfter, text = "a"))
        assertTrue(folded.afterFolded)
        assertEquals("a 1", folded.component._text)
    }

    @Test
    fun `the before bucket is left for the wrapper, untouched`() {
        val folded = PseudoTextFold.resolve(host(beforeAndAfter))
        // Only the ::after is claimed — this lane deliberately does not move
        // the ::before run (55 corpus tests carry a before-only bucket).
        assertEquals("A5", folded.component._text)
        // The wire object is kept WHOLE — consuming is not erasing: the
        // `before` side still has to reach the wrapper, and ContentsUnboxing's
        // `pseudos != null` veto plus the wire's re-derivability both read it.
        val kept = folded.component.pseudos
        assertNotNull(kept)
        assertNotNull(kept!!["before"])
        assertNotNull(kept["after"])
    }

    @Test
    fun `a styled after folds its typed declarations onto the host`() {
        // contain-content-011: `font-size: 3em` on the sole ink of a host
        // that declares no size of its own, so the em base is the 16px
        // document default (css-values-4 section 6.1.1) — the render site
        // resolves the symbolic wire shape.
        val folded = PseudoTextFold.resolve(host(styledAfter))
        assertTrue(folded.afterFolded)
        assertEquals("25", folded.component._text)
        val fontSize = folded.component.properties.single { it.type == "FontSize" }
        assertTrue(fontSize.data.toString().contains("\"EM\""))
    }

    // ── The refusals, each named rather than silent ──────────────────────

    @Test
    fun `a bucket that declares a real box is left to PseudoBoxFold's claim path`() {
        // width/height/background-color on an inline-block ::after is a BOX,
        // not a text run: folding its "A" would drop the 20x20 tomato square.
        val folded = PseudoTextFold.resolve(host(boxAfter))
        assertFalse(folded.afterFolded)
        assertNull(folded.component._text)
    }

    @Test
    fun `meta_runs owns the content slot, so nothing is folded`() {
        // schema/spec/03-children.md section 4.1: a resolved run plan makes
        // the renderer drop the text slot — a fold into it would vanish.
        val folded = PseudoTextFold.resolve(
            host(cjkAfterOnly, runs = listOf(IRRun(text = "x")))
        )
        assertFalse(folded.afterFolded)
        assertNull(folded.component._text)
    }

    @Test
    fun `an after on a parent with children is refused, not mis-ordered`() {
        // css-pseudo-4 section 4.1 places ::after AFTER the children; the
        // host's own text paints BEFORE them, so the fold would reverse it.
        val kid = IRComponent(id = "k", name = "k")
        val folded = PseudoTextFold.resolve(host(cjkAfterOnly, children = listOf(kid)))
        assertFalse(folded.afterFolded)
    }

    @Test
    fun `a body-root bucket stays with RootPseudoBox`() {
        val root = host(cjkAfterOnly, role = "body-root")
        val folded = PseudoTextFold.resolve(root)
        assertFalse(folded.afterFolded)
        // Identity, not a copy: RootPseudoBox owns the root-scope bucket and
        // would double-render anything this fold also claimed.
        assertSame(root, folded.component)
        assertNull(folded.component._text)
    }

    @Test
    fun `content empty-string bakes no glyphs, so there is nothing to fold`() {
        // css-content-3 section 2: `content: ""` generates a box with no ink.
        val folded = PseudoTextFold.resolve(host(emptyContentAfter))
        assertFalse(folded.afterFolded)
    }

    @Test
    fun `styling refuses when the pseudo is not the component's sole ink`() {
        // One uniform run cannot carry two sizes: the host's "x" would be
        // repainted at 3em along with the pseudo's "25".
        val folded = PseudoTextFold.resolve(host(styledAfter, text = "x"))
        assertFalse(folded.afterFolded)
        assertEquals("x", folded.component._text)
    }

    @Test
    fun `a relative pseudo font-size under a host that declares its own size is refused`() {
        // css-values-4 section 6.1.1: the pseudo's em resolves against the
        // ORIGINATING element's size, but a FontSize appended to the host's
        // own list resolves against what the HOST inherited. Different base
        // → refuse rather than paint the wrong size. (Zero corpus carriers.)
        val sized = IRProperty("FontSize", Json.parseToJsonElement("""{"px":32}"""))
        val folded = PseudoTextFold.resolve(host(styledAfter, properties = listOf(sized)))
        assertFalse(folded.afterFolded)
    }

    @Test
    fun `an after under a box-shaped before is refused, not painted first`() {
        // ComponentRenderer runs PseudoTextFold FIRST and PseudoBoxFold
        // SECOND, and PseudoBoxFold splices the claimed ::before box in as
        // the host's FIRST child — which does not exist yet when the
        // children guard above runs. Folding the ::after into `_text` would
        // therefore paint it BEFORE that box, inverting css-pseudo-4 §4.1's
        // ::before / content / ::after order. Refuse instead.
        val bare = host(boxBeforeTextAfter)
        val folded = PseudoTextFold.resolve(bare)
        assertFalse(folded.afterFolded)
        // Untouched instance: the wrapper path still owns both buckets.
        assertSame(bare, folded.component)
        assertNull(folded.component._text)
        // Control: the SAME ::after bucket folds when the ::before is a
        // plain text bucket — so this test fails on the guard, not on the
        // bridge refusing the payload.
        val textBefore = pseudos(
            """{"before": {"properties": {"content": "\"B\""}, "_text": "B"}, "after": {"properties": {"content": "\"25\""}, "_text": "25"}}"""
        )
        val control = PseudoTextFold.resolve(host(textBefore))
        assertTrue(control.afterFolded)
        assertEquals("25", control.component._text)
    }

    @Test
    fun `a component without pseudos comes back as the same instance`() {
        val bare = host(null)
        val folded = PseudoTextFold.resolve(bare)
        assertSame(bare, folded.component)
        assertFalse(folded.afterFolded)
    }

    // ── The double-render guard ─────────────────────────────────────────

    @Test
    fun `a folded after disappears from the wrapper config`() {
        val cfg = PseudoBucketExtractor.extractBeforeAfterConfig(
            beforeAndAfter, role = "ws-after", afterFolded = true
        )
        // The ::before still rides the wrapper; the ::after does not, or the
        // glyphs would paint twice — once in the host's text, once beside it.
        assertNotNull(cfg)
        assertNotNull(cfg!!.before)
        assertNull(cfg.after)
    }

    @Test
    fun `an unfolded after still rides the wrapper`() {
        // The default keeps every pre-wave-50 caller byte-identical.
        val cfg = PseudoBucketExtractor.extractBeforeAfterConfig(
            beforeAndAfter, role = "ws-after"
        )
        assertNotNull(cfg!!.after)
        assertEquals(
            listOf<ContentValue>(ContentValue.Text("A5")),
            cfg.after!!.content
        )
    }
}
