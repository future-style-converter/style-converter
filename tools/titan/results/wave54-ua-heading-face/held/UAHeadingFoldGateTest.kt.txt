package com.styleconverter.runtime.typography

// Wave 54 (lane L5, U1-android) — pins UAHeadingFoldGate on the VERBATIM
// wave53-final per-test IR. PLAN §2 L5 "Unit pins":
//   • inset-005 / -006 / -014 hosts → applies (the fold returns Folded);
//   • inset-011 → stands down (Bailed member-prop:TextDecorationColor-
//     divergence) — the control whose capture must stay byte-identical;
//   • a leaf heading → applies.
// Mutation: return the old `hasChildren` gate → the inset-005 pin is red.
// Plus the soundness pin of brief §4 D: the fold verdict on the PRE-UA list
// equals the verdict on the POST-UA list (admission ignores FontSize /
// FontWeight) — if a future fold starts reading the face, this goes red.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.InlineRunPlan
import com.styleconverter.runtime.typography.inline.InlineRunFold
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UAHeadingFoldGateTest {

    /** The three folded hosts the lane targets, plus the bailing control. */
    private val inset005 = UAHeadingFaceWire.INSET_005 to "text-decoration-inset-005__1__0-069"
    private val inset006 = UAHeadingFaceWire.INSET_006 to "text-decoration-inset-006__1__0-076"
    private val inset014 = UAHeadingFaceWire.INSET_014 to "text-decoration-inset-014__1__0-110"
    private val inset011 = UAHeadingFaceWire.INSET_011 to "text-decoration-inset-011__1__0-095"

    /** The fold exactly as the gate (and RenderContent) calls it. */
    private fun foldOf(host: IRComponent, hostProperties: List<IRProperty>): InlineRunFold.Outcome {
        val plan = InlineRunPlan.resolve(host.runs, host.children)!!
        return InlineRunFold.fold(plan.entries, host.children!!, hostProperties, host.lang)
    }

    /** The pre-UA seam site of a (document, id) pair. */
    private fun site(pair: Pair<String, String>) = UAHeadingFaceReplay.site(pair.first, pair.second)

    @Test
    fun `inset-005 - the folded h1 host applies (Folded, not the stacked stand-down)`() {
        val s = site(inset005)
        // The verbatim host hosts a <u> child — the old gate stood down here.
        assertFalse(s.component.children.isNullOrEmpty())
        // The fold engages on the merged list (inherited Color black is the
        // ink base that admits the u's TextDecorationColor black).
        val outcome = foldOf(s.component, s.mergedPreUa)
        assertTrue("fold must engage, got $outcome", outcome is InlineRunFold.Outcome.Folded)
        assertEquals("the ultra-quick brown fox", (outcome as InlineRunFold.Outcome.Folded).text)
        // …so the heading half applies.
        assertFalse(UAHeadingFoldGate.standsDown(s.component, s.mergedPreUa))
    }

    @Test
    fun `inset-006 - the folded h1 host applies`() {
        val s = site(inset006)
        assertTrue(foldOf(s.component, s.mergedPreUa) is InlineRunFold.Outcome.Folded)
        assertFalse(UAHeadingFoldGate.standsDown(s.component, s.mergedPreUa))
    }

    @Test
    fun `inset-014 - the folded monospace h1 host applies`() {
        val s = site(inset014)
        assertTrue(foldOf(s.component, s.mergedPreUa) is InlineRunFold.Outcome.Folded)
        assertFalse(UAHeadingFoldGate.standsDown(s.component, s.mergedPreUa))
    }

    @Test
    fun `inset-011 - the bailing h1 host stands down (the control)`() {
        val s = site(inset011)
        // Blue / green decoration over black ink: the span ring refuses.
        val outcome = foldOf(s.component, s.mergedPreUa) as InlineRunFold.Outcome.Bailed
        assertEquals("member-prop:TextDecorationColor-divergence", outcome.reason)
        assertTrue(UAHeadingFoldGate.standsDown(s.component, s.mergedPreUa))
    }

    @Test
    fun `a leaf heading applies`() {
        val s = UAHeadingFaceReplay.site(UAHeadingFaceWire.BLOCK_IN_INLINE_015_PRINT, "block-in-inline-015-print__0__0-190")
        assertFalse(UAHeadingFoldGate.standsDown(s.component, s.mergedPreUa))
    }

    @Test
    fun `a non-heading with children keeps the old flag (never read by the rule)`() {
        // inset-005's <u>: a runs host with sup/sub children, tag u.
        val s = UAHeadingFaceReplay.site(UAHeadingFaceWire.INSET_005, "text-decoration-inset-005__1__0__0-070")
        assertTrue(UAHeadingFoldGate.standsDown(s.component, s.mergedPreUa))
    }

    @Test
    fun `a relative heading host stands down even when its runs would fold`() {
        val s = site(inset005)
        // RenderContent resolves no run plan for a relative host (it renders
        // through the positioned-container branch) — the gate mirrors that.
        val relative = s.mergedPreUa.map {
            if (it.type == "Position") IRProperty("Position", JsonPrimitive("RELATIVE")) else it
        }
        assertTrue(foldOf(s.component, relative) is InlineRunFold.Outcome.Folded)
        assertTrue(UAHeadingFoldGate.standsDown(s.component, relative))
    }

    @Test
    fun `a heading with children but no runs stands down (the stacked block case)`() {
        val s = site(inset005)
        val stacked = s.component.copy(runs = null)
        assertTrue(UAHeadingFoldGate.standsDown(stacked, s.mergedPreUa))
    }

    @Test
    fun `soundness - the fold verdict is the same before and after the UA face (brief 4 D)`() {
        for (pair in listOf(inset005, inset006, inset014, inset011)) {
            val s = site(pair)
            // The post-UA list with the face FORCED on (standsDown = false),
            // so even the bailing control is checked against a 2em list.
            val postUa = UAElementFontRule.apply(s.component._tag, s.own, s.mergedPreUa, standsDown = false)
            val pre = foldOf(s.component, s.mergedPreUa)
            val post = foldOf(s.component, postUa)
            // Same outcome class and, for a fold, the same paragraph + spans.
            assertEquals(pair.second, pre::class, post::class)
            if (pre is InlineRunFold.Outcome.Folded && post is InlineRunFold.Outcome.Folded) {
                assertEquals(pre.text, post.text)
                assertEquals(pre.spans, post.spans)
            }
            if (pre is InlineRunFold.Outcome.Bailed) {
                assertEquals(pre.reason, (post as InlineRunFold.Outcome.Bailed).reason)
            }
        }
    }
}
