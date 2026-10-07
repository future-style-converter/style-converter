//
//  ListMarkerOutsideHang.swift
//  StyleEngine/lists — wave 52, lane L6 (T5): the `list-style-position:
//  outside` marker HANG, the deferred "B-RC3 part 3" ComponentRenderer's
//  marker branch has named since wave 28. BYTE-PARALLEL TWIN of
//  runtimes/compose/src/main/java/com/styleconverter/runtime/lists/
//  ListMarkerOutsideHang.kt — same `place` arithmetic, same pins.
//
//  ## The defect (measured on wave51-fix, pngjs over the run PNGs)
//  css-counter-styles/counter-suffix `ios f 0.9285`: rows 1–8 start at
//  x 64–65 (marker `1.` at x 64–73, item text at 80–103) where the frozen
//  ref's row 1 ink runs x 46–58 (the marker, hanging in the `<ol>`'s
//  3em = 48 px padding) and x 64–87 (the text). css-lists/
//  counter-list-item-2: ref x 38–61, iOS x 56–77. Every item is displaced
//  by exactly `markerWidth + gapPt` (+18 px) because `markerPlacement`
//  composes `HStack { Text(marker); item }` — the marker is a SIBLING that
//  takes inline space the CSS box model never gives it.
//
//  ## The spec
//  css-lists-3 §3.5: an `outside` ::marker box is positioned outside the
//  principal block box, before its start edge, its inline-end edge one
//  marker padding before the item's border-box start edge; the item's own
//  content edge does not move. So the item lays out EXACTLY as if it had
//  no marker and the marker paints into the item's margin area — the pair
//  reports the ITEM's size only.
//
//  ## Why a Layout and not `.offset` on the HStack's Text
//  An HStack cannot give a child negative inline extent; `.offset` moves
//  the marker's ink but not the space it reserves, so the item would still
//  start 18 px late. A `Layout` measures both, reports the item's size and
//  places the marker outside its bounds.
//
//  ## RTL (BACKLOG (c⁴): RTL markers are BLOCKED UPSTREAM)
//  SwiftUI's Layout engine mirrors every `place(at:)` x about the layout
//  bounds under an RTL layoutDirection (probe-verified — see
//  FloatBlockLayout.placedX), so the physical `-(w + gap)` handed in here
//  lands at `width + gap`: the inline-END side css-lists-3 §3.5 asks for,
//  with no code of its own. (Compose's `place` is NOT mirrored, so the
//  Kotlin twin spells the mirror explicitly.) The corpus's RTL rows still
//  lack markers for an UPSTREAM reason (no `meta.markerText` reaches
//  them), so this does not move a cell this wave — stated, not hidden.
//
//  Staged as a DEVICE A/B: the `markerPlacement` seam that swaps the HStack
//  for this Layout is tools/titan/results/wave52-counters-and-lists/
//  seam-2.patch, applied by the orchestrator against the closing gate with
//  the installed `.app` hash recorded — never blind-lifted.
//

import SwiftUI

enum ListMarkerOutsideHang {

    /// Where the two boxes go, in the pair's own coordinate space (origin
    /// = the item's border-box origin). `width` × `height` is the size the
    /// pair REPORTS — the item's alone, by §3.5.
    struct Placement: Equatable {
        var markerX: CGFloat
        var markerY: CGFloat
        var width: CGFloat
        var height: CGFloat
    }

