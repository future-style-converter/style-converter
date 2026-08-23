//
//  MulticolClonePlanTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 46 (lane Y3) — ColumnsApplier.fragmentPlan's css-break-3 §5.2
//  CLONE branch: the pure gate pins (leaf-only, sole-child, px bands,
//  box-sizing tri-state, the logged bails) and the ImageRenderer raster
//  pin of WPT css-break borders-008's shape — a 240px-tall, 10px-bordered,
//  50px-radius box in 100px columns renders as three CIRCLES, where the
//  wave-10 slice replay painted one tall box cut into three bands (the
//  wave45-final iOS capture: arcs + straight sides, SSIM 0.893).
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class MulticolClonePlanTests: XCTestCase {

    /// Decode a property list from wire-shaped JSON.
    private func props(_ json: String) throws -> [IRProperty] {
        try JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    /// borders-008's child: clone, 10px solid borders, 50px radius,
    /// 80×240 content, yellow background.
    private func borders008Child() throws -> [IRProperty] {
        try props(#"""
        [{"type":"BoxDecorationBreak","data":"CLONE"},
         {"type":"BorderTopWidth","data":{"px":10}},{"type":"BorderRightWidth","data":{"px":10}},
         {"type":"BorderBottomWidth","data":{"px":10}},{"type":"BorderLeftWidth","data":{"px":10}},
         {"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},
         {"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},
         {"type":"BorderTopLeftRadius","data":{"px":50}},{"type":"BorderTopRightRadius","data":{"px":50}},
         {"type":"BorderBottomRightRadius","data":{"px":50}},{"type":"BorderBottomLeftRadius","data":{"px":50}},
         {"type":"Width","data":{"type":"length","px":80}},
         {"type":"Height","data":{"type":"length","px":240}},
         {"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":0},"original":"yellow"}}]
        """#)
    }

    /// borders-008's container config: column-count 3 over a 320px
    /// content box with 10px gaps → §3 used width 100.
    private func cols3() -> ColumnsConfig {
        var cols = ColumnsConfig()
        cols.count = 3
        return cols
    }

    // MARK: - 1. Pure plan pins

    /// P1 — the full borders-008 plan under WPT capture (content-box):
    /// three full fragments, each child copy re-declared at 100 − 20 = 80.
    func testP1Borders008ClonePlan() throws {
        let plan = try XCTUnwrap(ColumnsApplier.fragmentPlan(
            columns: cols3(), verticalWritingMode: false, siblingCount: 1,
            contentWidthPx: 320, contentHeightPx: 100, gapPx: 10,
            childProperties: try borders008Child(), ctx: SpacingContext(),
            wptCaptureMode: true, childIsLeaf: true))
        XCTAssertEqual(plan.fragments.count, 3, "three clone fragments")
        XCTAssertEqual(plan.fragments.map { $0.translate.height }, [0, 0, 0],
                       "clone never translates in the block axis")
        XCTAssertEqual(plan.fragments.map { $0.clipRect.height }, [100, 100, 100])
        XCTAssertEqual(plan.cloneDeclaredHeightsPx, [80, 80, 80],
                       "content-box: each copy declares the fragment minus its 20px bands")
        // The plan's child for a fragment carries that declared height.
        let child = try JSONDecoder().decode(IRComponent.self, from: Data(#"""
        {"id":"c","name":"c","properties":[{"type":"Height","data":{"type":"length","px":240}}]}
        """#.utf8))
        XCTAssertEqual(SizeExtractor.extract(from: plan.child(child, forFragmentAt: 1).properties).height,
                       .exact(px: 80))
        // Out of range → the child itself (never produced; defensive).
        XCTAssertEqual(SizeExtractor.extract(from: plan.child(child, forFragmentAt: 7).properties).height,
                       .exact(px: 240))
    }

    /// P2 — under an explicit `box-sizing: border-box` the declared slot
    /// IS the border box: C = 240 → content 220, capacity 80 → three
    /// fragments (100, 100, 80 — the last holds 60px of content + bands),
    /// and the copies declare each fragment's border box VERBATIM.
    func testP2BorderBoxDeclaresTheFragmentVerbatim() throws {
        let plan = try XCTUnwrap(ColumnsApplier.fragmentPlan(
            columns: cols3(), verticalWritingMode: false, siblingCount: 1,
            contentWidthPx: 320, contentHeightPx: 100, gapPx: 10,
            childProperties: try borders008Child()
                + props(#"[{"type":"BoxSizing","data":"BORDER_BOX"}]"#),
            ctx: SpacingContext(), wptCaptureMode: true, childIsLeaf: true))
        XCTAssertEqual(plan.cloneDeclaredHeightsPx, [100, 100, 80])
        XCTAssertEqual(plan.fragments.map { $0.clipRect.height }, [100, 100, 80])
    }

    /// P3 — a SHORT last fragment declares exactly its leftover content:
    /// 250px content, capacity 80 → 4 fragments, the last 10px of content
    /// in a 30px box (the iOS-exact form of K4).
    func testP3ShortLastFragmentDeclaresLeftover() throws {
        var cols = ColumnsConfig(); cols.count = 4
        let plan = try XCTUnwrap(ColumnsApplier.fragmentPlan(
            columns: cols, verticalWritingMode: false, siblingCount: 1,
            contentWidthPx: 430, contentHeightPx: 100, gapPx: 10,
            childProperties: try props(#"""
                [{"type":"BoxDecorationBreak","data":"CLONE"},
                 {"type":"BorderTopWidth","data":{"px":10}},{"type":"BorderTopStyle","data":"SOLID"},
                 {"type":"BorderBottomWidth","data":{"px":10}},{"type":"BorderBottomStyle","data":"SOLID"},
                 {"type":"Height","data":{"type":"length","px":250}}]
                """#),
            ctx: SpacingContext(), wptCaptureMode: true, childIsLeaf: true))
        XCTAssertEqual(plan.cloneDeclaredHeightsPx, [80, 80, 80, 10])
        XCTAssertEqual(plan.fragments[3].clipRect.height, 30)
    }

    /// P4 — a content-bearing clone child keeps the SLICE plan (no
    /// cloneDeclaredHeights) and logs its bail once.
    func testP4ContentBearingCloneChildKeepsSliceAndLogs() throws {
        PropertyTracker._resetForTests()
        let plan = try XCTUnwrap(ColumnsApplier.fragmentPlan(
            columns: cols3(), verticalWritingMode: false, siblingCount: 1,
            contentWidthPx: 320, contentHeightPx: 100, gapPx: 10,
            childProperties: try borders008Child(), ctx: SpacingContext(),
            wptCaptureMode: true, childIsLeaf: false))
        XCTAssertNil(plan.cloneDeclaredHeightsPx, "slice plan for a content-bearing clone child")
        // Slice geometry: C = declared 240, H = 100 → 3 fragments with −i·H.
        XCTAssertEqual(plan.fragments.map { $0.translate.height }, [0, -100, -200])
        XCTAssertFalse(PropertyTracker.logOnce(key: "multicol-clone-content-child", message: "probe"),
                       "the content-bearing bail must have logged its breadcrumb")
    }

    /// P5 — a non-px band keeps slice and logs; a slice child never logs.
    func testP5UnresolvedBandKeepsSliceAndLogs() throws {
        PropertyTracker._resetForTests()
        let em = try props(#"""
            [{"type":"BoxDecorationBreak","data":"CLONE"},
             {"type":"PaddingTop","data":{"original":{"v":1,"u":"EM"}}},
             {"type":"Height","data":{"type":"length","px":240}}]
            """#)
        let plan = try XCTUnwrap(ColumnsApplier.fragmentPlan(
            columns: cols3(), verticalWritingMode: false, siblingCount: 1,
            contentWidthPx: 320, contentHeightPx: 100, gapPx: 10,
            childProperties: em, ctx: SpacingContext(),
            wptCaptureMode: true, childIsLeaf: true))
        XCTAssertNil(plan.cloneDeclaredHeightsPx)
        XCTAssertFalse(PropertyTracker.logOnce(key: "multicol-clone-band-unresolved", message: "probe"))
        // A plain slice child: the clone keys stay unlogged.
        PropertyTracker._resetForTests()
        _ = ColumnsApplier.fragmentPlan(
            columns: cols3(), verticalWritingMode: false, siblingCount: 1,
            contentWidthPx: 320, contentHeightPx: 100, gapPx: 10,
            childProperties: try props(#"[{"type":"Height","data":{"type":"length","px":240}}]"#),
            ctx: SpacingContext(), wptCaptureMode: true, childIsLeaf: true)
        XCTAssertTrue(PropertyTracker.logOnce(key: "multicol-clone-content-child", message: "probe"))
    }

    /// P6 — the legacy default (`childIsLeaf` omitted) never clones: every
    /// pre-wave-46 caller keeps the slice plan byte-identically.
    func testP6DefaultCallerKeepsSlice() throws {
        let plan = try XCTUnwrap(ColumnsApplier.fragmentPlan(
            columns: cols3(), verticalWritingMode: false, siblingCount: 1,
            contentWidthPx: 320, contentHeightPx: 100, gapPx: 10,
            childProperties: try borders008Child(), ctx: SpacingContext(),
            wptCaptureMode: true))
        XCTAssertNil(plan.cloneDeclaredHeightsPx)
    }

    /// P7 — dark stage (`wptCaptureMode` false): the clone branch never
    /// engages, matching the Compose twin's capture-nulled ChildSpecs;
    /// the slice plan (the wave-10 definite-height row) is returned and
    /// no clone breadcrumb is logged (not a fallthrough).
    func testP7DarkStageKeepsSliceSilently() throws {
        PropertyTracker._resetForTests()
        let plan = try XCTUnwrap(ColumnsApplier.fragmentPlan(
            columns: cols3(), verticalWritingMode: false, siblingCount: 1,
            contentWidthPx: 320, contentHeightPx: 100, gapPx: 10,
            childProperties: try borders008Child(), ctx: SpacingContext(),
            wptCaptureMode: false, childIsLeaf: true))
        XCTAssertNil(plan.cloneDeclaredHeightsPx, "dark stage: slice plan")
        XCTAssertEqual(plan.fragments.map { $0.translate.height }, [0, -100, -200])
        XCTAssertTrue(PropertyTracker.logOnce(key: "multicol-clone-content-child", message: "probe"))
        XCTAssertTrue(PropertyTracker.logOnce(key: "multicol-clone-band-unresolved", message: "probe"))
    }

    // MARK: - 2. The raster pin (borders-008: three circles)

    /// Render on a white canvas at scale 1 and return the RGB bytes at
    /// (x, y) — the FragmentGeometryTests CGContext harness.
    @MainActor
    private func pixel(_ comp: IRComponent, x: Int, y: Int) throws -> (r: UInt8, g: UInt8, b: UInt8) {
        let view = ComponentRenderer(component: comp)
            .environment(\.wptCaptureMode, true)
            .frame(width: 400, height: 150, alignment: .topLeading)
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

    /// borders-008 as an IR document: a 320×100 column-count-3 container
    /// (10px gaps → W = 100) holding the clone child with BLACK borders
    /// (the test's `border: 10px solid` is currentColor = black).
    private func borders008Fixture() throws -> IRComponent {
        let black = #"{"srgb":{"r":0,"g":0,"b":0},"original":"black"}"#
        return try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"p","name":"Multicol",
         "properties":[
           {"type":"Width","data":{"type":"length","px":320}},
           {"type":"Height","data":{"type":"length","px":100}},
           {"type":"ColumnCount","data":3},
           {"type":"ColumnGap","data":{"px":10}},
           {"type":"ColumnFill","data":"AUTO"}
         ],
         "children":[
           {"id":"c","name":"Circle","properties":[
             {"type":"BoxDecorationBreak","data":"CLONE"},
             {"type":"BorderTopWidth","data":{"px":10}},{"type":"BorderRightWidth","data":{"px":10}},
             {"type":"BorderBottomWidth","data":{"px":10}},{"type":"BorderLeftWidth","data":{"px":10}},
             {"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},
             {"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},
             {"type":"BorderTopColor","data":\(black)},{"type":"BorderRightColor","data":\(black)},
             {"type":"BorderBottomColor","data":\(black)},{"type":"BorderLeftColor","data":\(black)},
             {"type":"BorderTopLeftRadius","data":{"px":50}},{"type":"BorderTopRightRadius","data":{"px":50}},
             {"type":"BorderBottomRightRadius","data":{"px":50}},{"type":"BorderBottomLeftRadius","data":{"px":50}},
             {"type":"Width","data":{"type":"length","px":80}},
             {"type":"Height","data":{"type":"length","px":240}},
             {"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":0},"original":"yellow"}}
           ]}
         ]}
        """.utf8))
    }

    /// Loose color assertions — antialiasing never reaches the probes.
    private func assertBlack(_ p: (r: UInt8, g: UInt8, b: UInt8), _ what: String) {
        XCTAssertLessThan(p.r, 64, "\(what): expected black, got \(p)")
        XCTAssertLessThan(p.g, 64, "\(what): expected black, got \(p)")
    }
    private func assertYellow(_ p: (r: UInt8, g: UInt8, b: UInt8), _ what: String) {
        XCTAssertGreaterThan(p.r, 192, "\(what): expected yellow, got \(p)")
        XCTAssertGreaterThan(p.g, 192, "\(what): expected yellow, got \(p)")
        XCTAssertLessThan(p.b, 64, "\(what): expected yellow, got \(p)")
    }
    private func assertWhite(_ p: (r: UInt8, g: UInt8, b: UInt8), _ what: String) {
        XCTAssertGreaterThan(p.r, 192, "\(what): expected white, got \(p)")
        XCTAssertGreaterThan(p.g, 192, "\(what): expected white, got \(p)")
        XCTAssertGreaterThan(p.b, 192, "\(what): expected white, got \(p)")
    }

    /// Three circles: every column shows a COMPLETE 100×100 ring — a top
    /// border in column 1 (slice painted the tall box's straight side
    /// there, yellow at the top), a white corner outside the ring, and a
    /// bottom border at the very bottom of column 2 (slice's third band
    /// ended at child row 260 → y = 60, nothing at y = 95).
    @MainActor
    func testBorders008RendersThreeCircles() throws {
        let comp = try borders008Fixture()
        // Column 0 (x∈[0,100)): the ring's interior is yellow.
        assertYellow(try pixel(comp, x: 50, y: 50), "column 0 center")
        // Column 1 (x∈[110,210)): a TOP border at the circle's apex…
        assertBlack(try pixel(comp, x: 160, y: 5), "column 1 top border (clone)")
        // …and a white corner OUTSIDE the ring (slice: the straight left
        // side of the tall box painted black here).
        assertWhite(try pixel(comp, x: 113, y: 5), "column 1 corner outside the circle")
        // Column 2 (x∈[220,320)): interior yellow, BOTTOM border present.
        assertYellow(try pixel(comp, x: 270, y: 50), "column 2 center")
        assertBlack(try pixel(comp, x: 270, y: 95), "column 2 bottom border (clone)")
        // The gap stays unpainted.
        assertWhite(try pixel(comp, x: 105, y: 50), "gap")
    }
}
