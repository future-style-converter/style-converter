//
//  CSSFlexLayout.swift
//  StyleEngine/layout/flexbox — fidelity wave 2.
//
//  Real single-line CSS flexbox as a SwiftUI custom Layout. The legacy
//  HStack/VStack mapping ignored flex-grow / flex-shrink / flex-basis
//  (FR_GrowBasis, FR_ShrinkBasis, FC_GrowBasis), never applied
//  justify-content in either axis (FC_SpaceBetween), and couldn't
//  stretch an auto-cross-size child (FR_AlignSelf `d`). This Layout
//  implements the css-flexbox-1 §9.7 "resolve flexible lengths" loop
//  (min-clamped grow/shrink), §8.2 justify-content distribution and
//  §8.3/§8.4 cross-axis alignment incl. per-item align-self and true
//  stretch for auto-cross-size items.
//
//  Per-item inputs ride the ItemPlacementKey layout value (IR v2 Slot &
//  Placement contract, StyleEngine/core/placement/ItemPlacement.swift):
//  ComponentHost attaches every component's self-extracted placement,
//  and this Layout consumes ONLY the flex block — the SwiftUI-idiomatic
//  channel for per-subview parent-data, and the v2 resolution rule
//  (blocks for other container kinds stay inert). The arithmetic core
//  (CSSFlexMath) is pure and XCTest-pinned without constructing any view.
//

import SwiftUI

// MARK: - Pure arithmetic core

/// View-free flex arithmetic so unit tests pin the exact numbers.
enum CSSFlexMath {

    /// One item's resolved inputs for the main-axis pass.
    struct ItemInput: Equatable {
        /// Flex base size (css-flexbox-1 §9.2 step 3) — basisPx or intrinsic.
        var basis: CGFloat
        /// Automatic minimum main size (§4.5) — from the subview's own
        /// min-size response (the 50×30 harness floor rides in here).
        var min: CGFloat
        /// flex-grow factor.
        var grow: CGFloat
        /// flex-shrink factor.
        var shrink: CGFloat
    }

    /// §9.7 resolve flexible lengths, min-clamped (no max clamp — the
    /// fixtures don't exercise max-constrained flex items yet).
    /// `available` is the container's definite content-box main size;
    /// nil = indefinite (fit-content hug: items at hypothetical size).
    static func mainSizes(items: [ItemInput],
                          available: CGFloat?,
                          gap: CGFloat) -> [CGFloat] {
        guard !items.isEmpty else { return [] }
        // Hypothetical main size = basis clamped by min (§9.3 step 3).
        let hypothetical = items.map { max($0.basis, $0.min) }
        // Indefinite container → no free space to distribute; every item
        // sits at its hypothetical size (fit-content parity with the web
        // harness, which lays the box out at max-content width).
        guard let available = available, available.isFinite else {
            return hypothetical
        }
        // Space taken by the fixed inter-item gaps (§7 gutters).
        let gapTotal = gap * CGFloat(items.count - 1)
        let inner = available - gapTotal
        // Sum of hypothetical sizes decides grow vs shrink (§9.7 step 1).
        let hypSum = hypothetical.reduce(0, +)
        if hypSum == inner { return hypothetical }
        let growing = hypSum < inner

        // Frozen = item no longer participates in distribution. Start by
        // freezing items whose factor is zero (§9.7 step 2) at their
        // hypothetical size.
        var frozen = items.map { growing ? $0.grow == 0 : $0.shrink == 0 }
        var sizes = hypothetical

        // Distribution loop (§9.7 step 4) — min-violation freezing only.
        // Terminates: every iteration freezes ≥1 item or exits.
        for _ in 0..<(items.count + 1) {
            let unfrozenIdx = items.indices.filter { !frozen[$0] }
            if unfrozenIdx.isEmpty { break }
            // Free space relative to the UNfrozen items' flex base sizes
            // plus the frozen items' final sizes (§9.7 step 4a).
            let frozenSum = items.indices.filter { frozen[$0] }
                .reduce(CGFloat(0)) { $0 + sizes[$1] }
            let unfrozenBase = unfrozenIdx.reduce(CGFloat(0)) { $0 + items[$1].basis }
            let free = inner - frozenSum - unfrozenBase
            var violation: CGFloat = 0
            var minViolated: [Int] = []
            if growing {
                // Distribute positive free space proportional to grow.
                let growSum = unfrozenIdx.reduce(CGFloat(0)) { $0 + items[$1].grow }
                for i in unfrozenIdx {
                    let target = items[i].basis
                        + (growSum > 0 ? free * items[i].grow / growSum : 0)
                    // Min clamp (§9.7 step 4b) — track total violation.
                    let clamped = max(target, items[i].min)
                    violation += clamped - target
                    if clamped > target { minViolated.append(i) }
                    sizes[i] = clamped
                }
            } else {
                // Shrink: negative free space scaled by shrink × basis
                // (§9.7 step 4a "scaled flex shrink factor").
                let scaledSum = unfrozenIdx.reduce(CGFloat(0)) {
                    $0 + items[$1].shrink * items[$1].basis
                }
                for i in unfrozenIdx {
                    let scaled = items[i].shrink * items[i].basis
                    let target = items[i].basis
                        + (scaledSum > 0 ? free * scaled / scaledSum : 0)
                    let clamped = max(target, items[i].min)
                    violation += clamped - target
                    if clamped > target { minViolated.append(i) }
                    sizes[i] = clamped
                }
            }
            // Zero total violation → done; positive → freeze the items
            // clamped UP by their min and redistribute (§9.7 step 4d/e).
            if violation <= 0.5 { break }
            for i in minViolated { frozen[i] = true }
        }
        return sizes
    }

