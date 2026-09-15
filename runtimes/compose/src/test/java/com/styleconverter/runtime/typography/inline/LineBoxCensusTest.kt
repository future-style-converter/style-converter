package com.styleconverter.runtime.typography.inline

// Wave 50 (lane B9) — pins for the CROSS-BLOCK line-box census and the
// component-aware line-clamp cap resolver.
//
// Every wire document below is
// tools/titan/runs/wave49-final/sections/css-overflow/per-test-ir/<test>.json
// VERBATIM (minified only — no key and no value was altered), i.e. the exact
// bytes the wave-49 Android gate consumed, and each expectation is the
// MEASURED geometry of that test's frozen Chromium reference
// (tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/
//  white-black-ink-font-lh-imgpad-htmlpins/css-overflow/):
//
//   line-clamp-005  clamp 3, `16px/32px` root + `24px/48px` child →
//                   32+32+48 = 112px.  Uniform cap says 96 — the
//                   wave49-final Android yellow box is 16px short
//                   (android-ref 0.9615 P, iOS 0.9891 P).
//   line-clamp-006  clamp 5 → 32+32+48+48+32 = 192px.  Uniform cap 160
//                   discards "Line 5", which the ref paints
//                   (android-ref 0.9446 f, iOS — which has this census
//                   since wave 46 — 0.9822 P).
//   line-clamp-007  clamp 3, `overflow: auto` child = an independent
//                   formatting context whose lines are NOT counted
//                   (css-overflow-3 §3) → 32+32+(48+48)+32 = 192px.
//                   Uniform cap 96 cuts inside the child (android-ref
//                   0.9409 f, iOS 0.9822 P).
//
// The two no-change documents pin the other half of the contract — the
// census never moves a container it cannot prove:
//   block-ellipsis-012  two `<p>` children on the SAME line box as the
//                       root → uniform, cap unchanged at 18.25px.
//   block-ellipsis-013  no children at all → the early bail, 34.5px.

