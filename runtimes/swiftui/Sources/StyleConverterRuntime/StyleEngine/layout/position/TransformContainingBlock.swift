//
//  TransformContainingBlock.swift
//  StyleEngine/layout/position — wave 35 (lane B1).
//
//  css-transforms-1 §3 / css-transforms-2 §6: a box with a USED transform
//  (or perspective, or a preserved 3D rendering context) establishes the
//  containing block for ALL of its positioned descendants — absolutely
//  positioned AND fixed positioned alike. Until this file existed both
//  natives treated `position != static` as the only containing-block
//  source, so a `position: fixed` descendant of a transformed ancestor
//  escaped to the viewport (css-transforms 3d-rendering-context-and-fixpos:
//  the red 100×100 box painted at the canvas corner instead of inside the
//  transformed `.cb`, colorFailed on BOTH natives at the wave-34 gate).
//
//  THIS FILE IS A TWIN. Its Kotlin mirror is
//    runtimes/compose/src/main/java/com/styleconverter/runtime/layout/
//      position/TransformContainingBlock.kt
//  and the two rule tables are pinned against each other clause-by-clause
//  (TransformContainingBlockTests.swift / TransformContainingBlockTest.kt
//  carry the SAME shape matrix in the same order). Change one, change both.
//
//  ── Why the clause list is BLINK's, not the bare spec prose ─────────────
//  The campaign scores against Chromium refs, so the rule table is the one
//  Blink implements (`ComputedStyle::HasTransformRelatedProperty()` =
//  transform | individual transform properties | perspective | preserves3D |
//  will-change transform hint). The prose in css-transforms-1 §3 only names
//  `transform`; css-transforms-2 §6 adds `perspective`. `transform-style:
//  preserve-3d` is NOT named by either, yet Chromium makes it a containing
//  block — measured directly, not assumed:
//
//    _diag35/laneB1/probe-chromium.mjs, headless Chromium at 390×600,
//    six probes each nesting an out-of-flow box two levels under a styled
//    ancestor and reporting getBoundingClientRect():
//      A  nested absolute, NO cb ancestor        → ICB anchored   (20,10)
//      B  nested absolute under transform        → OUTER anchored (+20,+10)
//      C  fixed under transform                  → OUTER anchored (+20,+10)
//      D  fixed under perspective (no transform) → OUTER anchored (+20,+10)
//      E  fixed under transform-style:preserve-3d→ OUTER anchored (+20,+10)
//      F  nested absolute, no insets, no cb      → static position
//    E is the clause the spec prose does not state and the browser does.
//
//  ── Blast radius, enumerated BEFORE the change ──────────────────────────
//  _diag35/laneB1/scan-oof2.mjs replays this exact predicate over all 29
//  frozen wave34-final sections' per-test IR (326 tests). On iOS the ONLY
//  consumer is FixedHoist.split's fixed-descendant strip, so the changed
//  set is the single FIXED box under a transform-CB ancestor
//  (3d-rendering-context-and-fixpos). The dark-stage 363-pair baseline
//  carries the shape nowhere (scan-fixtures.mjs: all 25 hits are under
//  fixtures/wpt/).
//

// The decision is PURE over the wire — no SwiftUI, no view state.
import Foundation

/// Does this declaration list make the box a containing block for its
/// positioned descendants (absolute AND fixed)?
///
/// Read by both natives' out-of-flow hoist decision — Swift
/// `FixedHoist.split` and Compose `CanvasRootHoist.shouldHoistToCanvasRoot`
/// — so a fixed box under a transformed ancestor stops escaping to the
/// viewport.
///
/// public: the harness's composed canvas walks the same rule when it splits
/// a document, exactly like `FixedHoist.rendersInFlowAsStaticPosition`.
public enum TransformContainingBlock {

    /// The IR property types that can carry a containing-block-establishing
    /// value. Membership is necessary, NOT sufficient — every one of them
    /// has a `none` form that must NOT establish anything, which is why each
    /// is routed through its own clause in `establishes` rather than being
    /// tested by presence. A Set so the hot path bails on one lookup.
    static let candidateTypes: Set<String> = [
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
    ]