    /// §8.2 justify-content — returns each item's main-axis offset from
    /// the content-box origin. `available` nil/infinite → packed start.
    static func mainOffsets(sizes: [CGFloat],
                            available: CGFloat?,
                            gap: CGFloat,
                            justify: AlignmentKeyword?) -> [CGFloat] {
        guard !sizes.isEmpty else { return [] }
        let gapTotal = gap * CGFloat(sizes.count - 1)
        let content = sizes.reduce(0, +) + gapTotal
        // Leftover free space AFTER flexing — only non-zero when no item
        // grew (grow factors all 0) inside a definite container.
        let free = max(0, (available.flatMap { $0.isFinite ? $0 : nil } ?? content) - content)
        // Leading inset and extra between-item spacing per keyword.
        var lead: CGFloat = 0
        var between: CGFloat = 0
        let n = CGFloat(sizes.count)
        switch justify {
        case .end:          lead = free
        case .center:       lead = free / 2
        case .spaceBetween: between = sizes.count > 1 ? free / (n - 1) : 0
        case .spaceAround:  between = free / n; lead = free / (2 * n)
        case .spaceEvenly:  between = free / (n + 1); lead = between
        default:            break  // start / flex-start / nil → packed
        }
        var offsets: [CGFloat] = []
        var cursor = lead
        for s in sizes {
            offsets.append(cursor)
            cursor += s + gap + between
        }
        return offsets
    }

    /// §8.3/§8.4 cross-axis position for one item inside the line.
    /// `stretch` on a non-auto item behaves as flex-start per §8.3.
    static func crossOffset(itemCross: CGFloat,
                            lineCross: CGFloat,
                            align: AlignmentKeyword) -> CGFloat {
        switch align {
        case .center:        return (lineCross - itemCross) / 2
        case .end, .selfEnd: return lineCross - itemCross
        default:             return 0  // start / stretch / baseline → cross-start
        }
    }

    /// Resolve the effective per-item alignment: align-self (≠ auto)
    /// wins over the container's align-items; both default to stretch
    /// (css-align-3 §6.1 — `normal` behaves as stretch in flex).
    static func resolvedAlign(self alignSelf: AlignmentKeyword?,
                              items alignItems: AlignmentKeyword?) -> AlignmentKeyword {
        if let s = alignSelf, s != .auto { return s == .normal ? .stretch : s }
        guard let i = alignItems, i != .normal, i != .auto else { return .stretch }
        return i
    }
}

