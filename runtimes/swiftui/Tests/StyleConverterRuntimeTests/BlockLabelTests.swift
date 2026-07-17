//
//  BlockLabelTests.swift
//  Applier campaign — the harness-label block font.
//
//  Three layers of pinning, mirrored on all three platforms:
//    1. ATLAS CHECKSUM: recompute sha256 over the canonical serialization
//       of the EMBEDDED constants (BlockFont.gen.swift) and compare to
//       the embedded CHECKSUM. This is the cross-platform drift guard —
//       a hand-edit of any generated copy (or a regeneration that only
//       reached some platforms) fails here, not as a mystery SSIM dip.
//    2. LAYOUT MATH: transform (uppercase, unknown → '-'), truncation
//       inequality, and per-bit rect geometry (bit 4 = leftmost column).
//    3. RENDER SMOKE: the Canvas actually rasterizes under ImageRenderer
//       (the wave-3 lesson: a Canvas can silently produce an empty layer
//       under ImageRenderer, so the pixel probe is mandatory).
//

import XCTest
import SwiftUI
// CryptoKit supplies SHA256 on iOS 13+/Catalyst — no CommonCrypto shim.
import CryptoKit
@testable import StyleConverterRuntime

final class BlockLabelTests: XCTestCase {

    // MARK: - 1. Atlas checksum pin (cross-platform drift guard)

    /// Rebuild the generator's canonical serialization from the embedded
    /// constants: `charCode:rowInts,…` joined with ';' (glyphs sorted by
    /// char code), then `|<W>x<H>|adv<A>|lh<L>`, sha256 hex prefix 16
    /// (tools/visual/gen-block-font.mjs). Must equal the embedded value.
    func testAtlasChecksumMatchesEmbeddedConstants() {
        // Sort by char code — the generator sorts by charCodeAt(0); every
        // atlas char is ASCII so the first unicode scalar is identical.
        let chars = BlockFont.glyphs.keys.sorted {
            $0.unicodeScalars.first!.value < $1.unicodeScalars.first!.value
        }
        // `<code>:<row0>,<row1>,…` per glyph, ';'-joined.
        let body = chars.map { ch in
            "\(ch.unicodeScalars.first!.value):"
                + BlockFont.glyphs[ch]!.map(String.init).joined(separator: ",")
        }.joined(separator: ";")
        // Metric suffix binds cell size / advance / line-height too.
        let canonical = body
            + "|\(BlockFont.cellW)x\(BlockFont.cellH)"
            + "|adv\(BlockFont.advance)|lh\(BlockFont.lineHeight)"
        // sha256 → lowercase hex → first 16 chars, exactly like the mjs.
        let hex = SHA256.hash(data: Data(canonical.utf8))
            .map { String(format: "%02x", $0) }.joined()
        XCTAssertEqual(String(hex.prefix(16)), BlockFont.checksum,
                       "embedded atlas drifted from the generator's canonical checksum")
    }

    // MARK: - 2a. Transform

    /// Lowercase uppercases; chars with no atlas glyph become '-'.
    func testGlyphStringUppercasesAndMapsUnknowns() {
        // The standard label shape: name with underscores → spaces.
        XCTAssertEqual(BlockLabelLayout.glyphString("Card Complete"), "CARD COMPLETE")
        // 'é' uppercases to 'É' (no glyph) and '?' has no glyph → both '-';
        // digits and '%' pass through untouched.
        XCTAssertEqual(BlockLabelLayout.glyphString("é?a 10%"), "--A 10%")
    }

    // MARK: - 2b. Truncation

    /// Largest n with 8 + n*ADVANCE <= width - 8; no width = no cut.
    func testTruncationInequality() {
        // nil width (fit-content): the full run always survives.
        XCTAssertEqual(BlockLabelLayout.truncatedCount(20, componentWidth: nil), 20)
        // width 100: (100-16)/6 = 14 exactly → 14 chars fit.
        XCTAssertEqual(BlockLabelLayout.truncatedCount(20, componentWidth: 100), 14)
        // Tight boundary: width 34 → (34-16)/6 = 3 → the 3rd char's cell
        // ends at 8+18 = 26 = 34-8, satisfying the inequality with equality.
        XCTAssertEqual(BlockLabelLayout.truncatedCount(20, componentWidth: 34), 3)
        // One point narrower and the 3rd char no longer fits.
        XCTAssertEqual(BlockLabelLayout.truncatedCount(20, componentWidth: 33), 2)
        // Shorter strings never pad up to the fit count.
        XCTAssertEqual(BlockLabelLayout.truncatedCount(2, componentWidth: 100), 2)
        // Degenerate width (narrower than the two 8px margins) clamps to 0.
        XCTAssertEqual(BlockLabelLayout.truncatedCount(20, componentWidth: 10), 0)
    }

    // MARK: - 2c. Bit-rect geometry

