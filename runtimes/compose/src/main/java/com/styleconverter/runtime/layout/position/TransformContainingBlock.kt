package com.styleconverter.runtime.layout.position

// Wave 35 (lane B1) — css-transforms-1 §3 / css-transforms-2 §6: a box with
// a USED transform (or perspective, or a preserved 3D rendering context)
// establishes the containing block for ALL of its positioned descendants —
// absolutely positioned AND fixed positioned alike. Until this file existed
// both natives treated `position != static` as the only containing-block
// source, so a `position: fixed` descendant of a transformed ancestor
// escaped to the viewport (css-transforms 3d-rendering-context-and-fixpos:
// the red 100×100 box painted at the canvas corner instead of inside the
// transformed `.cb`, colorFailed on BOTH natives at the wave-34 gate).
//
// THIS FILE IS A TWIN. Its Swift mirror is
//   runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/
//     position/TransformContainingBlock.swift
// and the two rule tables are pinned against each other clause-by-clause
// (TransformContainingBlockTest.kt / TransformContainingBlockTests.swift
// carry the SAME shape matrix in the same order). Change one, change both.
//
// ── Why the clause list is BLINK's, not the bare spec prose ──────────────
// The campaign scores against Chromium refs, so the rule table is the one
// Blink implements (`ComputedStyle::HasTransformRelatedProperty()` =
// transform | individual transform properties | perspective | preserves3D |
// will-change transform hint). The prose in css-transforms-1 §3 only names
// `transform`; css-transforms-2 §6 adds `perspective`. `transform-style:
// preserve-3d` is NOT named by either, yet Chromium makes it a containing
// block — measured directly, not assumed:
//
//   _diag35/laneB1/probe-chromium.mjs, headless Chromium at 390×600,
//   six probes each nesting an out-of-flow box two levels under a styled
//   ancestor and reporting getBoundingClientRect():
//     A  nested absolute, NO cb ancestor        → ICB anchored   (20,10)
//     B  nested absolute under transform        → OUTER anchored (+20,+10)
//     C  fixed under transform                  → OUTER anchored (+20,+10)
//     D  fixed under perspective (no transform) → OUTER anchored (+20,+10)
//     E  fixed under transform-style:preserve-3d→ OUTER anchored (+20,+10)
//     F  nested absolute, no insets, no cb      → static position
//   E is the clause the spec prose does not state and the browser does.
//
// ── Blast radius, enumerated BEFORE the change ───────────────────────────
// _diag35/laneB1/scan-oof2.mjs replays this exact predicate over all 29
// frozen wave34-final sections' per-test IR (326 tests). Boxes whose hoist
// decision this file changes:
//   • FIXED with a transform-CB ancestor .... 1 (3d-rendering-context-and-fixpos)
//   • ABSOLUTE+inset with a transform-CB
//     ancestor and no positioned ancestor ... 2 (backface-visibility-hidden-001's
//                                                two faces)
// i.e. exactly the two css-transforms cells this wave targets, and nothing
// else in the frozen gate. _diag35/laneB1/scan-fixtures.mjs replays the same
// predicate over every source fixture: all 25 hits live under fixtures/wpt/,
// and NONE under fixtures/properties, fixtures/components or
// fixtures/fidelity — so the committed 363-pair dark-stage baseline cannot
// observe this file at all. Four deeper WPT tests the frozen gate does not
// sample DO carry the shape (css-contain/contain-paint-012,
// css-multicol/fixed-in-multicol-with-transform-container,
// css-masking/clip-path-rotated-will-change-transform,
// css-break/contain-strict-with-opacity-and-oof) — every one of them is a
// test OF this rule, so the depth gate should move toward, not away from,
// the refs.

// IR model — the decision is PURE over the wire (JVM-pinned, no Robolectric).
import com.styleconverter.runtime.core.ir.IRProperty
// Wire leaf readers: every clause below inspects the RAW serialized shape,
// deliberately NOT the platform extractors, because the question is "did the
// author declare a non-none value" and an extractor that resolves `none` to
// a default would erase exactly that distinction.
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Does this declaration list make the box a containing block for its
 * positioned descendants (absolute AND fixed)?
 *
 * Read by both natives' out-of-flow hoist decision — Compose
 * [CanvasRootHoist.shouldHoistToCanvasRoot] and its Swift twin
 * `FixedHoist.split` — so a fixed box under a transformed ancestor stops
 * escaping to the viewport, and an absolute box under one stops escaping to
 * the initial containing block.
 */
object TransformContainingBlock {

