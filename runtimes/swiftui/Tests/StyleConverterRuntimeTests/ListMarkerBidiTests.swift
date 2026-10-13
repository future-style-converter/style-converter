//
//  ListMarkerBidiTests.swift
//  StyleConverterRuntimeTests — wave 55, lane L5 (U1-ios).
//
//  Pins ListMarkerBidi: the `::marker` is an isolate whose base direction
//  is the ITEM's (css-lists-3 §3.1.1, css-writing-modes-4 §2.2), so a
//  marker whose first strong character disagrees with the item is wrapped
//  in LRI/RLI … PDI and every other marker is returned byte-identical.
//
//  The defect (wave54-final counter-suffix ios rows 3–4): `א.` painted as
//  `.א` because SwiftUI's Text takes the first strong character's
//  direction (UAX #9 P2/P3). The raster pins below render the VERBATIM
//  baked marker string of `counter-suffix__0__1__0` (`meta.markerText`
//  "א." in tools/titan/runs/wave54-final/sections/css-counter-styles/
//  per-test-ir/wpt__css-counter-styles__counter-suffix.json) through the
//  same `ScriptFallbackFonts.annotate` call the three marker placements
//  use, and read which side of the letter the period's ink lands on.
//
//  Mutations EXECUTED by the lane (bracketed in tools/titan/results/
//  wave55-counter-suffix-rows/_note.md): `painted` returning its input
//  turns the first pure pin and the painted raster pin red; dropping the
//  conditional (always wrap) turns the identity pins red.
//

import XCTest
// SwiftUI for the raster pins (Text / ImageRenderer).
import SwiftUI
@testable import StyleConverterRuntime

final class ListMarkerBidiTests: XCTestCase {

    // MARK: - painted: the conditional isolate (pure)

    /// The defect's string under an ltr item: natural R ≠ item ltr → LRI…PDI.
    func testHebrewMarkerUnderAnLtrItemIsLtrIsolated() {
        XCTAssertEqual(ListMarkerBidi.painted("א.", itemIsRTL: false), "\u{2066}א.\u{2069}")
    }

    /// Natural direction already the item's → the identity, byte-for-byte.
    func testMarkersWhoseNaturalDirectionMatchesAreUnchanged() {
        // Neutral-only (no strong char → P3 ltr) under an ltr item.
        XCTAssertEqual(ListMarkerBidi.painted("1.", itemIsRTL: false), "1.")
        // Hangul is strong L.
        XCTAssertEqual(ListMarkerBidi.painted("일,", itemIsRTL: false), "일,")
        // Han is strong L; the U+3001 suffix is neutral.
        XCTAssertEqual(ListMarkerBidi.painted("一、", itemIsRTL: false), "一、")
        // A strong-R marker under an rtl item: P2 already picks rtl.
        XCTAssertEqual(ListMarkerBidi.painted("א.", itemIsRTL: true), "א.")
    }

    /// The disclosure-styles reach: a neutral bullet under an rtl item.
    func testNeutralBulletUnderAnRtlItemIsRtlIsolated() {
        XCTAssertEqual(ListMarkerBidi.painted("•", itemIsRTL: true), "\u{2067}•\u{2069}")
    }

    /// No marker box, nothing to isolate (css-lists-3 §3.1).
    func testEmptyMarkerStaysEmpty() {
        XCTAssertEqual(ListMarkerBidi.painted("", itemIsRTL: true), "")
    }

    /// Wrapping twice is wrapping once: P2 skips the isolate it added.
    func testPaintedIsIdempotent() {
        let once = ListMarkerBidi.painted("א.", itemIsRTL: false)
        XCTAssertEqual(ListMarkerBidi.painted(once, itemIsRTL: false), once)
    }

    // MARK: - naturalIsRTL: UAX #9 P2/P3 approximation (pure)

    func testNaturalDirectionFollowsTheFirstStrongCharacter() {
        // Hebrew letter first → R.
        XCTAssertTrue(ListMarkerBidi.naturalIsRTL("א."))
        // Arabic-indic digit is AN, not strong → P3 ltr.
        XCTAssertFalse(ListMarkerBidi.naturalIsRTL("١."))
        // An Arabic letter is AL → R side.
        XCTAssertTrue(ListMarkerBidi.naturalIsRTL("ب."))
        // Latin first strong → L even with a later Hebrew letter.
        XCTAssertFalse(ListMarkerBidi.naturalIsRTL("a א"))
        // RLM is strong R; LRM strong L (UAX #9 Table 4).
        XCTAssertTrue(ListMarkerBidi.naturalIsRTL("\u{200F}1."))
        XCTAssertFalse(ListMarkerBidi.naturalIsRTL("\u{200E}א."))
        // Isolate content is skipped (P2): only the trailing L counts.
        XCTAssertFalse(ListMarkerBidi.naturalIsRTL("\u{2067}א\u{2069}a"))
        // Nothing strong at all → ltr.
        XCTAssertFalse(ListMarkerBidi.naturalIsRTL("•"))
    }

