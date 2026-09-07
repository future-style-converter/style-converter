//
//  ListMarkerAbsposItemRasterTests.swift
//  Wave 27 (lane NMARK, B-RC5 + B-RC6) — raster pin for the bidi-baked
//  counter-style list item: an `<ol>` whose every `<li>` carries ONLY an
//  absolutely-positioned text run (the bidi bake replaces the item's
//  inline content with a pre-positioned glyph box).
//
//  ## The two defects
//  B-RC5 — iOS painted NO marker at all on those items; web (browser-ref)
//  and Compose both paint one.
//  B-RC6 — iOS laid each item at ~2.2× its baked `height: 31.25px`
//  (measured pitch 67px on the wave27-gate capture vs the reference 32px).
//
//  ## The corrected diagnosis (this file's third test pins it)
//  The reported hypothesis for B-RC6 was that the out-of-flow text run was
//  contributing to the item's height. It is NOT — with the marker
//  suppressed (`list-style-type: none`) the very same tree, abspos child
//  and all, already lays out at the declared 31.25px. Both defects come
//  from the ONE marker row, for the two reasons ListMarkerRow's header
//  spells out. Removing either repair alone reproduces one of them.
//
//  ## The live artifact these shapes come from
//  tools/titan/runs/wave27-gate/sections/css-counter-styles/per-test-ir/
//  wpt__css-counter-styles__arabic-indic__css3-counter-styles-101.json —
//  components `…__1__0-003` (the `ol`), `…__1__0__0-004` (an `li`) and
//  `…__1__0__0__0-005` (its abspos text run), verbatim.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class ListMarkerAbsposItemRasterTests: XCTestCase {

    // MARK: - Fixture (live IR of css3-counter-styles-101, trimmed to 2 items)

    /// The `ol` → `li` → abspos-text subtree, byte-shapes verbatim from the
    /// live per-test IR. Two items are enough to measure the row pitch.
    ///
    /// `listStyleType` is a parameter so the third test can suppress the
    /// marker while changing NOTHING else — that control is what proves the
    /// out-of-flow child is not the height culprit.
    ///
    /// Note `PaddingLeft` carries NO `px`, only `original {v:8,u:EM}`,
    /// exactly as the converter emits an unresolved `em` (CLAUDE.md: `em`
    /// stays runtime-dependent). The runtime resolves it against the
    /// inherited 25px font, giving the 200px that leaves the item's
    /// declared 158px width EXACTLY filling the 358px canvas — the
    /// over-constraint that triggers both defects. Keeping the value
    /// unresolved on the wire is load-bearing here.
    private func counterStyles101(listStyleType: String = "arabic-indic",
                                  markerText: String? = "\u{0661}.") throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"cs101-root-002","name":"cs101-root",
         "properties":[{"type":"FontSize","data":{"px":25.0,
                        "original":{"type":"length","px":25.0}}}],
         "children":[
          {"id":"cs101-ol-003","name":"cs101-ol",
           "properties":[
             {"type":"MarginTop","data":{"px":0.0}},
             {"type":"MarginRight","data":{"px":0.0}},
             {"type":"MarginBottom","data":{"px":0.0}},
             {"type":"MarginLeft","data":{"px":0.0}},
             {"type":"PaddingLeft","data":{"original":{"v":8,"u":"EM"}}},
             {"type":"ListStylePosition","data":"INSIDE"}
           ],
           "meta":{"sourceTag":"ol"},
           "children":[
             \(Self.item(id: "cs101-li-004", glyph: "\u{0661}", type: listStyleType,
                         markerText: markerText)),
             \(Self.item(id: "cs101-li-006", glyph: "\u{0662}", type: listStyleType,
                         markerText: markerText.map { _ in "\u{0662}." }))
           ]}
         ]}
        """.utf8))
    }

    /// One `<li>`: RELATIVE, 158×31.25 border-box, holding a single
    /// absolutely-positioned 13.14×30 text run. `arabic-indic` is a counter
    /// style NEITHER native models, so the UA `ol { list-style-type:
    /// decimal }` (HTML §15.3.7) stands and the marker reads "1." / "2.".
    private static func item(id: String, glyph: String, type: String,
                             markerText: String?) -> String {
        // `meta.markerText` is the wave-27 lane-CBAKE baked marker — the
        // extractor's full css-counter-styles-3 §6 resolution, which is
        // what the live wire now carries for these tests (the pinned
        // wave27-gate IR predates that channel and carries none, which is
        // the `nil` case the control below still exercises).
        let baked = markerText.map { ",\"markerText\":\"\($0)\"" } ?? ""
        return """
        {"id":"\(id)","name":"\(id)",
         "properties":[
           {"type":"ListStyleType","data":"\(type)"},
           {"type":"Width","data":{"type":"length","px":158.0}},
           {"type":"Height","data":{"type":"length","px":31.25}},
           {"type":"BoxSizing","data":"BORDER_BOX"},
           {"type":"Position","data":"RELATIVE"}
         ],
         "meta":{"sourceTag":"li","role":"ws-after"\(baked)},
         "children":[
          {"id":"\(id)-text","name":"\(id)-text","text":"\(glyph)",
           "properties":[
             {"type":"Position","data":"ABSOLUTE"},
             {"type":"Left","data":{"px":26.58}},
             {"type":"Top","data":{"px":0.0}},
             {"type":"Width","data":{"type":"length","px":13.14}},
             {"type":"Height","data":{"type":"length","px":30.0}},
             {"type":"LineHeight","data":{"original":{"type":"length","px":30.0}}},
             {"type":"WhiteSpace","data":"PRE"},
             {"type":"Direction","data":"LTR"},
             {"type":"TextAlign","data":"LEFT"},
             {"type":"Color","data":{"srgb":{"r":0.0,"g":0.0,"b":0.0},
                                     "original":{"r":0,"g":0,"b":0}}},
             {"type":"FontSize","data":{"px":25.0,
                                        "original":{"type":"length","px":25.0}}},
             {"type":"FontStyle","data":"normal"},
             {"type":"FontWeight","data":400}
           ]}
         ]}
        """
    }

    // MARK: - Raster plumbing (AbsposFlexOverlayRasterTests pattern)

    /// Rasterize on the composed capture surface's geometry: a 358px
    /// content column inside the 16px canvas frame, on a 390-wide WHITE
    /// stage at scale 1 (ComposedCaptureCanvas.width / .padding). The
    /// content width is load-bearing — at 390 the row is NOT
    /// over-constrained and neither defect reproduces.
    @MainActor
    private func render(_ comp: IRComponent) throws -> (px: [UInt8], w: Int, h: Int) {
        let view = VStack(alignment: .leading, spacing: 0) {
            ComponentHost(component: comp)
        }
        .frame(width: 358, alignment: .topLeading)
        .padding(16)
        .frame(width: 390, height: 300, alignment: .topLeading)
        .background(Color.white)
        .environment(\.wptCaptureMode, true)
        .environment(\.styleViewport, StyleViewport(width: 390, height: 844,
                                                    rootContainingBlock: 358))
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
        return (buf, w, h)
    }

    /// Contiguous horizontal ink bands: one per rendered item row, as
    /// `(top, bottom, leftmostX, rightmostX)`. Measuring bands rather than
    /// exact glyph rects keeps the pins independent of which font the
    /// system substitutes for the arabic-indic digits.
    ///
    /// Wave 28 (lane MC) added `right`: the item's absolutely positioned
    /// reference glyph is the band's RIGHTMOST ink, so that edge is what
    /// moves when the marker displaces the item's principal box.
    private func inkBands(_ img: (px: [UInt8], w: Int, h: Int))
        -> [(top: Int, bottom: Int, left: Int, right: Int)] {
        var bands: [(top: Int, bottom: Int, left: Int, right: Int)] = []
        var cur: (top: Int, bottom: Int, left: Int, right: Int)?
        for y in 0..<img.h {
            let xs = (0..<img.w).filter { x in
                let i = (y * img.w + x) * 4
                return img.px[i] < 128 && img.px[i + 1] < 128 && img.px[i + 2] < 128
            }
            guard let first = xs.first, let last = xs.last else {
                if let c = cur { bands.append(c); cur = nil }
                continue
            }
            if var c = cur, y == c.bottom + 1 {
                c.bottom = y
                c.left = min(c.left, first)
                c.right = max(c.right, last)
                cur = c
            } else {
                if let c = cur { bands.append(c) }
                cur = (top: y, bottom: y, left: first, right: last)
            }
        }
        if let c = cur { bands.append(c) }
        return bands
    }

    // MARK: - B-RC5: the marker paints

    /// Every `<li>` under an `<ol>` gets a marker box, and it paints even
    /// when the item's declared width consumes the whole content column.
    ///
    /// The marker is the row's LEFTMOST ink: the canvas frame (16) plus the
    /// `<ol>`'s resolved 8em padding (200) puts the item's leading edge at
    /// x≈216, while the abspos glyph sits a further ~26.58px in. Before the
    /// fix the leftmost ink of every band was ≥247 — glyph only, no marker
    /// anywhere on the canvas.
    @MainActor
    func testListItemWithOnlyAbsposChildrenStillPaintsItsMarker() throws {
        let bands = inkBands(try render(try counterStyles101()))
        XCTAssertEqual(bands.count, 2, "expected one ink band per item: \(bands)")
        for band in bands {
            XCTAssertLessThan(band.left, 240,
                "leftmost ink at x=\(band.left) — B-RC5: the marker box was "
                + "compressed out of existence by the item's declared width "
                + "(css-lists-3 §3.5 makes it shrink-to-fit, not flexible)")
        }
    }

    // MARK: - B-RC6: the marker does not stretch the item

    /// The item's used height stays its declared 31.25px. Measured as the
    /// pitch between the two items' ink bands; the wave27-gate capture had
    /// 67px here.
    @MainActor
    func testMarkerRowDoesNotInflateTheDeclaredItemHeight() throws {
        let bands = inkBands(try render(try counterStyles101()))
        XCTAssertEqual(bands.count, 2, "expected one ink band per item: \(bands)")
        let pitch = bands[1].top - bands[0].top
        XCTAssertEqual(Double(pitch), 31.25, accuracy: 3.0,
            "item pitch \(pitch)px ≠ the declared 31.25px height — B-RC6: "
            + "the marker box is stretching the item's principal box "
            + "(css-sizing-3 §5.1: a definite height IS the used height)")
    }

    // MARK: - The control that corrects the reported diagnosis

    /// With the marker suppressed and the out-of-flow child left exactly as
    /// it is, the pitch is ALREADY the declared 31.25px. So the abspos text
    /// run never contributed to its containing block's height (css-position-3
    /// §2.1 was honored all along) — the marker row was the whole story.
    /// This guards against a future "fix" aimed at the wrong mechanism.
    @MainActor
    func testAbsposOnlyChildNeverContributedToTheItemHeight() throws {
        // No baked marker AND `list-style-type: none` — the ONE
        // configuration in which no marker box is generated at all.
        let bands = inkBands(try render(
            try counterStyles101(listStyleType: "none", markerText: nil)))
        XCTAssertEqual(bands.count, 2, "expected one ink band per item: \(bands)")
        let pitch = bands[1].top - bands[0].top
        XCTAssertEqual(Double(pitch), 31.25, accuracy: 3.0,
            "marker-free pitch \(pitch)px ≠ 31.25px — the out-of-flow child "
            + "IS sizing its containing block, which would be a second, "
            + "separate defect from the marker row this lane repaired")
    }

    // MARK: - Wave 28 (lane MC): the marker must not MOVE the item either

    /// Generating a marker box must leave the item's principal box exactly
    /// where a marker-free item's would be.
    ///
    /// A/B against the SAME tree with the marker suppressed, which makes
    /// the pin independent of the marker's own glyph width and of which
    /// font the system substitutes. The item's absolutely positioned
    /// reference glyph is the band's rightmost ink; css-position-3 §2.1
    /// anchors it to the ITEM's box, so if the marker displaced that box
    /// the two runs' right edges diverge by `markerWidth + gapPt`.
    ///
    /// This is the iOS half of the wave-28 diagnosis: on the live
    /// wave27-final capture the reference column sat at x≈264–280 against
    /// the browser-ref's 244–253, on every one of the 12
    /// css-counter-styles tests, purely from this displacement.
    @MainActor
    func testInsideMarkerDoesNotDisplaceTheItemsPrincipalBox() throws {
        let withMarker = inkBands(try render(try counterStyles101()))
        let markerFree = inkBands(try render(
            try counterStyles101(listStyleType: "none", markerText: nil)))
        XCTAssertEqual(withMarker.count, 2, "with marker: \(withMarker)")
        XCTAssertEqual(markerFree.count, 2, "marker-free: \(markerFree)")
        for (i, pair) in zip(withMarker, markerFree).enumerated() {
            XCTAssertEqual(Double(pair.0.right), Double(pair.1.right), accuracy: 2.0,
                "item \(i): the abspos reference glyph's right edge moved from "
                + "\(pair.1.right) (no marker) to \(pair.0.right) (marker) — the "
                + "`inside` marker is displacing the item's principal box, but "
                + "css-lists-3 §3.5 makes it the item's FIRST INLINE BOX, inside "
                + "that box and unable to move it")
        }
    }

    /// The placement truth table — the cheap, raster-free half, and the
    /// one a future refactor is most likely to widen by accident.
    func testInsideOverlayPlacementIsChosenPerPositionAndContent() {
        // The counter-styles shape: `inside`, no in-flow text ⇒ overlay.
        XCTAssertTrue(ListMarkerRow.rendersInsideOverlay(
            position: .inside, itemExposesTextBaseline: false))
        // An item WITH text needs the marker to push it along the line.
        XCTAssertFalse(ListMarkerRow.rendersInsideOverlay(
            position: .inside, itemExposesTextBaseline: true))
        // `outside` belongs in the item's margin area, not at its origin.
        XCTAssertFalse(ListMarkerRow.rendersInsideOverlay(
            position: .outside, itemExposesTextBaseline: false))
        // Unresolvable position ⇒ unchanged behaviour, never a guess.
        XCTAssertFalse(ListMarkerRow.rendersInsideOverlay(
            position: nil, itemExposesTextBaseline: false))
    }

    // MARK: - The constants themselves

    /// Cheap, device-free pin of the row's two layout decisions — a
    /// silent flip back to unconditional `.firstTextBaseline`
    /// reintroduces B-RC6 on every item whose box exposes no baseline.
    func testMarkerRowAlignmentIsChosenPerItem() {
        XCTAssertEqual(ListMarkerRow.rowAlignment(itemExposesTextBaseline: false), .top)
        XCTAssertEqual(ListMarkerRow.rowAlignment(itemExposesTextBaseline: true),
                       .firstTextBaseline)
        XCTAssertEqual(ListMarkerRow.gapPt, 4)
    }

    /// The baseline predicate: an item whose ONLY child is out of flow
    /// exposes none (the bidi-baked shape), the same item with the child
    /// in flow does, and the item's own leading text counts too.
    func testItemExposesTextBaselineIgnoresOutOfFlowDescendants() throws {
        let tree = try counterStyles101()
        let items = try XCTUnwrap(tree.children?.first?.children)
        XCTAssertEqual(items.count, 2)
        for item in items {
            XCTAssertFalse(ListMarkerRow.itemExposesTextBaseline(item),
                           "abspos-only item must expose no first baseline")
        }
        // Same item with the run left in normal flow.
        let inFlow = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"li","name":"li","meta":{"sourceTag":"li"},
         "children":[{"id":"t","name":"t","text":"x","properties":[]}]}
        """.utf8))
        XCTAssertTrue(ListMarkerRow.itemExposesTextBaseline(inFlow))
        // …and an item carrying its own leading text.
        let ownText = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"li2","name":"li2","text":"hello","properties":[]}
        """.utf8))
        XCTAssertTrue(ListMarkerRow.itemExposesTextBaseline(ownText))
    }
}
