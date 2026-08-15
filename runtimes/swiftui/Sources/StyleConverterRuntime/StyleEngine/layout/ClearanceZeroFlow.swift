//
//  ClearanceZeroFlow.swift
//  StyleEngine/layout — wave-42 lane W5.
//
//  The SwiftUI adapter half of the CSS 2.2 §9.5.2 clearance emulation
//  (pure math: FloatClearance.swift; Compose twin: ClearanceZeroFlow.kt).
//  Two pieces:
//    • the environment key carrying a resolved FloatClearance.Plan from
//      its scope-root block container down to every nested block loop
//      (adjustments are id-keyed, so a plan flowing past its scope is
//      inert by construction — ids are document-unique);
//    • the zero-flow modifier that renders a FLOAT at its flow slot
//      while contributing ZERO block-axis size to the parent VStack —
//      the "floats are out of flow" half of §9.5.2 (CSS 2.1 §9.5:
//      following in-flow block boxes position as if it were not there).
//

// SwiftUI for the environment plumbing + frame modifier.
import SwiftUI

/// Environment key for the scope's §9.5.2 plan. Default nil = no
/// clearance scope — the entire pre-wave-42 corpus renders through that
/// identity branch byte-for-byte.
private struct FloatClearancePlanKey: EnvironmentKey {
    static let defaultValue: FloatClearance.Plan? = nil
}

extension EnvironmentValues {
    /// The active §9.5.2 clearance plan, provided by the block container
    /// FloatClearance resolved for (a composed root or BFC root) and
    /// read by every descendant block child loop. Deliberately NOT reset
    /// per level: consumption is id-gated by `plan.adjustments`/
    /// `plan.scopeIds`, so flowing past the scope subtree is harmless.
    var floatClearancePlan: FloatClearance.Plan? {
        get { self[FloatClearancePlanKey.self] }
        set { self[FloatClearancePlanKey.self] = newValue }
    }
}

/// Render one float child with ZERO stacked height. `.frame(height: 0,
/// alignment: .topLeading)` keeps the child's own size chain intact
/// (its explicit width/height frames ignore the 0 proposal) and pins
/// its top-leading corner to the flow slot; SwiftUI does not clip, so
/// the float's ink overlays following in-flow content exactly like the
/// browser's out-of-flow paint (CSS 2.1 §9.5 / Appendix E step 4). A
/// float:right child's own trailing-alignment frame (the wave-5 float
/// wrapper in styledContent) still parks its ink at the right edge.
struct ClearanceZeroFlow: ViewModifier {
    /// Whether this child is a plan-marked float (id-keyed lookup at the
    /// call site). False = identity — the frozen composition shape.
    let active: Bool

    func body(content: Content) -> some View {
        if active {
            // Zero stacked height, ink anchored at the slot's top-left.
            content.frame(height: 0, alignment: .topLeading)
        } else {
            // Identity branch: the pre-wave-42 view tree byte-for-byte.
            content
        }
    }
}
