package com.styleconverter.runtime.columns

// Wave 49 (lane A7) — JVM pins for the CONTAINING-BLOCK CHAIN across a
// `column-span: all` box (css-multicol-1 §6.1 read through CSS 2.1 §10.1).
//
// Every payload below is copied VERBATIM out of the frozen wave-48 per-test
// IR the runtimes actually consumed:
//   tools/titan/runs/wave48-final/sections/css-multicol/per-test-ir/
//     wpt__css-multicol__abspos-containing-block-outside-spanner.json
// (ids `…__8-032` the multicol container, `…__8__0-033` the relpos wrapper,
// `…__8__0__0-034` the spanner, `…__8__0__0__0-035` / `…__1-036` the two
// green abspos probes). That test is the ONLY document in all 1435 per-test
// IRs of the 30 frozen sections with an out-of-flow descendant of a spanner
// — the blast-radius scan written up in MulticolSpannerContainingBlock's
// header — so these payloads are the whole live surface of the rule.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.layout.position.CanvasRootHoist
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MulticolSpannerContainingBlockTest {

    // ── Wire helpers (same idiom as CanvasRootHoistTest) ──────────────────

    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    private fun comp(
        id: String,
        properties: List<IRProperty> = emptyList(),
        children: List<IRComponent>? = null,
    ) = IRComponent(id = id, name = id, properties = properties, children = children)

    // The multicol container: `columns:3; column-gap:1em; width:20em`.
    // The em-carrier shapes are verbatim; only ColumnCount matters here.
    private val multicolProps = listOf(
        prop("ColumnCount", "3"),
        prop("ColumnGap", """{"type":"length","original":{"v":1,"u":"EM"}}"""),
        prop("Width", """{"type":"length","original":{"v":20,"u":"EM"}}"""),
    )

    // The wrapper column item: `position:relative` and nothing else.
    private val relposWrapperProps = listOf(prop("Position", "\"RELATIVE\""))

    // The spanner: `column-span:all; height:50px` — NOT positioned.
    private val spannerProps = listOf(
        prop("ColumnSpan", "\"ALL\""),
        prop("Height", """{"type":"length","px":50}"""),
    )

    // The two green probes: absolute, inset-anchored at opposite corners.
    private val greenTopLeftProps = listOf(
        prop("Position", "\"ABSOLUTE\""),
        prop("Top", """{"px":0}"""),
        prop("Left", """{"px":0}"""),
        prop("BackgroundColor", """{"srgb":{"r":0,"g":0.5019607843137255,"b":0}}"""),
        prop("Height", """{"type":"length","px":100}"""),
        prop("Width", """{"type":"length","px":100}"""),
    )
    private val greenBottomRightProps = listOf(
        prop("Position", "\"ABSOLUTE\""),
        prop("Bottom", """{"px":0}"""),
        prop("Right", """{"px":0}"""),
        prop("BackgroundColor", """{"srgb":{"r":0,"g":0.5019607843137255,"b":0}}"""),
        prop("Height", """{"type":"length","px":100}"""),
        prop("Width", """{"type":"length","px":100}"""),
    )

    /** The test document's multicol subtree, verbatim in shape. */
    private fun spannerDocument() = comp(
        "multicol", multicolProps,
        listOf(
            comp(
                "relpos-wrapper", relposWrapperProps,
                listOf(
                    comp(
                        "spanner", spannerProps,
                        listOf(
                            comp("green-tl", greenTopLeftProps),
                            comp("green-br", greenBottomRightProps),
                        )
                    )
                )
            )
        )
    )

    // ── S1: the two wire predicates ───────────────────────────────────────

    @Test fun `S1 - the spanner declaration is recognised, the wrapper is not`() {
        assertTrue(MulticolSpannerContainingBlock.isSpanner(spannerProps))
        assertFalse(MulticolSpannerContainingBlock.isSpanner(relposWrapperProps))
        // `column-span: none` is the initial value and must never match.
        assertFalse(
            MulticolSpannerContainingBlock.isSpanner(listOf(prop("ColumnSpan", "\"NONE\"")))
        )
    }

    @Test fun `S1 - a column-count box is a multicol container, a plain box is not`() {
        assertTrue(MulticolSpannerContainingBlock.isMulticolContainer(multicolProps))
        assertFalse(MulticolSpannerContainingBlock.isMulticolContainer(relposWrapperProps))
        // css-multicol-1 §3: column-width alone also makes a container.
        assertTrue(
            MulticolSpannerContainingBlock.isMulticolContainer(
                listOf(prop("ColumnWidth", """{"type":"length","px":120}"""))
            )
        )
    }

    // ── S2: the chain restart itself ──────────────────────────────────────

    @Test fun `S2 - a spanner restarts the chain at its multicol container`() {
        // The wrapper published TRUE (it is position:relative), but the
        // multicol container's own context was FALSE. css-multicol-1 §6.1
        // lays the spanner out as a block of the container, so the wrapper
        // is skipped and the spanner's children see FALSE.
        assertFalse(
            MulticolSpannerContainingBlock.childPositionedAncestor(
                ancestorPositioned = true,
                positionedAtMulticol = false,
                properties = spannerProps,
            )
        )
    }

    @Test fun `S2 - the spanner's OWN position still counts`() {
        // Skipping OVER the wrapper is not skipping the spanner itself: a
        // positioned spanner really is its descendants' containing block.
        assertTrue(
            MulticolSpannerContainingBlock.childPositionedAncestor(
                ancestorPositioned = true,
                positionedAtMulticol = false,
                properties = spannerProps + prop("Position", "\"RELATIVE\""),
            )
        )
    }

    @Test fun `S2 - a positioned ancestor OUTSIDE the multicol survives`() {
        // Only boxes BETWEEN the container and the spanner are skipped; the
        // container's own chain state is what the restart restores.
        assertTrue(
            MulticolSpannerContainingBlock.childPositionedAncestor(
                ancestorPositioned = true,
                positionedAtMulticol = true,
                properties = spannerProps,
            )
        )
    }

    @Test fun `S2 - outside any multicol the rule is inert`() {
        // `column-span` computes to `none` with no multicol ancestor
        // (css-multicol-1 §6.1), so a null stamp keeps the frozen wave-17
        // OR-accumulation byte-for-byte.
        assertTrue(
            MulticolSpannerContainingBlock.childPositionedAncestor(
                ancestorPositioned = true,
                positionedAtMulticol = null,
                properties = spannerProps,
            )
        )
    }

    @Test fun `S2 - a non-spanner keeps the frozen OR-accumulation`() {
        // The wrapper itself: positioned, so its children see true — the
        // pre-wave-49 answer, regardless of the multicol stamp.
        assertTrue(
            MulticolSpannerContainingBlock.childPositionedAncestor(
                ancestorPositioned = false,
                positionedAtMulticol = false,
                properties = relposWrapperProps,
            )
        )
        // A static box passes the incoming value straight through.
        assertFalse(
            MulticolSpannerContainingBlock.childPositionedAncestor(
                ancestorPositioned = false,
                positionedAtMulticol = false,
                properties = spannerProps.drop(1),
            )
        )
    }

    // ── S3: the stamp ─────────────────────────────────────────────────────

    @Test fun `S3 - the multicol container stamps its own chain state`() {
        assertEquals(
            true,
            MulticolSpannerContainingBlock.childPositionedAtMulticol(
                positionedAtMulticol = null,
                childPositionedAncestor = true,
                properties = multicolProps,
            )
        )
        assertEquals(
            false,
            MulticolSpannerContainingBlock.childPositionedAtMulticol(
                positionedAtMulticol = null,
                childPositionedAncestor = false,
                properties = multicolProps,
            )
        )
    }

    @Test fun `S3 - a non-container passes the stamp through unchanged`() {
        assertNull(
            MulticolSpannerContainingBlock.childPositionedAtMulticol(
                positionedAtMulticol = null,
                childPositionedAncestor = true,
                properties = relposWrapperProps,
            )
        )
        assertEquals(
            false,
            MulticolSpannerContainingBlock.childPositionedAtMulticol(
                positionedAtMulticol = false,
                childPositionedAncestor = true,
                properties = relposWrapperProps,
            )
        )
    }

    // ── S4: end-to-end over the real document ─────────────────────────────

    @Test fun `S4 - both green probes hoist to the initial containing block`() {
        // The measured defect: before wave 49 the relpos wrapper claimed
        // both probes, so neither hoisted and the two RED probes at the
        // canvas corners stayed uncovered (iOS 4.27 green / 8.55 red,
        // Android 2.14 / 8.55, against a reference of 8.55 green / 0 red).
        assertEquals(
            listOf("green-tl", "green-br"),
            CanvasRootHoist.collectCanvasHoisted(listOf(spannerDocument())).map { it.id },
        )
    }

    @Test fun `S4 - the host activates for that document`() {
        // The activation walk must agree with the collect walk or the boxes
        // hoist with no Host to anchor them.
        assertTrue(CanvasRootHoist.hostActivates(listOf(spannerDocument())))
    }

    @Test fun `S4 - without the spanner the wrapper still claims both probes`() {
        // The negative control that proves the rule is doing the work: drop
        // `column-span: all` from the middle box and the pre-wave-49 answer
        // (nothing hoists — the relpos wrapper is the containing block)
        // comes straight back.
        val noSpanner = comp(
            "multicol", multicolProps,
            listOf(
                comp(
                    "relpos-wrapper", relposWrapperProps,
                    listOf(
                        comp(
                            "plain-block", listOf(prop("Height", """{"type":"length","px":50}""")),
                            listOf(
                                comp("green-tl", greenTopLeftProps),
                                comp("green-br", greenBottomRightProps),
                            )
                        )
                    )
                )
            )
        )
        assertEquals(
            emptyList<String>(),
            CanvasRootHoist.collectCanvasHoisted(listOf(noSpanner)).map { it.id },
        )
    }

    @Test fun `S4 - a spanner with no multicol ancestor changes nothing`() {
        // Same subtree, but the outer box is a plain block: `column-span`
        // computes to `none`, so the wrapper keeps both probes.
        val noContainer = comp(
            "plain-outer", listOf(prop("Width", """{"type":"length","px":320}""")),
            listOf(
                comp(
                    "relpos-wrapper", relposWrapperProps,
                    listOf(
                        comp(
                            "spanner", spannerProps,
                            listOf(
                                comp("green-tl", greenTopLeftProps),
                                comp("green-br", greenBottomRightProps),
                            )
                        )
                    )
                )
            )
        )
        assertEquals(
            emptyList<String>(),
            CanvasRootHoist.collectCanvasHoisted(listOf(noContainer)).map { it.id },
        )
    }

    @Test fun `S4 - a positioned ancestor outside the multicol still vetoes`() {
        // Wrap the whole document in a `position: relative` root: the
        // restart restores THAT box, which legitimately is the probes'
        // containing block, so nothing hoists.
        val outerPositioned = comp(
            "outer-relpos", relposWrapperProps, listOf(spannerDocument())
        )
        assertEquals(
            emptyList<String>(),
            CanvasRootHoist.collectCanvasHoisted(listOf(outerPositioned)).map { it.id },
        )
    }
}
