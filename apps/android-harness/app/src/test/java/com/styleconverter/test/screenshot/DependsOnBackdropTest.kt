package com.styleconverter.test.screenshot

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit pins for [dependsOnBackdrop] — the mirror of [parentCreatesContext].
 *
 * WHY IT EXISTS. `parentCreatesContext` asks the question in the PARENT
 * direction ("does this node's paint context own how its children compose?")
 * and suppresses the children when it does. That misses the mirror case: a
 * child can be backdrop-dependent all by itself, under a perfectly ordinary
 * parent.
 *
 * Found on fixtures/composition-test.json. `Blend_Multiply_OverGradient` has
 * a plain `position: relative` parent, so the parent predicate does not fire,
 * and its `mix-blend-mode` child was emitted as a standalone capture
 * (`005_layer.png`). Composited against the bare capture canvas instead of
 * the gradient it was authored over, it produced four divergent
 * cross-platform pairs of pure noise.
 *
 * WHY THE TESTS MATTER MORE THAN USUAL. Capture indices are POSITIONAL — the
 * comparator matches iOS/Android/web captures by index. A suppression rule
 * that fires on one platform and not another does not merely lose a row; it
 * silently misaligns every component after it, so the harness compares
 * unrelated components and reports the mismatch as a rendering divergence.
 * These pins exist so the Kotlin predicate cannot drift from the TypeScript
 * one in apps/web-harness/tests/ui/CaptureGallery.test.tsx, which asserts the
 * same table.
 */
class DependsOnBackdropTest {

    /** Build a property whose `data` is raw JSON, matching real IR shapes. */
    private fun prop(type: String, json: String): IRProperty =
        IRProperty(type = type, data = Json.parseToJsonElement(json))

    private fun comp(vararg props: IRProperty): IRComponent =
        IRComponent(id = "c", name = "Comp", properties = props.toList())

    // ── True positives ──────────────────────────────────────────────────────

    @Test
    fun `backdrop-filter is backdrop-dependent`() {
        // The filter's input IS the backdrop; standalone it is the identity.
        assertTrue(dependsOnBackdrop(comp(prop("BackdropFilter", """[{"blur":8}]"""))))
    }

    @Test
    fun `every non-normal blend mode is backdrop-dependent`() {
        for (v in listOf("multiply", "screen", "difference", "overlay")) {
            assertTrue(v, dependsOnBackdrop(comp(prop("MixBlendMode", "\"$v\""))))
        }
    }

    @Test
    fun `accepts the uppercase enum shape (LIVE) and the value wrapper (tolerated)`() {
        // The uppercase BARE STRING is the live wire: probe `:converter:run`
        // 2026-09-05, `mix-blend-mode: multiply` → `"MULTIPLY"`, and the
        // 1436-document wave49-final catalogue carries MixBlendMode only as
        // a bare string. The spec-grade parser lowercases, so both cases
        // must read — a predicate handling one only would fire on some
        // platforms and not others, exactly the misalignment this rule
        // must avoid.
        assertTrue(dependsOnBackdrop(comp(prop("MixBlendMode", "\"MULTIPLY\""))))
        // TOLERANCE, not a live wire (retro R10, A8#5): no property has ever
        // arrived as `{"value":…}` for MixBlendMode. Kept because the shared
        // keyword reader accepts the wrapper for other longhands and the
        // predicate must not regress if one ever routes through it.
        assertTrue(dependsOnBackdrop(comp(prop("MixBlendMode", """{"value":"SCREEN"}"""))))
    }

    // ── True negatives ──────────────────────────────────────────────────────

    @Test
    fun `mix-blend-mode normal is NOT backdrop-dependent`() {
        // This is the CONTROL component in composition-test.json. If the rule
        // fired here the control would be suppressed and the case/control
        // pair — the only mechanism that distinguishes "implemented" from
        // "silently dropped" — would be lost.
        assertFalse(dependsOnBackdrop(comp(prop("MixBlendMode", "\"normal\""))))
        assertFalse(dependsOnBackdrop(comp(prop("MixBlendMode", "\"NORMAL\""))))
    }

    @Test
    fun `ordinary paint properties are not backdrop-dependent`() {
        assertFalse(
            dependsOnBackdrop(
                comp(
                    prop("BackgroundColor", """{"r":1,"g":0,"b":0,"a":1}"""),
                    prop("Width", "100"),
                    prop("Position", "\"absolute\""),
                )
            )
        )
        assertFalse(dependsOnBackdrop(comp()))
    }

    @Test
    fun `filter is NOT backdrop-dependent`() {
        // `filter` transforms the element's OWN paint and renders identically
        // with or without a backdrop, so suppressing it standalone would lose
        // real coverage. It stays a parentCreatesContext property only.
        assertTrue(parentCreatesContext(comp(prop("Filter", """[{"blur":4}]"""))))
        assertFalse(dependsOnBackdrop(comp(prop("Filter", """[{"blur":4}]"""))))
    }

    // ── The observable effect, and the backstop ─────────────────────────────

    @Test
    fun `flatten suppresses a blend child under an ordinary parent`() {
        val child = IRComponent(id = "c", name = "Layer",
            properties = listOf(prop("MixBlendMode", "\"multiply\"")))
        val parent = IRComponent(id = "p", name = "Parent",
            properties = listOf(prop("Position", "\"relative\"")), children = listOf(child))
        val out = flattenComponents(listOf(parent))
        assertEquals(listOf("Parent"), out.map { it.name })
    }

    @Test
    fun `flatten keeps a normal-blend child so case and control stay comparable`() {
        val child = IRComponent(id = "c", name = "Layer",
            properties = listOf(prop("MixBlendMode", "\"normal\"")))
        val parent = IRComponent(id = "p", name = "Parent",
            properties = listOf(prop("Position", "\"relative\"")), children = listOf(child))
        assertEquals(listOf("Parent", "Layer"), flattenComponents(listOf(parent)).map { it.name })
    }

    @Test
    fun `a backdrop-dependent ROOT is never suppressed`() {
        // THE BACKSTOP. fixtures/visual-test.json has zero components with
        // children, and its BlendMode_Multiply / Glass_Effect entries are
        // ROOTS. The rule only ever skips a CHILD, so the legacy 327-pair
        // flow and its 363 committed baselines provably cannot change.
        val roots = listOf(
            IRComponent(id = "a", name = "BlendMode_Multiply",
                properties = listOf(prop("MixBlendMode", "\"multiply\""))),
            IRComponent(id = "b", name = "Glass_Effect",
                properties = listOf(prop("BackdropFilter", """[{"blur":10}]"""))),
            IRComponent(id = "c", name = "Plain"),
        )
        assertEquals(
            listOf("BlendMode_Multiply", "Glass_Effect", "Plain"),
            flattenComponents(roots).map { it.name },
        )
    }
}
