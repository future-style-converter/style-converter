package com.styleconverter.runtime.core.renderer

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit pins for the wave-28 lane-PG root-scope generated-box bridge
 * (RootPseudoSpec.kt / RootPseudoBox.kt's pure helpers).
 *
 * THE MEASURED SHAPE: css-contain/contain-body-dir-001..004 declare
 * `html::before { content:""; width:100px; height:100px; background:orange;
 * display:block }` on a body that is `direction: rtl; contain: layout`. In
 * tools/titan/runs/wave27-final/sections/css-contain the Chromium ref paints
 * 10 000 orange px at image [16,16]-[115,115]; the Android and iOS captures
 * painted ZERO orange px because this runtime decoded the `pseudos` bucket
 * and rendered nothing from it. These tests hold the bridge's semantics; the
 * pixels themselves are a device-run concern.
 */
class RootPseudoSpecTest {

    /** The live contain-body-dir-001 `pseudos.before` bucket, verbatim. */
    private fun containBucket(): JsonObject = buildJsonObject {
        put("properties", buildJsonObject {
            put("content", "\"\"")
            put("width", "100px")
            put("height", "100px")
            put("background", "orange")
            put("display", "block")
        })
    }

    /** A body-root component carrying that bucket plus `contain: layout`. */
    private fun bodyRoot(contain: String? = "LAYOUT", bucket: JsonObject? = containBucket()) =
        IRComponent(
            id = "wpt__contain-body-dir-001__0-011",
            name = "wpt__contain-body-dir-001__0",
            properties = listOfNotNull(
                contain?.let { IRProperty("Contain", kotlinx.serialization.json.JsonArray(
                    listOf(kotlinx.serialization.json.JsonPrimitive(it)))) },
            ),
            role = "body-root",
            pseudos = bucket?.let { buildJsonObject { put("before", it) } },
        )

    @Test
    fun bridgesTheContainFamilyBucket() {
        // Every declaration of the family maps to a field; nothing is lost.
        val spec = rootPseudoSpec(containBucket(), "before")
        assertNotNull(spec)
        assertEquals(100f, spec!!.widthPx)
        assertEquals(100f, spec.heightPx)
        assertEquals("orange", spec.backgroundCss)
        // `content: ""` is a LITERAL empty string, not an unresolvable value.
        assertEquals("", spec.text)
        assertTrue(spec.isBlock)
        // A background makes the box paintable even with empty content.
        assertTrue(spec.paints)
    }

    @Test
    fun nonPxLengthsAreAutoNotZero() {
        // % / em / calc() are runtime-dependent by the IR's own rule, so the
        // axis stays auto (a silent 0 would paint an invisible box).
        val spec = rootPseudoSpec(buildJsonObject {
            put("properties", buildJsonObject {
                put("width", "50%"); put("height", "0"); put("background", "orange")
            })
        }, "before")
        assertNull(spec!!.widthPx)
        // A bare `0` IS a valid length (css-values-4 §6).
        assertEquals(0f, spec.heightPx)
    }

    @Test
    fun contentLiteralUnquotesAndRefusesFunctions() {
        // Quoted literals unquote; `none`/`normal` generate no text …
        assertEquals("x", contentLiteral("\"x\"", "before"))
        assertEquals("x", contentLiteral("'x'", "before"))
        assertEquals("", contentLiteral("none", "before"))
        // … and a function form has no context here, so it renders empty.
        assertEquals("", contentLiteral("counter(c)", "before"))
    }

    @Test
    fun blockDisplayAcceptsTheTwoValueSyntax() {
        // css-display-3 §2 two-value grammar and the block-level aliases.
        assertTrue(isBlockDisplay("block"))
        assertTrue(isBlockDisplay("block flow"))
        assertTrue(isBlockDisplay("flow-root"))
        assertFalse(isBlockDisplay("inline"))
    }

    @Test
    fun emptyOrNonPaintingBucketsRenderNothing() {
        // No bucket, no declarations, and a declarations-only bucket with
        // neither background nor content all resolve to "draw nothing".
        assertNull(rootPseudoSpec(null, "before"))
        assertNull(rootPseudoSpec(buildJsonObject { put("properties", buildJsonObject { }) }, "before"))
        val sizeOnly = rootPseudoSpec(buildJsonObject {
            put("properties", buildJsonObject { put("width", "100px"); put("display", "block") })
        }, "before")
        assertFalse(sizeOnly!!.paints)
    }

    @Test
    fun onlyABodyRootHostsARootScopeBox() {
        // The role gate is what keeps ordinary elements' ::before on the
        // ContentApplier channel and `pseudos.marker` on the list path.
        assertNotNull(rootPseudoSpecFor(bodyRoot(), "before"))
        assertNull(rootPseudoSpecFor(bodyRoot().copy(role = null), "before"))
        // A role with no bucket, and a bucket for a role nobody declared.
        assertNull(rootPseudoSpecFor(bodyRoot(bucket = null), "before"))
        assertNull(rootPseudoSpecFor(bodyRoot(), "after"))
    }

    @Test
    fun theContainmentGateMatchesTheCanvasGate() {
        // Same token rule as the background gate — delegation, not a copy.
        for (kind in listOf("LAYOUT", "PAINT", "CONTENT", "STRICT")) {
            assertTrue(containmentBlocksDirectionPropagation(listOf(kind)))
        }
        assertFalse(containmentBlocksDirectionPropagation(listOf("NONE")))
        assertFalse(containmentBlocksDirectionPropagation(emptyList()))
        assertFalse(containmentBlocksDirectionPropagation(null))
    }

    @Test
    fun containKeywordsReadBothWireShapes() {
        // Array wire (normal emission) and the defensive primitive form.
        assertEquals(listOf("LAYOUT"), containKeywordsOf(bodyRoot()))
        val bare = bodyRoot().copy(properties = listOf(
            IRProperty("Contain", kotlinx.serialization.json.JsonPrimitive("layout paint"))))
        assertEquals(listOf("layout", "paint"), containKeywordsOf(bare))
        // No leaf at all ⇒ no containment ⇒ the body still propagates.
        assertNull(containKeywordsOf(bodyRoot(contain = null)))
        assertFalse(containmentBlocksDirectionPropagation(containKeywordsOf(bodyRoot(contain = null))))
    }
}