    /// True when at least one clause fires. Ordered cheapest-first: the
    /// whole corpus is dominated by boxes with NO candidate property at all,
    /// and that case costs one `contains` per declaration.
    public static func establishes(_ properties: [IRProperty]) -> Bool {
        // Fast bail — no candidate type present, no clause can fire.
        guard properties.contains(where: { candidateTypes.contains($0.type) })
        else { return false }
        // One pass, first firing clause wins.
        return properties.contains { p in
            switch p.type {
            case "Transform": return transformIsUsed(p)
            case "Translate", "Rotate", "Scale": return individualIsUsed(p)
            case "Perspective": return perspectiveIsUsed(p)
            case "TransformStyle": return preserves3D(p)
            case "WillChange": return willChangeHintsTransform(p)
            default: return false
            }
        }
    }

    /// Convenience over a component — the shape `FixedHoist` walks.
    public static func establishes(_ component: IRComponent) -> Bool {
        establishes(component.properties)
    }

    /// `transform` — a non-empty function list. The converter serializes
    /// `transform: none` as `{"type":"functions","list":[]}` (verified
    /// against a live conversion, _diag35/laneB1/tf-none.json), so emptiness
    /// IS the `none` test; an unresolvable `var()`/`calc()` value serializes
    /// as a different discriminator and is treated as ESTABLISHING, because
    /// the author wrote something other than `none` and the conservative
    /// failure (a descendant anchored one box too deep) is strictly less
    /// wrong than letting a fixed box escape to the viewport.
    private static func transformIsUsed(_ p: IRProperty) -> Bool {
        // Non-object leaves (a bare keyword) are not the `functions` shape.
        guard let obj = p.data.objectValue else { return false }
        let kind = obj["type"]?.stringValue
        // The `none` case: the functions shape with an EMPTY list.
        if kind == "functions" { return !(obj["list"]?.arrayValue ?? []).isEmpty }
        // Any other discriminator (keyword / expression) — see doc comment.
        return true
    }

    /// `translate` / `rotate` / `scale` — the css-transforms-2 §4 individual
    /// transform properties. Each serializes its `none` as the discriminator
    /// `{"type":"none"}` (same live-conversion evidence), so the clause is a
    /// single discriminator comparison and every other shape establishes.
    private static func individualIsUsed(_ p: IRProperty) -> Bool {
        guard let obj = p.data.objectValue else { return false }
        return obj["type"]?.stringValue != "none"
    }

    /// `perspective` — css-transforms-2 §6 names this clause for FIXED
    /// descendants explicitly. `perspective: none` serializes as
    /// `{"type":"none"}`; a length as `{"type":"length","px":…}`.
    private static func perspectiveIsUsed(_ p: IRProperty) -> Bool {
        guard let obj = p.data.objectValue else { return false }
        return obj["type"]?.stringValue != "none"
    }

    /// `transform-style: preserve-3d` — the enum leaf is a bare string
    /// ("FLAT" / "PRESERVE_3D"). Browser-measured clause (probe E), not spec
    /// prose: Blink's HasTransformRelatedProperty ORs in preserves3D().
    private static func preserves3D(_ p: IRProperty) -> Bool {
        p.data.stringValue == "PRESERVE_3D"
    }

    /// `will-change: transform | perspective` — the promotion hint makes the
    /// box a containing block up-front, so a later transform does not reflow
    /// its descendants. The leaf is an array of hint objects
    /// (`[{"type":"property-name","name":"transform"}]`); only the two
    /// transform-family property names count, so `will-change: opacity`
    /// (which promotes but does NOT establish a containing block) is
    /// excluded.
    private static func willChangeHintsTransform(_ p: IRProperty) -> Bool {
        guard let arr = p.data.arrayValue else { return false }
        return arr.contains { hint in
            let name = hint["name"]?.stringValue
            return name == "transform" || name == "perspective"
        }
    }
}
