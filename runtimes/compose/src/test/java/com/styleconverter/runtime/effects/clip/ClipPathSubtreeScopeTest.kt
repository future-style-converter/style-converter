package com.styleconverter.runtime.effects.clip

// Wave 49 (lane A4) — JVM pins for the unescapable-clip ancestry channel
// (css-masking-1 §5) and the canvas-root-hoist veto it drives.
//
// Every payload below is copied VERBATIM out of the wave-48 gate's per-test
// IR — `tools/titan/runs/wave48-final/sections/css-masking/per-test-ir/
// wpt__css-masking__clip-path__clip-path-blending-offset.json` and its two
// neighbours — so a converter wire change fails here before it silently
// changes what the runtime clips.

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipPathSubtreeScopeTest {

    // One JSON literal into the IRProperty data slot — the wire the decoder
    // hands the extractors.
    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    // ── Verbatim corpus payloads ─────────────────────────────────────────

    /**
     * `#clip-path { width:100px; height:100px; overflow:hidden;
     * background:green; clip-path: polygon(0 0, 100px 0, 100px 30px,
     * 30px 30px, 30px 100px, 0 100px) }` — component `…blending-offset__0-002`.
     */
    private fun clippingParentProps(): List<IRProperty> = listOf(
        prop("Width", """{"type":"length","px":100}"""),
        prop("Height", """{"type":"length","px":100}"""),
        prop("OverflowX", "\"HIDDEN\""),
        prop("OverflowY", "\"HIDDEN\""),
        prop(
            "BackgroundColor",
            """{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}""",
        ),
        prop(
            "ClipPath",
            """
            {"type":"polygon","points":[
              {"x":{"px":0},"y":{"px":0}},
              {"x":{"px":100},"y":{"px":0}},
              {"x":{"px":100},"y":{"px":30}},
              {"x":{"px":30},"y":{"px":30}},
              {"x":{"px":30},"y":{"px":100}},
              {"x":{"px":0},"y":{"px":100}}]}
            """.trimIndent(),
        ),
    )

    /**
     * `#blend { width:100px; height:100px; position:absolute;
     * mix-blend-mode:multiply; left:40px; top:50px; background:red }` —
     * component `clip-path__clip-path-blending-offset__0__0-003`.
     */
    private fun blendChildProps(): List<IRProperty> = listOf(
        prop("Width", """{"type":"length","px":100}"""),
        prop("Height", """{"type":"length","px":100}"""),
        prop("Position", "\"ABSOLUTE\""),
        prop("MixBlendMode", "\"MULTIPLY\""),
        prop("Left", """{"px":40}"""),
        prop("Top", """{"px":50}"""),
        prop("BackgroundColor", """{"srgb":{"r":1,"g":0,"b":0},"original":"red"}"""),
    )

    // ── The predicate itself ─────────────────────────────────────────────

    @Test fun `a painted clip-path establishes an unescapable clip`() {
        assertTrue(establishesUnescapableClip(clippingParentProps()))
    }

    @Test fun `overflow hidden alone does NOT — CSS 2․1 §11․1․1 lets a positioned descendant escape it`() {
        // Same box, clip-path removed: the overflow declarations remain and
        // must not stand in for a clip-path. Widening the predicate to
        // overflow would break the escape css-position-3 relies on.
        val overflowOnly = clippingParentProps().filterNot { it.type == "ClipPath" }
        assertFalse(establishesUnescapableClip(overflowOnly))
    }

    @Test fun `a declaration list with no clip at all is not a clipping ancestor`() {
        assertFalse(establishesUnescapableClip(blendChildProps()))
    }

    @Test fun `an unparseable clip-path paints nothing so nothing can escape it`() {
        // No Modifier.clip is emitted for a shape the extractor refuses, so
        // the veto must not fire either (predicate and applier share the
        // ClipPathExtractor for exactly this reason).
        val junk = listOf(prop("ClipPath", """{"type":"not-a-shape"}"""))
        assertEquals(
            ClipPathExtractor.extractClipPathConfig(junk.map { it.type to it.data }).hasClipPath,
            establishesUnescapableClip(junk),
        )
        assertFalse(establishesUnescapableClip(junk))
    }
}
