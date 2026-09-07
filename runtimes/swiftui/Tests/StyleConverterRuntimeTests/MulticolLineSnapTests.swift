//
//  MulticolLineSnapTests.swift
//  Wave-42 lane W4 — the SHARED line-snap pin table.
//
//  Pins MulticolLineSnap exactly; the Android twin
//  (runtimes/compose .../columns/MulticolLineSnapTest.kt) carries the SAME
//  LS rows byte-for-byte (native-pair parity gate).
//
//  LS1 is pinned against the LIVE wave41-final Android capture geometry of
//  css-overflow discard-multicol-001: the container resolved `height: 2lh`
//  to ~29px while the text renders ~17px lines — the raw −i·H slice put
//  column 2's band mid-glyph 5px down and column 3's 10px down. The snap
//  emits whole-line bands instead (class-B breakpoints, css-break-3 §4).
//

import XCTest
@testable import StyleConverterRuntime

final class MulticolLineSnapTests: XCTestCase {

    /// LS1 — discard-multicol-001 shape: 7×17px lines, H=29 → 1 line/column, 3 columns.
    func testLS1SevenLinesSnapToWholeLineColumns() {
        let frags = MulticolLineSnap.snappedFragments(
            childBlockSize: 119, columnBlockSize: 29, lineBox: 17,
            columnWidth: 66, gapPx: 7, columnCount: 3)
        XCTAssertEqual(frags, [
            .init(columnIndex: 0, clipRect: CGRect(x: 0, y: 0, width: 66, height: 17),
                  translate: CGSize(width: 0, height: 0)),
            .init(columnIndex: 1, clipRect: CGRect(x: 73, y: 0, width: 66, height: 17),
                  translate: CGSize(width: 73, height: -17)),
            .init(columnIndex: 2, clipRect: CGRect(x: 146, y: 0, width: 66, height: 17),
                  translate: CGSize(width: 146, height: -34)),
        ])
    }

    /// LS2 — H an exact line multiple: the snap coincides with the raw slice.
    func testLS2LineMultipleColumnHeightAgreesWithRawSlice() {
        // C=96 (6·16), H=32 (2 lines exactly) → k=2, band=32, translate
        // −i·32 — byte-identical to the raw −i·H geometry, so fixing the
        // lh resolution upstream makes the snap a no-op relative to raw.
        let snapped = MulticolLineSnap.snappedFragments(
            childBlockSize: 96, columnBlockSize: 32, lineBox: 16,
            columnWidth: 60, gapPx: 4, columnCount: 3)
        let raw = FragmentGeometry.fragments(
            childBlockSize: 96, columnBlockSize: 32,
            columnWidth: 60, gapPx: 4, columnCount: 3)
        XCTAssertEqual(snapped, raw)
    }

    /// LS3 — a "line" taller than the column is a block child, not a line stack.
    func testLS3LineProbeTallerThanColumnDeclines() {
        // The floats-clear .container shape: the probe answers its 250px
        // float stack, H=100 → not a line stack → raw slice.
        XCTAssertNil(MulticolLineSnap.snappedFragments(
            childBlockSize: 500, columnBlockSize: 100, lineBox: 250,
            columnWidth: 100, gapPx: 0, columnCount: 3))
    }

    /// LS4 — no probe answer (dark stage / refused channel) declines.
    func testLS4NilLineProbeDeclines() {
        XCTAssertNil(MulticolLineSnap.snappedFragments(
            childBlockSize: 119, columnBlockSize: 29, lineBox: nil,
            columnWidth: 66, gapPx: 7, columnCount: 3))
    }

    /// LS5 — a single line can never tear mid-line: decline (raw identity owns it).
    func testLS5SingleLineChildDeclines() {
        // C=17 = one 17px line → n=1 < 2 → nil.
        XCTAssertNil(MulticolLineSnap.snappedFragments(
            childBlockSize: 17, columnBlockSize: 29, lineBox: 17,
            columnWidth: 66, gapPx: 7, columnCount: 3))
    }

    /// LS6 — C off the line grid beyond ±1px/line is mixed content: decline.
    func testLS6NonLineGridChildHeightDeclines() {
        // C=110 vs n=6 lines of 17 = 102: |110−102| = 8 > n=6 → nil.
        XCTAssertNil(MulticolLineSnap.snappedFragments(
            childBlockSize: 110, columnBlockSize: 29, lineBox: 17,
            columnWidth: 66, gapPx: 7, columnCount: 3))
    }

    /// LS7 — the overflow cap: more line chunks than columns clip at N.
    func testLS7FragmentCountCapsAtColumnCount() {
        // 10 lines of 10px, H=15 → k=1 → 10 chunks, capped at N=3 (the
        // column-fill:auto overflow clip — for `continue: discard`
        // containers this cap IS the discard, css-overflow-4 §5.3).
        XCTAssertEqual(MulticolLineSnap.snappedFragments(
            childBlockSize: 100, columnBlockSize: 15, lineBox: 10,
            columnWidth: 50, gapPx: 5, columnCount: 3)?.count, 3)
    }

    /// LS8 — the k-line band leaves the H − k·L remainder empty.
    func testLS8BandHeightIsWholeLineExtent() {
        // H=40, L=16 → k=2 lines, band=32: the trailing 8px stay empty
        // because the third line moved WHOLE to the next column.
        let frags = MulticolLineSnap.snappedFragments(
            childBlockSize: 96, columnBlockSize: 40, lineBox: 16,
            columnWidth: 60, gapPx: 0, columnCount: 3)
        XCTAssertEqual(frags?[0].clipRect.height, 32)
        XCTAssertEqual(frags?[1].translate.height, -32)
        // 6 lines at 2 per column fill exactly 3 columns.
        XCTAssertEqual(frags?.count, 3)
    }

    /// LS9 — ±1px/line rounding tolerance accepts real platform line grids.
    func testLS9OnePixelPerLineRoundingStillSnaps() {
        // 5 lines whose true pitch rounds unevenly: C=87 vs 5·17=85 →
        // |87−85| = 2 ≤ n=5 → snaps, k=1, F=min(3,5)=3.
        XCTAssertEqual(MulticolLineSnap.snappedFragments(
            childBlockSize: 87, columnBlockSize: 29, lineBox: 17,
            columnWidth: 66, gapPx: 7, columnCount: 3)?.count, 3)
    }
}
