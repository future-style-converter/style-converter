package com.styleconverter.runtime.layout.position

// Wave 49 lane F1 — the CRASH REGRESSION for the clip-ancestry walk, and the
// executed re-derivation of lane A4's blast radius.
//
// ── WHAT BROKE ─────────────────────────────────────────────────────────
// Lane A4 (seam-a4-1) gave CanvasRootHoist a third ancestry channel,
// `hasClippingAncestor`, computed by hopping into ClipPathExtractor for EVERY
// node of EVERY document. Three corpus documents carry a `clip-path` whose
// `<position>` was inlined by `IRPropertySerializer.deepFlatten`, and the
// extractor's legacy bare-number fallback called `.jsonPrimitive` on it:
//
//   java.lang.IllegalArgumentException: Element class
//     kotlinx.serialization.json.JsonObject is not a JsonPrimitive
//     at ClipPathExtractor.readCenterPercent(ClipPathExtractor.kt:399)
//     at ClipPathExtractor.extractCircle(:222)
//     at ClipPathExtractor.extractClipPathConfig(:105)
//     at ClipPathSubtreeScopeKt.establishesUnescapableClip(:91)
//     at CanvasRootHoist.establishesUnescapableClip(:204)
//     at CanvasRootHoist.collectCanvasHoisted$walk(:435)
//
// `Host` runs `remember(roots) { collectCanvasHoisted(roots) }` during
// composition and apps/android-harness has no error boundary, so this was a
// dead capture Activity for the whole css-masking Android section — not a
// dropped property. Pre-A4 the same walk cleared all 1435 wave-48 documents.
//
// Two independent repairs, both pinned below:
//   • ROOT CAUSE — ClipPathExtractor.positionObject now reads the flattened
//     `at` clause (and the bare-number fallback uses a SAFE cast).
//   • GUARD — CanvasRootHoist.establishesUnescapableClip routes through
//     clipDecodeOrElse, so no future malformed payload can escape either.
//
// ── PAYLOADS ───────────────────────────────────────────────────────────
// Each document below is the content of one file under
// tools/titan/runs/wave48-final/sections/css-masking/per-test-ir/, minified
// (same keys, same values, same order; only JSON whitespace differs). They
// are transcribed rather than read from disk because tools/titan/runs/ is
// gitignored (.gitignore:86) and would not exist on a fresh clone or in CI.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRDocumentDecoder
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.SlotComposer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipHoistCorpusCrashTest {

    // ── The three documents that crashed ───────────────────────────────

    /** `wpt__css-masking__clip-path__clip-path-circle-closest-corner.json` —
     *  `.test { position:absolute; left:150px; top:100px; 350×450; red;
     *  clip-path: circle(closest-corner at 150px 200px) }` over a green
     *  520×520 inner. The `closest-corner` radius keyword is not part of
     *  css-shapes-1 §3.1 `<shape-radius>`, so the converter drops it and the
     *  shape serialises with `pos` as its ONLY non-`type` field — which is
     *  exactly the condition deepFlatten inlines. */
    private val circleClosestCorner = """
        {"irVersion":2,"minReaderVersion":2,"components":[
        {"id":"wpt__css-masking__clip-path__clip-path-circle-closest-corner__0-025","name":"wpt__css-masking__clip-path__clip-path-circle-closest-corner__0","properties":[{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"meta":{"role":"body-root"}},
        {"id":"wpt__css-masking__clip-path__clip-path-circle-closest-corner__1-026","name":"wpt__css-masking__clip-path__clip-path-circle-closest-corner__1","properties":[],"text":"The test passes if there is a full green circle.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css-masking__clip-path__clip-path-circle-closest-corner__2-027","name":"wpt__css-masking__clip-path__clip-path-circle-closest-corner__2","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":150}},{"type":"Top","data":{"px":100}},{"type":"Width","data":{"type":"length","px":350}},{"type":"Height","data":{"type":"length","px":450}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"ClipPath","data":{"type":"circle","x":{"px":150},"y":{"px":200}}}]},
        {"id":"clip-path__clip-path-circle-closest-corner__1__0-028","name":"clip-path__clip-path-circle-closest-corner__1__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":-60}},{"type":"Left","data":{"px":-110}},{"type":"Width","data":{"type":"length","px":520}},{"type":"Height","data":{"type":"length","px":520}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"wpt__css-masking__clip-path__clip-path-circle-closest-corner__2-027"}}]}
    """.trimIndent()

    /** `…clip-path-circle-farthest-corner.json` — same shape, 200×300 box,
     *  `circle(farthest-corner at 200px 150px)`, two slotted children. */
    private val circleFarthestCorner = """
        {"irVersion":2,"minReaderVersion":2,"components":[
        {"id":"wpt__css-masking__clip-path__clip-path-circle-farthest-corner__0-029","name":"wpt__css-masking__clip-path__clip-path-circle-farthest-corner__0","properties":[{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"meta":{"role":"body-root"}},
        {"id":"wpt__css-masking__clip-path__clip-path-circle-farthest-corner__1-030","name":"wpt__css-masking__clip-path__clip-path-circle-farthest-corner__1","properties":[],"text":"The test passes if there is a full green circle.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css-masking__clip-path__clip-path-circle-farthest-corner__2-031","name":"wpt__css-masking__clip-path__clip-path-circle-farthest-corner__2","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":100}},{"type":"Top","data":{"px":150}},{"type":"Width","data":{"type":"length","px":200}},{"type":"Height","data":{"type":"length","px":300}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"ClipPath","data":{"type":"circle","x":{"px":200},"y":{"px":150}}}]},
        {"id":"clip-path__clip-path-circle-farthest-corner__1__0-032","name":"clip-path__clip-path-circle-farthest-corner__1__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":-110}},{"type":"Left","data":{"px":-100}},{"type":"Width","data":{"type":"length","px":600}},{"type":"Height","data":{"type":"length","px":520}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"wpt__css-masking__clip-path__clip-path-circle-farthest-corner__2-031"},"meta":{"role":"ws-after"}},
        {"id":"clip-path__clip-path-circle-farthest-corner__1__1-033","name":"clip-path__clip-path-circle-farthest-corner__1__1","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}}],"slot":{"parent":"wpt__css-masking__clip-path__clip-path-circle-farthest-corner__2-031"}}]}
    """.trimIndent()

    /** `…clip-path-ellipse-closest-farthest-corner.json` — the ellipse
     *  spelling, `ellipse(farthest-corner closest-corner at 175px 100px)`.
     *  Note this document has NO `body-root` component at all. */
    private val ellipseClosestFarthestCorner = """
        {"irVersion":2,"minReaderVersion":2,"components":[
        {"id":"wpt__css-masking__clip-path__clip-path-ellipse-closest-farthest-corner__0-065","name":"wpt__css-masking__clip-path__clip-path-ellipse-closest-farthest-corner__0","properties":[],"text":"The test passes if there is a full green ellipse.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css-masking__clip-path__clip-path-ellipse-closest-farthest-corner__1-066","name":"wpt__css-masking__clip-path__clip-path-ellipse-closest-farthest-corner__1","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":150}},{"type":"Top","data":{"px":100}},{"type":"Width","data":{"type":"length","px":250}},{"type":"Height","data":{"type":"length","px":275}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"ClipPath","data":{"type":"ellipse","x":{"px":175},"y":{"px":100}}}]},
        {"id":"clip-path__clip-path-ellipse-closest-farthest-corner__1__0-067","name":"clip-path__clip-path-ellipse-closest-farthest-corner__1__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":-60}},{"type":"Left","data":{"px":-110}},{"type":"Width","data":{"type":"length","px":550}},{"type":"Height","data":{"type":"length","px":420}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"wpt__css-masking__clip-path__clip-path-ellipse-closest-farthest-corner__1-066"}}]}
    """.trimIndent()

    /** `…clip-path-blending-offset.json` — the document the veto ACTUALLY
     *  changes (one of exactly two in the corpus). Its clip carrier is a
     *  STATIC box, so the carrier never hoists and only its abspos child's
     *  decision moves. */
    private val blendingOffset = """
        {"irVersion":2,"minReaderVersion":2,"components":[
        {"id":"wpt__css-masking__clip-path__clip-path-blending-offset__0-002","name":"wpt__css-masking__clip-path__clip-path-blending-offset__0","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"ClipPath","data":{"type":"polygon","points":[{"x":{"px":0},"y":{"px":0}},{"x":{"px":100},"y":{"px":0}},{"x":{"px":100},"y":{"px":30}},{"x":{"px":30},"y":{"px":30}},{"x":{"px":30},"y":{"px":100}},{"x":{"px":0},"y":{"px":100}}]}}]},
        {"id":"clip-path__clip-path-blending-offset__0__0-003","name":"clip-path__clip-path-blending-offset__0__0","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"Position","data":"ABSOLUTE"},{"type":"MixBlendMode","data":"MULTIPLY"},{"type":"Left","data":{"px":40}},{"type":"Top","data":{"px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}],"slot":{"parent":"wpt__css-masking__clip-path__clip-path-blending-offset__0-002"}}]}
    """.trimIndent()

    /** Decode + slot-compose one document exactly as the harness screen does
     *  (IRDocumentDecoder → SlotComposer.compose), so the walk under test
     *  sees the real root forest and not a hand-built tree. */
    private fun roots(documentJson: String): List<IRComponent> =
        SlotComposer.compose(IRDocumentDecoder.decode(documentJson))

    /** The three documents that threw, keyed by the id of their clip carrier
     *  — the component whose `clip-path` payload carries the flattened `at`
     *  clause, and (measured below) the component that HOISTS. */
    private val crashers = listOf(
        "wpt__css-masking__clip-path__clip-path-circle-closest-corner__2-027" to circleClosestCorner,
        "wpt__css-masking__clip-path__clip-path-circle-farthest-corner__2-031" to circleFarthestCorner,
        "wpt__css-masking__clip-path__clip-path-ellipse-closest-farthest-corner__1-066"
            to ellipseClosestFarthestCorner,
    )

    // ── 1. The crash itself ────────────────────────────────────────────

    @Test fun `the three crashing documents walk both hoist walks without throwing`() {
        for ((carrierId, document) in crashers) {
            val forest = roots(document)
            // collectCanvasHoisted is the site that threw: it has no early
            // return, so it computes the clip flag for every node. A throw
            // here fails the test by escaping — which is exactly how the
            // wave-49 skeptic's corpus walk died.
            val hoisted = CanvasRootHoist.collectCanvasHoisted(forest)
            // hostActivates escaped the same throw pre-fix only by an accident
            // of short-circuit ordering (its activation test returns before it
            // computes the child clip flag). Walked here too so the ordering
            // can never silently become load-bearing again.
            CanvasRootHoist.hostActivates(forest)
            // A walk that returned an empty list because it swallowed the
            // document would pass a "did not throw" assertion, so pin the
            // POSITIVE outcome as well: the carrier is in the overlay list.
            assertEquals(
                "overlay list for $carrierId",
                listOf(carrierId),
                hoisted.map { it.id },
            )
        }
    }

    // ── 2. Lane A4's blast-radius claim, re-derived by execution ───────

    @Test fun `the clip carriers in all three corner documents DO hoist`() {
        // A4's census asserted these three "have a positioned ancestor, so
        // they were never hoist candidates". Executed: in each document the
        // clip-path carrier is an absolutely positioned ROOT (no positioned
        // ancestor) carrying insets, which is precisely the wave-17 hoist
        // condition. The claim was wrong about which box it described — it is
        // the carrier's inner child that has the positioned ancestor.
        for ((carrierId, document) in crashers) {
            val carrier = roots(document).single { it.id == carrierId }
            assertTrue(
                "$carrierId is a hoist candidate",
                CanvasRootHoist.shouldHoistToCanvasRoot(
                    carrier.properties,
                    hasPositionedAncestor = false,
                ),
            )
            // …and its inner child, which DOES have a positioned ancestor,
            // is the box that was never a candidate.
            val child = carrier.children.orEmpty().first()
            assertFalse(
                "${child.id} is not a hoist candidate",
                CanvasRootHoist.shouldHoistToCanvasRoot(
                    child.properties,
                    hasPositionedAncestor = true,
                ),
            )
        }
    }

    @Test fun `the corner documents' hoist decisions are unchanged by the veto`() {
        // The veto reads the ANCESTOR clip flag, and a box's own clip never
        // feeds its own flag — so the carrier keeps hoisting even though it
        // paints a clip. This is why the executed corpus delta is 2 documents
        // and not 5, despite three more documents newly ENTERING the
        // predicate (which is where the crash lived).
        for ((carrierId, document) in crashers) {
            assertEquals(
                listOf(carrierId),
                CanvasRootHoist.collectCanvasHoisted(roots(document)).map { it.id },
            )
        }
    }

    @Test fun `blending-offset is a document the veto really does change`() {
        // The control for the test above: here the clip carrier is STATIC and
        // the abspos child sits under it, so the child's decision flips. Pinned
        // on the verbatim document rather than hand-built properties, because
        // it is the pair (this one and clip-path-on-fixed-position-scroll)
        // that the measured "2 of 1435" number is made of.
        val forest = roots(blendingOffset)
        val childId = "clip-path__clip-path-blending-offset__0__0-003"
        // Wave-48 behaviour, reconstructed by running the walk with the clip
        // channel forced off: the child was hoisted into the canvas overlay.
        assertEquals(
            listOf(childId),
            CanvasRootHoist.collectCanvasHoisted(
                forest.map { it.copy(properties = withoutClipPath(it.properties)) },
            ).map { it.id },
        )
        // With the clip channel live it is vetoed back out of the overlay…
        assertEquals(emptyList<String>(), CanvasRootHoist.collectCanvasHoisted(forest).map { it.id })
        // …and the host still activates, because the vetoed box needs the
        // zero-flow anchor only a live host installs.
        assertTrue(CanvasRootHoist.hostActivates(forest))
    }

    /** Strip `ClipPath` from a declaration list, to reconstruct the
     *  pre-wave-49 answer through the SAME walk rather than a mirror of it
     *  (a hand-written mirror is a second implementation that can drift). */
    private fun withoutClipPath(properties: List<IRProperty>): List<IRProperty> =
        properties.filterNot { it.type == "ClipPath" }

    // ── 3. The guard, independent of the root-cause fix ────────────────

    @Test fun `a malformed clip payload yields false instead of escaping`() {
        // Synthetic, not a corpus carrier: `geometry-box` is read with
        // `.jsonPrimitive` inside extractClipPathConfig, so an object there
        // still throws today. That is the point — the guard has to hold for
        // payloads nobody has enumerated, which is the class the wave-49
        // crash belonged to before it was enumerated.
        val malformed = listOf(
            IRProperty("ClipPath", Json.parseToJsonElement("""{"geometry-box":{"unexpected":1}}""")),
        )
        assertFalse(CanvasRootHoist.establishesUnescapableClip(malformed))
    }

    @Test fun `a malformed clip payload does not take the pure walks down`() {
        // The same bag threaded through the two walks that have no error
        // boundary above them, on a tree whose shape would otherwise hoist.
        val child = IRComponent(
            id = "child",
            name = "child",
            properties = listOf(
                IRProperty("Position", Json.parseToJsonElement("\"ABSOLUTE\"")),
                IRProperty("Left", Json.parseToJsonElement("""{"px":10}""")),
            ),
        )
        val parent = IRComponent(
            id = "parent",
            name = "parent",
            properties = listOf(
                IRProperty(
                    "ClipPath",
                    Json.parseToJsonElement("""{"geometry-box":{"unexpected":1}}"""),
                ),
            ),
            children = listOf(child),
        )
        // Undecodable clip ⇒ no `Modifier.clip` is painted ⇒ nothing to
        // escape ⇒ the child keeps its pre-veto hoist. Same answer the guard's
        // `false` asserts, reached through the real walk.
        assertEquals(listOf("child"), CanvasRootHoist.collectCanvasHoisted(listOf(parent)).map { it.id })
        assertTrue(CanvasRootHoist.hostActivates(listOf(parent)))
    }
}
