//
//  MarginCollapseChildGates.swift
//  StyleEngine/spacing — Lane IOS-COLLAPSE (per-child + parent-bucket gates).
//
//  The eligibility PREDICATES for the unified collapse gate contract's
//  container-level bails, split out of MarginCollapsePlanner.swift to keep
//  both files inside the house ≤300-line target. containerPlan (in the
//  planner file) calls these; each mirrors a Compose BlockMarginCollapse /
//  ComponentRenderer.blockCollapsePlanFor predicate byte-for-byte so the
//  two natives cannot drift (the shared S1-S12 pin table enforces it):
//    • rawDisplayKeyword / INLINE_LEVEL_DISPLAY_KEYWORDS — B1, B2
//    • childIsFloated — B3
//    • bucketsDeclareMargins — B5
//    • isSelfCollapsingCandidate — B9
//    • isNestedHoistChainChild — B10
//    • parentBucketsDeclareGateAffecting — B6
//    • logFallback — the no-silent-fallthrough breadcrumb
//

// Same-module internals (StyleBuilder / ValueExtractors / PropertyTracker /
// MarginProperty) need no import; nothing here touches CoreGraphics/SwiftUI.
import Foundation

extension MarginCollapse {

    // MARK: - Contract keyword / type tables

    /// Inline-level outer display values for bail B2 (css-display-3 §2.1),
    /// normalized to underscore form — matches Compose's
    /// INLINE_LEVEL_DISPLAY_KEYWORDS exactly.
    static let INLINE_LEVEL_DISPLAY_KEYWORDS: Set<String> =
        ["INLINE", "INLINE_BLOCK", "INLINE_FLEX", "INLINE_GRID"]

    /// Block-size floors that stop a box from self-collapsing (bail B9;
    /// §8.3.1 requires "min-height of zero, and zero or auto computed
    /// height"). Logical block aliases resolve to these physical types in
    /// the horizontal-tb engine.
    static let SELF_COLLAPSE_HEIGHT_TYPES: Set<String> =
        ["Height", "BlockSize", "MinHeight", "MinBlockSize"]

    /// Declared top / bottom vertical-margin type sets for B9's "declares
    /// BOTH vertical margins" test (logical block aliases → top/bottom,
    /// css-logical-1 §4.2).
    static let TOP_MARGIN_TYPES: Set<String> = ["MarginTop", "MarginBlockStart"]
    static let BOTTOM_MARGIN_TYPES: Set<String> = ["MarginBottom", "MarginBlockEnd"]

    // MARK: - Per-child predicates

    /// A child's raw `display` keyword (uppercased, hyphen→underscore), or
    /// nil when undeclared. Read directly instead of through mapDisplay so
    /// inline-block/-flex/-grid stay distinguishable from block-level
    /// flex/grid (B2 must bail the former but not the latter).
    static func rawDisplayKeyword(_ child: IRComponent) -> String? {
        guard let p = child.properties.first(where: { $0.type == "Display" }),
              let kw = ValueExtractors.extractKeyword(p.data) else { return nil }
        return ValueExtractors.normalize(kw)
    }

    /// Bail B3 predicate — a child carrying a `float` other than `none`.
    /// An undeclared Float, or an explicit NONE, keeps the box in flow.
    static func childIsFloated(_ child: IRComponent) -> Bool {
        guard let p = child.properties.first(where: { $0.type == "Float" }),
              let kw = ValueExtractors.extractKeyword(p.data) else { return false }
        let normalized = ValueExtractors.normalize(kw)
        return !normalized.isEmpty && normalized != "NONE"
    }

    /// Bail B9 predicate — a child whose own top and bottom margins would
    /// be adjoining (a §8.3.1 self-collapsing box): no text, no children,
    /// no explicit height/min-height, and BOTH vertical margins declared
    /// with either one positive. The pairwise fold applies prev.bottom vs
    /// own.top per gap; a self-collapsing box needs max across ONE combined
    /// gap instead — unreproducible, so bail (pin S12).
    static func isSelfCollapsingCandidate(_ child: IRComponent) -> Bool {
        // Any content or a block-size floor → the box has extent; its own
        // margins never touch each other and the pairwise fold is exact.
        if child.text?.isEmpty == false { return false }
        if !(child.children ?? []).isEmpty { return false }
        if child.properties.contains(where: { SELF_COLLAPSE_HEIGHT_TYPES.contains($0.type) }) {
            return false
        }
        // BOTH vertical margins must be DECLARED (an undeclared side is the
        // initial 0 — a zero-width adjoining margin the fold handles fine).
        guard child.properties.contains(where: { TOP_MARGIN_TYPES.contains($0.type) }),
              child.properties.contains(where: { BOTTOM_MARGIN_TYPES.contains($0.type) })
        else { return false }
        // Either margin positive triggers the bail: two declared ZEROS
        // self-collapse harmlessly (the combined gap is still the plain
        // pairwise max). A non-static declared pair is a conservative bail.
        guard let e = staticVerticalEdges(child.properties) else { return true }
        return e.top > 0 || e.bottom > 0
    }

