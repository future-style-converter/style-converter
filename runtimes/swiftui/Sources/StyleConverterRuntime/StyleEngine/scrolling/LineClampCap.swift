//
//  LineClampCap.swift
//  StyleEngine/scrolling — Wave 46 (lane Y1): the BLOCK-LEVEL line-clamp
//  height cap, iOS twin of Compose's scrolling/LineClampCap.kt (wave 41).
//
//  css-overflow-4 §5 expands `line-clamp: <n>` into `max-lines: <n>` +
//  `block-ellipsis: auto` + `continue: discard`: the clamp container keeps
//  its first <n> LINE BOXES and discards everything after them, and §4.3
//  makes the discarded content unpaintable ("is not rendered"). The iOS
//  leaf path honors this for a component whose text lives in ONE label
//  (PlaceholderLabel's inner `.lineLimit(textConfig.lineClampLimit)`), but
//  a clamp root whose line boxes live in CHILD components — `<span>` runs,
//  nested `<p>`/`<div>` blocks, an inline-block atom — rendered every child
//  unclamped: the wave-45 iOS captures show block-ellipsis-012's 1-line
//  box 3 lines tall (iOS-ref 0.9245), -022's 2-line box 3 lines tall
//  (0.9451), -027/-031/-015 5 lines where the ref closes after 2
//  (0.868/0.870/0.870) — all tests whose Android twin, capped since wave
//  41, passes. SwiftUI's `.lineLimit` is an ENVIRONMENT value: each child
//  Text clamps itself at N, the container never does. So the container
//  must cap: this file is the pure half — the wire gate, the line-box
//  pick, the cap height and the cap decision — pinned by
//  LineClampCapTests without a render surface; LineClampHeightCapLayout
//  is the layout half; LineClampCensus walks the children for the
//  non-uniform case.
//
//  Chain slot (the renderer seam): the cap wraps the CONTENT box — inside
//  the border inset / padding / sizing chain (StyleBuilder.applyBoxDecoration),
//  i.e. on `flowContainer(style:)` — so the budget is N line boxes only;
//  the Compose twin sits at its step-7 overflow node and therefore adds
//  the padding + border band that nest inside that node. Same model,
//  different measuring point.
//

import CoreGraphics
import Foundation

enum LineClampCap {

    // Tolerance (pt) between the estimated cap and a measurement that is
    // ALREADY clamped by the leaf label. A leaf's N clamped lines measure
    // N × the same pinned line box this file computes, so the two agree up
    // to rounding of fractional line heights (monospace 13px × 1.25 =
    // 16.25pt/line); 2pt absorbs that rounding so the cap NEVER re-clips a
    // correctly clamped leaf (block-ellipsis-013's passing 2-line box stays
    // byte-identical), while a genuine overflow is always ≥ one extra line
    // box (≥ 16pt) past the cap and still trips it. Same constant as
    // Compose's LineClampCap.SLACK_PX — the cross-native pin.
    static let slackPx: CGFloat = 2

    /// The clamp line count, gated on the RAW wire shape.
    ///
    /// Only the `{"type":"lines","count":N}` variant caps (the shorthand's
    /// `<integer [1,∞]>` grammar, css-overflow-4 §5.1 — the converter's
    /// LineClampPropertyParser emits exactly this for it). `none` (clamp
    /// off), `auto` (clamp at the box's own block-size constraint — no
    /// fixed line count to turn into a height) and the legacy bare-integer
    /// shape deliberately return nil so this cap never guesses; the read
    /// is delegated to LineClampExtractor so the leaf's `.lineLimit` and
    /// this cap can never disagree about the count.
    static func linesCount(_ properties: [IRProperty]) -> Int? {
        // The LAST LineClamp declaration wins (cascade order) — the
        // extractor folds the whole list in order, exactly like that.
        guard let last = properties.last(where: { $0.type == LineClampProperty.name }),
              // Gate on the sealed `lines` discriminator: the extractor
              // ALSO accepts the legacy bare integer, which this cap
              // refuses (Compose parity — only the live wire shape caps).
              case .object(let o) = last.data,
              o["type"]?.stringValue == "lines"
        else { return nil }
        // The ≥1 guard lives in the extractor (css-overflow-4 §5.1).
        return LineClampExtractor.extract(from: properties)?.lines
    }

