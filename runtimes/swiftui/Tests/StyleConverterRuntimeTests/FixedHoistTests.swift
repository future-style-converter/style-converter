//
//  FixedHoistTests.swift
//  Wave 17 — the out-of-flow contract (lane IOS): F1 fixed → viewport,
//  F2 absolute → containing-block channels, F3 no flow-slot contribution.
//
//  css-position-3 §2.1/§3.1/§3.5: out-of-flow boxes leave flow (no space
//  reserved), their insets are ABSOLUTE anchors against their containing
//  block (the viewport for fixed; the initial containing block — our
//  unpadded canvas — for un-ancestored absolute), never deltas added to
//  the natural flow position. The confirmed wave-17 diagnosis was exactly
//  that delta bug: parent flow slot + inset compounded.
//
//  Two layers of pins, mirroring the repo's device-free discipline:
//    • PURE-TRANSFORM pins on FixedHoist.split — the flow/hoist
//      classification (S1/S2/S5 membership, nested-fixed stripping at
//      any depth, nested-absolute preservation, fixed-free identity);
//    • RASTER pins via ImageRenderer on a composed-canvas replica (the
//      exact frame chain ComposedCaptureCanvas builds: 358 content box,
//      16px pad, 390×600 white canvas, canvas-root FixedHoistOverlay) —
//      the same capture path the harness uses, asserting the S-table
//      geometry pixel-by-pixel (PositionedContainerPaintOrderTests
//      pattern). S3 is the required raster proof: a fixed child of a
//      MOVED relative parent paints at the child's own canvas coords,
//      not compounded.
//
//  Wire-shape literals follow the live converter output already pinned
//  by FidelityWave3Tests ("Position":"ABSOLUTE"/"FIXED", {"px":N} insets,
//  {"type":"length","px":N} sizes) and BordersTests (Border* longhands).
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class FixedHoistTests: XCTestCase {

    // MARK: - Component builders (wire-shape JSON, decoded like the harness)

    /// Decode a component from wire-shaped JSON — the same decode path
    /// production IR takes, so these pins exercise real extraction.
    private func component(_ json: String) throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    /// A positioned colored box. `position` is the WIRE keyword
    /// ("FIXED"/"ABSOLUTE"/"RELATIVE"); nil sides are omitted; `rgb`
    /// picks one of the pure primaries so raster samples are unambiguous.
    private func box(id: String, position: String? = nil,
                     left: Double? = nil, top: Double? = nil,
                     w: Double, h: Double,
                     rgb: (Double, Double, Double),
                     childrenJSON: String? = nil) throws -> IRComponent {
        // Assemble the property list from the declared pieces only —
        // omitted insets stay omitted (CSS auto), matching real IR.
        var props: [String] = []
        if let p = position { props.append(#"{"type":"Position","data":"\#(p)"}"#) }
        if let l = left { props.append(#"{"type":"Left","data":{"px":\#(l)}}"#) }
        if let t = top { props.append(#"{"type":"Top","data":{"px":\#(t)}}"#) }
        props.append(#"{"type":"Width","data":{"type":"length","px":\#(w)}}"#)
        props.append(#"{"type":"Height","data":{"type":"length","px":\#(h)}}"#)
        props.append(#"{"type":"BackgroundColor","data":{"srgb":{"r":\#(rgb.0),"g":\#(rgb.1),"b":\#(rgb.2)}}}"#)
        // Children ride verbatim when supplied (nested pins).
        let childrenField = childrenJSON.map { #","children":[\#($0)]"# } ?? ""
        return try component(
            #"{"id":"\#(id)","name":"x","properties":[\#(props.joined(separator: ","))]\#(childrenField)}"#)
    }

    // MARK: - Pure-transform pins (FixedHoist.split)

    /// S1/S2/S5 membership: fixed and absolute ROOTS hoist (no flow
    /// space), relative/static roots stay in flow, document order holds
    /// in both halves.
    func testSplitHoistsOutOfFlowRootsAndKeepsFlowOrder() throws {
        let roots = [
            try box(id: "f", position: "FIXED", left: 100, top: 0, w: 50, h: 50, rgb: (1, 0, 0)),
            try box(id: "a", position: "ABSOLUTE", left: 100, top: 0, w: 50, h: 50, rgb: (0, 0, 1)),
            try box(id: "r", position: "RELATIVE", left: 10, w: 50, h: 50, rgb: (0, 1, 0)),
            try box(id: "s", w: 50, h: 50, rgb: (0, 1, 0)),
        ]
        let split = FixedHoist.split(roots: roots)
        // Flow keeps ONLY the in-flow roots, in order (S5's precondition:
        // nothing reserves space for the hoisted boxes).
        XCTAssertEqual(split.flow.map(\.id), ["r", "s"],
                       "in-flow roots must stay in the flow stack, in document order — out-of-flow roots must reserve no slot (css-position-3 §2.1)")
        // Hoisted carries BOTH out-of-flow schemes (measured web behavior:
        // absolute AND fixed roots anchor at the unpadded canvas), in order.
        XCTAssertEqual(split.hoisted.map(\.id), ["f", "a"],
                       "absolute and fixed ROOTS must both hoist to the canvas overlay, in document order")
    }

    /// F1 at depth: a `position: fixed` DESCENDANT strips out of its
    /// parent's children (any depth), while its in-flow sibling stays.
    func testSplitStripsNestedFixedDescendants() throws {
        // Parent (in flow) → [fixed child, static child → [fixed grandchild]].
        let parent = try box(
            id: "p", w: 100, h: 100, rgb: (0, 1, 0),
            childrenJSON:
                #"{"id":"fc","name":"x","properties":[{"type":"Position","data":"FIXED"},{"type":"Left","data":{"px":5.0}}]},"# +
                #"{"id":"sc","name":"x","properties":[],"children":[{"id":"fg","name":"x","properties":[{"type":"Position","data":"FIXED"},{"type":"Top","data":{"px":5.0}}]}]}"#)
        let split = FixedHoist.split(roots: [parent])
        // The parent stays in flow, with ONLY the static child left.
        XCTAssertEqual(split.flow.map(\.id), ["p"])
        XCTAssertEqual(split.flow[0].children?.map(\.id), ["sc"],
                       "the fixed child must be stripped from its parent — it anchors at the viewport, never the parent (F1)")
        // The kept static child lost its fixed grandchild too (any depth).
        XCTAssertNil(split.flow[0].children?[0].children,
                     "a fixed GRANDchild must strip at any depth, and an emptied children array must normalize to nil (decode contract: nil, never [])")
        // Both fixed boxes hoisted, pre-order.
        XCTAssertEqual(split.hoisted.map(\.id), ["fc", "fg"],
                       "fixed descendants hoist in document pre-order")
    }

    /// Nested ABSOLUTE children must NOT strip — they keep the wave-8/9
    /// positioned-ancestor padding-box containing block (F2).
    func testSplitKeepsNestedAbsoluteChildren() throws {
        let parent = try box(
            id: "p", position: "RELATIVE", w: 100, h: 100, rgb: (0, 1, 0),
            childrenJSON:
                #"{"id":"ac","name":"x","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":10.0}}]}"#)
        let split = FixedHoist.split(roots: [parent])
        // The absolute child stays exactly where the wave-8 overlay
        // machinery expects it — in the parent's children.
        XCTAssertEqual(split.flow[0].children?.map(\.id), ["ac"],
                       "nested absolute children must keep their positioned-ancestor containing block (css-position-3 §3.1) — only FIXED descendants hoist")
        XCTAssertTrue(split.hoisted.isEmpty)
    }

    /// Fixed-free documents split to (identity, []) — the composed
    /// canvas's view tree is then built from an identical root list.
    func testSplitIsIdentityForFixedFreeDocuments() throws {
        let roots = [
            try box(id: "a", w: 50, h: 50, rgb: (0, 1, 0)),
            try box(id: "b", position: "RELATIVE", left: 5, w: 50, h: 50, rgb: (0, 0, 1)),
        ]
        let split = FixedHoist.split(roots: roots)
        XCTAssertEqual(split.flow.map(\.id), ["a", "b"])
        XCTAssertTrue(split.hoisted.isEmpty,
                      "a document with no out-of-flow boxes must hoist nothing — the flow stack renders the same roots as before wave 17")
    }

    // MARK: - Raster plumbing (PositionedContainerPaintOrderTests pattern)

    /// Rasterize a root list through a REPLICA of the composed canvas's
    /// exact frame chain (ComposedCaptureCanvas: 358 content box, 16px
    /// pad, 390 wide, 600 min height, white, canvas-root overlay) at
    /// scale 1. wptCaptureMode is ON — the composed canvas only ever
    /// runs in WPT capture (name placeholders suppressed, content-box
    /// default), so the raster matches the production composed path.
    @MainActor
    private func renderComposed(_ roots: [IRComponent]) throws -> (px: [UInt8], w: Int, h: Int) {
        // The wave-17 split under test: flow half → padded VStack,
        // hoisted half → the unpadded canvas-root overlay.
        let split = FixedHoist.split(roots: roots)
        let view = VStack(alignment: .leading, spacing: 0) {
            // Positional identity, same as the harness root loop.
            ForEach(Array(split.flow.enumerated()), id: \.offset) { _, root in
                ComponentHost(component: root)
            }
        }
        // The ref 358px content box, block-flow origin.
        .frame(maxWidth: 390 - 32, alignment: .topLeading)
        // The ref's 16px body padding — what hoisted boxes must escape.
        .padding(16)
        // Full 390 width, 600 min height (the ref min-height:100vh floor).
        .frame(minWidth: 390, maxWidth: 390, minHeight: 600, alignment: .topLeading)
        // Natural height above the floor, like the harness canvas.
        .fixedSize(horizontal: false, vertical: true)
        // Corpus-v4 white canvas → "unpainted" is assertable.
        .background(Color.white)
        // The wave-17 overlay: attached OUTSIDE the padding, on the full
        // unpadded frame — its top-leading corner is canvas (0,0).
        .overlay(alignment: .topLeading) {
            if !split.hoisted.isEmpty {
                FixedHoistOverlay(components: split.hoisted)
            }
        }
        // Same capture geometry the harness publishes (390×844, 358 CB).
        .environment(\.styleViewport, StyleViewport(width: 390, height: 844,
                                                    rootContainingBlock: 358))
        // Composed captures always run in WPT mode (see doc above).
        .environment(\.wptCaptureMode, true)
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

    /// RGB triple at a raster point (RGBA8 buffer from renderComposed).
    private func rgb(_ img: (px: [UInt8], w: Int, h: Int),
                     _ x: Int, _ y: Int) -> (r: UInt8, g: UInt8, b: UInt8) {
        let i = (y * img.w + x) * 4
        return (img.px[i], img.px[i + 1], img.px[i + 2])
    }

    // Channel thresholds: ±25/255 absorbs colorspace conversion while
    // keeping the pure primaries + white/black unambiguous.
    private func isRed(_ c: (r: UInt8, g: UInt8, b: UInt8)) -> Bool {
        c.r > 230 && c.g < 25 && c.b < 25
    }
    private func isGreen(_ c: (r: UInt8, g: UInt8, b: UInt8)) -> Bool {
        c.r < 25 && c.g > 230 && c.b < 25
    }
    private func isBlue(_ c: (r: UInt8, g: UInt8, b: UInt8)) -> Bool {
        c.r < 25 && c.g < 25 && c.b > 230
    }
    private func isWhite(_ c: (r: UInt8, g: UInt8, b: UInt8)) -> Bool {
        c.r > 230 && c.g > 230 && c.b > 230
    }
    private func isBlack(_ c: (r: UInt8, g: UInt8, b: UInt8)) -> Bool {
        c.r < 25 && c.g < 25 && c.b < 25
    }

    // MARK: - S1: root-level fixed → unpadded canvas (100, 0)

    /// A root `position: fixed; left: 100; top: 0` box must paint at
    /// canvas (100, 0) — OUTSIDE the 16px pad (F1: the viewport is the
    /// containing block; the canvas pad is body padding, not an inset
    /// basis) — and reserve no flow space.
    @MainActor
    func testS1RootFixedAnchorsAtUnpaddedCanvasOrigin() throws {
        let img = try renderComposed([
            try box(id: "f", position: "FIXED", left: 100, top: 0,
                    w: 50, h: 50, rgb: (1, 0, 0)),
        ])
        // (102, 2): just inside the box's (100, 0) corner — a padded
        // anchor would put the corner at (116, 16), leaving this white.
        XCTAssertTrue(isRed(rgb(img, 102, 2)),
                      "root fixed left:100/top:0 must anchor at UNPADDED canvas (100,0) — a white sample means the 16px pad leaked into the fixed containing block")
        // (98, 2): left of the box — proves the box did not smear from 0.
        XCTAssertTrue(isWhite(rgb(img, 98, 2)),
                      "canvas left of the fixed box must stay white — the box is anchoring left of its declared inset")
        // (152, 52): past the 50×50 span — no runaway fill.
        XCTAssertTrue(isWhite(rgb(img, 152, 52)),
                      "past the fixed box's 50px span the canvas must stay white")
    }

    // MARK: - S2: root-level absolute → unpadded canvas (100, 0)

    /// Same anchor for a root ABSOLUTE box — the measured web behavior:
    /// with no positioned ancestor, the initial containing block is the
    /// unpadded canvas (left:100 lands at x=100, never 116).
    @MainActor
    func testS2RootAbsoluteAnchorsAtUnpaddedCanvasOrigin() throws {
        let img = try renderComposed([
            try box(id: "a", position: "ABSOLUTE", left: 100, top: 0,
                    w: 50, h: 50, rgb: (0, 0, 1)),
        ])
        // (102, 2): inside the (100, 0)-anchored box (see S1 rationale).
        XCTAssertTrue(isBlue(rgb(img, 102, 2)),
                      "root absolute left:100/top:0 must anchor at UNPADDED canvas (100,0) — the initial containing block is the canvas, not its padded content box")
        // (98, 2) / (152, 52): outside the box on both sides — white.
        XCTAssertTrue(isWhite(rgb(img, 98, 2)))
        XCTAssertTrue(isWhite(rgb(img, 152, 52)))
    }

    // MARK: - S3: fixed child of a moved relative parent (the raster proof)

    /// THE diagnosed-bug pin: a fixed child (left:116, top:16) of an
    /// in-flow RELATIVE parent painted at (116, 16) must land at canvas
    /// (116, 16) — its OWN viewport anchor — never at parent + offset
    /// = (232, 32). The parent must still paint at its relative slot.
    @MainActor
    func testS3FixedChildOfMovedRelativeParentDoesNotCompound() throws {
        // Parent: in flow at (16,16) + relative left:100 → paints at
        // (116,16), 50×50 blue. Child: fixed left:116/top:16, 30×30 red.
        let parent = try box(
            id: "p", position: "RELATIVE", left: 100, w: 50, h: 50, rgb: (0, 0, 1),
            childrenJSON:
                #"{"id":"fc","name":"x","properties":["# +
                #"{"type":"Position","data":"FIXED"},"# +
                #"{"type":"Left","data":{"px":116.0}},"# +
                #"{"type":"Top","data":{"px":16.0}},"# +
                #"{"type":"Width","data":{"type":"length","px":30.0}},"# +
                #"{"type":"Height","data":{"type":"length","px":30.0}},"# +
                #"{"type":"BackgroundColor","data":{"srgb":{"r":1.0,"g":0.0,"b":0.0}}}]}"#)
        let img = try renderComposed([parent])
        // (118, 18): inside the child's OWN canvas anchor (116..146 both
        // axes) — and the child paints ABOVE the parent it overlaps
        // (fixed boxes paint over in-flow content, Appendix E step 8).
        XCTAssertTrue(isRed(rgb(img, 118, 18)),
                      "the fixed child must paint at ITS OWN canvas (116,16) — viewport anchor, above the overlapping parent")
        // (160, 60): inside the parent (116..166, 16..66) but outside the
        // child — the relative parent still paints at its moved slot.
        XCTAssertTrue(isBlue(rgb(img, 160, 60)),
                      "the relative parent must still paint at flow origin + relative offset (116,16)")
        // (234, 34): where the COMPOUNDED bug would paint the child
        // (parent (116,16) + child inset (116,16) = (232,32)) — white.
        XCTAssertTrue(isWhite(rgb(img, 234, 34)),
                      "canvas at (232,32)+ must stay white — red here means the fixed child compounded parent position + inset (the diagnosed wave-17 bug)")
    }

    // MARK: - S4: absolute child anchors at its ancestor's padding box

    /// A hoisted absolute ANCESTOR at (50, 40) with a 5px border and an
    /// absolute child at left:10/top:10: the child anchors at the
    /// ancestor's PADDING box (css-position-3 §3.1, the wave-8 overlay
    /// inset) → canvas x = 50 + 5 + 10 = 65, y = 40 + 5 + 10 = 55.
    @MainActor
    func testS4AbsoluteChildAnchorsAtAncestorPaddingBox() throws {
        // Ancestor: absolute root (hoisted, unpadded anchor (50,40)),
        // 60×60 content + 5px solid black border, blue background.
        let ancestor = try component(#"""
        {"id":"anc","name":"x","properties":[
          {"type":"Position","data":"ABSOLUTE"},
          {"type":"Left","data":{"px":50.0}},
          {"type":"Top","data":{"px":40.0}},
          {"type":"Width","data":{"type":"length","px":60.0}},
          {"type":"Height","data":{"type":"length","px":60.0}},
          {"type":"BackgroundColor","data":{"srgb":{"r":0.0,"g":0.0,"b":1.0}}},
          {"type":"BorderTopWidth","data":{"px":5.0}},
          {"type":"BorderRightWidth","data":{"px":5.0}},
          {"type":"BorderBottomWidth","data":{"px":5.0}},
          {"type":"BorderLeftWidth","data":{"px":5.0}},
          {"type":"BorderTopStyle","data":"SOLID"},
          {"type":"BorderRightStyle","data":"SOLID"},
          {"type":"BorderBottomStyle","data":"SOLID"},
          {"type":"BorderLeftStyle","data":"SOLID"},
          {"type":"BorderTopColor","data":{"srgb":{"r":0.0,"g":0.0,"b":0.0}}},
          {"type":"BorderRightColor","data":{"srgb":{"r":0.0,"g":0.0,"b":0.0}}},
          {"type":"BorderBottomColor","data":{"srgb":{"r":0.0,"g":0.0,"b":0.0}}},
          {"type":"BorderLeftColor","data":{"srgb":{"r":0.0,"g":0.0,"b":0.0}}}
        ],
        "children":[
          {"id":"ch","name":"x","properties":[
            {"type":"Position","data":"ABSOLUTE"},
            {"type":"Left","data":{"px":10.0}},
            {"type":"Top","data":{"px":10.0}},
            {"type":"Width","data":{"type":"length","px":20.0}},
            {"type":"Height","data":{"type":"length","px":20.0}},
            {"type":"BackgroundColor","data":{"srgb":{"r":1.0,"g":0.0,"b":0.0}}}
          ]}
        ]}
        """#)
        let img = try renderComposed([ancestor])
        // (67, 57): inside the child's (65, 55) anchor — padding-box
        // basis (50+5+10). A border-box anchor would start it at (60,50);
        // a canvas-anchored child would start at (10,10).
        XCTAssertTrue(isRed(rgb(img, 67, 57)),
                      "the absolute child must anchor at the ancestor's PADDING box: 50 + 5 border + 10 inset = 65 (css-position-3 §3.1)")
        // (52, 42): inside the ancestor's 5px border band — still black,
        // proving the ancestor itself anchored at unpadded (50, 40).
        XCTAssertTrue(isBlack(rgb(img, 52, 42)),
                      "the hoisted ancestor's border corner must sit at unpadded canvas (50,40)")
        // (60, 50): inside the padding box but LEFT of the child's 65px
        // anchor — ancestor background, not child ink (no border-box or
        // canvas mis-anchor).
        XCTAssertTrue(isBlue(rgb(img, 60, 50)),
                      "between the padding-box origin and the child's inset the ancestor background must show — the child is anchoring at the border box or the canvas")
    }

    // MARK: - S5: no flow space reserved for a fixed sibling

    /// An in-flow sibling AFTER a fixed box must start where the fixed
    /// box would have been (css-position-3 §2.1: out-of-flow boxes leave
    /// flow entirely) — at the flow origin (16, 16), not 40px lower.
    @MainActor
    func testS5InFlowSiblingStartsWhereFixedBoxWouldHaveBeen() throws {
        let img = try renderComposed([
            // Fixed first root, parked right so the flow origin is clear.
            try box(id: "f", position: "FIXED", left: 200, top: 0,
                    w: 40, h: 40, rgb: (1, 0, 0)),
            // In-flow sibling AFTER it.
            try box(id: "g", w: 40, h: 30, rgb: (0, 1, 0)),
        ])
        // (20, 20): the padded flow origin — the green sibling must own
        // it. If the fixed box had reserved its 40px slot, green would
        // start at y=56 and this sample would be white.
        XCTAssertTrue(isGreen(rgb(img, 20, 20)),
                      "the in-flow sibling must start at the flow origin — the fixed box reserved flow space (css-position-3 §2.1 violation)")
        // (20, 60): below the 30px green box — white, proving green
        // rendered at (16,16) with its natural height and nothing else
        // occupies the flow.
        XCTAssertTrue(isWhite(rgb(img, 20, 60)),
                      "below the in-flow sibling the canvas must be white — something is still occupying the fixed box's old slot")
        // (202, 2): the fixed box itself at its viewport anchor.
        XCTAssertTrue(isRed(rgb(img, 202, 2)),
                      "the fixed box must still paint at its own unpadded canvas anchor (200,0)")
    }
}
