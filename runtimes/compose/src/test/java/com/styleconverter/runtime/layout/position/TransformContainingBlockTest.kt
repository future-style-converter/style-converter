package com.styleconverter.runtime.layout.position

// Wave 35 (lane B1) — JVM pins for the transform containing-block rule
// table (css-transforms-1 §3 / css-transforms-2 §8) and for the hoist
// decisions that consume it.
//
// THE CROSS-NATIVE PIN. `shapeMatrix` below is the SAME matrix, in the SAME
// order, as the Swift twin's `TransformContainingBlockTests.shapeMatrix`
// (runtimes/swiftui/Tests/StyleConverterRuntimeTests/
// TransformContainingBlockTests.swift). The two rule tables are only
// "byte-parallel" if both suites accept the identical rows, so a clause
// added to one platform and forgotten on the other fails HERE rather than
// three waves later on a device.
//
// Every expected value is browser-derived, not spec-derived — the campaign
// scores against Chromium refs and Blink's HasTransformRelatedProperty is
// broader than the prose. The measurement is _diag35/laneB1/
// probe-chromium.mjs (six nesting probes, getBoundingClientRect at 390x600);
// the `none`-form wire shapes are from a live conversion of
// _diag35/laneB1/tf-none.json.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformContainingBlockTest {

    // Parse one JSON literal into the IRProperty data slot — the same
    // helper shape CanvasRootHoistTest uses, so wire drift fails in one
    // obvious place first.
    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    private fun comp(
        id: String,
        properties: List<IRProperty> = emptyList(),
        children: List<IRComponent>? = null,
    ) = IRComponent(id = id, name = id, properties = properties, children = children)

    /**
     * The cross-native shape matrix: (label, declarations, establishes?).
     * Ordered clause-by-clause — used value first, then that clause's
     * `none` form — so a diff against the Swift twin reads line for line.
     */
    private val shapeMatrix: List<Triple<String, List<IRProperty>, Boolean>> = listOf(
        // ── no candidate property at all ─────────────────────────────────
        Triple("bare box", emptyList(), false),
        Triple("position:relative only", listOf(prop("Position", "\"RELATIVE\"")), false),
        // ── transform (css-transforms-1 §3, probe B/C) ───────────────────
        Triple(
            "transform: translateX(0)",
            listOf(prop("Transform", """{"type":"functions","list":[{"fn":"translateX","x":{"px":0}}]}""")),
            true,
        ),
        Triple(
            "transform: none",
            listOf(prop("Transform", """{"type":"functions","list":[]}""")),
            false,
        ),
        // ── individual transform properties (css-transforms-2 §5) ────────
        Triple("rotate: 45deg", listOf(prop("Rotate", """{"type":"angle","deg":45}""")), true),
        Triple("rotate: none", listOf(prop("Rotate", """{"type":"none"}""")), false),
        Triple("scale: none", listOf(prop("Scale", """{"type":"none"}""")), false),
        Triple("translate: none", listOf(prop("Translate", """{"type":"none"}""")), false),
        // ── perspective (css-transforms-2 §8, probe D) ───────────────────
        Triple(
            "perspective: 500px",
            listOf(prop("Perspective", """{"type":"length","px":500}""")),
            true,
        ),
        Triple("perspective: none", listOf(prop("Perspective", """{"type":"none"}""")), false),
        // ── transform-style (Blink preserves3D; probe E — NOT spec prose) ─
        Triple("transform-style: preserve-3d", listOf(prop("TransformStyle", "\"PRESERVE_3D\"")), true),
        Triple("transform-style: flat", listOf(prop("TransformStyle", "\"FLAT\"")), false),
        // ── will-change (Blink transform hint) ───────────────────────────
        Triple(
            "will-change: transform",
            listOf(prop("WillChange", """[{"type":"property-name","name":"transform"}]""")),
            true,
        ),
        Triple(
            "will-change: opacity",
            listOf(prop("WillChange", """[{"type":"property-name","name":"opacity"}]""")),
            false,
        ),
    )

    @Test fun `the cross-native shape matrix holds clause for clause`() {
        for ((label, props, expected) in shapeMatrix) {
            assertEquals(label, expected, TransformContainingBlock.establishes(props))
        }
    }

    @Test fun `the hoist reads the rule table through one accessor`() {
        // CanvasRootHoist must not re-derive "transformed" — one decision
        // function, two consumers (the composition local and the pure walk).
        for ((label, props, expected) in shapeMatrix) {
            assertEquals(
                label, expected,
                CanvasRootHoist.establishesTransformContainingBlock(props),
            )
        }
    }

    // ── The hoist truth table the rule table feeds ────────────────────────

    @Test fun `a fixed box under a transformed ancestor does NOT hoist`() {
        // css-transforms-2 §8 / probe C: the transformed ancestor is the
        // containing block, so the box stays where it is instead of
        // anchoring at the viewport. This is the 3d-rendering-context-and-
        // fixpos cell on BOTH natives.
        val fixed = listOf(
            prop("Position", "\"FIXED\""),
            prop("Top", """{"type":"length","px":0}"""),
            prop("Left", """{"type":"length","px":0}"""),
        )
        assertFalse(
            CanvasRootHoist.shouldHoistToCanvasRoot(
                fixed, hasPositionedAncestor = false, hasTransformedAncestor = true,
            )
        )
    }

    @Test fun `a fixed box under a merely POSITIONED ancestor still hoists`() {
        // Pin S3, unchanged by wave 35: `position: relative` is NOT a
        // containing block for fixed descendants (css-position-3 §2.1), so
        // only the transform flag may veto the hoist.
        val fixed = listOf(
            prop("Position", "\"FIXED\""),
            prop("Top", """{"type":"length","px":0}"""),
        )
        assertTrue(
            CanvasRootHoist.shouldHoistToCanvasRoot(
                fixed, hasPositionedAncestor = true, hasTransformedAncestor = false,
            )
        )
    }

    @Test fun `an inset absolute box under a transformed ancestor does NOT hoist`() {
        // Probe B: the transformed ancestor claims absolute descendants too,
        // so the box must not escape to the ICB. This is the
        // backface-visibility-hidden-001 cell on Android.
        val abs = listOf(
            prop("Position", "\"ABSOLUTE\""),
            prop("Top", """{"type":"length","px":50}"""),
            prop("Left", """{"type":"length","px":50}"""),
        )
        assertFalse(
            CanvasRootHoist.shouldHoistToCanvasRoot(
                abs, hasPositionedAncestor = false, hasTransformedAncestor = true,
            )
        )
        // …and with NO containing-block ancestor of either kind it still
        // hoists to the ICB (probe A) — the wave-18 clause is untouched.
        assertTrue(
            CanvasRootHoist.shouldHoistToCanvasRoot(
                abs, hasPositionedAncestor = false, hasTransformedAncestor = false,
            )
        )
    }

    @Test fun `the default argument keeps every pre-wave-35 call site identical`() {
        // Additive-parameter discipline: omitting the flag must reproduce
        // the wave-17/18 truth table exactly, which is what keeps the
        // hostless dark-stage paths byte-stable.
        val fixed = listOf(prop("Position", "\"FIXED\""))
        assertEquals(
            CanvasRootHoist.shouldHoistToCanvasRoot(fixed, hasPositionedAncestor = false),
            CanvasRootHoist.shouldHoistToCanvasRoot(
                fixed, hasPositionedAncestor = false, hasTransformedAncestor = false,
            ),
        )
    }

    // ── The WALK must mirror the per-node decision ────────────────────────

    @Test fun `the collect walk threads the transform flag through a static middle box`() {
        // The exact 3d-rendering-context-and-fixpos tree: a transformed
        // relative `.cb` → a plain static `.parent` → a fixed `.abspos`.
        // The middle box carries NO transform, so only OR-accumulation down
        // the chain can reach the fixed grandchild.
        val fixedGrandchild = comp(
            "abspos",
            listOf(
                prop("Position", "\"FIXED\""),
                prop("Top", """{"type":"length","px":0}"""),
                prop("Left", """{"type":"length","px":0}"""),
            ),
        )
        val cb = comp(
            "cb",
            listOf(
                prop("Position", "\"RELATIVE\""),
                prop("Transform", """{"type":"functions","list":[{"fn":"translateX","x":{"px":0}}]}"""),
            ),
            children = listOf(comp("parent", emptyList(), children = listOf(fixedGrandchild))),
        )
        assertTrue(
            "no box may hoist out of a transformed subtree",
            CanvasRootHoist.collectCanvasHoisted(listOf(cb)).isEmpty(),
        )
        // Remove the transform and the same grandchild hoists again — the
        // control that proves the flag, not the tree shape, is the cause.
        val untransformedCb = comp(
            "cb",
            listOf(prop("Position", "\"RELATIVE\"")),
            children = listOf(comp("parent", emptyList(), children = listOf(fixedGrandchild))),
        )
        assertEquals(
            listOf("abspos"),
            CanvasRootHoist.collectCanvasHoisted(listOf(untransformedCb)).map { it.id },
        )
    }

    @Test fun `interceptsInFlow mirrors the hoist decision under the new flag`() {
        // The composition-side interception and the pure walk MUST agree or
        // a box is dropped from flow with no overlay slot.
        val fixed = comp(
            "fixed",
            listOf(
                prop("Position", "\"FIXED\""),
                prop("Top", """{"type":"length","px":0}"""),
            ),
        )
        assertFalse(
            CanvasRootHoist.interceptsInFlow(
                fixed, hostActive = true, hasPositionedAncestor = false,
                bypass = null, hasTransformedAncestor = true,
            )
        )
        assertTrue(
            CanvasRootHoist.interceptsInFlow(
                fixed, hostActive = true, hasPositionedAncestor = false,
                bypass = null, hasTransformedAncestor = false,
            )
        )
    }
}
