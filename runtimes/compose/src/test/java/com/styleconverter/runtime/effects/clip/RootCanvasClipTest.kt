package com.styleconverter.runtime.effects.clip

// Wave 49 (lane A4) — JVM pins for the document-element clip resolver
// (css-masking-1 §5 on the root element).
//
// Payloads copied VERBATIM out of the wave-48 gate's per-test IR
// (`tools/titan/runs/wave48-final/sections/css-masking/per-test-ir/
// wpt__css-masking__clip-path__clip-path-document-element.json`).
//
// The Shape half ([rootCanvasClipShape]) is deliberately NOT pinned here:
// `Shape.createOutline` allocates an `androidx.compose.ui.graphics.Path`,
// which on the JVM unit-test classpath is the android.jar stub — this suite
// has no Robolectric (its standing constraint), so shape geometry is
// unpinnable by construction, exactly as it already is for
// ClipPathApplier.createShape. What IS pinnable — the decision to clip at
// all, and which config is used — is pinned below.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootCanvasClipTest {

    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    /** The verbatim `html { background: red; clip-path: polygon(…) }` bag. */
    private fun rootProps(): List<IRProperty> = listOf(
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

    /** The verbatim `div { width:500px; height:500px; background:green }`. */
    private fun greenDivProps(): List<IRProperty> = listOf(
        prop("Width", """{"type":"length","px":500}"""),
        prop("Height", """{"type":"length","px":500}"""),
        prop(
            "BackgroundColor",
            """{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}""",
        ),
    )

    private fun comp(id: String, properties: List<IRProperty>, role: String? = null) =
        IRComponent(id = id, name = id, properties = properties, role = role)

    private fun documentElementRoots(rootProperties: List<IRProperty>) = listOf(
        comp(
            "wpt__css-masking__clip-path__clip-path-document-element__0-047",
            rootProperties,
            role = "body-root",
        ),
        comp("wpt__css-masking__clip-path__clip-path-document-element__1-048", greenDivProps()),
    )

    @Test fun `the document element's polygon is resolved as the canvas clip`() {
        val config = rootCanvasClipConfig(documentElementRoots(rootProps()))
        assertNotNull("root clip must be resolved", config)
        assertTrue(config!!.hasClipPath)
        // Six vertices, in wire order — the "L" the ref draws at [66,66]-[165,165].
        val polygon = config.shape as ClipShape.Polygon
        assertEquals(6, polygon.points.size)
    }

    @Test fun `a body-root with no clip-path leaves the canvas chain untouched`() {
        // The shape of 1433 of the corpus's 1435 documents — this null is
        // what keeps every other composed capture byte-identical.
        assertNull(rootCanvasClipConfig(documentElementRoots(listOf(rootProps()[0]))))
    }

    @Test fun `the initial none keyword resolves to no clip`() {
        assertNull(rootCanvasClipConfig(documentElementRoots(listOf(prop("ClipPath", "\"none\"")))))
    }

    @Test fun `a clip on an ordinary root is NOT the page clip`() {
        // Only the merged `meta.role: body-root` bag is the document element;
        // an ordinary sibling's clip belongs to its own box.
        val roots = listOf(
            comp("r", emptyList(), role = "body-root"),
            comp("c", rootProps()),
        )
        assertNull(rootCanvasClipConfig(roots))
    }

    @Test fun `a document with no body-root has no page clip`() {
        assertNull(rootCanvasClipConfig(listOf(comp("only", greenDivProps()))))
    }

    @Test fun `containment does not block the root clip - it is not a propagation`() {
        // css-contain-2 §2 / css-contain-1 §2 take a contained root off
        // the background / writing-mode / direction propagation path. A
        // clip-path is the root's own clip over its own subtree, so no
        // containment clause applies to it.
        val contained = rootProps() + prop("Contain", """["LAYOUT"]""")
        val config = rootCanvasClipConfig(documentElementRoots(contained))
        // Retro R10 (A8#1): "does not block" must mean the SAME clip is
        // resolved — a config with `hasClipPath == false` (the audit's
        // mutation M6, which returned an empty ClipPathConfig whenever a
        // Contain leaf was present) is non-null yet paints no clip, so the
        // old not-null assertion could not see the block it claimed to rule
        // out. Pin the decision AND the geometry against the uncontained bag.
        assertNotNull("containment must not suppress the root clip", config)
        assertTrue(config!!.hasClipPath)
        // Identical polygon to the uncontained resolve — the six wire vertices
        // of the document-element "L", untouched by the Contain leaf.
        val uncontained = rootCanvasClipConfig(documentElementRoots(rootProps()))!!
        assertEquals(uncontained.shape, config.shape)
        assertEquals(6, (config.shape as ClipShape.Polygon).points.size)
    }
}
