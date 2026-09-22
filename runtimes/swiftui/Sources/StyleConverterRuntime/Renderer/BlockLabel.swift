//
//  BlockLabel.swift
//  Renderer — the harness-label block-font canvas (applier campaign;
//  wave 51 PR (A): drawn as HARNESS CHROME, never by the runtime).
//
//  A deterministic 5x7 block-font raster: every glyph is a fixed bit grid
//  drawn as 1x1 integer-coordinate rect fills, so the label rasterizes
//  PIXEL-IDENTICALLY on all three platforms (real font stacks antialias
//  differently per platform and capped ~50 label-bearing fixtures at SSIM
//  0.90-0.949 however well the boxes matched). Real element text (the IR
//  `text` channel) is NOT routed here: it keeps PlaceholderLabel's full
//  typography path — fonts are the thing under test there.
//
//  WHO DRAWS IT (wave 51 PR (A); normative home: docs/DYNAMIC_CAPTURE.md,
//  section "Harness label chrome"): the capture HARNESS, as a sibling
//  overlay over the whole capture root — HarnessLabelChrome.swift, mounted
//  by apps/ios-harness/…/Screenshot/CaptureCanvas.swift right after the
//  root's `.background(…)`. Until (A) ComponentRenderer's leaf branch drew
//  this view INSIDE the styled element, so the ink rode the element's blend
//  group / opacity / filter / clip / transform and sat at the content-box
//  origin after the element's own layout — the platforms could not agree
//  on effect-bearing fixtures. The runtime now paints NO name label.
//
//  Geometry contract (shared verbatim with Compose's BlockLabel and web's
//  BlockFontLabel.ts — all three MUST produce byte-identical output):
//    * input = the PLAIN component name, underscores → spaces; transform
//      = uppercase, unknown glyph → '-'
//    * single line at integer origin (8, 6) in the CAPTURE FRAME; char i's
//      cell starts at x = 8 + i*ADVANCE. Rows 6..12 lie inside the 16px
//      top pad band, so on an in-flow root with non-negative margin-top
//      the ink never overlaps the border box (frame y >= 16).
//    * truncation against the FRAME width: largest n with
//      8 + n*ADVANCE <= frameWidth - 8 → 62 glyphs at 390, 39 at 250,
//      0 below 22 px (zero glyphs, logged once).
//    * color = rgba(237,237,237, 179/255); the CONTRACT is the COMPOSITED
//      byte, exactly (174,174,180) over #1A1A2E on all three (labelColor)
//    * NO antialiasing: integer coordinates only.
//
//  Glyph data comes from BlockFont.gen.swift (GENERATED — checksum
//  cb3c6e411c7b2859, pinned by BlockLabelTests against the canonical
//  serialization so the three embedded copies can never drift apart).
//

// SwiftUI for Canvas/Path/Color; CoreGraphics types (CGRect) ride along.
import SwiftUI

/// Pure layout math for the block label, split from the View so XCTest
/// pins transform/truncation/bit-geometry without a render surface (the
/// same pattern as StyleBuilder.minFloor / suppressesNamePlaceholder).
// public: the label contract's harness-visible geometry (design C23).
public enum BlockLabelLayout {

    /// Fixed label origin in the capture frame: 8px from the left edge.
    /// Part of the shared spec — web/Compose place the run at the same x.
    public static let originX: CGFloat = 8
    /// Fixed label origin in the capture frame: 6px from the top edge —
    /// inside the canvas's 16px top pad band, clear of the border box.
    public static let originY: CGFloat = 6
    /// Symmetric right margin: the truncation bound keeps the glyph run
    /// out of the last 8px of the frame (mirror of originX).
    public static let rightMargin: CGFloat = 8

