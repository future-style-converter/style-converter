//
//  TableBodyForestTests.swift
//  StyleConverterRuntimeTests — wave-53 lane L3 (item B).
//
//  Pins for TableBodyForest, the composed canvas's `display: table` BODY
//  rewrite (CSS 2.1 §17.2.1 rule 2, §8.3), on the VERBATIM wave52-ship
//  per-test IR (tools/titan/runs/wave52-ship/sections/<section>/per-test-ir/).
//  Kotlin twin: runtimes/compose/src/test/…/table/TableBodyForestTest.kt.
//
//  WHY: s-11-1-1b-006's body children are sibling ROOTS, so `Display TABLE`
//  applied to an empty box (wave52-ship ios P 0.9953, DEGENERATE: the square
//  at image rows 51-70, ref 56-75).
//
//  Besides the shape pins this file carries the STACK pin (the canvas's
//  §8.3.1 fold over the rewritten flow gives 0 above the table) and a
//  Catalyst RASTER pin: the rewritten flow mounted the way
//  ComposedCaptureCanvas mounts it (frame 16 + body margin 40/8, the
//  358-wide block-fill channel, wptCaptureMode) — the black square's rows
//  and columns are read off the ImageRenderer bitmap.
//
//  EXECUTED MUTATIONS (TableBodyForest.swift, these tests run alone, source
//  restored byte-exact — sha256 in tools/titan/results/wave53-canvas-root/_note.md):
//    BS  the run re-parented under the body-root (no fresh TABLE root) → testShape red.
//    BM1 the `Display ∈ {TABLE, INLINE_TABLE}` trigger dropped       → testM1 red (a98rgb-003).
//    BM2 the §8.3 Margin* strip skipped                               → testM2 red.
//    BM3 the out-of-flow filter dropped                               → testM3 red.
//    BM4 a non-cell root placed straight into the row                 → testM4 red.
//    BM5 BorderSpacing not copied onto the synthetic table            → testM5 red.
//    BK  rewrite returns `roots` (no table)                           → testStack red.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class TableBodyForestTests: XCTestCase {

    /// CSS2/css21-errata/s-11-1-1b-006 — all four roots, verbatim (`body { display: table }`).
    private let s006 = """
        {"id":"wpt__css2__css21-errata__s-11-1-1b-006__0-141","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__0","properties":[{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Display","data":"TABLE"},{"type":"BorderSpacing","data":{"type":"single","px":0}},{"type":"MarginTop","data":{"px":40}},{"type":"MarginRight","data":{"px":8}},{"type":"MarginBottom","data":{"px":8}},{"type":"MarginLeft","data":{"px":8}}],"meta":{"role":"body-root"}},
        {"id":"wpt__css2__css21-errata__s-11-1-1b-006__1-142","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__1","properties":[{"type":"Generic","data":{"propertyName":"display","rawValue":"caption","_unmapped":true}},{"type":"MarginBottom","data":{"px":10}}],"meta":{"role":"ws-after"}},
        {"id":"wpt__css2__css21-errata__s-11-1-1b-006__2-143","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__2","properties":[{"type":"Display","data":"TABLE_CELL"},{"type":"Width","data":{"type":"length","px":20}},{"type":"Height","data":{"type":"length","px":20}},{"type":"MarginTop","data":{"px":-15}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"role":"ws-after"}},
        {"id":"wpt__css2__css21-errata__s-11-1-1b-006__3-144","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__3","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":0}},{"type":"Left","data":{"px":8}}],"text":"Test passes if there is a black square below.","meta":{"sourceTag":"p"}}
        """
    /// CSS2/css21-errata/s-11-1-1b-005 — verbatim (`display: table-cell` body).
    private let s005 = """
        {"id":"wpt__css2__css21-errata__s-11-1-1b-005__0-139","name":"wpt__CSS2__css21-errata__s-11-1-1b-005__0","properties":[{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Display","data":"TABLE_CELL"},{"type":"BorderSpacing","data":{"type":"single","px":0}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"MarginTop","data":{"px":-15}},{"type":"MarginRight","data":{"px":8}},{"type":"MarginBottom","data":{"px":8}},{"type":"MarginLeft","data":{"px":8}},{"type":"Width","data":{"type":"length","px":20}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"role":"body-root"}},
        {"id":"css21-errata__s-11-1-1b-005__0-140","name":"css21-errata__s-11-1-1b-005__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":0}}],"slot":{"parent":"wpt__css2__css21-errata__s-11-1-1b-005__0-139"},"text":"Test passes if there is a black square below.","meta":{"sourceTag":"p"}}
        """
    /// CSS2/css21-errata/s-11-1-1b-001 — verbatim (a real <table>, no body-root).
    private let s001 = """
        {"id":"wpt__css2__css21-errata__s-11-1-1b-001__0-117","name":"wpt__CSS2__css21-errata__s-11-1-1b-001__0","properties":[],"text":"Test passes if there is a black square below.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css2__css21-errata__s-11-1-1b-001__1-118","name":"wpt__CSS2__css21-errata__s-11-1-1b-001__1","properties":[{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"BorderSpacing","data":{"type":"single","px":0}}],"meta":{"sourceTag":"table"}},
        {"id":"css21-errata__s-11-1-1b-001__1__0-119","name":"css21-errata__s-11-1-1b-001__1__0","properties":[{"type":"MarginBottom","data":{"px":10}}],"slot":{"parent":"wpt__css2__css21-errata__s-11-1-1b-001__1-118"},"meta":{"sourceTag":"caption","role":"ws-after"}},
        {"id":"css21-errata__s-11-1-1b-001__1__1-120","name":"css21-errata__s-11-1-1b-001__1__1","properties":[],"slot":{"parent":"wpt__css2__css21-errata__s-11-1-1b-001__1-118"},"meta":{"sourceTag":"tr"}},
        {"id":"css21-errata__s-11-1-1b-001__1__1__0-121","name":"css21-errata__s-11-1-1b-001__1__1__0","properties":[{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}}],"slot":{"parent":"css21-errata__s-11-1-1b-001__1__1-120"},"meta":{"sourceTag":"td"}},
        {"id":"css21-errata__s-11-1-1b-001__1__1__0__0-122","name":"css21-errata__s-11-1-1b-001__1__1__0__0","properties":[{"type":"Width","data":{"type":"length","px":20}},{"type":"Height","data":{"type":"length","px":25}},{"type":"BorderTopWidth","data":{"px":10}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"MarginTop","data":{"px":-15}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"slot":{"parent":"css21-errata__s-11-1-1b-001__1__1__0-121"}}
        """
    /// css-color/a98rgb-003 — verbatim (a colour-only body-root followed by in-flow roots).
    private let a98 = """
        {"id":"wpt__css-color__a98rgb-003__0-005","name":"wpt__css-color__a98rgb-003__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.5019607843137255,"g":0.5019607843137255,"b":0.5019607843137255},"original":"grey"}}],"meta":{"role":"body-root"}},
        {"id":"wpt__css-color__a98rgb-003__1-006","name":"wpt__css-color__a98rgb-003__1","properties":[],"text":"Test passes if you see a single square, and not two rectangles of different colors.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css-color__a98rgb-003__2-007","name":"wpt__css-color__a98rgb-003__2","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.996078431372549,"g":0.996078431372549,"b":0.996078431372549},"original":{"r":254,"g":254,"b":254}}},{"type":"Width","data":{"type":"length","original":{"v":12,"u":"EM"}}},{"type":"Height","data":{"type":"length","original":{"v":6,"u":"EM"}}},{"type":"MarginBottom","data":{"px":0}}],"meta":{"role":"ws-after"}},
        {"id":"wpt__css-color__a98rgb-003__3-008","name":"wpt__css-color__a98rgb-003__3","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.9999300658875595,"g":1,"b":1},"original":{"type":"color","colorSpace":"a98-rgb","values":[1,1,1]}}},{"type":"Width","data":{"type":"length","original":{"v":12,"u":"EM"}}},{"type":"Height","data":{"type":"length","original":{"v":6,"u":"EM"}}},{"type":"MarginTop","data":{"px":0}}]}
        """

    /// Decode a v2 document's components exactly as the harness does.
    private func rootsOf(_ componentsJSON: String) throws -> [IRComponent] {
        let json = "{\"irVersion\":2,\"minReaderVersion\":2,\"components\":[\(componentsJSON)]}"
        return try JSONDecoder().decode(IRDocument.self, from: Data(json.utf8)).components
    }

    /// A component's Display enum leaf.
    private func displayOf(_ c: IRComponent) -> String? {
        c.properties.last(where: { $0.type == "Display" })?.data.stringValue
    }

    func testShapeBodyRootUnchangedTableRowCellsAbspos() throws {
        let roots = try rootsOf(s006)
        let out = TableBodyForest.rewrite(roots)
        XCTAssertEqual(out.count, 3)
        // [body-root unchanged, TABLE#table → ROW → [CELL{1-142}, 2-143], 3-144 unchanged]
        XCTAssertEqual(out[0].id, roots[0].id)
        XCTAssertEqual(out[0].properties, roots[0].properties)
        XCTAssertEqual(out[1].id, roots[0].id + "#table")
        XCTAssertEqual(displayOf(out[1]), "TABLE")
        let row = try XCTUnwrap(out[1].children?.first)
        XCTAssertEqual(out[1].children?.count, 1)
        XCTAssertEqual(displayOf(row), "TABLE_ROW")
        XCTAssertEqual(row.children?.map(\.id), [roots[0].id + "#cell0", roots[2].id])
        XCTAssertEqual(row.children?.first?.children?.map(\.id), [roots[1].id])
        XCTAssertEqual(out[2].id, roots[3].id)
    }

    func testM1IdentityOnNonTableBodies() throws {
        // 005: table-cell body; 001: no body-root; a98rgb-003: a colour body + in-flow roots.
        for doc in [s005, s001, a98] {
            let roots = try rootsOf(doc)
            let out = TableBodyForest.rewrite(roots)
            XCTAssertEqual(out.map(\.id), roots.map(\.id))
            XCTAssertEqual(out.map(\.properties), roots.map(\.properties))
        }
    }

    func testM2CellLosesItsMargins() throws {
        let td = try XCTUnwrap(TableBodyForest.rewrite(try rootsOf(s006))[1].children?.first?.children?[1])
        // CSS 2.1 §8.3: no margin on a table cell; size and fill kept.
        XCTAssertEqual(td.properties.map(\.type), ["Display", "Width", "Height", "BackgroundColor"])
    }

    func testM3AbsposStaysARootNeverWrapped() throws {
        let roots = try rootsOf(s006)
        let out = TableBodyForest.rewrite(roots)
        XCTAssertEqual(out[2].id, roots[3].id)
        let cells = try XCTUnwrap(out[1].children?.first?.children)
        XCTAssertEqual(cells.count, 2)
        XCTAssertFalse(cells.contains { c in c.id == roots[3].id || (c.children ?? []).contains { $0.id == roots[3].id } })
    }

    func testM4NonCellRunIsWrappedInAnAnonymousCell() throws {
        let first = try XCTUnwrap(TableBodyForest.rewrite(try rootsOf(s006))[1].children?.first?.children?.first)
        XCTAssertEqual(displayOf(first), "TABLE_CELL")
    }

    func testM5TableCarriesTheBodysBorderSpacing() throws {
        let table = TableBodyForest.rewrite(try rootsOf(s006))[1]
        let bs = try XCTUnwrap(table.properties.first(where: { $0.type == "BorderSpacing" }))
        XCTAssertEqual(bs.data["type"]?.stringValue, "single")
        XCTAssertEqual(bs.data["px"]?.doubleValue, 0)
    }

    /// The canvas's flow after its split-input rewrite (CaptureCanvas.splitRoots, item B + M1 + T6).
    private func flowRoots(_ roots: [IRComponent]) -> [IRComponent] {
        FixedHoist.split(roots: UABlockMargin.withCanvasOwnedBodyMargin(
            TableBodyForest.rewrite(roots), UABlockMargin.canvasBodyMargin(roots))
            .map(UABlockMargin.withUaBlockMarginOnHoistedRoot)).flow
    }

    func testStackZeroGapAboveTheSyntheticTable() throws {
        let roots = try rootsOf(s006)
        let flow = flowRoots(roots)
        // The flow is [body-root, #table]; the abspos <p> is hoisted, the td is in the table.
        XCTAssertEqual(flow.map(\.id), [roots[0].id, roots[0].id + "#table"])
        let spacing = UABlockMargin.stackedSpacing(plans: flow.map { UABlockMargin.composedRootStackPlan($0) })
        XCTAssertEqual(spacing.leading, [0, 0])
    }

    /// The black square's (rows, columns) bbox on the rewritten flow, mounted as the canvas mounts it.
    @MainActor
    private func squareBox(_ flow: [IRComponent]) throws -> (Int, Int, Int, Int)? {
        let plans = flow.map { UABlockMargin.composedRootStackPlan($0) }
        let spacing = UABlockMargin.stackedSpacing(plans: plans)
        let view = VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(flow.enumerated()), id: \.offset) { i, root in
                ComponentHost(component: root)
                    .composedRootBlockMarginStrip(plans[i].stripDeclared)
                    .padding(.top, spacing.leading[i])
            }
        }
        // The body's content box: 358 − 8 − 8 (its margin), framed at (16 + 8, 16 + 40).
        .frame(maxWidth: 342, alignment: .topLeading)
        .padding(EdgeInsets(top: 56, leading: 24, bottom: 24, trailing: 24))
        .frame(minWidth: 390, maxWidth: 390, minHeight: 600, alignment: .topLeading)
        .fixedSize(horizontal: false, vertical: true)
        .background(Color.white)
        .environment(\.wptCaptureMode, true)
        .environment(\.wptBlockFlowFillWidth, 342)
        let r = ImageRenderer(content: view); r.scale = 1
        let cg = try XCTUnwrap(r.cgImage)
        var buf = [UInt8](repeating: 0, count: cg.width * cg.height * 4)
        let ctx = try XCTUnwrap(CGContext(data: &buf, width: cg.width, height: cg.height, bitsPerComponent: 8,
                                          bytesPerRow: cg.width * 4, space: CGColorSpaceCreateDeviceRGB(),
                                          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: cg.width, height: cg.height))
        var box: (Int, Int, Int, Int)? = nil
        for y in 0..<cg.height { for x in 0..<cg.width {
            let i = (y * cg.width + x) * 4
            guard Int(buf[i]) + Int(buf[i + 1]) + Int(buf[i + 2]) < 30 else { continue }   // pure black
            box = box.map { (min($0.0, y), max($0.1, y), min($0.2, x), max($0.3, x)) } ?? (y, y, x, x)
        } }
        return box
    }

    /// RASTER PIN: where the square lands on Catalyst, read off the bitmap
    /// (the reference has rows 56-75, x 24-43). With the rewrite disabled the
    /// same mount reads 51-70 — the wave52-ship ios device capture — which is
    /// what makes this emulation trustworthy. The device-side verdict stays
    /// display-table-body.geometry.py on the gate's own run.
    @MainActor
    func testRasterSquareAtTheReferenceRows() throws {
        let box = try XCTUnwrap(try squareBox(flowRoots(try rootsOf(s006))))
        print("L3-B RASTER READOUT square rows \(box.0)-\(box.1) x \(box.2)-\(box.3)")
        XCTAssertEqual(box.0, 56); XCTAssertEqual(box.1, 75)
        XCTAssertEqual(box.2, 24); XCTAssertEqual(box.3, 43)
    }
}
