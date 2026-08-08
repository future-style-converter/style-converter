//
//  VerticalTextFlowLayout.swift
//  StyleConverterRuntime/Renderer — wave 35 lane B5, the renderer's
//  VERTICAL-FLOW region.
//
//  The SwiftUI twin of
//  runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/
//  VerticalTextFlowLayout.kt. One view, owned by `writing-mode` and nothing
//  else, and every decision it takes comes from the pure module
//  `StyleEngine/typography/writing/VerticalTextFlow.swift` — so the Kotlin and
//  Swift renderers cannot drift about which runs are upright or where a
//  vertical line breaks.
//
//  ## What it draws
//  An UPRIGHT vertical run: glyphs stand up and stack DOWN each line, lines
//  stack across the block axis (right-to-left for `vertical-rl`, left-to-right
//  for `vertical-lr` — css-writing-modes-4 §3).
//
//  ## Why iOS needs it
//  Measured on the frozen wave34-final capture of WPT
//  css-writing-modes/available-size-011 (fullwidth "ＰＡＳＳ",
//  Vertical_Orientation U ⇒ upright under the initial `text-orientation:
//  mixed`): iOS has no vertical text path at all and paints the run
//  horizontally in SOURCE order — "Ｓ Ｓ Ａ Ｐ" where the browser-ref reads
//  "ＰＡＳＳ", 0.9415 against a web 0.9898. The letters are right; only the
//  line stacking is missing, and line stacking is exactly what this layout is.
//
//  ## The decline contract, and the one honest gap
//  The wrap budget is the block-axis extent available to the run, which
//  SwiftUI only reveals as `proposal.height` at measure time. When it is
//  unspecified — and `PlaceholderLabel`'s chain ends in
//  `.fixedSize(horizontal: false, vertical: true)`, which proposes nil on
//  exactly that axis — `VerticalTextFlow.uprightColumnIndices` DECLINES and
//  this layout places the `fallback` subview instead, i.e. the frozen
//  horizontal label, byte for byte. `budgetOverridePx` exists for the caller
//  that can name the extent from the IR rather than the proposal; nil means
//  "ask the proposal", which is all any caller does today.
//

import SwiftUI

/// THE GATE: does this run take the upright vertical path, and if so which way
/// do its lines stack? `nil` = "no", and the caller must keep whatever it did
/// before — a horizontal mode, a sideways mode, an ASCII run under `mixed`, an
/// explicit `text-orientation: sideways`, or a run that needs both classes.
///
/// One entry point so the decision has ONE spelling per platform; the Kotlin
/// twin is `verticalUprightStack(config:text:)` in
/// core/renderer/VerticalTextFlowLayout.kt, which reaches the same two
/// `VerticalTextFlow` calls through `TextExtractor.extractWritingModeConfig`.
enum VerticalUprightGate {
    /// - Parameters:
    ///   - properties: the MERGED, inheritance-resolved list — `writing-mode`
    ///     and `text-orientation` are both `Inherited: yes`
    ///     (css-writing-modes-4 §3.1 / §5.1), so they usually sit on an
    ///     ancestor and the component's own list would answer "horizontal".
    ///   - text: the run as it will be painted (post text-transform).
    static func stack(properties: [IRProperty], text: String?) -> LineStack? {
        guard let text, !text.isEmpty else { return nil }
        // No `writing-mode` anywhere in the chain ⇒ horizontal ⇒ not ours.
        guard let mode = WritingModeExtractor.extract(from: properties)?.mode else { return nil }
        let orientation = VerticalTextFlow.runOrientation(
            writingMode: mode,
            // Absent `text-orientation` ⇒ `.mixed`, the initial value.
            textOrientation: TextOrientationExtractor.extract(from: properties)?.value ?? .mixed,
            text: text)
        guard orientation == .upright else { return nil }
        return VerticalTextFlow.lineStack(mode)
    }
}

/// Lays out `[fallback, glyph₀, glyph₁, …]` as upright vertical lines,
/// or places `fallback` alone when the plan declines.
///
/// Subview 0 is ALWAYS the decline fallback and subviews 1…n are one glyph
/// each, in logical order — the same slot contract the Compose twin's
/// `Layout` content uses, so the two placement loops read alike.
struct VerticalUprightTextFlowLayout: Layout {
    /// The run as per-scalar strings — `VerticalTextFlow.codePointsOf(_:)`.
    /// Held (rather than recomputed) so the plan's indices and the subview
    /// order come from ONE split.
    let glyphs: [String]
    /// Which side line 1 sits on — `VerticalTextFlow.lineStack(_:)`.
    let stack: LineStack
    /// An explicit block-axis budget in px, or nil to read `proposal.height`.
    var budgetOverridePx: CGFloat? = nil

