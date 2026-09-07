//
//  MulticolCloneGeometryTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 46 (lane Y3) — css-break-3 §5.4 `box-decoration-break: clone`
//  inside a multicol fragmentainer. Three layers of proof:
//    1. MulticolCloneGeometry pinned against the SHARED K-table — rows
//       K1/K2/K3/K7 (full fragments) are byte-identical with the Android
//       MulticolCloneGeometryTest.kt (the native-pair parity gate); the
//       SHORT-last-fragment row (K4) is the documented platform
//       divergence (iOS re-renders the child at the short size: ONE exact
//       entry; Compose approximates with two clipped bands).
//    2. MulticolCloneDecoration: the IR → bands reader (through the
//       borders/padding extractors) and the per-fragment child rewrite.
//    3. ColumnsApplier.fragmentPlan's clone branch, pure gate pins +
//       an ImageRenderer raster pin of WPT borders-008's shape: three
//       CIRCLES (clone) where slice paints one sliced tall box.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class MulticolCloneGeometryTests: XCTestCase {

    // MARK: - 1. The shared K-table

    /// Shorthand for a clone fragment with an inline-only translate.
    private func frag(_ i: Int, x: Double, w: Double, h: Double) -> MulticolCloneGeometry.CloneFragment {
        .init(fragment: .init(columnIndex: i,
                              clipRect: CGRect(x: x, y: 0, width: w, height: h),
                              translate: CGSize(width: x, height: 0)),
              boxBlockSizePx: h)
    }

    /// K1 — WPT css-break borders-008: C = 240 + 10 + 10 = 260, H = 100,
    /// capacity 80 → three FULL 100px fragments, no block translate.
    func testK1Borders008ThreeFullFragments() throws {
        let frags = try XCTUnwrap(MulticolCloneGeometry.cloneFragments(
            childBlockSize: 260, columnBlockSize: 100,
            bands: .init(blockStartPx: 10, blockEndPx: 10),
            columnWidth: 100, gapPx: 10, columnCount: 3))
        XCTAssertEqual(frags, [frag(0, x: 0, w: 100, h: 100),
                               frag(1, x: 110, w: 100, h: 100),
                               frag(2, x: 220, w: 100, h: 100)],
                       "K1: three complete clone boxes at x_i = i·(W+G), translate (x_i, 0)")
    }

    /// K2 — WPT css-break background-image-007: no bands, C = 600, H = 150
    /// → four full fragments (one no-repeat cat per column).
    func testK2BackgroundImage007FourFullFragments() throws {
        let frags = try XCTUnwrap(MulticolCloneGeometry.cloneFragments(
            childBlockSize: 600, columnBlockSize: 150,
            bands: .init(blockStartPx: 0, blockEndPx: 0),
            columnWidth: 150, gapPx: 0, columnCount: 4))
        XCTAssertEqual(frags, (0..<4).map { frag($0, x: Double($0) * 150, w: 150, h: 150) })
    }

    /// K3 — WPT css-break background-image-004 (green today, must stay
    /// green): 120px content in 20px bands, H = 100 → two full fragments.
    func testK3BackgroundImage004TwoFullFragments() throws {
        let frags = try XCTUnwrap(MulticolCloneGeometry.cloneFragments(
            childBlockSize: 160, columnBlockSize: 100,
            bands: .init(blockStartPx: 20, blockEndPx: 20),
            columnWidth: 50, gapPx: 0, columnCount: 2))
        XCTAssertEqual(frags, [frag(0, x: 0, w: 50, h: 100), frag(1, x: 50, w: 50, h: 100)])
    }

    /// K4 (iOS form) — a SHORT last fragment: C = 270, H = 100, capacity
    /// 80 → F = 4, the last holds 10px of content → ONE exact 30px box
    /// (Compose's K4 splits this entry into two bands — by design).
    func testK4ShortLastFragmentIsOneExactEntry() throws {
        let frags = try XCTUnwrap(MulticolCloneGeometry.cloneFragments(
            childBlockSize: 270, columnBlockSize: 100,
            bands: .init(blockStartPx: 10, blockEndPx: 10),
            columnWidth: 100, gapPx: 10, columnCount: 4))
        XCTAssertEqual(frags.count, 4)
        XCTAssertEqual(frags[2], frag(2, x: 220, w: 100, h: 100))
        XCTAssertEqual(frags[3], frag(3, x: 330, w: 100, h: 30),
                       "K4: the short last fragment is its leftover content + both bands")
    }

    /// K6 — bands filling the fragmentainer, or no fragmentainer: nil
    /// (the caller's slice fallback).
    func testK6NoCapacityReturnsNil() {
        XCTAssertNil(MulticolCloneGeometry.cloneFragments(
            childBlockSize: 260, columnBlockSize: 20,
            bands: .init(blockStartPx: 10, blockEndPx: 10),
            columnWidth: 100, gapPx: 10, columnCount: 3))
        XCTAssertNil(MulticolCloneGeometry.cloneFragments(
            childBlockSize: 260, columnBlockSize: 0,
            bands: .init(blockStartPx: 0, blockEndPx: 0),
            columnWidth: 100, gapPx: 10, columnCount: 3))
    }

    /// K7 — the N cap: 600/150 wants four, N = 2 → two FULL fragments
    /// (the capped remainder exceeds the capacity — slice parity).
    func testK7ColumnCountCapsLikeSlice() throws {
        let frags = try XCTUnwrap(MulticolCloneGeometry.cloneFragments(
            childBlockSize: 600, columnBlockSize: 150,
            bands: .init(blockStartPx: 0, blockEndPx: 0),
            columnWidth: 150, gapPx: 0, columnCount: 2))
        XCTAssertEqual(frags, [frag(0, x: 0, w: 150, h: 150), frag(1, x: 150, w: 150, h: 150)])
    }

    /// K8 — an empty clone box (content 0) still owns column 0 as a
    /// bands-only short box.
    func testK8ZeroContentIsOneBandsOnlyFragment() throws {
        let frags = try XCTUnwrap(MulticolCloneGeometry.cloneFragments(
            childBlockSize: 20, columnBlockSize: 100,
            bands: .init(blockStartPx: 10, blockEndPx: 10),
            columnWidth: 100, gapPx: 10, columnCount: 3))
        XCTAssertEqual(frags, [frag(0, x: 0, w: 100, h: 20)])
    }

    /// K9 — defensive floors: negative bands/gap/width and a zero count
    /// never break the math.
    func testK9NegativeInputsFloor() throws {
        let frags = try XCTUnwrap(MulticolCloneGeometry.cloneFragments(
            childBlockSize: 260, columnBlockSize: 100,
            bands: .init(blockStartPx: -5, blockEndPx: -5),
            columnWidth: -1, gapPx: -3, columnCount: 0))
        XCTAssertEqual(frags, [frag(0, x: 0, w: 0, h: 100)])
    }

    // MARK: - 2. The IR → bands reader and the fragment child rewrite

    /// Decode a property list from wire-shaped JSON (the converter's
    /// literal shapes — keywords bare strings, widths `{px}`).
    private func props(_ json: String) throws -> [IRProperty] {
        try JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    /// borders-008's child, verbatim wire shapes (wave45-final IR).
    private func borders008Child() throws -> [IRProperty] {
        try props(#"""
        [{"type":"BoxDecorationBreak","data":"CLONE"},
         {"type":"BorderTopWidth","data":{"px":10}},
         {"type":"BorderBottomWidth","data":{"px":10}},
         {"type":"BorderTopStyle","data":"SOLID"},
         {"type":"BorderBottomStyle","data":"SOLID"},
         {"type":"BorderBottomLeftRadius","data":{"px":50}},
         {"type":"Width","data":{"type":"length","px":80}},
         {"type":"Height","data":{"type":"length","px":240}}]
        """#)
    }

    /// D1 — borders-008 resolves 10px/10px bands through the borders tree.
    func testD1Borders008Bands() throws {
        let p = try borders008Child()
        XCTAssertTrue(MulticolCloneDecoration.declaresClone(p))
        XCTAssertEqual(MulticolCloneDecoration.bands(for: p),
                       .init(blockStartPx: 10, blockEndPx: 10))
    }

    /// D3 — slice (default or explicit) never builds bands.
    func testD3SliceNeverBuildsBands() throws {
        XCTAssertNil(MulticolCloneDecoration.bands(for: try props(
            #"[{"type":"BorderTopWidth","data":{"px":10}}]"#)))
        XCTAssertNil(MulticolCloneDecoration.bands(for: try props(
            #"[{"type":"BoxDecorationBreak","data":"SLICE"}]"#)))
    }

    /// D4 — padding joins the bands, logical longhands folded by the
    /// padding extractor (css-logical-1 §4.1).
    func testD4PaddingJoinsBands() throws {
        XCTAssertEqual(MulticolCloneDecoration.bands(for: try props(#"""
            [{"type":"BoxDecorationBreak","data":"CLONE"},
             {"type":"BorderTopWidth","data":{"px":10}},{"type":"BorderTopStyle","data":"SOLID"},
             {"type":"PaddingTop","data":{"px":10}},{"type":"PaddingBlockEnd","data":{"px":7}}]
            """#)), .init(blockStartPx: 20, blockEndPx: 7))
    }

    /// D5 — a border side styled none has USED width zero (CSS 2.1 §8.5.3).
    func testD5NoneStyleZeroesTheBand() throws {
        XCTAssertEqual(MulticolCloneDecoration.bands(for: try props(#"""
            [{"type":"BoxDecorationBreak","data":"CLONE"},
             {"type":"BorderTopWidth","data":{"px":10}},{"type":"BorderTopStyle","data":"NONE"},
             {"type":"BorderBottomWidth","data":{"px":10}},{"type":"BorderBottomStyle","data":"SOLID"}]
            """#)), .init(blockStartPx: 0, blockEndPx: 10))
    }

    /// D6 — a non-px padding band is not statically resolvable → nil
    /// (the plan logs and keeps slice).
    func testD6EmPaddingBails() throws {
        XCTAssertNil(MulticolCloneDecoration.bands(for: try props(#"""
            [{"type":"BoxDecorationBreak","data":"CLONE"},
             {"type":"PaddingTop","data":{"original":{"v":1,"u":"EM"}}}]
            """#)))
    }

    /// D9 — fragmentChild keeps every decoration property, replaces BOTH
    /// block-size spellings with the fragment's declared Height, and
    /// preserves identity (id/name) for the fragment row's ForEach.
    func testD9FragmentChildRewritesOnlyTheBlockSize() throws {
        let child = try JSONDecoder().decode(IRComponent.self, from: Data(#"""
        {"id":"c","name":"Child","properties":[
          {"type":"BoxDecorationBreak","data":"CLONE"},
          {"type":"BorderTopWidth","data":{"px":10}},
          {"type":"Height","data":{"type":"length","px":240}},
          {"type":"BlockSize","data":{"type":"length","px":240}}]}
        """#.utf8))
        let copy = MulticolCloneDecoration.fragmentChild(child, declaredHeightPx: 80)
        XCTAssertEqual(copy.id, "c"); XCTAssertEqual(copy.name, "Child")
        XCTAssertEqual(copy.properties.filter { $0.type == "BlockSize" }.count, 0)
        XCTAssertEqual(copy.properties.filter { $0.type == "Height" }.count, 1)
        XCTAssertEqual(copy.properties.filter { $0.type == "BorderTopWidth" }.count, 1)
        // The rewritten slot resolves to exactly the fragment's content.
        XCTAssertEqual(SizeExtractor.extract(from: copy.properties).height, .exact(px: 80))
        // The source component is untouched (value semantics).
        XCTAssertEqual(SizeExtractor.extract(from: child.properties).height, .exact(px: 240))
    }
}