// MARK: - Column-flex stretch environment channel

/// Environment key for the column-flex inline-axis stretch injection —
/// the width analogue of CSSGridLayout's gridStretchHeight. Written by
/// the parent's child loop (ComponentRenderer), folded into the child's
/// SizeConfig before its style chain builds so backgrounds/borders paint
/// at the stretched width (an outer .frame can't reach the paint chain).
private struct FlexStretchWidthKey: EnvironmentKey {
    /// Default nil = no stretch injection.
    static let defaultValue: CGFloat? = nil
}

extension EnvironmentValues {
    /// Content width a column-flex child should stretch to (nil = none).
    var flexStretchWidth: CGFloat? {
        get { self[FlexStretchWidthKey.self] }
        set { self[FlexStretchWidthKey.self] = newValue }
    }
}

// MARK: - The Layout

/// Single-line (nowrap) flex container. Wrap containers keep routing
/// through FlowLayout; multi-line justify/align-content stay TODO there.
@available(iOS 16.0, *)
struct CSSFlexLayout: Layout {
    /// Main axis — horizontal for row, vertical for column.
    var axis: ContainerAxis
    /// row-reverse / column-reverse — items placed in reverse order.
    var reverse: Bool = false
    /// Container `justify-content` (main axis, §8.2).
    var justify: AlignmentKeyword? = nil
    /// Container `align-items` (cross axis default, §8.3).
    var alignItems: AlignmentKeyword? = nil
    /// Resolved gap along the main axis (§7 gutters).
    var gap: CGFloat = 0
    /// True when the IR declared an explicit main-axis size — only then
    /// does the proposal carry a definite main size to flex against;
    /// otherwise the container hugs content (web-harness fit-content).
    var definiteMain: Bool = false
    /// Same for the cross axis — decides the stretch line size source.
    var definiteCross: Bool = false

    /// Split a size into (main, cross) per our axis.
    private func mc(_ s: CGSize) -> (main: CGFloat, cross: CGFloat) {
        axis == .horizontal ? (s.width, s.height) : (s.height, s.width)
    }