import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRDocumentDecoder
import com.styleconverter.runtime.core.renderer.SlotComposer
import com.styleconverter.runtime.scrolling.LineClampCap
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
// Wave-50 lane F1 — the no-silent-fallthrough pin asserts a Regex match was
// found before it reads the captured group.
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineBoxCensusTest {

    /** Decode + compose a captured v2 document, return root [index]. */
    private fun root(doc: String, index: Int = 0): IRComponent =
        SlotComposer.compose(IRDocumentDecoder.decode(doc))[index]

    /** The property pairs the style chain hands every extractor. */
    private fun pairs(c: IRComponent): List<Pair<String, JsonElement?>> =
        c.properties.map { it.type to it.data }

    // ── the three measured victims, verbatim wire ────────────────────────

    @Test
    fun `line-clamp-005 - the 3rd line box is the child's 48px one, not the root's 32px`() {
        val host = root(LINE_CLAMP_005)
        // The wave-41 uniform cap: 3 × the ROOT's box. 16px short of the ref.
        assertEquals(96f, LineClampCap.capPx(pairs(host))!!, 0.001f)
        // The census: 2 root lines (32 each) + the child's 1st line (48).
        assertEquals(112f, LineClampCap.resolveCapPx(host, pairs(host))!!, 0.001f)
    }

    @Test
    fun `line-clamp-006 - five line boxes across two fonts close at 192px`() {
        val host = root(LINE_CLAMP_006)
        assertEquals(160f, LineClampCap.capPx(pairs(host))!!, 0.001f)
        // 32 + 32 + 48 + 48 + 32 — the 5th box is "Line 5", which the
        // uniform 160px cap discards and the ref paints.
        assertEquals(192f, LineClampCap.resolveCapPx(host, pairs(host))!!, 0.001f)
    }

    @Test
    fun `line-clamp-007 - an overflow-auto child is monolithic and its lines are not counted`() {
        val host = root(LINE_CLAMP_007)
        assertEquals(96f, LineClampCap.capPx(pairs(host))!!, 0.001f)
        // 32 + 32 (2 counted) + the whole 96px scroll box (0 counted) + 32
        // (the 3rd counted line box, "Line 5").
        assertEquals(192f, LineClampCap.resolveCapPx(host, pairs(host))!!, 0.001f)
    }

    // ── the no-change half of the contract, verbatim wire ────────────────

    @Test
    fun `block-ellipsis-012 - children on the root's own line box keep the uniform cap`() {
        val host = root(BLOCK_ELLIPSIS_012)
        // Monospace UA 13px × the 1.25 composed-WPT ratio = 16.25, + the
        // 1px top and bottom border band = 18.25.
        val uniform = LineClampCap.capPx(pairs(host))!!
        assertEquals(18.25f, uniform, 0.001f)
        // Both `<p>` children inherit the root's size and line box, so the
        // content is uniform and the census must not re-derive the number.
        assertEquals(uniform, LineClampCap.resolveCapPx(host, pairs(host))!!, 0.0f)
    }

    @Test
    fun `block-ellipsis-013 - a childless clamp root never walks the census`() {
        val host = root(BLOCK_ELLIPSIS_013)
        val uniform = LineClampCap.capPx(pairs(host))!!
        assertEquals(34.5f, uniform, 0.001f)
        assertEquals(uniform, LineClampCap.resolveCapPx(host, pairs(host))!!, 0.0f)
    }

    @Test
    fun `no fixed-count clamp on the wire means no cap at all`() {
        // The clamp-less fast path: the resolver opens with the same wire
        // gate capPx does, so every non-clamp component is untouched.
        val host = root(LINE_CLAMP_006).copy(properties = emptyList())
        assertNull(LineClampCap.resolveCapPx(host, pairs(host)))
    }

    // ── the renderer's no-silent-fallthrough guard (wave 50, lane F1) ────

    @Test
    fun `a census wire that throws is breadcrumbed, never silently dropped`() {
        // HALF 1 — EXECUTED: the guard is load-bearing, not dead code.
        // `resolveCapPx` reads the root's typography wire, and
        // TypographyExtractor's FontStretch reader dereferences
        // `json["percentage"].jsonPrimitive` unguarded — a nested object
        // there is a shape no reader expects and it throws out of the
        // resolver. Any future hardening that makes THIS wire safe leaves
        // the guard still needed (the census walks arbitrary child wire),
        // but it would make this half fail loudly rather than rot silently.
        val host = root(CENSUS_THROWING_WIRE)
        val thrown = runCatching { LineClampCap.resolveCapPx(host, pairs(host)) }
            .exceptionOrNull()
        assertNotNull(
            "the probe wire must actually throw out of resolveCapPx — " +
                "otherwise this pin proves nothing about the renderer's guard",
            thrown)

        // HALF 2 — SOURCE SCAN: the renderer answers that throw with a
        // breadcrumb. ComponentRenderer's call site is a @Composable, so the
        // JVM suite cannot invoke it; the established idiom for pinning a
        // renderer-side wiring fact without Robolectric is to read the
        // source (WptBoxSizingDefaultTest's LocalWptCaptureMode pin,
        // FragmentGeometryTest). Captured with a Regex so the guard survives
        // reformatting, and the KEY is extracted rather than re-typed here.
        val guard = Regex(
            "LineClampCap\\.resolveCapPx\\(component, propertyPairs\\)\\s*\\}" +
                "\\s*\\.onFailure \\{(.*?)\\}\\s*\\.getOrNull\\(\\)",
            RegexOption.DOT_MATCHES_ALL,
        ).find(rendererSource)?.groupValues?.get(1)
        assertNotNull(
            "the renderer's line-clamp census call must be guarded by " +
                ".onFailure { … }.getOrNull() — a bare .getOrNull() is the " +
                "silent fallthrough this pin exists to forbid",
            guard)
        val key = Regex("markUnhandled\\(\"([^\"]+)\"\\)").find(guard!!)?.groupValues?.get(1)
        assertNotNull(
            "the guard must raise a PropertyTracker breadcrumb — got: $guard", key)
        // The throwable must reach logcat too, or the offending wire is
        // unfindable on device (the report only carries the key).
        assertTrue(
            "the guard must log the throwable for the LineClampCap tag — got: $guard",
            guard.contains("android.util.Log.w") && guard.contains("\"LineClampCap\""))

        // HALF 3 — EXECUTED: the key the renderer raises is a key the
        // report actually surfaces. Bracketed on purpose, so it cannot be
        // mistaken for the `LineClamp` PROPERTY's coverage claim.
        assertTrue(
            "the breadcrumb key must be namespaced away from the LineClamp " +
                "property's own coverage row — got: $key",
            key!!.startsWith("LineClamp[") && key.endsWith("]"))
        PropertyTracker.markUnhandled(key)
        assertTrue(
            "PropertyTracker.getReport() must carry the renderer's breadcrumb key $key",
            PropertyTracker.getReport().unhandled.contains(key))
    }

    /** The renderer source, located by walking up from the test working dir. */
    private val rendererSource: String by lazy {
        // Same locator WptBoxSizingDefaultTest uses — the unit-test working
        // directory is the module dir, so walk up until the path resolves.
        val rel = "runtimes/compose/src/main/java/com/styleconverter/runtime/" +
            "core/renderer/ComponentRenderer.kt"
        var dir: java.io.File? =
            java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !java.io.File(dir, rel).exists()) dir = dir.parentFile
        java.io.File(
            requireNotNull(dir) { "repo root ($rel) not found above ${System.getProperty("user.dir")}" },
            rel,
        ).readText()
    }

    // ── the pure verdict rules ───────────────────────────────────────────

    @Test
    fun `verdict takes line boxes in order until the Nth closes the box`() {
        val runs = listOf(
            LineBoxRun(lineBoxPx = 32f, exactLines = 2),
            LineBoxRun(lineBoxPx = 48f, exactLines = 2),
        )
        assertEquals(
            LineBoxCensus.Verdict.Capped(112f),
            LineBoxCensus.verdict(lines = 3, runs = runs),
        )
    }

    @Test
    fun `a monolithic run adds its height and yields no line box`() {
        // css-overflow-4 §5.3 clamps the CONTAINER's own line boxes; a
        // child in its own formatting context is kept whole.
        val runs = listOf(
            LineBoxRun(lineBoxPx = 32f, exactLines = 1),
            LineBoxRun(lineBoxPx = 48f, exactLines = 0, monolithicPx = 96f),
            LineBoxRun(lineBoxPx = 32f, exactLines = 1),
        )
        assertEquals(
            LineBoxCensus.Verdict.Capped(160f),
            LineBoxCensus.verdict(lines = 2, runs = runs),
        )
    }

    @Test
    fun `a leading band sits above the child's first line box`() {
        // CSS 2.1 §8.1 — padding + border + margin precede the content box.
        val runs = listOf(LineBoxRun(lineBoxPx = 20f, exactLines = 2, leadingBandPx = 6f))
        assertEquals(
            LineBoxCensus.Verdict.Capped(26f),
            LineBoxCensus.verdict(lines = 1, runs = runs),
        )
    }

    @Test
    fun `an unprovable count or band makes the whole verdict unprovable`() {
        assertEquals(
            LineBoxCensus.Verdict.Unprovable,
            LineBoxCensus.verdict(1, listOf(LineBoxRun(20f, exactLines = null))),
        )
        assertEquals(
            LineBoxCensus.Verdict.Unprovable,
            LineBoxCensus.verdict(1, listOf(LineBoxRun(20f, exactLines = 1, leadingBandPx = null))),
        )
    }

    @Test
    fun `fewer proven line boxes than the clamp is Short, not a cap`() {
        assertEquals(
            LineBoxCensus.Verdict.Short,
            LineBoxCensus.verdict(4, listOf(LineBoxRun(20f, exactLines = 2))),
        )
    }

    // ── exactLineCount, the css-text-3 white-space rules ─────────────────

    @Test
    fun `pre keeps every segment as a line box and nowrap collapses to one`() {
        assertEquals(3, LineBoxCensus.exactLineCount("a b\nc d\ne", "PRE"))
        assertEquals(1, LineBoxCensus.exactLineCount("a b\nc d\ne", "nowrap"))
    }

    @Test
    fun `wrapping keywords are provable only without a soft wrap opportunity`() {
        assertNull(LineBoxCensus.exactLineCount("Line 1\nLine 2", "PRE_WRAP"))
        assertEquals(2, LineBoxCensus.exactLineCount("Line1\nLine2", "pre-wrap"))
        assertNull(LineBoxCensus.exactLineCount("two words", null))
        assertEquals(1, LineBoxCensus.exactLineCount("oneword", null))
    }

    @Test
    fun `an empty run produces no line box`() {
        assertEquals(0, LineBoxCensus.exactLineCount("", "PRE"))
        assertEquals(0, LineBoxCensus.exactLineCount(null, "PRE"))
    }

    // ── the per-child readers ────────────────────────────────────────────

    @Test
    fun `a br child budgets no box even though the converter measured one`() {
        // block-ellipsis-002/-004/-005 ship `<br>` members carrying the
        // converter's measured `height` (0 or 20px). A forced break
        // (HTML §4.5.27) generates no box: budgeting that height would
        // inflate every br-bearing clamp root's cap.
        val host = root(BR_MEMBER_DOC)
        val metrics = LineBoxRootMetrics(
            fontSizePx = 16f, lineBoxPx = 20f, whiteSpace = null,
            declaredLineHeightPx = null, declaredMultiplier = null,
        )
        val br = host.children!!.single()
        val run = LineBoxCensusRuns.childRun(br, metrics)
        assertEquals(0, run.exactLines)
        assertNull("a forced break is not a monolithic box", run.monolithicPx)
    }

    @Test
    fun `uniform content is detected so the census never re-derives a calibrated cap`() {
        val metrics = LineBoxRootMetrics(
            fontSizePx = 16f, lineBoxPx = 20f, whiteSpace = null,
            declaredLineHeightPx = null, declaredMultiplier = null,
        )
        val uniform = listOf(
            LineBoxRun(20f, exactLines = null),
            LineBoxRun(20f, exactLines = 1),
        )
        assertTrue(!LineBoxCensusRuns.hasNonUniformContent(uniform, metrics))
        // Each of the three non-uniform signals on its own.
        assertTrue(
            LineBoxCensusRuns.hasNonUniformContent(
                uniform + LineBoxRun(48f, exactLines = 1), metrics,
            )
        )
        assertTrue(
            LineBoxCensusRuns.hasNonUniformContent(
                uniform + LineBoxRun(20f, exactLines = 0, monolithicPx = 40f), metrics,
            )
        )
        assertTrue(
            LineBoxCensusRuns.hasNonUniformContent(
                uniform + LineBoxRun(20f, exactLines = 1, leadingBandPx = 2f), metrics,
            )
        )
        assertTrue(
            LineBoxCensusRuns.hasNonUniformContent(
                uniform + LineBoxRun(20f, exactLines = 1, leadingBandPx = null), metrics,
            )
        )
    }

    @Test
    fun `uniform unbanded runs make the census identical to the uniform cap`() {
        // The IDENTITY that makes the gate above behaviour-neutral: when
        // every run shares the root's line box, opens no band and is not
        // monolithic, taking line boxes in order can only ever sum to
        // N × that box — whatever the per-run counts are. Any distribution
        // of the same total must land on the same edge.
        val box = 20f
        val distributions = listOf(
            listOf(5),            // one run holds them all
            listOf(1, 1, 1, 1, 1), // one line box each
            listOf(2, 0, 3, 9),    // a glyph-less run, then an overshoot
        )
        for (counts in distributions) {
            val runs = counts.map { LineBoxRun(box, exactLines = it) }
            assertEquals(
                "distribution $counts must close the 4th line box at 4 × $box",
                LineBoxCensus.Verdict.Capped(4 * box),
                LineBoxCensus.verdict(lines = 4, runs = runs),
            )
        }
    }

    private companion object {
        // ── wave49-final per-test IR, verbatim (minified) ────────────────
        const val LINE_CLAMP_005 =
            """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-overflow__line-clamp__line-clamp-005__0-152","name":"wpt__css-overflow__line-clamp__line-clamp-005__0","properties":[{"type":"LineClamp","data":{"type":"lines","count":3}},{"type":"FontSize","data":{"px":16,"original":{"type":"length","px":16}}},{"type":"LineHeight","data":{"original":{"type":"length","px":32}}},{"type":"FontFamily","data":["serif"]},{"type":"WhiteSpace","data":"PRE"},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":4}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":4}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":0},"original":"yellow"}}],"text":"Line 1\nLine 2","meta":{"runs":[{"text":"Line 1\nLine 2"},{"child":"line-clamp__line-clamp-005__0__0"}]}},{"id":"line-clamp__line-clamp-005__0__0-153","name":"line-clamp__line-clamp-005__0__0","properties":[{"type":"FontSize","data":{"px":24,"original":{"type":"length","px":24}}},{"type":"LineHeight","data":{"original":{"type":"length","px":48}}},{"type":"FontFamily","data":["serif"]},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}}],"slot":{"parent":"wpt__css-overflow__line-clamp__line-clamp-005__0-152"},"text":"Line 3\nLine 4"}]}"""

        const val LINE_CLAMP_006 =
            """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-overflow__line-clamp__line-clamp-006__0-154","name":"wpt__css-overflow__line-clamp__line-clamp-006__0","properties":[{"type":"LineClamp","data":{"type":"lines","count":5}},{"type":"FontSize","data":{"px":16,"original":{"type":"length","px":16}}},{"type":"LineHeight","data":{"original":{"type":"length","px":32}}},{"type":"FontFamily","data":["serif"]},{"type":"WhiteSpace","data":"PRE"},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":4}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":4}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":0},"original":"yellow"}}],"text":"Line 1\nLine 2Line 5\nLine 6","meta":{"runs":[{"text":"Line 1\nLine 2"},{"child":"line-clamp__line-clamp-006__0__0"},{"text":"Line 5\nLine 6"}]}},{"id":"line-clamp__line-clamp-006__0__0-155","name":"line-clamp__line-clamp-006__0__0","properties":[{"type":"FontSize","data":{"px":24,"original":{"type":"length","px":24}}},{"type":"LineHeight","data":{"original":{"type":"length","px":48}}},{"type":"FontFamily","data":["serif"]},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}}],"slot":{"parent":"wpt__css-overflow__line-clamp__line-clamp-006__0-154"},"text":"Line 3\nLine 4"}]}"""

        const val LINE_CLAMP_007 =
            """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-overflow__line-clamp__line-clamp-007__0-156","name":"wpt__css-overflow__line-clamp__line-clamp-007__0","properties":[{"type":"LineClamp","data":{"type":"lines","count":3}},{"type":"FontSize","data":{"px":16,"original":{"type":"length","px":16}}},{"type":"LineHeight","data":{"original":{"type":"length","px":32}}},{"type":"FontFamily","data":["serif"]},{"type":"WhiteSpace","data":"PRE"},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":4}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":4}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":0},"original":"yellow"}}],"text":"Line 1\nLine 2Line 5\nLine 6","meta":{"runs":[{"text":"Line 1\nLine 2"},{"child":"line-clamp__line-clamp-007__0__0"},{"text":"Line 5\nLine 6"}]}},{"id":"line-clamp__line-clamp-007__0__0-157","name":"line-clamp__line-clamp-007__0__0","properties":[{"type":"OverflowX","data":"AUTO"},{"type":"OverflowY","data":"AUTO"},{"type":"FontSize","data":{"px":24,"original":{"type":"length","px":24}}},{"type":"LineHeight","data":{"original":{"type":"length","px":48}}},{"type":"FontFamily","data":["serif"]},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":4}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":4}}],"slot":{"parent":"wpt__css-overflow__line-clamp__line-clamp-007__0-156"},"text":"Line 3\nLine 4"}]}"""

        const val BLOCK_ELLIPSIS_012 =
            """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-overflow__line-clamp__block-ellipsis-012__0-087","name":"wpt__css-overflow__line-clamp__block-ellipsis-012__0","properties":[{"type":"LineClamp","data":{"type":"lines","count":1}},{"type":"Width","data":{"type":"length","original":{"v":38.1,"u":"CH"}}},{"type":"BorderTopWidth","data":{"px":1}},{"type":"BorderRightWidth","data":{"px":1}},{"type":"BorderBottomWidth","data":{"px":1}},{"type":"BorderLeftWidth","data":{"px":1}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"FontFamily","data":["monospace"]}]},{"id":"line-clamp__block-ellipsis-012__0__0-088","name":"line-clamp__block-ellipsis-012__0__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"slot":{"parent":"wpt__css-overflow__line-clamp__block-ellipsis-012__0-087"},"text":"This line should have an ellipsis here","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"line-clamp__block-ellipsis-012__0__1-089","name":"line-clamp__block-ellipsis-012__0__1","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"slot":{"parent":"wpt__css-overflow__line-clamp__block-ellipsis-012__0-087"},"text":"After all, it is not the last line in the line-clamp container","meta":{"sourceTag":"p"}}]}"""

        const val BLOCK_ELLIPSIS_013 =
            """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-overflow__line-clamp__block-ellipsis-013__0-090","name":"wpt__css-overflow__line-clamp__block-ellipsis-013__0","properties":[{"type":"LineClamp","data":{"type":"lines","count":2}},{"type":"Width","data":{"type":"length","original":{"v":63.1,"u":"CH"}}},{"type":"BorderTopWidth","data":{"px":1}},{"type":"BorderRightWidth","data":{"px":1}},{"type":"BorderBottomWidth","data":{"px":1}},{"type":"BorderLeftWidth","data":{"px":1}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"FontFamily","data":["monospace"]},{"type":"WhiteSpace","data":"PRE"}],"text":"This time, Mark, who had always been the center of attention in\nany social gathering, walked into the room uncharacteristically quietly, barely speaking as he settled into a chair.\n\nWhen asked, he said that he was fine, when he wasn't really fine."}]}"""

        // Wave-50 lane F1 — the HOSTILE wire for the renderer's guard pin.
        // block-ellipsis-013's clamp root reduced to its clamp declaration,
        // plus ONE child (so the census would walk) and one malformed
        // `FontStretch`: css-fonts-4 §6 serializes font-stretch as a
        // percentage NUMBER, and TypographyExtractor.extractFontStretch
        // reads `json["percentage"].jsonPrimitive` unguarded, so a nested
        // object there throws instead of degrading. Hand-authored, not
        // corpus wire — the corpus has no such document, which is exactly
        // why the renderer needs a guard rather than a pre-validation.
        const val CENSUS_THROWING_WIRE =
            """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"throwing-host-1","name":"throwing-host","properties":[{"type":"LineClamp","data":{"type":"lines","count":2}},{"type":"FontSize","data":{"px":16,"original":{"type":"length","px":16}}},{"type":"FontStretch","data":{"percentage":{"nested":"not a primitive"}}}],"text":"Line 1\nLine 2"},{"id":"throwing-child-1","name":"throwing-child","properties":[],"slot":{"parent":"throwing-host-1"},"text":"Line 3"}]}"""

        // block-ellipsis-004's second `<br>` member, verbatim (its host and
        // the other members trimmed away): the shape whose measured
        // `height: 20px` must not be budgeted as a box.
        const val BR_MEMBER_DOC =
            """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"host-1","name":"host","properties":[{"type":"LineClamp","data":{"type":"lines","count":3}}]},{"id":"line-clamp__block-ellipsis-004__0__1-1","name":"line-clamp__block-ellipsis-004__0__1","properties":[{"type":"Height","data":{"type":"length","px":20}}],"slot":{"parent":"host-1"},"meta":{"sourceTag":"br"}}]}"""
    }
}
