//
//  BlockLabel.swift
//  Renderer — the harness-label block-font canvas (applier campaign).
//
//  Replaces the SYNTHESIZED component-name placeholder's `Text` with a
//  deterministic 5x7 block-font raster: every glyph is a fixed bit grid
//  drawn as 1x1 integer-coordinate rect fills, so the label rasterizes
//  PIXEL-IDENTICALLY on all three platforms. This is the fix for the
//  cross-platform glyph wall — real font stacks antialias differently on
//  web/Android/iOS and capped ~50 label-bearing fixtures at SSIM
//  0.90-0.949 no matter how well the boxes matched. Real element text
//  (the IR `text` channel) is NOT routed here: it keeps the full
//  PlaceholderLabel typography path (fonts are the thing under test there).
//
//  Geometry contract (shared verbatim with Compose's BlockLabel and web's
//  renderer — all three MUST produce byte-identical output):
//    * input = the label string the platform showed before (name with
//      underscores → spaces); transform = uppercase, unknown glyph → '-'
//    * single line at integer origin (8, 6) from the component top-left
//      (the same slot the placeholder Text occupied); char i's cell
//      starts at x = 8 + i*ADVANCE
//    * truncation drops trailing chars so 8 + n*ADVANCE <= width - 8
//    * color = rgba(237,237,237,0.7) — the legacy default label color
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
enum BlockLabelLayout {

    /// Fixed label origin inside the component: 8px from the left edge.
    /// Part of the shared spec — web/Compose place the run at the same x.
    static let originX: CGFloat = 8
    /// Fixed label origin inside the component: 6px from the top edge.
    static let originY: CGFloat = 6
    /// Symmetric right margin: the truncation bound keeps the glyph run
    /// out of the last 8px of the component (mirror of originX).
    static let rightMargin: CGFloat = 8

    /// The label transform: uppercase the WHOLE string first (Swift's
    /// ICU-backed `uppercased()` matches JS `toUpperCase()` / Kotlin
    /// `uppercase()` for the ASCII names the harness emits, so all three
    /// platforms agree on the post-transform character count), then map
    /// every character the atlas has no glyph for to '-' — a visible,
    /// deterministic stand-in instead of a silent drop.
    static func glyphString(_ label: String) -> String {
        // Per-character atlas lookup AFTER the whole-string uppercase;
        // '-' is guaranteed present in the atlas (BlockFont.gen.swift).
        return String(label.uppercased().map { ch in
            BlockFont.glyphs[ch] != nil ? ch : "-"
        })
    }

    /// How many leading characters fit: largest n with
    /// 8 + n*ADVANCE <= componentWidth - 8 (no ellipsis — trailing chars
    /// just drop, per the shared spec). nil width = fit-content: the box
    /// grows around the label, so the full run always fits.
    static func truncatedCount(_ glyphCount: Int, componentWidth: CGFloat?) -> Int {
        // No statically-known width → no truncation by construction.
        guard let w = componentWidth else { return glyphCount }
        // n <= (W - leftInset - rightMargin) / ADVANCE, floored to an
        // integer character count and clamped to [0, glyphCount].
        let fit = Int(((w - originX - rightMargin) / CGFloat(BlockFont.advance)).rounded(.down))
        return max(0, min(glyphCount, fit))
    }

    /// One 1x1 rect per SET bit, in CANVAS-LOCAL coordinates (the canvas
    /// itself is offset to (originX, originY) by the view below, which
    /// puts char i's cell at component-space x = 8 + i*ADVANCE, y = 6
    /// exactly as the shared spec requires). Row 0 is the top row; bit 4
    /// of each row int is the LEFTMOST of the 5 columns (generator
    /// contract, BlockFont.gen.swift header).
    static func bitRects(_ glyphs: String) -> [CGRect] {
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

/// The block-font label view: a Canvas of 1x1 rect fills sitting in the
/// exact layout slot the placeholder `Text` occupied (leaf branch of
/// ComponentRenderer.contentOrPlaceholder). Frame = truncated run width
/// x LINE_HEIGHT; the (8, 6) origin is applied as top/leading padding so
/// the component's intrinsic (fit-content) size still hugs the label.
struct BlockLabel: View {

    /// The label string exactly as the old placeholder Text showed it
    /// (component name, underscores already replaced by spaces) — the
    /// spec's "do not change what is labeled".
    let label: String

    /// The component's resolved CSS width in px, when statically known
    /// (explicit/percent/stretch-injected width) — drives truncation.
    /// nil = fit-content: the box hugs the label, nothing to truncate.
    let componentWidth: CGFloat?

    /// rgba(237, 237, 237, 0.7) — the legacy default placeholder color
    /// (Color(white: 0.93) == 237/255). Alpha is 179/255, NOT 0.7:
    /// Compose stores the color as packed ARGB 0xB3EDEDED where the
    /// alpha byte is round(0.7 * 255) = 179; writing 0.7 here would
    /// hand SwiftUI 178.5/255 and leave the final byte to per-platform
    /// rounding. 179/255 pins the exact same blended bytes as Android.
    static let labelColor = Color(red: 237.0 / 255.0,
                                  green: 237.0 / 255.0,
                                  blue: 237.0 / 255.0,
                                  opacity: 179.0 / 255.0)

    var body: some View {
        // Transform once per render: uppercase + unknown→'-' (pure).
        let glyphs = BlockLabelLayout.glyphString(label)
        // Truncate against the component width (shared-spec formula).
        let shown = String(glyphs.prefix(
            BlockLabelLayout.truncatedCount(glyphs.count,
                                            componentWidth: componentWidth)))
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
        // ZERO LAYOUT FOOTPRINT: the label is dev chrome, not content — it
        // must not contribute to the component's auto/fit-content size.
        // The first block-font cut framed the canvas in-flow with padding,
        // and because the three platforms' OLD text line boxes all differed
        // (~19px web vs ~13px natives), auto-height components rendered
        // different canvas heights per platform and every pixel below the
        // label shifted (X-web pairs cratered while iOS-Android agreed at
        // 1.000). All three platforms now report ZERO label size and draw
        // the ink as out-of-flow overlay at the shared (8, 6) origin.
        // The canvas itself still spans the truncated run; the zero frame +
        // topLeading overlay keeps it anchored without occupying space.
        .frame(width: CGFloat(shown.count * BlockFont.advance),
               height: CGFloat(BlockFont.lineHeight))
        // Out-of-flow: collapse the label's layout slot to 0x0 and hang
        // the (already-framed) canvas off its top-leading corner at the
        // spec origin. `overlay` content never influences the host's size
        // (the wave-3 CaptureCanvas lesson), and Canvas draws are not
        // clipped by the zero frame.
        .offset(x: BlockLabelLayout.originX, y: BlockLabelLayout.originY)
        .frame(width: 0, height: 0, alignment: .topLeading)
    }
}