    /// Bail B10 predicate for one edge child: is it an eligible block
    /// container (block-flow display, children present, no leading text)
    /// whose OWN hoist gate on the touching edge is open (unpadded/
    /// unbordered per hoistAllowed)? If so, its grandchild edge margin
    /// would chain-collapse through it — beyond the single-level emulation.
    static func isNestedHoistChainChild(_ child: IRComponent, edgeIsTop: Bool) -> Bool {
        // Leaf children (or text-first containers) cannot hoist anything —
        // the plan builder structurally skips them.
        if (child.children ?? []).isEmpty { return false }
        if child.text?.isEmpty == false { return false }
        // Only block-flow containers hoist: an explicit non-block display
        // (flex/grid/inline/…) establishes an independent formatting
        // context whose margins never collapse through (css-display-3 §2.1).
        if let d = rawDisplayKeyword(child), d != "BLOCK" { return false }
        // The chain only forms through the edge that touches THIS parent:
        // the child's top gate for a first child, bottom gate for a last.
        // hoistAllowed reads the same gate inputs (G1-G5) as the parent's.
        let childStyle = StyleBuilder.build(from: child.properties)
        return hoistAllowed(style: childStyle, topEdge: edgeIsTop)
    }

    /// Do the child's selector or media buckets declare any margin
    /// longhand? (The plan reads BASE properties only; a hover/media
    /// margin would desync the static fold from the live style chain.)
    static func bucketsDeclareMargins(_ child: IRComponent) -> Bool {
        // One shared predicate over the canonical margin-longhand list
        // (MarginExtractor's single source of truth).
        let declares: ([IRProperty]) -> Bool = { props in
            props.contains { MarginProperty.names.contains($0.type) }
        }
        // Either bucket kind carrying a margin longhand trips the gate.
        return child.selectors?.contains { declares($0.properties) } == true
            || child.media?.contains { declares($0.properties) } == true
    }

    // MARK: - Parent-bucket predicate (B6)

    /// Bail B6 predicate — a parent selector/media bucket that could flip a
    /// hoist gate when it applies. Families mirror the gate inputs in
    /// hoistAllowed: padding (G1), border (G2 — the whole Border*
    /// namespace, conservatively including radius/image), overflow (G3 —
    /// OverflowWrap/OverflowAnchor screened out: text wrapping and scroll
    /// anchoring never establish a BFC), position (G4), and the block-size
    /// family (G5 — Max* included even though it doesn't pin the STATIC
    /// gate, because a bucket could swap it for a Height). Matches
    /// Compose's gateAffectingParentBucketType exactly.
    static func parentBucketsDeclareGateAffecting(_ component: IRComponent) -> Bool {
        let affects: (String) -> Bool = { type in
            type.hasPrefix("Padding")
                || type.hasPrefix("Border")
                || (type.hasPrefix("Overflow") && type != "OverflowWrap" && type != "OverflowAnchor")
                || type == "Position"
                || GATE_HEIGHT_BUCKET_TYPES.contains(type)
        }
        let bucketHits: ([IRProperty]) -> Bool = { props in props.contains { affects($0.type) } }
        return component.selectors?.contains { bucketHits($0.properties) } == true
            || component.media?.contains { bucketHits($0.properties) } == true
    }

    /// The block-size family a parent bucket could restyle into a static
    /// hoist-gate closer (B6). MaxHeight/MaxBlockSize ARE here (unlike the
    /// static G5 set) because a bucket could swap them for a Height.
    static let GATE_HEIGHT_BUCKET_TYPES: Set<String> = [
        "Height", "BlockSize", "MinHeight", "MinBlockSize",
        "MaxHeight", "MaxBlockSize", "AspectRatio",
    ]

    // MARK: - Fallback logging

    /// The no-silent-fallthrough breadcrumb: once per container per
    /// process (PropertyTracker's dedupe), naming the reason the
    /// emulation stood down so device-report divergences are traceable.
    static func logFallback(_ component: IRComponent, reason: String) {
        PropertyTracker.logOnce(
            // Keyed per component so distinct containers each report.
            key: "MarginCollapse.fallback.\(component.id)",
            message: "margin-collapse emulation off for '\(component.name)': \(reason) — legacy sum spacing applies")
    }
}