    /// True when the declaration forbids a marker (`no-ellipsis` / `""`,
    /// LineClampConfig.markerSuppressed). SwiftUI's `.lineLimit` tail
    /// truncation ALWAYS paints "…", so a marker-less clamp cannot ride the
    /// leaf path: the renderer routes it through the cap instead — the text
    /// lays out every line and the cap discards the tail without a glyph
    /// (block-ellipsis-023's iOS capture paints the forbidden "…").
    static func markerSuppressed(_ properties: [IRProperty]) -> Bool {
        LineClampExtractor.extract(from: properties)?.markerSuppressed ?? false
    }

    /// The leaf label's `.lineLimit` argument: the clamp count as today,
    /// or nil when the marker is suppressed — SwiftUI cannot truncate
    /// without painting "…", so the marker-less clamp is enforced by the
    /// cap/clip route alone (the leaf must lay out ALL its lines for the
    /// first N to keep their natural geometry, css-overflow-4 §4.3). Pure,
    /// so the seam is a one-call fold on TextConfig.lineClampLimit.
    static func leafLineLimit(_ limit: Int?, properties: [IRProperty]) -> Int? {
        markerSuppressed(properties) ? nil : limit
    }

    /// One line box in pt: a DECLARED line-height wins verbatim (css-inline-3
    /// §4.2 — line-clamp-005 pins `font: 16px/32px` → 32pt boxes); otherwise
    /// the corpus-v4.1 composed default — font-size × 1.25, the REF_LINE_
    /// HEIGHT ratio shared by all four surfaces (ComponentRenderer.
    /// wptRefLineHeightRatio; Compose composedDefaultLineHeightPx with
    /// composedWpt = true — the twin ALSO pins the ratio regardless of
    /// capture mode, because a cap needs a number and the face's natural
    /// metrics are not known without a layout pass). A non-positive font
    /// size can only come from a broken wire — refuse to build a 0-height
    /// cap out of it (nil).
    static func lineBoxPx(declaredLineHeightPx: CGFloat?, fontSizePx: CGFloat) -> CGFloat? {
        // Declared px line-height → the line box, verbatim.
        if let lh = declaredLineHeightPx, lh > 0 { return lh }
        // Calibrated default: the ref's unitless 1.25 × THIS run's size.
        guard fontSizePx > 0 else { return nil }
        return fontSizePx * ComponentRenderer.wptRefLineHeightRatio
    }

    /// The uniform cap: N line boxes of one height — the Compose model,
    /// and the fallback when LineClampCensus cannot prove the children's
    /// line boxes (wrapping runs of unknown count).
    static func capPx(lines: Int, lineBoxPx: CGFloat) -> CGFloat? {
        // The grammar is <integer [1,∞]>; a 0-line cap would blank the box.
        guard lines >= 1, lineBoxPx > 0 else { return nil }
        return CGFloat(lines) * lineBoxPx
    }

    /// The pure cap decision (pinnable without a layout pass): the
    /// measured height survives untouched unless it exceeds the cap by
    /// more than [slackPx] — i.e. the content genuinely laid out extra
    /// line boxes the clamp must discard. The cap itself is ceil'd so a
    /// fractional line box (16.25pt monospace lines) never loses its last
    /// physical pixel row — byte-parallel to Compose's cappedHeight.
    static func cappedHeight(measuredPx: CGFloat, capPx: CGFloat) -> CGFloat {
        // The integer cap the box reports when clamping fires.
        let cap = ceil(capPx)
        // Within tolerance → the leaf path (or short content) already
        // satisfies the clamp: keep the measurement byte-identically.
        return measuredPx > cap + slackPx ? cap : measuredPx
    }
}