    /// The pure geometry, pinned in ListMarkerOutsideHangTests.
    ///
    /// - Parameters:
    ///   - markerBaseline: the marker's first text baseline, nil if it
    ///     exposes none; `itemBaseline` likewise for the item.
    ///   - gap: the inline gap — the caller hands in `ListMarkerRow.gapPt`
    ///     so both placements share ONE gap constant.
    ///   - alignsByBaseline: the row's per-item decision
    ///     (`ListMarkerRow.rowAlignment == .firstTextBaseline`); false ⇒
    ///     both boxes stack by their tops.
    ///   - itemIsEmpty: `ListMarkerEmptyItem.isEmpty` — the marker's line is
    ///     the item's only line box, so the pair is at least its height.
    static func place(markerSize: CGSize, markerBaseline: CGFloat?,
                      itemSize: CGSize, itemBaseline: CGFloat?,
                      gap: CGFloat, alignsByBaseline: Bool,
                      itemIsEmpty: Bool = false) -> Placement {
        // Inline: the marker's END edge sits `gap` before the item's START
        // edge — `markerX + markerWidth + gap == 0` is the pinned identity.
        let markerX = -(markerSize.width + gap)
        // Block: share the first line's baseline when the row aligns by
        // baseline and BOTH boxes expose one (css-lists-3 §3.5); otherwise
        // top-align, exactly the HStack's `.top` fallback.
        let markerY: CGFloat
        if alignsByBaseline, let mb = markerBaseline, let ib = itemBaseline {
            markerY = ib - mb
        } else {
            markerY = 0
        }
        // The pair reports the ITEM's size only: the marker adds no width
        // (the whole defect) and no height (css-sizing-3 §5.1 — a taller
        // marker overflows a definite-height item) — except for an EMPTY
        // item, whose one line box is the marker's (CSS 2.1 §10.6.3).
        return Placement(markerX: markerX, markerY: markerY, width: itemSize.width,
                         height: itemIsEmpty ? max(itemSize.height, markerY + markerSize.height) : itemSize.height)
    }
}

/// The Layout: subview 0 is the marker, subview 1 the item.
struct ListMarkerOutsideHangLayout: Layout {
    /// `ListMarkerRow.gapPt`, threaded so the constant has one home.
    let gap: CGFloat
    /// `ListMarkerRow.rowAlignment(itemExposesTextBaseline:) == .firstTextBaseline`.
    let alignsByBaseline: Bool
    /// `ListMarkerEmptyItem.isEmpty` of the item — see `place`.
    var itemIsEmpty: Bool = false

    /// The pair is the ITEM's size (§3.5) — the marker reserves nothing,
    /// except the line height of an EMPTY item (the same rule as `place`).
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard subviews.count == 2 else { return .zero }
        let item = subviews[1].sizeThatFits(proposal)
        guard itemIsEmpty else { return item }
        return CGSize(width: item.width, height: max(item.height, subviews[0].sizeThatFits(.unspecified).height))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 2 else { return }
        // The marker is shrink-to-fit inline content sized by its glyphs
        // (css-lists-3 §3.5) — the same `.fixedSize` the call site keeps.
        let markerSize = subviews[0].sizeThatFits(.unspecified)
        let itemSize = subviews[1].sizeThatFits(proposal)
        // `dimensions(in:)[.firstTextBaseline]` answers the BOTTOM edge for a
        // view without text (SwiftUI's documented fallback) — which is why
        // the baseline read is gated on the per-item decision, never blind.
        let mb: CGFloat? = alignsByBaseline
            ? subviews[0].dimensions(in: .unspecified)[VerticalAlignment.firstTextBaseline] : nil
        let ib: CGFloat? = alignsByBaseline
            ? subviews[1].dimensions(in: proposal)[VerticalAlignment.firstTextBaseline] : nil
        let p = ListMarkerOutsideHang.place(markerSize: markerSize, markerBaseline: mb,
                                            itemSize: itemSize, itemBaseline: ib,
                                            gap: gap, alignsByBaseline: alignsByBaseline,
                                            itemIsEmpty: itemIsEmpty)
        // The item at the origin, laid out as if the marker did not exist.
        subviews[1].place(at: bounds.origin, anchor: .topLeading, proposal: proposal)
        // The marker outside the bounds (negative x; the engine mirrors it
        // to the end side under RTL — see the header).
        subviews[0].place(at: CGPoint(x: bounds.minX + p.markerX, y: bounds.minY + p.markerY),
                          anchor: .topLeading, proposal: .unspecified)
    }

    /// The pair's text baselines are the ITEM's, so an outer baseline-
    /// aligned container still sees the right line (the marker is at the
    /// origin's left and contributes no alignment guide of its own).
    func explicitAlignment(of guide: VerticalAlignment, in bounds: CGRect,
                           proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGFloat? {
        guard subviews.count == 2,
              guide == .firstTextBaseline || guide == .lastTextBaseline else { return nil }
        return subviews[1].dimensions(in: proposal)[guide]
    }
}
