//
//  MulticolFloatStripSeam.swift
//  StyleEngine/columns — wave-45 lane X3 (the consumption half).
//
//  The iOS CONSUMPTION of the wave-44 float-strip twin: everything the
//  ComponentRenderer multicol-branch seam (tagged X3 there) needs,
//  mirrored from the Kotlin consumers (MulticolFloatStrip.zeroFlowPlan
//  in MulticolFloatStrip.kt, MulticolFloatStripPlan.engagesPreMeasure/
//  provableStripInkPx in MulticolFloatStripPlan.kt). This closed seam 1
//  of the CONSUMPTION STATUS banner in MulticolFloatStrip.swift: the
//  §9.5.2 zero-flow plan reaches the float container's child loop as an
//  environment value the renderer attaches AROUND the strip content.
//
//  Seam 2 — the css-break-3 §4 clip+translate slice REPLAY — closed in
//  wave 48 (lane W3): the renderer composes one clone of the whole strip
//  content per used column and MulticolFloatStripSliceLayout (the
//  measure half, MulticolFloatStripSlice.swift) shows each clone's
//  column band — so the engagement decided HERE now feeds the replay
//  row, not a per-child anchor-column placement. The wave-45 columnSlot
//  whole-child mapping this file used to carry left with that placement.
//

// Foundation for the numeric helpers; view-free and XCTest-pinnable.
import Foundation

extension MulticolFloatStrip {

    /// The IR-provable floor on the strip's ink extent C (px), rounded
    /// the same way the plan rounds its own terms (`.rounded()` here ==
    /// Kotlin's Math.round in the twin): the tallest declared float
    /// height or trailing-ink band over the children. MEASURED child
    /// heights are deliberately absent — this is exactly the half of C
    /// knowable BEFORE a measure pass, which is what makes it usable as
    /// a composition-time gate (see engagesPreMeasure).
    static func provableStripInkPx(_ specs: [MulticolSpannerFlow.ChildSpec]?) -> Double {
        // No specs / no facts ⇒ nothing provable (engagement rejects
        // those shapes anyway; 0 keeps this function total).
        guard let specs, !specs.isEmpty else { return 0 }
        // Both IR-derived terms of C: a float's declared height extends
        // its side ledger and a child's trailing ink extends the paint
        // floor — either one rounding to ≥ 1 already forces C ≥ 1.
        return specs.map { s -> Double in
            guard let f = s.floatStrip else { return 0 }
            return max(f.floats.map { $0.heightPx.rounded() }.max() ?? 0,
                       f.trailingInkPx.rounded())
        }.max() ?? 0
    }

    /// The PRE-MEASURE strip decision — the ONE predicate the zero-flow
    /// paint half (zeroFlowPlan / the renderer seam) and the layout half
    /// (MulticolGreedyLayout's strip branch) both gate on, so the paint
    /// half can never zero-flow floats under a layout that still stacks
    /// them (the wave-44 skeptic-S5 lesson, mirrored from the Kotlin
    /// MulticolFloatStripPlan.engagesPreMeasure).
    ///
    /// Why the fill mode participates: under §7.1 balance the used
    /// column size is min(H, ceil(C/N)), which is 0 — the plan's one
    /// degenerate decline — exactly when C == 0, and C == 0 additionally
    /// needs every MEASURED height to be 0, unknowable before measuring.
    /// The answer is the conservative one: under balance the strip
    /// engages only when the IR alone proves C ≥ 1; under §7.2 fill the
    /// used size is the definite H, which every caller already gates
    /// > 0. What the conservative side costs, stated plainly: a
    /// balance-mode container whose floats ALL declare height 0 and
    /// whose children carry no trailing ink keeps the legacy layout —
    /// a shape no ref in the proven floats-clear-multicol family has
    /// (a 0-height proven float has no ink at all; factsFor rejects
    /// every wire that could grow it).
    static func engagesPreMeasure(_ specs: [MulticolSpannerFlow.ChildSpec]?,
                                  columnFillAuto: Bool) -> Bool {
        // Shape first: everything engages() rejects (roles, unproven
        // facts, no real float) is rejected here too.
        guard engages(specs) else { return false }
        // §7.2 sequential fill keeps the definite H — no C-dependent
        // size, so nothing can turn degenerate after the measure.
        if columnFillAuto { return true }
        // §7.1 balance: only a provable ink floor guarantees h ≥ 1.
        return provableStripInkPx(specs) > 0
    }

