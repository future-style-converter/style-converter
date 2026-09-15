package com.styleconverter.runtime.layout.position

// Wave 50 (lane B2) — JVM pins for the CSS 2.1 §10.3.9 gate that keeps the
// composed-WPT block-auto-width fill off ATOMIC inline-level boxes.
//
// Every positive payload is copied VERBATIM out of the frozen wave49-final
// per-test IR the Android capture consumed:
//   tools/titan/runs/wave49-final/sections/css-masking/per-test-ir/
//     wpt__css-masking__clip-path__clip-path-contentBox-1{d,e}.json
//   tools/titan/runs/wave49-final/sections/css-text-decor/per-test-ir/
//     wpt__css-text-decor__text-decoration-propagation-0{2,3}.json
//   tools/titan/runs/wave49-final/sections/css-writing-modes/per-test-ir/
//     wpt__css-writing-modes__available-size-001.json
// and every negative is a shape the predicate must NOT claim.

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomicInlineShrinkToFitTest {

    private fun ir(type: String, json: String) = IRProperty(type, Json.parseToJsonElement(json))

    // ── S1: the two target carriers ───────────────────────────────────────

    @Test fun `S1 - clip-path-contentBox-1d's clipped box is shrink-to-fit`() {
        // Verbatim `wpt__css-masking__clip-path__clip-path-contentbox-1d__1-040`
        // (leading properties only — the predicate reads Display and the sizes,
        // and the box declares no Width/MinWidth/InlineSize at all, which is
        // what makes ComponentRenderer's `hasExplicitWidth` gate let the fill
        // through today).
        val props = listOf(
            ir("Display", "\"INLINE_BLOCK\""),
            ir("ClipPath", """{"geometry-box":"content-box"}"""),
            ir("BackgroundColor", """{"srgb":{"r":1,"g":0,"b":0},"original":"red"}"""),
            ir("PaddingTop", """{"px":4}"""), ir("PaddingLeft", """{"px":4}"""),
            ir("BorderTopWidth", """{"px":4}"""), ir("BorderLeftWidth", """{"px":4}"""),
            ir("OutlineWidth", """{"type":"length","px":100}"""),
        )
        assertTrue(AtomicInlineShrinkToFit.suppressesBlockAutoWidth(props, verticalWritingMode = false))
    }

    @Test fun `S1 - 1e's radius-carrying twin is the same box`() {
        // 1e is 1d plus `border-radius: 58px`; the radius is irrelevant to the
        // width decision, and pinning it here records that the 8 834-vs-7 647
        // green-pixel gap measured on the Android capture is a consequence of
        // the WIDTH, not a second defect in the radius model.
        val props = listOf(
            ir("Display", "\"INLINE_BLOCK\""),
            ir("ClipPath", """{"geometry-box":"content-box"}"""),
            ir("BorderTopLeftRadius", """{"type":"single","px":58}"""),
        )
        assertTrue(AtomicInlineShrinkToFit.suppressesBlockAutoWidth(props, verticalWritingMode = false))
    }

    // ── S2: the other two atomic roles ────────────────────────────────────

    @Test fun `S2 - inline-flex and inline-grid are atomic inline-level too`() {
        // css-display-3 §2.1: inline-flex / inline-grid are inline-level OUTER
        // roles, so CSS 2.1 §10.3.3 never applied to them either. Verbatim
        // from text-decoration-propagation-02 / -03.
        assertTrue(
            AtomicInlineShrinkToFit.suppressesBlockAutoWidth(
                listOf(ir("Display", "\"INLINE_FLEX\"")), verticalWritingMode = false
            )
        )
        assertTrue(
            AtomicInlineShrinkToFit.suppressesBlockAutoWidth(
                listOf(ir("Display", "\"INLINE_GRID\"")), verticalWritingMode = false
            )
        )
    }

    // ── S3: the negatives that keep the blast radius at nine tests ────────

    @Test fun `S3 - a block box still fills its containing block`() {
        // CSS 2.1 §10.3.3 is untouched: the corpus is overwhelmingly this.
        assertFalse(
            AtomicInlineShrinkToFit.suppressesBlockAutoWidth(
                listOf(ir("Display", "\"BLOCK\"")), verticalWritingMode = false
            )
        )
        assertFalse(
            AtomicInlineShrinkToFit.suppressesBlockAutoWidth(emptyList(), verticalWritingMode = false)
        )
    }

    @Test fun `S3 - plain display inline is NOT claimed`() {
        // 88 of the 123 inline-level corpus carriers are `display: inline`
        // (the css-pseudo first-letter family, the selectors invalidation
        // family, the visited-link tests). A non-replaced inline box has no
        // width at all (CSS 2.1 §10.3.1) and this renderer models it as a
        // block; claiming it here would move 70+ tests with no measurement.
        assertFalse(
            AtomicInlineShrinkToFit.suppressesBlockAutoWidth(
                listOf(ir("Display", "\"INLINE\"")), verticalWritingMode = false
            )
        )
    }

    @Test fun `S3 - inline-table stays with the table lane`() {
        // TableBoxTree.uaRoleOf folds INLINE_TABLE to Role.TABLE, whose
        // shrinkToFitBox already suppresses the same fill. One decision, one
        // place — the CSS2/css21-errata/s-11-1-1b-009 carrier must not be
        // claimed twice.
        assertFalse(
            AtomicInlineShrinkToFit.suppressesBlockAutoWidth(
                listOf(ir("Display", "\"INLINE_TABLE\"")), verticalWritingMode = false
            )
        )
    }

    @Test fun `S3 - a vertical writing mode keeps its frozen geometry`() {
        // The 24 vertical-mode carriers (css-writing-modes available-size-*,
        // ch-units-vrl-*, and three css-tables collapsed-border overflow
        // tests) are owned by the wave-47 Z2 orthogonal branch. Verbatim
        // `available-size-001__1__1__0-347`.
        val props = listOf(ir("Display", "\"INLINE_BLOCK\""), ir("BackgroundColor", """{"srgb":{"r":0,"g":0.5,"b":0}}"""))
        assertTrue(AtomicInlineShrinkToFit.suppressesBlockAutoWidth(props, verticalWritingMode = false))
        assertFalse(AtomicInlineShrinkToFit.suppressesBlockAutoWidth(props, verticalWritingMode = true))
    }

    // ── S4: the wire reader ───────────────────────────────────────────────

    @Test fun `S4 - both live Display wire shapes decode`() {
        // The bare string is what every corpus carrier uses; the
        // {"keyword": …} wrapper is the fixture spelling (see
        // layout/InlineBlockAtom.kt's note on the two shapes).
        assertEquals("INLINE_BLOCK", AtomicInlineShrinkToFit.declaredDisplay(listOf(ir("Display", "\"INLINE_BLOCK\""))))
        assertEquals(
            "INLINE_BLOCK",
            AtomicInlineShrinkToFit.declaredDisplay(listOf(ir("Display", """{"keyword":"inline-block"}""")))
        )
        assertTrue(
            AtomicInlineShrinkToFit.suppressesBlockAutoWidth(
                listOf(ir("Display", """{"keyword":"inline-block"}""")), verticalWritingMode = false
            )
        )
    }

    @Test fun `S4 - an unreadable Display payload answers null, it does not guess`() {
        assertNull(AtomicInlineShrinkToFit.declaredDisplay(listOf(ir("Display", "7"))))
        assertNull(AtomicInlineShrinkToFit.declaredDisplay(listOf(ir("Display", """{"value":"inline-block"}"""))))
        assertNull(AtomicInlineShrinkToFit.declaredDisplay(emptyList()))
    }

    @Test fun `S4 - the LAST Display declaration wins`() {
        // The wave-7 selector/media bucket fold appends the winning
        // declaration, so a bucket that restyles `display` must be the one
        // this gate reads.
        assertFalse(
            AtomicInlineShrinkToFit.suppressesBlockAutoWidth(
                listOf(ir("Display", "\"INLINE_BLOCK\""), ir("Display", "\"BLOCK\"")),
                verticalWritingMode = false,
            )
        )
        assertTrue(
            AtomicInlineShrinkToFit.suppressesBlockAutoWidth(
                listOf(ir("Display", "\"BLOCK\""), ir("Display", "\"INLINE_BLOCK\"")),
                verticalWritingMode = false,
            )
        )
    }
}