    // MARK: - itemIsRTL: own wins over inherited

    func testItemDirectionIsOwnElseInherited() {
        // The wire keyword is the SHOUTY enum ("RTL" / "LTR").
        let rtl = IRProperty(type: "Direction", data: .string("RTL"))
        let ltr = IRProperty(type: "Direction", data: .string("LTR"))
        // Inherited rtl reaches an item that declares nothing.
        XCTAssertTrue(ListMarkerBidi.itemIsRTL(own: [], inherited: [rtl]))
        // The item's own ltr blocks the inherited rtl.
        XCTAssertFalse(ListMarkerBidi.itemIsRTL(own: [ltr], inherited: [rtl]))
        // Nothing declared anywhere → the initial ltr.
        XCTAssertFalse(ListMarkerBidi.itemIsRTL(own: [], inherited: []))
    }

    // MARK: - raster: which side of the letter the period paints

    /// Ink column groups (gap ≥ 2 px splits) of a one-line Text, plus the
    /// rows of the inked bbox — rendered white-on-nothing at scale 1.
    @MainActor
    private func inkGroups(_ s: String) throws -> (groups: [(lo: Int, hi: Int, ink: Int)], rows: ClosedRange<Int>) {
        // The production call: per-script runs over the painted string.
        let text = Text(ScriptFallbackFonts.annotate(s, enabled: true, size: 32))
            .font(.system(size: 32))
            .foregroundColor(.black)
            .fixedSize()
            .padding(8)
            .background(Color.white)
        // Scale 1 so a column is a pixel.
        let renderer = ImageRenderer(content: text)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        // Draw into a known RGBA buffer.
        var buf = [UInt8](repeating: 0, count: cg.width * cg.height * 4)
        let ctx = try XCTUnwrap(CGContext(
            data: &buf, width: cg.width, height: cg.height, bitsPerComponent: 8,
            bytesPerRow: cg.width * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: cg.width, height: cg.height))
        // Per column ink counts; per row presence for the bbox rows.
        var col = [Int](repeating: 0, count: cg.width)
        var inkRows: [Int] = []
        for y in 0..<cg.height {
            var rowInk = false
            for x in 0..<cg.width {
                let i = (y * cg.width + x) * 4
                // Dark = ink (the Text is black on white).
                if buf[i] < 128 && buf[i + 1] < 128 && buf[i + 2] < 128 { col[x] += 1; rowInk = true }
            }
            if rowInk { inkRows.append(y) }
        }
        // Group inked columns; a ≥ 2 px blank run separates two glyphs.
        var groups: [(lo: Int, hi: Int, ink: Int)] = []
        for x in 0..<cg.width where col[x] > 0 {
            // A column within 1 blank px of the open group extends it.
            if let last = groups.last, x - last.hi <= 2 {
                groups[groups.count - 1] = (last.lo, x, last.ink + col[x])
            } else {
                // Otherwise it opens a new glyph group.
                groups.append((x, x, col[x]))
            }
        }
        let rows = try XCTUnwrap(inkRows.first.map { $0...inkRows.last! }, "no ink")
        return (groups, rows)
    }

    /// The period is the SMALLEST ink group, the letter the largest.
    @MainActor
    private func periodIsAfterLetter(_ s: String) throws -> Bool {
        let g = try inkGroups(s).groups
        print("ListMarkerBidiTests ink groups for \(s.unicodeScalars.map { String($0.value, radix: 16) }): \(g)")
        XCTAssertEqual(g.count, 2, "expected letter + period groups")
        let period = try XCTUnwrap(g.min { $0.ink < $1.ink })
        let letter = try XCTUnwrap(g.max { $0.ink < $1.ink })
        return period.lo > letter.hi
    }

    /// Documents the defect: the bare string paints `.א` (period first).
    @MainActor
    func testBareHebrewMarkerPaintsThePeriodBeforeTheLetter() throws {
        XCTAssertFalse(try periodIsAfterLetter("א."))
    }

    /// The repair: the painted string paints `א.` (period after) — the ref.
    @MainActor
    func testPaintedHebrewMarkerPaintsThePeriodAfterTheLetter() throws {
        XCTAssertTrue(try periodIsAfterLetter(ListMarkerBidi.painted("א.", itemIsRTL: false)))
    }

    /// R1 proxy (device-free): the isolate controls must not move the ink
    /// vertically (a fallback face for U+2066/2069 would grow the line box
    /// and drop the baseline — the probe's `H-low` picture).
    @MainActor
    func testIsolateControlsDoNotMoveTheMarkerInkRows() throws {
        let bare = try inkGroups("א.").rows
        let painted = try inkGroups(ListMarkerBidi.painted("א.", itemIsRTL: false)).rows
        XCTAssertEqual(painted, bare, "isolate moved the marker ink rows")
    }
}
