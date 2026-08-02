//
//  FlexWrapStretchTests.swift
//  Wave 25, lane ISTRETCH — CAL-RC5, the wrap path's cross-axis stretch.
//
//  PRODUCT-PATH raster pins: real IR decoded into IRComponent, rendered
//  through ComponentRenderer, rasterized with ImageRenderer. Nothing
//  synthetic is fed to the Layout.
//
//  WHY THAT MATTERS (round-2 skeptic finding). The first version of this
//  file fed FlowLayout four bare `Color` views. A Color is GREEDY: it
//  takes whatever size it is proposed, so the pins went green while the
//  product still rendered 50×30 boxes — SwiftUI's `place(at:proposal:)`
//  is advisory and an IR child ignores it, answering `sizeThatFits` with
//  its intrinsic cross for every proposal including `.infinity`. Those
//  pins were a false positive and are gone; every raster below goes
//  through ComponentRenderer, the same path the harness captures.
//
//  The geometry pinned here is WPT css-gaps flex-gap-decorations-002's:
//  a 110×110 wrap container, 10px gaps, four `width: 50px` items with no
//  height ⇒ two lines of two 50×50 squares (css-flexbox-1 §8.4 item
//  stretch over §9.6 line stretch). The `*-rule-*` ink those refs also
//  paint is a different lane — GapDecorationSurfaceTests owns it — so
//  the IR here carries the geometry without the rules.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

@available(iOS 16.0, *)
final class FlexWrapStretchTests: XCTestCase {

    // MARK: - Product-path raster helper