    /// The label transform: uppercase the WHOLE string first (Swift's
    /// ICU-backed `uppercased()` matches JS `toUpperCase()` / Kotlin
    /// `uppercase()` for the ASCII names the harness emits), then map every
    /// unicode SCALAR the atlas has no glyph for to '-' — a visible stand-in,
    /// never a silent drop. Scalars, NOT `Character`s: web/tooling walk code
    /// points (`Array.from`) and Kotlin walks chars, so e + U+0301 must give
    /// TWO glyphs ('E','-') here too, not one grapheme '-' (BlockLabelTests).
    public static func glyphString(_ label: String) -> String {
        // Per-scalar atlas lookup AFTER the whole-string uppercase; every
        // atlas key is one ASCII scalar, so Character(s) is the exact key,
        // and '-' is guaranteed present in the atlas (BlockFont.gen.swift).
        return String(label.uppercased().unicodeScalars.map { s in
            BlockFont.glyphs[Character(s)] != nil ? Character(s) : "-"
        })
    }

    /// How many leading characters fit: largest n with
    /// 8 + n*ADVANCE <= width - 8 (no ellipsis — trailing chars just
    /// drop, per the shared spec). `componentWidth` is the FRAME width
    /// under PR (A) (the parameter keeps its historical name so the
    /// cross-platform math reads the same); nil = no bound, so the full
    /// run always fits (unit tests, fit-content callers).
    public static func truncatedCount(_ glyphCount: Int, componentWidth: CGFloat?) -> Int {
        // No statically-known width → no truncation by construction.
        guard let w = componentWidth else { return glyphCount }
        // n <= (W - leftInset - rightMargin) / ADVANCE, floored to an
        // integer character count and clamped to [0, glyphCount].
        let fit = Int(((w - originX - rightMargin) / CGFloat(BlockFont.advance)).rounded(.down))
        return max(0, min(glyphCount, fit))
    }

    /// One 1x1 rect per SET bit, in CANVAS-LOCAL coordinates (the canvas
    /// itself is offset to (originX, originY) by the view below, which
    /// puts char i's cell at frame-space x = 8 + i*ADVANCE, y = 6 exactly
    /// as the shared spec requires). Row 0 is the top row; bit 4 of each
    /// row int is the LEFTMOST of the 5 columns (generator contract,
    /// BlockFont.gen.swift header).
    public static func bitRects(_ glyphs: String) -> [CGRect] {
        // Flat rect list — order is irrelevant (all fills are the same
        // color), only the covered pixel set matters.
        var rects: [CGRect] = []
        for (i, ch) in glyphs.enumerated() {
            // Post-transform every char has a glyph; the guard is an
            // honest no-silent-fallthrough for direct (test) callers
            // that skip glyphString — unknown chars draw nothing.
            guard let rows = BlockFont.glyphs[ch] else { continue }
            // Cell origin advances one fixed-width cell per character.
            let cellX = CGFloat(i * BlockFont.advance)
            for (row, bits) in rows.enumerated() {
                // Column c reads bit (cellW-1-c): bit 4 = leftmost.
                for c in 0..<BlockFont.cellW where (bits >> (BlockFont.cellW - 1 - c)) & 1 == 1 {
                    // Integer 1x1 rect = exactly one full pixel at 1x —
                    // full coverage, so no antialiased edge can appear.
                    rects.append(CGRect(x: cellX + CGFloat(c), y: CGFloat(row),
                                        width: 1, height: 1))
                }
            }
        }
        return rects
    }
}

/// The block-font label view: a Canvas of 1x1 rect fills that collapses
/// to a 0×0 layout slot and hangs its ink at (8, 6) from that slot's
/// top-leading corner. Hosted ONLY by HarnessLabelChrome inside the
/// harness's `.overlay(alignment: .topLeading)` over the capture root, so
/// the slot's corner IS the frame origin and the ink lands at frame (8, 6).
// public: a public struct's synthesized memberwise init is INTERNAL, so
// the init below is spelled out (design C23; the ComponentHost precedent).
public struct BlockLabel: View {

    /// The PLAIN component name with underscores already replaced by
    /// spaces (HarnessLabelChrome.labelText).
    public let label: String

