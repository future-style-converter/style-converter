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
//  ## The decline contract, and the gap wave 52 closed
//  The wrap budget is the block-axis extent available to the run, which
//  SwiftUI only reveals as `proposal.height` at measure time. When it is
//  unspecified — and `PlaceholderLabel`'s chain ends in
//  `.fixedSize(horizontal: false, vertical: true)`, which proposes nil on
//  exactly that axis — the pre-wave-52 layout DECLINED and placed the
//  `fallback` subview, i.e. the frozen horizontal label (wave51-fix
//  ch-units-vrl-005..008: the orange upright `00000` drew 60×25 where the
//  ref stacks five upright glyphs in one 120×120 column).
//
//  Wave 52 lane L8 (M-C): css-writing-modes-4 §7.3.1 gives an orthogonal
//  flow with an indefinite available inline size a DEFINITE one — the
//  nearest ancestor's definite block size, else the initial containing
//  block's extent. `budgetOverridePx` is that §7.3.1 fallback: the label
//  call site resolves it (`VerticalUprightGate.budgetPx`, from the element's
//  own definite height, the containing-block channel and the composed
//  capture's `StyleViewport.height` = the 568 px ICB) and passes it under
//  `wptCaptureMode` only. A BOUNDED proposal still wins — the twin-pinned
//  `VerticalInlineAxis.uprightBudget` makes the pick — and nil (every
//  non-capture caller) keeps the historical decline byte for byte.
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
    ///     (css-writing-modes-4 §3.2 / §5.1), so they usually sit on an
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

    /// Wave 52 lane L8 (M-C) — the css-writing-modes-4 §7.3.1 available
    /// inline size for an orthogonal flow whose proposal is indefinite, as
    /// `budgetOverridePx` for `VerticalUprightTextFlow`.
    ///
    /// - Parameters:
    ///   - ownBlockExtentPx: the element's OWN definite extent along the
    ///     flow's inline axis (its used `height` minus the vertical padding
    ///     band under a horizontal parent), or nil when `auto`. A box that
    ///     names its own inline size wraps against exactly that, so it
    ///     outranks every ancestor reading.
    ///   - containingBlockHeightPx: the nearest ancestor's definite block
    ///     extent (`SpacingContext.containingBlockHeightPx`), or nil.
    ///   - icbBlockExtentPx: the initial containing block's extent —
    ///     `StyleViewport.height`, which the composed capture publishes as
    ///     the ref's 568 px ICB. The caller gates on `wptCaptureMode`,
    ///     because outside a capture that value is the 844 pt app default.
    /// - Returns: the wrap budget in px, or nil to keep the historical
    ///   decline. The precedence is the twin-pinned `VerticalInlineAxis
    ///   .orthogonalBudget`, fed the own extent as the "nearest definite".
    static func budgetPx(ownBlockExtentPx: Double?,
                         containingBlockHeightPx: Double?,
                         icbBlockExtentPx: Double?) -> CGFloat? {
        // Own definite extent first; a non-positive one is "not definite".
        let nearest = ownBlockExtentPx.flatMap { $0.isFinite && $0 > 0 ? $0 : nil }
            ?? containingBlockHeightPx
        return VerticalInlineAxis.orthogonalBudget(nearestDefiniteBlockSizePx: nearest,
                                                   icbBlockExtentPx: icbBlockExtentPx)
            .map { CGFloat($0) }
    }

    /// The label call site's one-line form (ComponentRenderer seam patch,
    /// `tools/titan/results/wave52-vertical-wedges/seam-2.patch`): reads the
    /// three inputs off the component's built style and the published
    /// viewport so the seam hunk stays a single argument.
    ///
    /// - Parameters:
    ///   - style: the component's `ComponentStyle` — its definite content
    ///     HEIGHT (`ContainingBlockBasis.contentBox(vertical: true)`, the
    ///     declared border box minus padding and painted borders) is the
    ///     own extent; `spacing.context.containingBlockHeightPx` the
    ///     ancestor's.
    ///   - viewport: the host's `\.styleViewport` (the composed capture
    ///     publishes the ICB height there), or nil.
    ///   - wptCaptureMode: the `\.wptCaptureMode` flag. FALSE ⇒ nil, so
    ///     every non-capture surface (the dark stage, the app) keeps the
    ///     historical decline byte for byte — the app's 844 pt viewport is
    ///     not the ref's ICB and must not become a wrap budget.
    static func budgetPx(style: ComponentStyle,
                         viewport: StyleViewport?,
                         wptCaptureMode: Bool) -> CGFloat? {
        // Outside a WPT capture there is no ref ICB to stand in for.
        guard wptCaptureMode else { return nil }
        return budgetPx(
            // Own definite content height, when the element declares one.
            ownBlockExtentPx: ContainingBlockBasis.contentBox(style: style, vertical: true).map(Double.init),
            // The ancestor-published definite block size, when there is one.
            containingBlockHeightPx: style.spacing.context.containingBlockHeightPx,
            // The composed capture's ICB height (568 at the defaults).
            icbBlockExtentPx: viewport?.height)
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
        // `.infinity` is SwiftUI's "as much as you like" and is NOT a wrap
        // budget — map it to nil so an unbounded proposal reads as indefinite.
        let bounded: Double? = proposal.height.flatMap { $0.isFinite ? Double($0) : nil }
        // Wave 52 lane L8 (M-C): a BOUNDED proposal (a definite available
        // inline size) wins; only an indefinite one takes the §7.3.1
        // `budgetOverridePx` fallback the call site resolved; nil declines.
        // The pick is the twin-pinned `VerticalInlineAxis.uprightBudget` —
        // the Compose measure policy makes the identical choice.
        let budget = VerticalInlineAxis.uprightBudget(
            boundedPx: bounded,
            fallbackPx: budgetOverridePx.flatMap { $0.isFinite ? Double($0) : nil })
        guard let lines = VerticalTextFlow.uprightColumnIndices(glyphs: glyphs,
                                                               charAdvancePx: advance,
                                                               budgetPx: budget)
        else {
            // No silent fallthrough: the GATE already said this run is
            // upright, so a decline here is a real, named gap and not a
            // routine "not our case". Logged once per process. With the
            // §7.3.1 fallback in place a decline means no budget at all
            // (non-capture caller) or a glyphless / zero-advance run.
            _ = PropertyTracker.logOnce(
                key: "writing-mode:upright-vertical-budget",
                message: "upright vertical run declined: no finite block-axis " +
                    "budget (proposal.height \(String(describing: proposal.height)), " +
                    "§7.3.1 fallback \(String(describing: budgetOverridePx))) or a " +
                    "glyphless/zero-advance run; run kept horizontal.")
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
