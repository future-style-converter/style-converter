package com.styleconverter.runtime.content

// Wave-42 lane W1 — pin table for PseudoBucketExtractor, the v2 `pseudos`
// wire → ContentApplier.BeforeAfterConfig bridge.
//
// Every payload below is VERBATIM from the wave41-final per-test IR
// (tools/titan/runs/wave41-final/sections/*/per-test-ir/), the documents
// whose baked pseudo text rendered as BLANK on Android before this lane —
// so these tests pin the bridge against the exact wire the gate replays.

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PseudoBucketExtractorTest {

    /** Parse one wire `pseudos` object exactly as the decoder retains it. */
    private fun pseudos(json: String): JsonObject =
        Json.parseToJsonElement(json) as JsonObject

    /** The single resolved text run of one side, for compact assertions. */
    private fun textOf(cfg: ContentApplier.PseudoElementConfig?): String? =
        (cfg?.content?.singleOrNull() as? ContentValue.Text)?.value

    // ── the wave-41 fail set, verbatim ──────────────────────────────────

    @Test
    fun `counter-name-case-sensitive baked before renders its _text`() {
        // css-counter-styles/counter-name-case-sensitive component __1-514.
        val p = pseudos(
            """{"before": {"properties": {"content": "\"1\" \"-\" \"5\""}, "_text": "1-5", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}}"""
        )
        val cfg = PseudoBucketExtractor.extractBeforeAfterConfig(p, role = "ws-after")
        assertNotNull(cfg)
        // The baked answer, never the raw quoted sequence.
        assertEquals("1-5", textOf(cfg!!.before))
        assertNull(cfg.after)
        // No display declared → inline pseudo box (css-display-3 §2).
        assertTrue(cfg.isInline)
        // The wrapper's engage gate must see content.
        assertTrue(cfg.hasBeforeAfter)
    }

    @Test
    fun `overflow-underflow keeps the baked text despite counter-increment`() {
        // css-lists/counter-reset-increment-overflow-underflow __0__0-330:
        // the raw counter-increment is an INPUT the bake already consumed.
        val p = pseudos(
            """{"before": {"properties": {"counter-increment": "over-counter 50000000", "content": "\"2050000000\""}, "_text": "2050000000", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}}"""
        )
        val cfg = PseudoBucketExtractor.extractBeforeAfterConfig(p, role = "ws-after")
        assertEquals("2050000000", textOf(cfg?.before))
    }

    @Test
    fun `counter-list-item carries both sides with their spaces intact`() {
        // css-lists/counter-list-item __1__0__0-281: "1 " before, " 1" after
        // — the spaces ARE the ref's "1. 1 a 1" spacing, never trimmed.
        val p = pseudos(
            """{"before": {"properties": {"content": "\"1\" \" \""}, "_text": "1 ", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}, "after": {"properties": {"content": "\" \" \"1\""}, "_text": " 1", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}}"""
        )
        val cfg = PseudoBucketExtractor.extractBeforeAfterConfig(p, role = "ws-after")
        assertEquals("1 ", textOf(cfg?.before))
        assertEquals(" 1", textOf(cfg?.after))
    }

    @Test
    fun `unbaked counter() content resolves to nothing`() {
        // css-lists/counter-reset-reversed-display-none __0__2-358: no
        // `_text` and a context-dependent function — the bridge must yield
        // NOTHING rather than a wrong literal (the element is display:none
        // in the source; the seam's display gate is belt, this is braces).
        val p = pseudos("""{"before": {"properties": {"content": "counter(foo)"}}}""")
        assertNull(PseudoBucketExtractor.extractBeforeAfterConfig(p, role = "ws-after"))
    }

    @Test
    fun `marker bucket is left to the markerText channel`() {
        // css-lists/counter-reset-reversed-list-item __1__0-371: the marker
        // bucket must NOT become a before/after box (it rides
        // meta.markerText into RenderListItemMarker), the before must.
        val p = pseudos(
            """{"before": {"properties": {"content": "\"7\""}, "_text": "7", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}, "marker": {"properties": {"content": "none"}}}"""
        )
        val cfg = PseudoBucketExtractor.extractBeforeAfterConfig(p, role = "ws-after")
        assertEquals("7", textOf(cfg?.before))
        assertNull(cfg?.after)
    }

    @Test
    fun `marker-only bucket resolves to null`() {
        // css-lists/counter-reset-reversed-list-item __0-369: markers
        // suppressed, no before/after — the seam must fall back cleanly.
        val p = pseudos("""{"marker": {"properties": {"content": "none"}}}""")
        assertNull(PseudoBucketExtractor.extractBeforeAfterConfig(p, role = "ws-after"))
    }

    // ── ownership and gate rules ────────────────────────────────────────

    @Test
    fun `body-root buckets belong to RootPseudoBox`() {
        // css-contain/contain-body-dir family: consuming the root bucket
        // here too would DOUBLE-render the orange box RootPseudoBox paints.
        val p = pseudos(
            """{"before": {"properties": {"content": "\"\"", "width": "100px", "height": "100px", "background": "orange", "display": "block"}}}"""
        )
        assertNull(PseudoBucketExtractor.extractBeforeAfterConfig(p, role = "body-root"))
    }

    @Test
    fun `absent pseudos is a clean null`() {
        // Every pre-wave-27 document: the seam falls to the legacy channel.
        assertNull(PseudoBucketExtractor.extractBeforeAfterConfig(null, role = null))
    }

    // ── the literal fallback (no baked _text) ───────────────────────────

    @Test
    fun `literal string sequence resolves without a bake`() {
        // Context-free literals are resolvable without counters/attrs.
        val p = pseudos("""{"after": {"properties": {"content": "\"a\" \"-\" \"b\""}}}""")
        val cfg = PseudoBucketExtractor.extractBeforeAfterConfig(p, role = null)
        assertEquals("a-b", textOf(cfg?.after))
    }

    @Test
    fun `content none and normal generate no box`() {
        // css-content-3 §1.2: none/normal suppress the pseudo box outright.
        assertNull(PseudoBucketExtractor.extractBeforeAfterConfig(
            pseudos("""{"before": {"properties": {"content": "none"}}}"""), role = null))
        assertNull(PseudoBucketExtractor.extractBeforeAfterConfig(
            pseudos("""{"before": {"properties": {"content": "normal"}}}"""), role = null))
    }

    @Test
    fun `empty baked text paints nothing`() {
        // `content: ""` bakes to "" — a box with no ink and no bridged
        // decoration draws nothing (mirrors RootPseudoSpec.paints).
        val p = pseudos("""{"before": {"properties": {"content": "\"\""}, "_text": ""}}""")
        assertNull(PseudoBucketExtractor.extractBeforeAfterConfig(p, role = null))
    }

    @Test
    fun `a block display declaration stacks the wrapper`() {
        // css-display-3 §2: a block-level pseudo flips the Row to a Column.
        val p = pseudos(
            """{"before": {"properties": {"content": "\"x\"", "display": "block"}, "_text": "x"}}"""
        )
        val cfg = PseudoBucketExtractor.extractBeforeAfterConfig(p, role = null)
        assertEquals("x", textOf(cfg?.before))
        assertFalse(cfg!!.isInline)
    }

    @Test
    fun `a display none bucket generates no box`() {
        // css-pseudo/before-dynamic-display-none: the ::before declares its
        // OWN `display: none`, so css-display-3 §2.4 generates NO boxes for
        // it — Android used to read `none` as "not block" = inline and paint
        // the literal word FAIL (web/iOS already refuse the bucket).
        val p = pseudos(
            """{"before": {"properties": {"content": "\"FAIL\"", "position": "absolute", "width": "100px", "height": "100px", "background-color": "red", "display": "none"}, "_text": "FAIL"}}"""
        )
        assertNull(PseudoBucketExtractor.extractBeforeAfterConfig(p, role = null))
    }

    // ── literalContentText corners ──────────────────────────────────────

    @Test
    fun `escaped quotes survive the simplified unescape`() {
        // css-syntax-3 §4.3.7 simplified to backslash-next-char.
        assertEquals("a\"b", PseudoBucketExtractor.literalContentText("\"a\\\"b\"", "before"))
    }

    @Test
    fun `single-quoted strings and mixed whitespace concatenate`() {
        // Both quote forms are legal; inter-token whitespace is not content.
        assertEquals("xy", PseudoBucketExtractor.literalContentText("'x'  \"y\"", "before"))
    }

    @Test
    fun `unterminated and non-literal values resolve to null`() {
        // Malformed wire → never guess; functions → context this bridge
        // does not have (the producer bakes the resolvable ones).
        assertNull(PseudoBucketExtractor.literalContentText("\"abc", "before"))
        assertNull(PseudoBucketExtractor.literalContentText("attr(title)", "before"))
        assertNull(PseudoBucketExtractor.literalContentText("open-quote", "before"))
    }
}