    /// The width the run truncates against — the capture FRAME width
    /// (CaptureCanvas.width) under PR (A). nil = no bound (unit tests).
    public let componentWidth: CGFloat?

    // public: explicit memberwise init for cross-module callers (the
    // harness app's plain `import StyleConverterRuntime`).
    public init(label: String, componentWidth: CGFloat?) {
        self.label = label
        self.componentWidth = componentWidth
    }

    /// rgba(237,237,237, 179/255). The CONTRACT is the COMPOSITED byte: ink
    /// over the #1A1A2E stage reads exactly (174,174,180) on all three
    /// platforms (design-record §2, docs/DYNAMIC_CAPTURE.md §5). 179/255 is
    /// spelled as the byte for cross-platform readability (Compose packs
    /// 0xB3EDEDED, 0xB3 = 179); measured on the Catalyst raster, 0.7
    /// composites to the same byte (skeptic-ios.md M-α), so it is not a
    /// rounding fix. Web spells it 0.706 (byte 180): Chromium rounds CSS
    /// alpha to 8 bits first and byte 179 lands one LSB dark (173,173,179).
    static let labelColor = Color(red: 237.0 / 255.0,
                                  green: 237.0 / 255.0,
                                  blue: 237.0 / 255.0,
                                  opacity: 179.0 / 255.0)

    /// The glyph run that actually draws: transform (uppercase, unknown →
    /// '-') then truncation against `width` (shared-spec formula). Also
    /// the ONE place the zero-glyph case is reported — a frame narrower
    /// than 22 px drops every glyph (design C5: zero glyphs, no crash; the
    /// Android twin logs the same case). Logged once per label so a blank
    /// band is explainable; a statement, so it lives outside the ViewBuilder.
    static func shownGlyphs(label: String, width: CGFloat?) -> String {
        let glyphs = BlockLabelLayout.glyphString(label)
        let count = BlockLabelLayout.truncatedCount(glyphs.count, componentWidth: width)
        if count == 0 && !glyphs.isEmpty {
            PropertyTracker.logOnce(
                key: "blocklabel.zero-glyphs.\(label)",
                message: "[BlockLabel] frame \(width.map { "\($0)" } ?? "nil")px "
                    + "too narrow for any glyph of \"\(label)\" — label dropped")
        }
        return String(glyphs.prefix(count))
    }

    // public: View protocol witness on a public type must be public.
    public var body: some View {
        // Transform + truncate once per render (pure apart from the
        // once-only zero-glyph log above).
        let shown = Self.shownGlyphs(label: label, width: componentWidth)
        // Precompute the pixel set outside the draw closure — the draw
        // closure then only replays fills (cheap + deterministic).
        let rects = BlockLabelLayout.bitRects(shown)
        // rendersAsynchronously: false — the harness captures via
        // ImageRenderer immediately after layout; async rasterization
        // could hand the capture an empty layer (wave-3 Canvas lesson).
        Canvas(rendersAsynchronously: false) { context, _ in
            // One fill per set bit, exactly as the shared spec words it.
            // Integer-aligned unit rects have full pixel coverage, so
            // the default antialiasing state can never soften an edge.
            for r in rects {
                context.fill(Path(r), with: .color(Self.labelColor))
            }
        }
        // ZERO LAYOUT FOOTPRINT: the label is chrome, not content — it
        // must never contribute to the capture's size. The canvas spans
        // the truncated run × LINE_HEIGHT …
        .frame(width: CGFloat(shown.count * BlockFont.advance),
               height: CGFloat(BlockFont.lineHeight))
        // … then the layout slot collapses to 0×0 and the (already-framed)
        // canvas hangs off its top-leading corner at the spec origin.
        // `overlay` content never influences the host's size (the wave-3
        // CaptureCanvas lesson), and Canvas draws are not clipped by the
        // zero frame — so under the harness overlay this is frame (8, 6).
        .offset(x: BlockLabelLayout.originX, y: BlockLabelLayout.originY)
        .frame(width: 0, height: 0, alignment: .topLeading)
    }
}