    /// The synthetic §9.5.2 zero-flow plan for an engaged container —
    /// the Kotlin zeroFlowPlan twin: every proven float reports ZERO
    /// block-axis size at its flow slot (CSS 2.1 §9.5 — floats are out
    /// of flow), which the existing block child loops consume through
    /// the floatClearancePlan environment + ClearanceZeroFlow with no
    /// further renderer change. Id-keyed and scoped (the multicol
    /// children + the floats themselves), so the plan is inert outside
    /// the multicol subtree by construction.
    static func zeroFlowPlan(specs: [MulticolSpannerFlow.ChildSpec]?,
                             columnFillAuto: Bool) -> FloatClearance.Plan? {
        // The shared pre-measure gate — paint and layout decide together.
        guard engagesPreMeasure(specs, columnFillAuto: columnFillAuto),
              let specs else { return nil }
        // Every proven float id zero-flows (engage proved facts non-nil).
        let floatIds = specs.flatMap { s in s.floatStrip!.floats.map(\.componentId) }
        // Scope: the multicol children (so their block loops ADOPT the
        // inherited plan — clearanceScopePlan's scopeIds gate) + floats.
        let scope = Set(specs.map { $0.floatStrip!.componentId }).union(floatIds)
        return FloatClearance.Plan(
            scopeIds: scope,
            // Ids are document-unique; the uniquing closure is defensive
            // totality only (a duplicate would carry the same adjustment).
            adjustments: Dictionary(
                floatIds.map { ($0, FloatClearance.Adjustment(zeroFlowHeight: true)) },
                uniquingKeysWith: { a, _ in a }))
    }

    /// Everything the X3 renderer seam threads into the strip's layout
    /// half (since wave 48: the slice-replay row's
    /// MulticolFloatStripSliceLayout windows) when the strip owns a
    /// container's layout: the proven specs, the fill mode, and the
    /// zero-flow plan the renderer ALSO publishes on the
    /// floatClearancePlan environment — one decision, two consumers (the
    /// Compose MultiColumnApplier principle), so paint and layout can
    /// never half-engage.
    struct EngagedStrip {
        /// Per-subview specs, in contentOrPlaceholder order (leading
        /// text first) — every floatStrip fact proven non-nil.
        let specs: [MulticolSpannerFlow.ChildSpec]
        /// §7.2 auto (sequential fill) vs the initial §7.1 balance.
        let columnFillAuto: Bool
        /// The §9.5.2 zero-flow plan the renderer publishes around the
        /// layout's content (floats report zero flow height at their
        /// slot — the paint half of the model).
        let zeroFlowPlan: FloatClearance.Plan
    }

    /// The composition-time strip decision for the X3 renderer seam:
    /// non-nil exactly when BOTH halves may engage. Gates mirror the
    /// Compose composition site (MultiColumnApplier's floatStripZeroFlow
    /// guard): a definite positive column block-size (the strip needs a
    /// real fragmentainer — css-break-3 §2), ≥ 2 used columns (N == 1
    /// keeps the overflow-not-clip legacy semantics), horizontal-tb only
    /// (a vertical writing mode remaps the axes the strip's y-math
    /// assumes — css-writing-modes-4 §3), and the shared pre-measure
    /// predicate via zeroFlowPlan. The used-column geometry is resolved
    /// by the CALLER from the container's declared width through the
    /// same MulticolMath lane the greedy layout resolves with, and the
    /// slice row composes with THAT geometry; a live width that
    /// disagreed with the declared one (no fixture in the proven family
    /// does) would render the composed columns against the live box —
    /// the same committed-geometry semantics as Compose's post-engage
    /// measure pass, stated in MulticolFloatStripSlice.swift.
    static func engagedStrip(specs: [MulticolSpannerFlow.ChildSpec]?,
                             columnFillAuto: Bool,
                             definiteColumnBlockSizePx: Double?,
                             usedColumnCount: Int,
                             horizontalWritingMode: Bool) -> EngagedStrip? {
        // css-break-3 §2: no fragmentainer, no strip (also excludes the
        // auto-height family — the strip is definite-block-size only,
        // exactly the Compose containerBlockSizeDefinite gate).
        guard let h = definiteColumnBlockSizePx, h > 0 else { return nil }
        // N == 1 overflows below instead of slicing; vertical modes are
        // blocked-platform for the strip's y-math (both Compose gates).
        guard usedColumnCount > 1, horizontalWritingMode else { return nil }
        // The shared pre-measure predicate + the plan itself.
        guard let plan = zeroFlowPlan(specs: specs, columnFillAuto: columnFillAuto),
              let specs else { return nil }
        return EngagedStrip(specs: specs,
                            columnFillAuto: columnFillAuto,
                            zeroFlowPlan: plan)
    }
}
