package com.styleconverter.runtime.effects.clip

// Wave 49 lane F1 — pins for the clip category's totality seam.
//
// Two composition-time call sites hop into ClipPathExtractor with no error
// boundary above them (apps/android-harness installs none — grep for
// ErrorBoundary / uncaughtException over apps/android-harness/app/src/main
// returns nothing):
//   • CanvasRootHoist.establishesUnescapableClip — per node, both pure walks
//     plus ComponentRenderer's `childHasClippingAncestor`.
//   • rootCanvasClipConfig — once per composed capture canvas, inside the
//     canvas's own modifier chain.
// A throw at either is a dead capture Activity, not a dropped property. This
// suite pins that neither can throw, and that the fallback each takes is the
// TRUTHFUL one rather than merely the quiet one.
//
// The malformed payloads below are synthetic: no corpus document carries
// them. That is deliberate — the wave-49 crash was itself a payload class
// nobody had enumerated (`{"type":"circle","x":{"px":150},"y":{"px":200}}`,
// three carriers in tools/titan/runs/wave48-final), so a guard pinned only
// against known-bad input would not have caught it either.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ClipDecodeGuardTest {

    @Before fun clearLogOnceGuard() {
        // The breadcrumb is once-per-key process-wide; reset so each case
        // starts from a known-clean state and cases cannot mask each other.
        ClipDecodeLog.resetForTest()
    }

    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    /** A `clip-path` bag that still throws inside extractClipPathConfig:
     *  `geometry-box` is read with `.jsonPrimitive`, which raises
     *  IllegalArgumentException on a JSON object. */
    private fun malformedClip() =
        listOf(prop("ClipPath", """{"geometry-box":{"unexpected":1}}"""))

    /** The verbatim root bag of WPT css-masking/clip-path/
     *  clip-path-document-element (per-test IR component `…__0`): a red
     *  `html` with the six-point L polygon. */
    private fun wellFormedRootProps(): List<IRProperty> = listOf(
        prop("BackgroundColor", """{"srgb":{"r":1,"g":0,"b":0},"original":"red"}"""),
        prop(
            "ClipPath",
            """
            {"type":"polygon","points":[
              {"x":{"px":50},"y":{"px":50}},
              {"x":{"px":100},"y":{"px":50}},
              {"x":{"px":100},"y":{"px":100}},
              {"x":{"px":150},"y":{"px":100}},
              {"x":{"px":150},"y":{"px":150}},
              {"x":{"px":50},"y":{"px":150}}]}
            """.trimIndent(),
        ),
    )

    private fun root(properties: List<IRProperty>) =
        IRComponent(id = "root", name = "root", properties = properties, role = "body-root")

    @Test fun `clipDecodeOrElse returns the fallback instead of propagating`() {
        // The seam itself, exercised directly: an exception from the block is
        // absorbed and the caller's stated fallback comes back.
        val answer = clipDecodeOrElse("unit-test", fallback = "fallback") {
            throw IllegalArgumentException("simulated wire failure")
        }
        assertEquals("fallback", answer)
    }

    @Test fun `clipDecodeOrElse does not swallow Errors`() {
        // An OutOfMemoryError / StackOverflowError is not a decode failure and
        // must keep propagating — catching Throwable here would turn a real
        // VM problem into a silently unclipped page.
        var propagated = false
        try {
            clipDecodeOrElse("unit-test", fallback = "fallback") { throw StackOverflowError("vm") }
        } catch (e: StackOverflowError) {
            propagated = true
        }
        assertEquals(true, propagated)
    }

    @Test fun `a malformed root clip degrades to no page clip`() {
        // rootCanvasClipConfig must answer "this canvas paints no root clip"
        // rather than dying: an undecodable value paints no clip on the
        // element path either, so no-clip is the honest degradation.
        assertNull(rootCanvasClipConfig(listOf(root(malformedClip()))))
    }

    @Test fun `a well-formed root clip is still resolved`() {
        // The guard must not have turned the feature off. The document-element
        // polygon still produces a config, and it still reports a clip.
        val config = rootCanvasClipConfig(listOf(root(wellFormedRootProps())))
        assertNotNull(config)
        assertEquals(true, config!!.hasClipPath)
    }

    @Test fun `a root with no clip-path is still null, not a guard artefact`() {
        // The overwhelming-majority path: no ClipPath leaf at all. It must
        // return null through the ordinary branch, so a future regression that
        // makes EVERY document throw cannot hide behind the same null.
        val config = rootCanvasClipConfig(
            listOf(root(listOf(prop("BackgroundColor", """{"srgb":{"r":1,"g":0,"b":0}}""")))),
        )
        assertNull(config)
        // …and the guarded predicate agrees that nothing is clipped here.
        assertFalse(
            establishesUnescapableClip(
                listOf(prop("BackgroundColor", """{"srgb":{"r":1,"g":0,"b":0}}""")),
            ),
        )
    }

    @Test fun `the unguarded category predicate is what actually throws`() {
        // Names the boundary explicitly: the raw category predicate is NOT
        // total, by design — it is the two composition-time callers that wrap
        // it. If a future refactor makes this stop throwing the guard is no
        // longer load-bearing and this pin should be revisited, not deleted.
        var threw = false
        try {
            establishesUnescapableClip(malformedClip())
        } catch (e: Exception) {
            threw = true
        }
        assertEquals(true, threw)
    }
}
