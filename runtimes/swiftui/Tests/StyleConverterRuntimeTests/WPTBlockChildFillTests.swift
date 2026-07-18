//
//  WPTBlockChildFillTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 9 — pins the WPT block-CHILD auto-width fill
//  (ComponentRenderer.wptChildFillWidth): CSS 2.1 §10.3.3's width:auto
//  block fill was ROOT-scoped in the Round-4 composed-WPT channel, so
//  every child block rendered at zero/intrinsic width and a complete
//  url-background pipeline painted nothing on the css-break tests.
//  Two layers of proof, same shape as WPTCaptureModeTests:
//    1. the pure static publication decision (scoping guards);
//    2. an end-to-end pixel pass: an auto-width child block of a
//       definite-width parent paints full containing-block width in
//       WPT capture mode and ONLY there (flag off = baseline hug).
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class WPTBlockChildFillTests: XCTestCase {

    // MARK: - Pure scoping pins (no render surface)

    /// Flag OFF is the product path AND all 327 committed baselines —
    /// a block parent must publish nil regardless of geometry.
    func testFlagOffNeverPublishes() {
        XCTAssertNil(ComponentRenderer.wptChildFillWidth(
            parentDisplay: .block, wptCaptureMode: false,
            isMarkerRow: false, parentContentWidth: 300,
            parentColumns: nil, columnGapPx: 0),
            "flag OFF must never publish a fill width (baseline behaviour)")
    }

    /// The target case: WPT mode + block container + definite content
    /// width ⇒ publish exactly the containing-block width.
    func testBlockParentInWptModePublishesContainingBlock() {
        XCTAssertEqual(ComponentRenderer.wptChildFillWidth(
            parentDisplay: .block, wptCaptureMode: true,
            isMarkerRow: false, parentContentWidth: 300,
            parentColumns: nil, columnGapPx: 0), 300,
            "a WPT-mode block container must publish its content-box width")
    }

    /// Non-block formatting contexts size their items themselves
    /// (css-flexbox-1 §9 / css-grid-1 §11 / inline §10.3.1) — every
    /// non-block display must keep publishing nil.
    func testNonBlockParentsPublishNil() {
        // Every non-block container kind the renderer distinguishes.
        for display in [LayoutConfig.DisplayType.flexRow, .flexColumn,
                        .grid, .inline, .none] {
            XCTAssertNil(ComponentRenderer.wptChildFillWidth(
                parentDisplay: display, wptCaptureMode: true,
                isMarkerRow: false, parentContentWidth: 300,
                parentColumns: nil, columnGapPx: 0),
                "\(display) parents must not publish a block fill")
        }
    }

    /// ol/ul marker rows lay the li out inside a synthesized HStack next
    /// to the marker — a full-width li would overflow the row.
    func testMarkerRowPublishesNil() {
        XCTAssertNil(ComponentRenderer.wptChildFillWidth(
            parentDisplay: .block, wptCaptureMode: true,
            isMarkerRow: true, parentContentWidth: 300,
            parentColumns: nil, columnGapPx: 0),
            "marker rows must not receive the fill width")
    }

    /// An indefinite parent (fit-content / auto width) has no containing
    /// block to publish — nil in, nil out (no invented geometry).
    func testIndefiniteParentPublishesNil() {
        XCTAssertNil(ComponentRenderer.wptChildFillWidth(
            parentDisplay: .block, wptCaptureMode: true,
            isMarkerRow: false, parentContentWidth: nil,
            parentColumns: nil, columnGapPx: 0),
            "an indefinite parent must not publish a fill width")
    }

    // MARK: - Multicol parents (wave-9 regression fix)

    /// css-multicol-1 §2: the containing block of a multicol element's
    /// children is the COLUMN box — the fill basis under a multicol
    /// parent is the §3 USED column width, never the container width
    /// (css-break background-image-001: the container-width fill
    /// stretched a block child across all 3 columns, SSIM 0.140 → 0.040).
    /// 300px content box, column-count 3, 10px gaps → (300 − 2×10)/3.
    func testMulticolParentPublishesColumnWidth() {
        var cols = ColumnsConfig()
        cols.count = 3
        XCTAssertEqual(ComponentRenderer.wptChildFillWidth(
            parentDisplay: .block, wptCaptureMode: true,
            isMarkerRow: false, parentContentWidth: 300,
            parentColumns: cols, columnGapPx: 10).map { Double($0) } ?? 0,
            (300.0 - 2 * 10) / 3, accuracy: 0.001,
            "a multicol parent must publish the used COLUMN width (§2/§3)")
    }

    /// column-width-driven multicol (count auto): 300px box, 140px
    /// columns, 10px gap → floor((300+10)/(140+10)) = 2 columns of
    /// (300 − 10)/2 = 145px each (§3's width branch).
    func testColumnWidthOnlyMulticolPublishesUsedWidth() {
        var cols = ColumnsConfig()
        cols.widthPx = 140
        XCTAssertEqual(ComponentRenderer.wptChildFillWidth(
            parentDisplay: .block, wptCaptureMode: true,
            isMarkerRow: false, parentContentWidth: 300,
            parentColumns: cols, columnGapPx: 10).map { Double($0) } ?? 0,
            145.0, accuracy: 0.001,
            "a column-width multicol parent must publish the §3 used width")
    }

    /// Rule-only columns config (column-rule-* / column-fill, both count
    /// and width auto) does NOT establish a multicol context (§2) — the
    /// fill stays the plain block containing-block basis.
    func testRuleOnlyColumnsConfigKeepsBlockBasis() {
        var cols = ColumnsConfig()
        cols.touched = true
        cols.rawByType["ColumnRuleStyle"] = "solid"
        XCTAssertEqual(ComponentRenderer.wptChildFillWidth(
            parentDisplay: .block, wptCaptureMode: true,
            isMarkerRow: false, parentContentWidth: 300,
            parentColumns: cols, columnGapPx: 0), 300,
            "column-rule alone is not a multicol container — full basis")
    }

    /// `column-count: 1` with no gaps degenerates to the container width
    /// — the single column box IS the content box, so the multicol path
    /// and the plain block path publish the identical basis.
    func testSingleColumnMulticolEqualsBlockBasis() {
        var cols = ColumnsConfig()
        cols.count = 1
        XCTAssertEqual(ComponentRenderer.wptChildFillWidth(
            parentDisplay: .block, wptCaptureMode: true,
            isMarkerRow: false, parentContentWidth: 300,
            parentColumns: cols, columnGapPx: 0), 300,
            "a 1-column multicol box's column width equals its content box")
    }

    /// Flag OFF stays nil for multicol parents too — the multicol basis
    /// is a WPT-capture concern only (baselines + product untouched).
    func testFlagOffMulticolStillPublishesNil() {
        var cols = ColumnsConfig()
        cols.count = 3
        XCTAssertNil(ComponentRenderer.wptChildFillWidth(
            parentDisplay: .block, wptCaptureMode: false,
            isMarkerRow: false, parentContentWidth: 300,
            parentColumns: cols, columnGapPx: 10),
            "flag OFF must never publish a fill width, multicol included")
    }

    // MARK: - End-to-end pixel proof

    /// Render on a white canvas at scale 1 with the WPT flag as given and
    /// return the RGBA byte at (x, y) — same CGContext harness as
    /// WPTCaptureModeTests / ContainingBlockHeightTests.
    @MainActor
    private func pixel(_ comp: IRComponent, wpt: Bool,
                       x: Int, y: Int) throws -> (r: UInt8, g: UInt8, b: UInt8) {
        // Publish the flag exactly the way the capture path does.
        let view = ComponentRenderer(component: comp)
            .environment(\.wptCaptureMode, wpt)
            .frame(width: 390, height: 100, alignment: .topLeading)
            .background(Color.white)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        let w = cg.width, h = cg.height
        var buf = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = try XCTUnwrap(CGContext(
            data: &buf, width: w, height: h, bitsPerComponent: 8,
            bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        let i = (y * w + x) * 4
        return (buf[i], buf[i + 1], buf[i + 2])
    }

    /// A definite 300px block parent with an auto-width, 20px-tall blue
    /// child (the css-break shape: the box that carries the painted
    /// background). WPT mode ON: the child must fill the containing
    /// block, so a probe near the right edge (x = 280) is blue. Flag
    /// OFF (product/baseline): the child hugs — the probe stays white.
    @MainActor
    func testAutoWidthChildFillsOnlyInWptMode() throws {
        // Wire shapes pinned against live converter output (wave 9).
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"p","name":"BlockParent",
         "properties":[
           {"type":"Width","data":{"type":"length","px":300.0}}
         ],
         "children":[
           {"id":"c","name":"ChildBlock",
            "properties":[
              {"type":"Height","data":{"type":"length","px":20.0}},
              {"type":"BackgroundColor","data":{"srgb":{"r":0.0,"g":0.0,"b":1.0},"original":"#0000ff"}}
            ]}
         ]}
        """.utf8))
        // WPT capture: child fills the 300px containing block → blue at
        // x=280 (well past any intrinsic hug, before the 300px edge).
        let on = try pixel(comp, wpt: true, x: 280, y: 10)
        XCTAssertGreaterThan(on.b, 192,
            "WPT mode: the auto-width child block must fill its containing block")
        XCTAssertLessThan(on.r, 64,
            "WPT mode: the probe must be the child's blue, not canvas white")
        // Product path: the child hugs its (suppression-free) content —
        // x=280 stays canvas white, proving the 327 baselines untouched.
        let off = try pixel(comp, wpt: false, x: 280, y: 10)
        XCTAssertGreaterThan(off.r, 192,
            "flag OFF: the child must keep hugging (white canvas at x=280)")
        XCTAssertGreaterThan(off.g, 192,
            "flag OFF: the child must keep hugging (white canvas at x=280)")
    }

    /// Wave-9 regression pin (css-break background-image-001 shape): the
    /// SAME auto-width blue child under a MULTICOL parent (column-count 3,
    /// column-gap 10px, 300px content box) must fill only its COLUMN box —
    /// (300 − 2×10)/3 ≈ 93px (css-multicol-1 §2/§3) — never the container.
    /// Probe x=50 sits inside the column (blue); probe x=280 sits in the
    /// container but past the first column box (white — this exact pixel
    /// was blue under the too-broad container-width fill).
    @MainActor
    func testMulticolParentChildFillsColumnBoxOnly() throws {
        // Wire shapes pinned against live converter output: ColumnCount
        // ships as a bare JSON number (ColumnCountSerializer), ColumnGap
        // as the raw `{px}` length object (GapExtractor's extractLength).
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"p","name":"MulticolParent",
         "properties":[
           {"type":"Width","data":{"type":"length","px":300.0}},
           {"type":"ColumnCount","data":3.0},
           {"type":"ColumnGap","data":{"px":10.0}}
         ],
         "children":[
           {"id":"c","name":"ChildBlock",
            "properties":[
              {"type":"Height","data":{"type":"length","px":20.0}},
              {"type":"BackgroundColor","data":{"srgb":{"r":0.0,"g":0.0,"b":1.0},"original":"#0000ff"}}
            ]}
         ]}
        """.utf8))
        // Inside the ~93px column box: the child paints blue.
        let inside = try pixel(comp, wpt: true, x: 50, y: 10)
        XCTAssertGreaterThan(inside.b, 192,
            "WPT mode: the child must fill its column box (blue at x=50)")
        // Past the column box but inside the container: canvas white —
        // the container-width fill regression painted this pixel blue.
        let outside = try pixel(comp, wpt: true, x: 280, y: 10)
        XCTAssertGreaterThan(outside.r, 192,
            "WPT mode: the child must NOT stretch past its column box")
        XCTAssertGreaterThan(outside.g, 192,
            "WPT mode: the child must NOT stretch past its column box")
    }
}