    /// Measure everything the placement needs, shared by both protocol
    /// methods so the wrap decision can't drift between them.
    private func plan(proposal: ProposedViewSize, subviews: Subviews)
        -> (sizes: [CGFloat], offsets: [CGFloat],
            cross: [CGFloat], lineCross: CGFloat,
            aligns: [AlignmentKeyword], stretch: [Bool],
            totalMain: CGFloat) {
        // Proposal split into axes (nil = unspecified probe).
        let pMain: CGFloat? = axis == .horizontal ? proposal.width : proposal.height
        let pCross: CGFloat? = axis == .horizontal ? proposal.height : proposal.width
        // The definite main size we flex against — only when the IR set
        // one AND the proposal is finite (infinity probes must hug).
        let available: CGFloat? = (definiteMain && pMain?.isFinite == true) ? pMain : nil

        // Per-item inputs: intrinsic ideal, minimum, spec.
        var inputs: [CSSFlexMath.ItemInput] = []
        var aligns: [AlignmentKeyword] = []
        var stretchable: [Bool] = []
        for sub in subviews {
            // v2 placement contract: read the child-carried parent-data
            // and consume ONLY the flex block (design §2.2 resolution
            // rule). nil placement = anonymous subview (e.g. the leading
            // `_text` placeholder) → CSS initial values, exactly the old
            // FlexItemSpec defaults.
            let placement = sub[ItemPlacementKey.self]
            let claim = placement?.flex ?? ItemPlacement.FlexClaim()
            // Intrinsic (ideal) size — CSS `flex-basis: auto` fallback.
            let ideal = mc(sub.sizeThatFits(.unspecified))
            // Min main size: the subview's response to a zero proposal
            // on the main axis (carries the 50×30 web-harness floor and
            // any explicit width/height, mirroring §4.5 automatic min).
            let zeroProbe = axis == .horizontal
                ? ProposedViewSize(width: 0, height: nil)
                : ProposedViewSize(width: nil, height: 0)
            let minMain = mc(sub.sizeThatFits(zeroProbe)).main
            inputs.append(.init(basis: claim.basisPx ?? ideal.main,
                                min: minMain,
                                grow: claim.grow, shrink: claim.shrink))
            let a = CSSFlexMath.resolvedAlign(self: claim.alignSelf, items: alignItems)
            aligns.append(a)
            // §8.3: stretch only stretches items with an auto cross size.
            // The placement carries axis-agnostic size FACTS; cross-axis
            // resolution happens here because only the container knows
            // its axis: row (horizontal main) → cross is the block axis
            // (Height/BlockSize), column → cross is the inline axis.
            let crossAuto = placement.map {
                axis == .horizontal ? !$0.explicitHeight : !$0.explicitWidth
            } ?? true  // anonymous items have no declared sizes
            stretchable.append(a == .stretch && crossAuto)
        }

        // Main-axis resolution (§9.7) + justify offsets (§8.2).
        let sizes = CSSFlexMath.mainSizes(items: inputs, available: available, gap: gap)
        let offsets = CSSFlexMath.mainOffsets(sizes: sizes, available: available,
                                              gap: gap, justify: justify)
        let totalMain = available
            ?? (sizes.reduce(0, +) + gap * CGFloat(max(0, sizes.count - 1)))

        // Cross-axis: measure each item at its flexed main size so text
        // re-wraps (shrunken items grow taller, like CSS).
        var crossSizes: [CGFloat] = []
        for (i, sub) in subviews.enumerated() {
            let p = axis == .horizontal
                ? ProposedViewSize(width: sizes[i], height: nil)
                : ProposedViewSize(width: nil, height: sizes[i])
            crossSizes.append(mc(sub.sizeThatFits(p)).cross)
        }
        // Line cross size: the definite container cross when declared,
        // else the tallest/widest item (single-line container = one line
        // whose cross size is the max hypothetical cross, §9.4).
        let lineCross: CGFloat = {
            if definiteCross, let c = pCross, c.isFinite { return c }
            return crossSizes.max() ?? 0
        }()
        return (sizes, offsets, crossSizes, lineCross, aligns, stretchable, totalMain)
    }

    func sizeThatFits(proposal: ProposedViewSize,
                      subviews: Subviews,
                      cache: inout ()) -> CGSize {
        guard !subviews.isEmpty else { return .zero }
        let p = plan(proposal: proposal, subviews: subviews)
        return axis == .horizontal
            ? CGSize(width: p.totalMain, height: p.lineCross)
            : CGSize(width: p.lineCross, height: p.totalMain)
    }

    func placeSubviews(in bounds: CGRect,
                       proposal: ProposedViewSize,
                       subviews: Subviews,
                       cache: inout ()) {
        guard !subviews.isEmpty else { return }
        let p = plan(proposal: proposal, subviews: subviews)
        for (i, sub) in subviews.enumerated() {
            // row-reverse / column-reverse: mirror the main offset so
            // declaration order runs end→start (§5.1).
            let main = reverse
                ? p.totalMain - p.offsets[i] - p.sizes[i]
                : p.offsets[i]
            // Stretching items get the full line proposed on the cross
            // axis (§8.3); others keep their measured cross size.
            let itemCross = p.stretch[i] ? p.lineCross : p.cross[i]
            let cross = CSSFlexMath.crossOffset(itemCross: itemCross,
                                                lineCross: p.lineCross,
                                                align: p.aligns[i])
            let origin = axis == .horizontal
                ? CGPoint(x: bounds.minX + main, y: bounds.minY + cross)
                : CGPoint(x: bounds.minX + cross, y: bounds.minY + main)
            let prop = axis == .horizontal
                ? ProposedViewSize(width: p.sizes[i],
                                   height: p.stretch[i] ? p.lineCross : nil)
                : ProposedViewSize(width: p.stretch[i] ? p.lineCross : nil,
                                   height: p.sizes[i])
            sub.place(at: origin, anchor: .topLeading, proposal: prop)
        }
    }
}
