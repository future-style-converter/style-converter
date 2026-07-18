//
//  FragmentGeometryTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 10 — the fragmentation contract (css-break-3 §4 +
//  box-decoration-break:slice). Three layers of proof:
//    1. FragmentGeometry pinned against the SHARED S1–S5 table (the
//       identical expected values Android's mirror pins — the
//       cross-platform geometry contract).
//    2. ColumnsApplier.fragmentPlan gate pins (multicol gate, the
//       vertical-writing-mode bail, definite-geometry requirements,
//       the single-child family guard) — pure, no render surface.
//    3. The ImageRenderer raster pin: a 350-tall red/blue-striped
//       child in the S1 geometry shows STRIPE CONTINUITY across
//       columns (fragment 1's top stripe continues fragment 0's
//       bottom stripe) with the [3H, C) tail clipped.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class FragmentGeometryTests: XCTestCase {

    // MARK: - 1. The shared S-table (identical on Android)

    /// S1: C=350 H=120 W=150 G=10 N=3 → F=3 with the exact
    /// {columnIndex, clipRect, translate} triples of the contract.
    func testS1ThreeFragments() throws {
        let frags = try XCTUnwrap(FragmentGeometry.fragments(
            childBlockSize: 350, columnBlockSize: 120,
            columnWidth: 150, gapPx: 10, columnCount: 3))
        XCTAssertEqual(frags, [
            .init(columnIndex: 0,
                  clipRect: CGRect(x: 0, y: 0, width: 150, height: 120),
                  translate: CGSize(width: 0, height: 0)),
            .init(columnIndex: 1,
                  clipRect: CGRect(x: 160, y: 0, width: 150, height: 120),
                  translate: CGSize(width: 160, height: -120)),
            .init(columnIndex: 2,
                  clipRect: CGRect(x: 320, y: 0, width: 150, height: 120),
                  translate: CGSize(width: 320, height: -240)),
        ], "S1: three fragments at x_i = i·(W+G), translated (+x_i, −i·H)")
    }

    /// S2: C=100 fits in H=120 → nil (identity — the existing
    /// unfragmented path renders byte-identically).
    func testS2FitsReturnsNilIdentity() {
        XCTAssertNil(FragmentGeometry.fragments(
            childBlockSize: 100, columnBlockSize: 120,
            columnWidth: 150, gapPx: 10, columnCount: 3),
            "S2: a fitting child (C <= H) never fragments — identity")
    }

    /// S3: C=240 H=120 N=3 → F=2 — the third column stays empty
    /// (ceil(240/120) = 2 exactly, no partial third band).
    func testS3TwoFragmentsThirdColumnEmpty() throws {
        let frags = try XCTUnwrap(FragmentGeometry.fragments(
            childBlockSize: 240, columnBlockSize: 120,
            columnWidth: 150, gapPx: 10, columnCount: 3))
        XCTAssertEqual(frags.count, 2, "S3: exactly F = ceil(C/H) = 2 fragments")
        XCTAssertEqual(frags[1].columnIndex, 1, "S3: last fragment in column 1")
        XCTAssertEqual(frags[1].translate,
                       CGSize(width: 160, height: -120),
                       "S3: fragment 1 shows the [120, 240) band")
    }

    /// S4: C=500 H=120 N=3 → ceil = 5 CAPPED at F=3; fragment 2 shows
    /// [240, 360) and the [360, 500) tail is clipped (column-fill:auto
    /// overflow — no fourth column is synthesized).
    func testS4CapAtColumnCountClipsTail() throws {
        let frags = try XCTUnwrap(FragmentGeometry.fragments(
            childBlockSize: 500, columnBlockSize: 120,
            columnWidth: 150, gapPx: 10, columnCount: 3))
        XCTAssertEqual(frags.count, 3, "S4: F capped at N = 3")
        // Fragment 2's translate −240 + clip height 120 exposes exactly
        // the [240, 360) band; everything past 360 falls outside every
        // fragment's clip → clipped.
        XCTAssertEqual(frags[2].translate,
                       CGSize(width: 320, height: -240),
                       "S4: fragment 2 shows the [240, 360) band")
        XCTAssertEqual(frags[2].clipRect,
                       CGRect(x: 320, y: 0, width: 150, height: 120),
                       "S4: the clip stays the column rect — tail cut")
    }

    /// S5: C=350 H=120 N=1 → F=1 — the single column shows [0, 120)
    /// and the whole [120, 350) tail is clipped.
    func testS5SingleColumnClipsTail() throws {
        let frags = try XCTUnwrap(FragmentGeometry.fragments(
            childBlockSize: 350, columnBlockSize: 120,
            columnWidth: 150, gapPx: 10, columnCount: 1))
        XCTAssertEqual(frags, [
            .init(columnIndex: 0,
                  clipRect: CGRect(x: 0, y: 0, width: 150, height: 120),
                  translate: CGSize(width: 0, height: 0)),
        ], "S5: one clipped fragment — F capped at N = 1, tail cut")
    }

    /// Degenerate H (<= 0) can host no fragment band — identity, never
    /// a crash (the ceil(C/H) guard).
    func testNonPositiveColumnHeightReturnsNil() {
        XCTAssertNil(FragmentGeometry.fragments(
            childBlockSize: 350, columnBlockSize: 0,
            columnWidth: 150, gapPx: 10, columnCount: 3),
            "H <= 0: no fragment geometry exists — identity path")
    }

    // MARK: - 2. fragmentPlan gate pins (pure)

    /// Decode a property list from wire-shaped JSON (the same literal
    /// shapes the converter emits — pinned in WPTBlockChildFillTests).
    private func props(_ json: String) throws -> [IRProperty] {
        try JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    /// The child property list of the overflow family: an explicit
    /// 350px block-size (Height wire shape: typed length object).
    private func tallChild() throws -> [IRProperty] {
        try props(#"[{"type":"Height","data":{"type":"length","px":350.0}}]"#)
    }

    /// The S1 container config: column-count 3 (ColumnsExtractor's
    /// typed count) over a 470px content box with 10px gaps.
    private func s1Columns() -> ColumnsConfig {
        var cols = ColumnsConfig()
        cols.count = 3
        return cols
    }

    /// The full S1 plan: U=470 → §3 used width (470 − 2·10)/3 = 150,
    /// H=120, C=350 → the S1 fragment list plus the HStack geometry.
    func testPlanS1Geometry() throws {
        let plan = try XCTUnwrap(ColumnsApplier.fragmentPlan(
            columns: s1Columns(), verticalWritingMode: false,
            siblingCount: 1, contentWidthPx: 470, contentHeightPx: 120,
            gapPx: 10, childProperties: tallChild(),
            ctx: SpacingContext()))
        XCTAssertEqual(plan.fragments.count, 3, "S1 plan: three fragments")
        XCTAssertEqual(plan.columnWidthPx, 150, accuracy: 0.001,
            "S1 plan: §3 used column width (470 − 20)/3 = 150")
        XCTAssertEqual(plan.columnBlockSizePx, 120, "S1 plan: H = 120")
        XCTAssertEqual(plan.gapPx, 10, "S1 plan: G = 10")
    }

    /// Not a multicol container (no non-auto count/width) → nil even
    /// with overflowing geometry (css-multicol-1 §2 gate).
    func testPlanRequiresMulticolContainer() throws {
        XCTAssertNil(ColumnsApplier.fragmentPlan(
            columns: nil, verticalWritingMode: false,
            siblingCount: 1, contentWidthPx: 470, contentHeightPx: 120,
            gapPx: 10, childProperties: try tallChild(),
            ctx: SpacingContext()),
            "no multicol context: nothing to fragment into")
    }

    /// Vertical writing modes are blocked-platform (contract:
    /// horizontal-tb only) — bail to identity, surfaced via logOnce.
    func testPlanBailsOnVerticalWritingMode() throws {
        // Reset the dedupe set so THIS test observes the emission
        // regardless of execution order.
        PropertyTracker._resetForTests()
        XCTAssertNil(ColumnsApplier.fragmentPlan(
            columns: s1Columns(), verticalWritingMode: true,
            siblingCount: 1, contentWidthPx: 470, contentHeightPx: 120,
            gapPx: 10, childProperties: try tallChild(),
            ctx: SpacingContext()),
            "vertical writing-mode: blocked-platform — no fragmentation")
        // The bail is logged (no silent fallthrough): the key is now in
        // the dedupe set, so a fresh logOnce on it reports NOT-first.
        XCTAssertFalse(PropertyTracker.logOnce(
            key: "multicol-fragment-vertical-writing", message: "probe"),
            "the vertical-writing bail must have logged its breadcrumb")
    }

    /// An indefinite (auto-height) container grows instead of breaking
    /// (css-break-3 §4.1) — nil H, nil plan.
    func testPlanRequiresDefiniteHeight() throws {
        XCTAssertNil(ColumnsApplier.fragmentPlan(
            columns: s1Columns(), verticalWritingMode: false,
            siblingCount: 1, contentWidthPx: 470, contentHeightPx: nil,
            gapPx: 10, childProperties: try tallChild(),
            ctx: SpacingContext()),
            "auto-height multicol boxes grow — no column block-size to break at")
    }

    /// A fitting child (C=100 <= H=120) keeps the identity path — the
    /// gate that pins every non-overflowing fixture byte-identical.
    func testPlanFittingChildIsIdentity() throws {
        XCTAssertNil(ColumnsApplier.fragmentPlan(
            columns: s1Columns(), verticalWritingMode: false,
            siblingCount: 1, contentWidthPx: 470, contentHeightPx: 120,
            gapPx: 10,
            childProperties: try props(
                #"[{"type":"Height","data":{"type":"length","px":100.0}}]"#),
            ctx: SpacingContext()),
            "S2: a fitting child must keep the existing render path")
    }

    /// An auto-height child's laid-out size is not statically knowable
    /// — identity (the overflow family always declares C).
    func testPlanAutoHeightChildIsIdentity() throws {
        XCTAssertNil(ColumnsApplier.fragmentPlan(
            columns: s1Columns(), verticalWritingMode: false,
            siblingCount: 1, contentWidthPx: 470, contentHeightPx: 120,
            gapPx: 10, childProperties: try props(#"[]"#),
            ctx: SpacingContext()),
            "no explicit child block-size: C unknown — identity path")
    }

    /// Multi-child containers need §7 column balancing (not built) —
    /// identity, surfaced via logOnce.
    func testPlanMultiChildBailsLogged() throws {
        PropertyTracker._resetForTests()
        XCTAssertNil(ColumnsApplier.fragmentPlan(
            columns: s1Columns(), verticalWritingMode: false,
            siblingCount: 2, contentWidthPx: 470, contentHeightPx: 120,
            gapPx: 10, childProperties: try tallChild(),
            ctx: SpacingContext()),
            "multi-child balancing is not built — identity path")
        XCTAssertFalse(PropertyTracker.logOnce(
            key: "multicol-fragment-multi-child", message: "probe"),
            "the multi-child bail must have logged its breadcrumb")
    }

    // MARK: - 3. ImageRenderer raster pin (S1 stripe continuity)

    /// Render on a white canvas at scale 1 and return the RGB bytes at
    /// (x, y) — the same CGContext harness as WPTBlockChildFillTests.
    @MainActor
    private func pixel(_ comp: IRComponent, wpt: Bool,
                       x: Int, y: Int) throws -> (r: UInt8, g: UInt8, b: UInt8) {
        // topLeading pin so probe coordinates are container coordinates.
        let view = ComponentRenderer(component: comp)
            .environment(\.wptCaptureMode, wpt)
            .frame(width: 500, height: 200, alignment: .topLeading)
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

    /// The S1 fixture: a 470×120 column-count-3 container (10px gaps →
    /// W = 150) holding one 150×350 child striped in 70px red/blue
    /// bands (red [0,70), blue [70,140), red [140,210), blue [210,280),
    /// red [280,350)). Wire shapes as pinned in WPTBlockChildFillTests:
    /// ColumnCount as a bare number, ColumnGap as `{px}`, lengths as
    /// typed `{type:length, px}` objects.
    private func s1StripedFixture() throws -> IRComponent {
        // Stripe helper: explicit 150×70 block with a solid background
        // (explicit sizes keep the raster identical with the WPT flag
        // on or off — nothing depends on the auto-width fill).
        func stripe(_ id: Int, _ hex: String, r: Double, b: Double) -> String {
            #"{"id":"s\#(id)","name":"Stripe\#(id)","properties":[            {"type":"Width","data":{"type":"length","px":150.0}},            {"type":"Height","data":{"type":"length","px":70.0}},            {"type":"BackgroundColor","data":{"srgb":{"r":\#(r),"g":0.0,"b":\#(b)},"original":"\#(hex)"}}]}"#
        }
        let stripes = (0..<5).map { i in
            // Even stripes red, odd stripes blue.
            stripe(i, i % 2 == 0 ? "#ff0000" : "#0000ff",
                   r: i % 2 == 0 ? 1.0 : 0.0,
                   b: i % 2 == 0 ? 0.0 : 1.0)
        }.joined(separator: ",")
        return try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"p","name":"MulticolParent",
         "properties":[
           {"type":"Width","data":{"type":"length","px":470.0}},
           {"type":"Height","data":{"type":"length","px":120.0}},
           {"type":"ColumnCount","data":3.0},
           {"type":"ColumnGap","data":{"px":10.0}}
         ],
         "children":[
           {"id":"c","name":"TallChild",
            "properties":[
              {"type":"Width","data":{"type":"length","px":150.0}},
              {"type":"Height","data":{"type":"length","px":350.0}}
            ],
            "children":[\(stripes)]}
         ]}
        """.utf8))
    }

    /// Assert a probe is (near-)pure red / blue / white. Tolerances are
    /// generous (64/192) — antialiasing never reaches band centers.
    private func assertColor(_ p: (r: UInt8, g: UInt8, b: UInt8),
                             is want: String, _ what: String) {
        switch want {
        case "red":
            XCTAssertGreaterThan(p.r, 192, "\(what): expected red, got \(p)")
            XCTAssertLessThan(p.b, 64, "\(what): expected red, got \(p)")
        case "blue":
            XCTAssertGreaterThan(p.b, 192, "\(what): expected blue, got \(p)")
            XCTAssertLessThan(p.r, 64, "\(what): expected blue, got \(p)")
        default: // white canvas
            XCTAssertGreaterThan(p.r, 192, "\(what): expected white, got \(p)")
            XCTAssertGreaterThan(p.g, 192, "\(what): expected white, got \(p)")
            XCTAssertGreaterThan(p.b, 192, "\(what): expected white, got \(p)")
        }
    }

    /// The stripe-continuity pin (css-break-3 §5.2 slice): fragment 1's
    /// top is the CONTINUATION of fragment 0's bottom stripe — plus the
    /// gap stays unpainted, the tail past C is clipped, and nothing
    /// paints below the container (the pre-wave-10 overflow is gone).
    @MainActor
    func testS1RasterStripeContinuity() throws {
        let comp = try s1StripedFixture()
        // Fragment 0 (column 0, x∈[0,150)) shows child [0,120):
        // y=35 → stripe 0 (red); y=100 → stripe 1 (blue).
        assertColor(try pixel(comp, wpt: true, x: 75, y: 35), is: "red",
                    "fragment 0 top band")
        assertColor(try pixel(comp, wpt: true, x: 75, y: 100), is: "blue",
                    "fragment 0 bottom band")
        // CONTINUITY: fragment 1 (column 1, x∈[160,310)) shows child
        // [120,240) — its top (child y≈125) is still stripe 1 (blue),
        // continuing fragment 0's bottom band across the column break.
        assertColor(try pixel(comp, wpt: true, x: 200, y: 5), is: "blue",
                    "fragment 1 top = continuation of fragment 0's bottom stripe")
        // Deeper into fragment 1: y=40 → child 160 → stripe 2 (red).
        assertColor(try pixel(comp, wpt: true, x: 200, y: 40), is: "red",
                    "fragment 1 mid band")
        // Fragment 2 (column 2, x∈[320,470)) shows child [240,350):
        // y=10 → child 250 → stripe 3 (blue).
        assertColor(try pixel(comp, wpt: true, x: 400, y: 10), is: "blue",
                    "fragment 2 top band")
        // The slice cap min((i+1)·H, C): child paint ends at 350, so
        // fragment 2's y=115 (child 355) is past the child — white.
        assertColor(try pixel(comp, wpt: true, x: 400, y: 115), is: "white",
                    "fragment 2 tail past C is unpainted")
        // The 10px gap between columns 0 and 1 (x∈[150,160)) never
        // receives paint — gaps have no painted extent (css-align-3 §8).
        assertColor(try pixel(comp, wpt: true, x: 155, y: 60), is: "white",
                    "column gap stays unpainted")
        // Below the container (y=150 > H=120): the old unfragmented
        // child painted here; the fragment pass clips every band to H.
        assertColor(try pixel(comp, wpt: true, x: 75, y: 150), is: "white",
                    "no paint below the column row")
        // Product path (WPT flag OFF) fragments identically — the pass
        // is a product rendering feature, not a capture-mode carve-out
        // (every input here is explicit, so both modes must agree).
        assertColor(try pixel(comp, wpt: false, x: 200, y: 5), is: "blue",
                    "flag OFF: fragmentation is product behaviour")
    }
}
