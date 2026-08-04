//
//  ListMarkerInFlowItemRasterTests.swift
//  Wave 28 (lane MC, skeptic pass) — raster pin for the OTHER `<li>` shape
//  in the live css-counter-styles corpus: an item whose content is its own
//  IN-FLOW text run, with no absolutely positioned descendant anywhere.
//
//  ## Why this file exists next to ListMarkerAbsposItemRasterTests
//  That file pins the abspos-only item (css3-counter-styles-101/102/103)
//  — the shape `ListMarkerRow.rendersInsideOverlay` moves onto the overlay
//  placement. It is only 3 of the section's 12 tests. The other 9
//  (armenian 006-009, bengali 116-118, cambodian 158-159) are THIS shape:
//  measured over the live per-test IRs of
//  tools/titan/runs/wave27-final/sections/css-counter-styles/per-test-ir/,
//  104 of the section's 139 `<li>` carry their own `text` and no
//  out-of-flow child, so `itemExposesTextBaseline` is true for them and
//  they deliberately KEEP the HStack. Nothing pinned that, which left the
//  larger half of the corpus resting on the overlay branch's tests.
//
//  ## What this pins
//  The `itemExposesTextBaseline` gate on `rendersInsideOverlay`. Widening
//  the overlay to items that DO have in-flow text (dropping that gate, or
//  letting the predicate return false for an item whose only content is
//  its own `text`) makes the marker paint ON TOP of the first word instead
//  of ahead of it — a collision no unit-level truth table can see, because
//  both configurations return a Bool and only the raster distinguishes
//  them. The corpus shape is exactly the one that would collide.
//
//  ## The live artifact these shapes come from
//  …/per-test-ir/wpt__css-counter-styles__armenian__css3-counter-styles-007
//  .json — components `…__1__0-112` (an `ol`, PaddingLeft 8em,
//  ListStylePosition INSIDE, meta.attrs.start "10") and `…__1__0__0-113`
//  (its single `li`, ListStyleType armenian, text "Ժ", markerText "Ժ.").
//  That test declares 24 such `<ol>`s, one item each; three are enough to
//  measure the row pitch.
//
//  Browser-ref columns for the first row, measured off
//  …/screenshots/wpt__css-counter-styles__armenian__css3-counter-styles-007
//  .png at the same 390-wide capture geometry: marker ink x 218–239, item
//  text ink x 250–266, row pitch 31–32px.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class ListMarkerInFlowItemRasterTests: XCTestCase {

    // MARK: - Fixture (live IR of css3-counter-styles-007, trimmed to 3 rows)

    /// N sibling `<ol start=…>` containers, one `<li>` each, byte-shapes
    /// verbatim from the live per-test IR.
    ///
    /// `PaddingLeft` carries NO `px`, only `original {v:8,u:EM}` — exactly
    /// as the converter emits an unresolved `em` (CLAUDE.md: `em` stays
    /// runtime-dependent). The runtime resolves it against the inherited
    /// 25px font for the 200px that puts the item's leading edge at
    /// x = 16 (canvas frame) + 200 = 216, which is where the browser-ref's
    /// 218 marker ink starts. Keeping it unresolved on the wire is
    /// load-bearing for the absolute columns asserted below.
    private func counterStyles007() throws -> IRComponent {
        // (start, item text, baked marker) — the first three rows of the
        // live test: Ժ=10, ԺԱ=11, ԺԲ=12 in the armenian counter style.
        let rows = [("10", "\u{0538}", "\u{0538}."),
                    ("11", "\u{0538}\u{0531}", "\u{0538}\u{0531}."),
                    ("12", "\u{0538}\u{0532}", "\u{0538}\u{0532}.")]
        let ols = rows.map { (start, text, marker) in
            """
            {"id":"ol\(start)","name":"ol\(start)",
             "properties":[
               {"type":"MarginTop","data":{"px":0.0}},
               {"type":"MarginRight","data":{"px":0.0}},
               {"type":"MarginBottom","data":{"px":0.0}},
               {"type":"MarginLeft","data":{"px":0.0}},
               {"type":"PaddingLeft","data":{"original":{"v":8,"u":"EM"}}},
               {"type":"ListStylePosition","data":"INSIDE"}
             ],
             "meta":{"sourceTag":"ol","role":"ws-after","attrs":{"start":"\(start)"}},
             "children":[
              {"id":"li\(start)","name":"li\(start)","text":"\(text)",
               "properties":[{"type":"ListStyleType","data":"armenian"}],
               "meta":{"sourceTag":"li","markerText":"\(marker)"}}
             ]}
            """
        }
        return try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"cs007-root","name":"cs007-root",
         "properties":[{"type":"FontSize","data":{"px":25.0,
                        "original":{"type":"length","px":25.0}}}],
         "children":[\(ols.joined(separator: ","))]}
        """.utf8))
    }

    // MARK: - Raster plumbing (ListMarkerAbsposItemRasterTests pattern)

    /// Rasterize on the composed capture surface's geometry: a 358px
    /// content column inside the 16px canvas frame, on a 390-wide WHITE
    /// stage at scale 1 (ComposedCaptureCanvas.width / .padding), so the
    /// x columns below are directly comparable to the browser-ref PNG.
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

    /// One entry per contiguous horizontal ink band (= one rendered row),
    /// each carrying the band's COLUMN GROUPS: runs of inked x separated by
    /// a gap wider than `split`. The groups are the measurement that
    /// matters here — a marker ahead of the text is two groups, a marker
    /// painted on top of it is one.
    ///
    /// 3px matches the split used to read the browser-ref PNGs, so the
    /// group boundaries are comparable to the numbers in the file header.
    private func bandColumnGroups(_ img: (px: [UInt8], w: Int, h: Int),
                                  split: Int = 3) -> [(top: Int, groups: [(Int, Int)])] {
        var out: [(top: Int, groups: [(Int, Int)])] = []
        var bandTop: Int?
        var inked = Set<Int>()
        func flush(_ endY: Int) {
            guard let top = bandTop, !inked.isEmpty else { bandTop = nil; return }
            let xs = inked.sorted()
            var groups: [(Int, Int)] = []
            var start = xs[0], prev = xs[0]
            for x in xs.dropFirst() {
                if x > prev + split { groups.append((start, prev)); start = x }
                prev = x
            }
            groups.append((start, prev))
            out.append((top: top, groups: groups))
            bandTop = nil
            inked = []
            _ = endY
        }
        for y in 0..<img.h {
            var rowInk: [Int] = []
            for x in 0..<img.w {
                let i = (y * img.w + x) * 4
                if img.px[i] < 128 && img.px[i + 1] < 128 && img.px[i + 2] < 128 { rowInk.append(x) }
            }
            if rowInk.isEmpty { flush(y) } else {
                if bandTop == nil { bandTop = y }
                inked.formUnion(rowInk)
            }
        }
        flush(img.h)
        return out
    }

    // MARK: - The pin

    /// An `inside` marker on an item that HAS in-flow text is painted
    /// AHEAD of that text, never on top of it.
    ///
    /// This is the negative half of `ListMarkerRow.rendersInsideOverlay`'s
    /// second gate, on the shape that actually occurs: 104 of the live
    /// css-counter-styles section's 139 `<li>` are this shape. Dropping the
    /// `itemExposesTextBaseline` gate collapses the row's two ink groups
    /// into one overlapping group — the marker glyphs drawn over the item's
    /// first word — which every Bool-level truth table still passes.
    ///
    /// Absolute columns are asserted loosely (±6px) because the system font
    /// substituted for the armenian glyphs is not the browser's; the
    /// SEPARATION is the strict part.
    @MainActor
    func testInsideMarkerOnAnItemWithTextPaintsAheadOfIt() throws {
        let bands = bandColumnGroups(try render(try counterStyles007()))
        XCTAssertEqual(bands.count, 3, "expected one ink band per item: \(bands)")

        let first = try XCTUnwrap(bands.first)
        // A `guard`, not a bare assertion: the column reads below index
        // into `groups`, and the one failure this test exists to catch is
        // precisely "there is only one group". Letting it fall through
        // would trap on the out-of-range read and abort the whole test
        // bundle instead of reporting this message (observed while
        // verifying the pin discriminates).
        guard first.groups.count >= 2 else {
            return XCTFail(
                "the first row rendered as ONE ink group \(first.groups) — the "
                + "marker is overlapping the item's own text. css-lists-3 §3.2 "
                + "makes an `inside` marker the item's FIRST INLINE BOX: it "
                + "precedes the text on the line, it does not paint over it. "
                + "(This is exactly what dropping rendersInsideOverlay's "
                + "itemExposesTextBaseline gate looks like — verified by "
                + "stubbing that gate out: the two groups collapse to one.)")
        }

        let marker = first.groups[0]
        let text = first.groups[1]
        // The marker starts at the item's inline edge: 16px canvas frame +
        // the <ol>'s resolved 8em (200px) padding = 216. Browser-ref: 218.
        XCTAssertEqual(Double(marker.0), 217.0, accuracy: 6.0,
            "marker ink starts at x=\(marker.0), not at the item's inline "
            + "edge (~216; browser-ref 218) — groups \(first.groups)")
        // …and the text follows it, with a gap in the marker-suffix range.
        // Browser-ref gap is 11px (250 − 239); iOS pays gapPt = 4 plus the
        // glyphs' own side bearings.
        let gap = text.0 - marker.1
        XCTAssertTrue((2...16).contains(gap),
            "marker→text gap \(gap)px is outside the plausible marker-suffix "
            + "range (browser-ref 11px) — groups \(first.groups)")

        // The row pitch stays the single-line box, not a marker-inflated
        // one (browser-ref 31–32px at this 25px font).
        let pitch = bands[1].top - bands[0].top
        XCTAssertEqual(Double(pitch), 32.0, accuracy: 4.0,
            "row pitch \(pitch)px — the marker row is changing the item's "
            + "line box (browser-ref 31–32px); bands \(bands.map { $0.top })")
    }

    /// The predicate itself, on the SAME live shape: an `<li>` whose only
    /// content is its own text run exposes a baseline, so
    /// `rendersInsideOverlay` declines the overlay for it.
    ///
    /// Cheap companion to the raster above — it names WHICH decision the
    /// raster is protecting, so a failure pair localizes immediately.
    func testInFlowTextItemsDeclineTheOverlayPlacement() throws {
        let tree = try counterStyles007()
        let ols = try XCTUnwrap(tree.children)
        XCTAssertEqual(ols.count, 3)
        for ol in ols {
            let item = try XCTUnwrap(ol.children?.first)
            XCTAssertTrue(ListMarkerRow.itemExposesTextBaseline(item),
                "an <li> carrying its own in-flow text exposes a first baseline")
            XCTAssertFalse(ListMarkerRow.rendersInsideOverlay(
                position: .inside, itemExposesTextBaseline:
                    ListMarkerRow.itemExposesTextBaseline(item)),
                "…so it keeps the HStack: the marker must push that text "
                + "along the line, which an overlay cannot do")
        }
    }
}