    /// Rect COUNT = total set bits ('A' rows 14,17,17,31,17,17,17 hold
    /// 3+2+2+5+2+2+2 = 18 bits), and every rect is a 1x1 unit square.
    func testBitRectCountForKnownGlyph() {
        let rects = BlockLabelLayout.bitRects("A")
        XCTAssertEqual(rects.count, 18, "'A' must fill exactly its 18 set bits")
        // All rects are exactly one pixel — the no-antialiasing guarantee
        // rests on unit rects at integer coordinates.
        XCTAssertTrue(rects.allSatisfy { $0.width == 1 && $0.height == 1
            && $0.origin.x == $0.origin.x.rounded() && $0.origin.y == $0.origin.y.rounded() })
    }

    /// Bit 4 is the LEFTMOST column: 'L' row 0 = 16 = 0b10000 must fill
    /// column 0 (a flipped decode would put it at column 4).
    func testBitFourIsLeftmostColumn() {
        let rects = BlockLabelLayout.bitRects("L")
        // Row 0 of 'L' has exactly one set bit; find it and pin column 0.
        let row0 = rects.filter { $0.origin.y == 0 }
        XCTAssertEqual(row0.map(\.origin.x), [0], "'L' top row must fill only column 0 (bit 4 = leftmost)")
    }

    /// Character i's cell starts at x = i*ADVANCE: 'II' second glyph's
    /// rects are the first glyph's shifted right by exactly 6.
    func testAdvancePerCharacter() {
        let one = BlockLabelLayout.bitRects("I").sorted(by: rectOrder)
        let two = BlockLabelLayout.bitRects("II").sorted(by: rectOrder)
        // Two identical glyphs → twice the rects.
        XCTAssertEqual(two.count, one.count * 2)
        // The second half must equal the first half offset by the advance.
        let shifted = one.map { $0.offsetBy(dx: CGFloat(BlockFont.advance), dy: 0) }
        XCTAssertEqual(Array(two.suffix(one.count)), shifted)
    }

    /// Deterministic sort for rect-set comparison (order is irrelevant to
    /// rendering; the tests canonicalize it to compare sets exactly).
    private func rectOrder(_ a: CGRect, _ b: CGRect) -> Bool {
        (a.origin.x, a.origin.y) < (b.origin.x, b.origin.y)
    }

    // MARK: - 3. ImageRenderer smoke (Canvas actually rasterizes)

    /// Alpha of the buffer pixel at (x, y) — adapted from
    /// MaskURLRenderTests.alphaAt (the wave-3 Canvas-under-ImageRenderer
    /// diagnostic pattern).
    private func alphaAt(_ cg: CGImage, _ x: Int, _ y: Int) -> CGFloat {
        guard let data = cg.dataProvider?.data as Data? else { return -1 }
        let bpr = cg.bytesPerRow, bpp = cg.bitsPerPixel / 8
        // Alpha byte position depends on the renderer's pixel format.
        let alphaFirst = cg.alphaInfo == .premultipliedFirst || cg.alphaInfo == .first
        let idx = y * bpr + x * bpp + (alphaFirst ? 0 : 3)
        guard idx < data.count else { return -1 }
        return CGFloat(data[idx]) / 255
    }

    /// Render 'L' at scale 1 and probe pixels: the set bit at component
    /// (8, 6) must carry the ~0.7 label alpha; the clear bit right of it
    /// must be fully transparent. Fails if the Canvas hands ImageRenderer
    /// an empty layer (the wave-3 failure mode) or the origin drifts.
    @MainActor
    func testCanvasRendersSetAndClearBitsUnderImageRenderer() throws {
        // topLeading frame pins the label's (8,6) origin to the buffer's
        // (8,6); scale 1 = one buffer pixel per logical point.
        let view = BlockLabel(label: "L", componentWidth: nil)
            .frame(width: 40, height: 30, alignment: .topLeading)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        // 'L' row 0 = 0b10000: column 0 set → (8, 6) is a set bit. The
        // fill alpha is 179/255 ≈ 0.70, so "opaque-ish" = well above 0.5.
        XCTAssertGreaterThan(alphaAt(cg, 8, 6), 0.5,
                             "set bit at (8,6) should carry the label alpha — Canvas drew nothing?")
        // Column 1 of row 0 is clear → (9, 6) must stay transparent.
        XCTAssertLessThan(alphaAt(cg, 9, 6), 0.1,
                          "clear bit at (9,6) must be transparent — glyph shifted or smeared?")
        // 'L' row 6 = 0b11111: column 4 set → (8+4, 6+6) = (12, 12).
        XCTAssertGreaterThan(alphaAt(cg, 12, 12), 0.5,
                             "bottom-row set bit at (12,12) should be filled")
        // One pixel below the cell (row 7 does not exist) must be clear —
        // pins the vertical origin at exactly y = 6.
        XCTAssertLessThan(alphaAt(cg, 8, 13), 0.1,
                          "pixel below the 7-row cell must be transparent")
    }
}