    /// The measured plan: per-line glyph indices plus every glyph's natural
    /// size. Nil = DECLINE (the caller's frozen subtree wins).
    private func plan(proposal: ProposedViewSize,
                      subviews: Subviews) -> (lines: [[Int]], sizes: [CGSize])? {
        guard subviews.count > 1 else { return nil }
        // EVERY subview is sized on BOTH paths — including the fallback the
        // plan path never places. Sizing costs one text measurement and paints
        // nothing (an unPLACED subview is not drawn), and it keeps the two
        // paths symmetric with the Compose twin's measure loop.
        _ = subviews[0].sizeThatFits(proposal)
        // Every glyph measures UNSPECIFIED: an upright glyph is its own line
        // box and is never squeezed by the run's box (CSS overflows instead).
        let sizes = (1..<subviews.count).map { subviews[$0].sizeThatFits(.unspecified) }
        // The advance along the vertical inline axis = one line box's height.
        let advance = Double(sizes.first?.height ?? 0)
        let raw = budgetOverridePx ?? proposal.height
        // `.infinity` is SwiftUI's "as much as you like" and is NOT a wrap
        // budget — map it to nil so the planner declines rather than packing
        // the whole run into one impossibly long line.
        let budget: Double? = raw.flatMap { $0.isFinite ? Double($0) : nil }
        guard let lines = VerticalTextFlow.uprightColumnIndices(glyphs: glyphs,
                                                               charAdvancePx: advance,
                                                               budgetPx: budget)
        else {
            // No silent fallthrough: the GATE already said this run is
            // upright, so a decline here is a real, named gap and not a
            // routine "not our case". Logged once per process.
            _ = PropertyTracker.logOnce(
                key: "writing-mode:upright-vertical-budget",
                message: "upright vertical run declined: no finite block-axis " +
                    "budget (proposal.height was \(String(describing: raw))). " +
                    "PlaceholderLabel's .fixedSize(vertical: true) erases that " +
                    "axis, so the wrap width needs a containing-block channel " +
                    "that publishes MAX-height bases; run kept horizontal.")
            return nil
        }
        return (lines, sizes)
    }

    /// Per-line cross extent (the line box's width) and main extent (the sum
    /// of its glyph advances) — shared by measure and place so the two can
    /// never compute different columns.
    private func extents(_ lines: [[Int]], _ sizes: [CGSize]) -> (w: [CGFloat], h: [CGFloat]) {
        let w = lines.map { line in line.map { sizes[$0].width }.max() ?? 0 }
        let h = lines.map { line in line.reduce(CGFloat(0)) { $0 + sizes[$1].height } }
        return (w, h)
    }

    func sizeThatFits(proposal: ProposedViewSize,
                      subviews: Subviews,
                      cache: inout Void) -> CGSize {
        guard let (lines, sizes) = plan(proposal: proposal, subviews: subviews) else {
            // DECLINE — the frame is whatever the frozen subtree wanted.
            return subviews.first?.sizeThatFits(proposal) ?? .zero
        }
        let (w, h) = extents(lines, sizes)
        return CGSize(width: w.reduce(0, +), height: h.max() ?? 0)
    }

    func placeSubviews(in bounds: CGRect,
                       proposal: ProposedViewSize,
                       subviews: Subviews,
                       cache: inout Void) {
        guard let (lines, sizes) = plan(proposal: proposal, subviews: subviews) else {
            subviews.first?.place(at: bounds.origin, anchor: .topLeading, proposal: proposal)
            return
        }
        let (lineW, _) = extents(lines, sizes)
        let totalW = lineW.reduce(0, +)
        // `vertical-rl` puts line 1 at the RIGHT edge and walks left;
        // `vertical-lr` starts at the left edge and walks right. Both walk the
        // plan in LOGICAL order — only the anchor differs.
        var x: CGFloat = (stack == .rightToLeft) ? totalW : 0
        for (index, line) in lines.enumerated() {
            if stack == .rightToLeft { x -= lineW[index] }
            var y: CGFloat = 0
            for glyphIndex in line {
                let size = sizes[glyphIndex]
                // Centre the glyph across its line box — the vertical
                // typesetting equivalent of a baseline-centred glyph in a
                // horizontal line box. A no-op when every glyph in the line
                // has the same advance (the CJK/fullwidth case, i.e. every
                // upright run).
                subviews[glyphIndex + 1].place(
                    at: CGPoint(x: bounds.minX + x + (lineW[index] - size.width) / 2,
                                y: bounds.minY + y),
                    anchor: .topLeading,
                    proposal: ProposedViewSize(size))
                y += size.height
            }
            if stack == .leftToRight { x += lineW[index] }
        }
    }
}

/// View wrapper for `VerticalUprightTextFlowLayout` — splits the run into
/// glyph slots once and hands the layout its `[fallback, glyphs…]` subviews.
struct VerticalUprightTextFlow<Fallback: View, Glyph: View>: View {
    /// The run as the caller will paint it (post text-transform).
    let text: String
    /// Which side line 1 sits on — `VerticalTextFlow.lineStack(_:)`.
    let stack: LineStack
    /// An explicit block-axis budget in px, or nil to read the proposal.
    var budgetOverridePx: CGFloat? = nil
    /// The frozen horizontal subtree, placed verbatim when the plan declines.
    @ViewBuilder var fallback: () -> Fallback
    /// Paints ONE code point upright, styled like the run it came from.
    @ViewBuilder var glyph: (String) -> Glyph

    var body: some View {
        let slots = VerticalTextFlow.codePointsOf(text)
        VerticalUprightTextFlowLayout(glyphs: slots,
                                      stack: stack,
                                      budgetOverridePx: budgetOverridePx) {
            fallback()                                   // subview 0
            // `id: \.offset` — the run may repeat a glyph, so the STRING is
            // not a stable identity; the slot index is.
            ForEach(Array(slots.enumerated()), id: \.offset) { _, s in
                glyph(s)                                 // subviews 1 … n
            }
        }
    }
}