    /**
     * The IR property types that can carry a containing-block-establishing
     * value. Membership is necessary, NOT sufficient — every one of them has
     * a `none` form that must NOT establish anything, which is why each is
     * routed through its own clause in [establishes] rather than being tested
     * by presence. Kept as a set so the hot path can bail on the first read.
     */
    internal val CANDIDATE_TYPES = setOf(
        // css-transforms-1 §3 — the named clause.
        "Transform",
        // css-transforms-2 §4 — the individual transform properties compose
        // into the same used transform, so any non-none value counts.
        "Translate", "Rotate", "Scale",
        // css-transforms-2 §6 — perspective, explicitly named for FIXED.
        "Perspective",
        // Blink's `preserves3D()` half of HasTransformRelatedProperty —
        // probe E above; the spec prose does not state it.
        "TransformStyle",
        // Blink's will-change transform hint: the box is promoted exactly as
        // if it were transformed, containing block included.
        "WillChange",
    )

    /**
     * True when at least one clause fires. Ordered cheapest-first: the whole
     * corpus is dominated by boxes with NO candidate property at all, and
     * that case costs one `any` over the type strings.
     */
    fun establishes(properties: List<IRProperty>): Boolean {
        // Fast bail — no candidate type present, no clause can fire.
        if (properties.none { it.type in CANDIDATE_TYPES }) return false
        // One pass, first firing clause wins.
        return properties.any { p ->
            when (p.type) {
                "Transform" -> transformIsUsed(p)
                "Translate", "Rotate", "Scale" -> individualIsUsed(p)
                "Perspective" -> perspectiveIsUsed(p)
                "TransformStyle" -> preserves3D(p)
                "WillChange" -> willChangeHintsTransform(p)
                else -> false
            }
        }
    }

    /**
     * `transform` — a non-empty function list. The converter serializes
     * `transform: none` as `{"type":"functions","list":[]}` (verified against
     * a live conversion, _diag35/laneB1/tf-none.json), so emptiness IS the
     * `none` test; an unresolvable `var()`/`calc()` value serializes as a
     * different discriminator and is treated as ESTABLISHING, because the
     * author wrote something other than `none` and the conservative failure
     * (a descendant anchored one box too deep) is strictly less wrong than
     * letting a fixed box escape to the viewport.
     */
    private fun transformIsUsed(p: IRProperty): Boolean {
        // Non-object leaves (a bare keyword) are not the `functions` shape.
        val obj = p.data as? JsonObject ?: return false
        val kind = (obj["type"] as? JsonPrimitive)?.contentOrNull
        // The `none` case: the functions shape with an EMPTY list.
        if (kind == "functions") return (obj["list"] as? JsonArray)?.isNotEmpty() == true
        // Any other discriminator (keyword / expression) — see kdoc.
        return true
    }

    /**
     * `translate` / `rotate` / `scale` — the css-transforms-2 §4 individual
     * transform properties. Each serializes its `none` as the discriminator
     * `{"type":"none"}` (same live-conversion evidence), so the clause is a
     * single discriminator comparison and every other shape establishes.
     */
    private fun individualIsUsed(p: IRProperty): Boolean {
        val obj = p.data as? JsonObject ?: return false
        return (obj["type"] as? JsonPrimitive)?.contentOrNull != "none"
    }

    /**
     * `perspective` — css-transforms-2 §6 names this clause for FIXED
     * descendants explicitly. `perspective: none` serializes as
     * `{"type":"none"}`; a length as `{"type":"length","px":…}`.
     */
    private fun perspectiveIsUsed(p: IRProperty): Boolean {
        val obj = p.data as? JsonObject ?: return false
        return (obj["type"] as? JsonPrimitive)?.contentOrNull != "none"
    }

    /**
     * `transform-style: preserve-3d` — the enum leaf is a bare string
     * ("FLAT" / "PRESERVE_3D"). Browser-measured clause (probe E), not spec
     * prose: Blink's HasTransformRelatedProperty ORs in preserves3D().
     */
    private fun preserves3D(p: IRProperty): Boolean =
        (p.data as? JsonPrimitive)?.contentOrNull == "PRESERVE_3D"

    /**
     * `will-change: transform | perspective` — the promotion hint makes the
     * box a containing block up-front, so a later transform does not reflow
     * its descendants. The leaf is an array of hint objects
     * (`[{"type":"property-name","name":"transform"}]`); only the two
     * transform-family property names count, so `will-change: opacity`
     * (which promotes but does NOT establish a containing block) is excluded.
     */
    private fun willChangeHintsTransform(p: IRProperty): Boolean {
        val arr = p.data as? JsonArray ?: return false
        return arr.any { hint ->
            val name = ((hint as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull
            name == "transform" || name == "perspective"
        }
    }
}