    /// Build ref-002's container IR with overridable pieces and render it
    /// through ComponentRenderer on a white 160×160 stage (bigger than
    /// the 110×110 container, so "the box did not grow" reads as white
    /// instead of falling off the image).
    ///
    /// - Parameters:
    ///   - containerExtra: extra container properties (JSON fragments).
    ///   - definiteHeight: emit `Height: 110px` on the container — the
    ///     §9.6 leftover-space precondition.
    ///   - itemExtra: extra properties on every item.
    ///   - perItemExtra: per-item extra properties, one entry per item —
    ///     overrides the item COUNT too (nil keeps ref-002's four).
    @MainActor
    private func render(containerExtra: [String] = [],
                        definiteHeight: Bool = true,
                        itemExtra: [String] = [],
                        perItemExtra: [[String]]? = nil)
        throws -> (px: [UInt8], w: Int, h: Int) {
        // One flex item: `width: 50px`, a SPEC-blue background (not
        // Color.blue, which is the system #0088FF), no height.
        let item = { (n: Int, extra: [String]) -> String in
            let props = ([
                #"{"type":"Width","data":{"type":"length","px":50}}"#,
                #"{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}}"#
            ] + itemExtra + extra).joined(separator: ",")
            return #"{"id":"i\#(n)","name":"I\#(n)","properties":[\#(props)]}"#
        }
        let extras = perItemExtra ?? Array(repeating: [], count: 4)
        let kids = extras.enumerated()
            .map { item($0.offset + 1, $0.element) }.joined(separator: ",")
        // The container: the wrap flex box the ref declares.
        let containerProps = ([
            #"{"type":"Display","data":"FLEX"}"#,
            #"{"type":"FlexWrap","data":"WRAP"}"#,
            #"{"type":"Width","data":{"type":"length","px":110}}"#
        ] + (definiteHeight
             ? [#"{"type":"Height","data":{"type":"length","px":110}}"#] : [])
          + [
            #"{"type":"ColumnGap","data":{"type":"length","px":10}}"#,
            #"{"type":"RowGap","data":{"type":"length","px":10}}"#
        ] + containerExtra).joined(separator: ",")
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data(#"""
        {"id":"box2","name":"Box2","properties":[\#(containerProps)],
         "children":[\#(kids)]}
        """#.utf8))
        let view = ZStack(alignment: .topLeading) {
            // Opaque stage first so "no ink here" reads as white.
            Color.white
            ComponentRenderer(component: comp)
        }.frame(width: 160, height: 160, alignment: .topLeading)
        // Scale 1 = one buffer pixel per logical point, like the harness.
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        // Redraw into a known RGBA8 layout for format-stable byte reads.
        let w = cg.width, h = cg.height
        var buf = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = try XCTUnwrap(CGContext(
            data: &buf, width: w, height: h, bitsPerComponent: 8,
            bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        return (buf, w, h)
    }

    /// RGB triple at a raster point.
    private func rgb(_ img: (px: [UInt8], w: Int, h: Int),
                     _ x: Int, _ y: Int) -> (r: UInt8, g: UInt8, b: UInt8) {
        let i = (y * img.w + x) * 4
        return (img.px[i], img.px[i + 1], img.px[i + 2])
    }

    /// ±24 antialiasing tolerance, as in the other raster pins.
    private func matches(_ c: (r: UInt8, g: UInt8, b: UInt8),
                         _ t: (r: Int, g: Int, b: Int)) -> Bool {
        abs(Int(c.r) - t.r) <= 24 && abs(Int(c.g) - t.g) <= 24 && abs(Int(c.b) - t.b) <= 24
    }

    private let blue = (r: 0, g: 0, b: 255)
    private let white = (r: 255, g: 255, b: 255)

    /// Assert a whole list of points at once, naming the failure.
    private func expect(_ img: (px: [UInt8], w: Int, h: Int),
                        _ points: [(Int, Int)],
                        _ target: (r: Int, g: Int, b: Int),
                        _ what: String,
                        file: StaticString = #filePath, line: UInt = #line) {
        for (x, y) in points {
            XCTAssertTrue(matches(rgb(img, x, y), target),
                          "\(what): (\(x),\(y)) was \(rgb(img, x, y))",
                          file: file, line: line)
        }
    }

    // MARK: - §8.4 item stretch, end to end

    /// THE pin this lane exists for: ref-002's four width-only items
    /// render as four 50×50 squares through the real renderer.
    ///
    /// §9.6 gives each of the two lines (110 − 10 row-gap) / 2 = 50 of
    /// cross space, and §8.4 makes each auto-cross item fill its line.
    /// Row 45 is the discriminator — inside line 1's 50pt band but past
    /// the item's intrinsic 30pt box, so it can only be inked if the
    /// forced cross frame reached the child's own paint chain.
    @MainActor
    func testRefShapeItemsRenderAsFiftySquares() throws {
        let img = try render()
        // The four squares: centres, and the far corner of each band.
        expect(img, [(25, 25), (85, 25), (25, 85), (85, 85)], blue, "item centre")
        expect(img, [(25, 45), (85, 45), (25, 105), (85, 105)], blue,
               "item did not fill its line (the §8.4 stretch)")
        // Line 1 really ends at 50 and line 2 really starts at 60.
        expect(img, [(25, 49), (25, 61)], blue, "line band edge")
        // The gaps stay bare: 10pt column gap at x 50–60, row gap y 50–60.
        expect(img, [(55, 25), (55, 85)], white, "column gap inked")
        expect(img, [(25, 55), (85, 55)], white, "row gap inked")
        // …and the container did not grow past its declared 110×110 box.
        expect(img, [(115, 25), (25, 115)], white, "painted outside the box")
    }

    /// An item with an EXPLICIT cross size is never overridden
    /// (css-flexbox-1 §8.4: stretch applies to an AUTO cross size only).
    /// The LINES still stretch — base 20 + (110 − 40 − 10)/2 = 50 — so
    /// line 2 starts at 60 while each item stays 20 tall.
    @MainActor
    func testExplicitItemHeightIsNeverStretched() throws {
        let img = try render(itemExtra: [
            #"{"type":"Height","data":{"type":"length","px":20}}"#])
        // Each item paints its own 20pt band at its line's start edge…
        expect(img, [(25, 10), (85, 10), (25, 70), (85, 70)], blue, "item box")
        // …and nothing below it, on either line.
        expect(img, [(25, 45), (25, 90), (25, 105)], white,
               "explicit-height item was stretched")
    }

    // MARK: - The guards (what must NOT stretch)

    /// No definite container cross size ⇒ no leftover space to divide
    /// (css-align-3 §5.3), so the lines hug their items and nothing
    /// stretches. `fixtures/visual-test.json`'s `Flex_Wrap` is exactly
    /// this shape — it declares a width and no height — so this pin is
    /// what keeps the committed baseline inert.
    @MainActor
    func testHuggingContainerStretchesNothing() throws {
        let img = try render(definiteHeight: false)
        // Two lines of intrinsic 30pt boxes: y 0–30 and y 40–70.
        expect(img, [(25, 15), (85, 15), (25, 45), (85, 45)], blue, "item box")
        // The 10pt row gap between them, and everything below line 2.
        expect(img, [(25, 35), (25, 75), (25, 105)], white,
               "hugging container distributed cross space")
    }

    /// A non-stretch `align-items` keeps the item at its intrinsic cross
    /// size at the line's start edge. The LINE is still 50 tall (§9.6
    /// sizes lines, §8.4 sizes items — two different rules), so line 2
    /// still starts at 60.
    @MainActor
    func testAlignItemsFlexStartKeepsTheIntrinsicCross() throws {
        let img = try render(containerExtra: [
            #"{"type":"AlignItems","data":"FLEX_START"}"#])
        // Top of each line is inked (the 30pt intrinsic box)…
        expect(img, [(25, 15), (85, 15), (25, 65), (85, 65)], blue, "item box")
        // …and the rest of each 50pt line band is bare.
        expect(img, [(25, 45), (25, 105)], white, "start-aligned item stretched")
    }

    // MARK: - The refusals that keep the two runs in agreement (skeptic)

    /// A DECLARED cross size the static plan cannot resolve to points —
    /// here `height: 50%`, which the plan refuses (`allowPercent: false`)
    /// but the RUNTIME resolves against the published containing block —
    /// must abandon the whole container, not be mistaken for an auto
    /// cross size.
    ///
    /// It is not the same thing as an absent height: StyleBuilder's fold
    /// only adopts an injected height when `size.height == nil`, so such
    /// an item renders at its own resolved size (55 = 50% of 110) while
    /// the plan was counting it as a stretching 30pt box. Before the
    /// guard the lines came out 55 + 10 + 50 = 115 inside a declared
    /// 110pt box — the container OVERFLOWED, and by more than the
    /// pre-wave-25 hug did.
    @MainActor
    func testUnresolvablePercentCrossRefusesTheContainer() throws {
        let img = try render(perItemExtra: [
            [#"{"type":"Height","data":{"type":"percentage","value":50}}"#],
            [], [], []])
        // The percent item paints its own 55pt box (item 1, line 1)…
        expect(img, [(25, 50)], blue, "percent item lost its own height")
        // …its auto-cross neighbours are back to the honest 30pt hug…
        expect(img, [(85, 45)], white, "container was planned from a fiction")
        // …and FlowLayout's OWN measure-time §9.6 still runs on the real
        // measurements: base 55 / 30, leftover 110 − 85 − 10 = 15, so the
        // lines are 62.5 and 37.5 and line 2 starts at 72.5.
        expect(img, [(25, 60)], white, "line 1 band inked past the 55pt item")
        expect(img, [(25, 80)], blue, "line 2 did not start at 72.5")
        // The stacked lines sum to exactly 110 — the point of the pin is
        // that they stay INSIDE the declared box instead of the 115pt
        // overflow the missing guard produced.
        expect(img, [(25, 105)], white, "lines overflowed the declared box")
    }

    /// A `display: none` child contributes NO Layout subview, so counting
    /// it in the build-time plan makes the two runs disagree about how
    /// many items there are and where the lines break. Three items with
    /// the middle one hidden: the plan used to break 2 lines of 50 while
    /// FlowLayout made ONE 110pt line, and the two survivors painted 50pt
    /// tall inside a 110pt band. The guard refuses instead, leaving the
    /// honest 30pt hug.
    @MainActor
    func testDisplayNoneChildRefusesTheContainer() throws {
        let img = try render(perItemExtra: [
            [], [#"{"type":"Display","data":"NONE"}"#], []])
        // Two visible items on one line, at their intrinsic 30pt box…
        expect(img, [(25, 15), (85, 15)], blue, "survivors did not render")
        // …and nothing at the 50pt band the mismatched plan injected.
        expect(img, [(25, 45), (85, 45)], white,
               "stretched from a line count FlowLayout never produced")
    }

    /// §9.6's equal division is gated on `align-content`, not just on a
    /// definite cross size (css-align-3 §5.3): under `space-between` the
    /// leftover stays FREE SPACE and merely positions the line block, so
    /// the lines keep their hypothetical 30pt cross. Compose's
    /// FlexWrapLines applies the identical `alignContentStretches`
    /// precondition — without this gate the two runtimes would place the
    /// lines at different coordinates the moment a fixture pairs
    /// `align-content` with `flex-wrap`.
    @MainActor
    func testNonStretchAlignContentLeavesTheLinesHypothetical() throws {
        let img = try render(containerExtra: [
            #"{"type":"AlignContent","data":"SPACE_BETWEEN"}"#])
        // Lines hug their items again: y 0–30 and y 40–70.
        expect(img, [(25, 15), (25, 45)], blue, "item box")
        // Nothing reaches the bottom of the 110pt box…
        expect(img, [(25, 105)], white, "space-between distributed the leftover")
        // …and the control case (no align-content) does — the two
        // renders differ ONLY by the keyword, so this really is the gate.
        expect(try render(), [(25, 105)], blue, "control: stretch case regressed")
    }
}
