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

    /// Wave 35 (lane B1) — the ONE row of the cross-native out-of-flow rule
    /// table where this file and its Compose twin deliberately DISAGREE,
    /// pinned so the divergence is a decision on the record rather than a
    /// drift nobody re-derives.
    ///
    /// Shape: a nested inset-anchored ABSOLUTE box with NO positioned and NO
    /// transformed ancestor anywhere above it.
    ///   • Chromium (probe A, _diag35/laneB1/probe-chromium.mjs): the
    ///     INITIAL CONTAINING BLOCK is the containing block — the box lands
    ///     at (20,10) in viewport space, not at its parent.
    ///   • Compose `CanvasRootHoist.shouldHoistToCanvasRoot`: hoists it,
    ///     i.e. implements the browser rule.
    ///   • This file: keeps it, i.e. anchors it at the parent's padding box.
    ///
    /// The conservative clause is kept ON PURPOSE and the reason is measured:
    /// the IR's positioned-ancestor chain is LOSSY. Of the six frozen tests
    /// carrying this shape (_diag35/laneB1/scan-oof2.mjs), four are
    /// css-writing-modes available-size-00x, whose `body > div { position:
    /// relative }` never reaches the wire — the extractor drops that
    /// descendant-combinator rule, so the IR claims "no positioned ancestor"
    /// for a box that demonstrably has one. iOS passes all four by keeping
    /// them in place; Compose hoists them to the canvas corner and FAILS
    /// available-size-001/012 at 0.8998. Adopting the browser-correct clause
    /// here would trade four green cells for none. The defect to fix is the
    /// extractor, not this table — named as a follow-up, not silently lived
    /// with.
    func testEXPECTED_DIVERGENCE_nestedInsetAbsoluteWithNoContainingBlockAncestor() throws {
        // A plain STATIC wrapper — no position, no transform — holding an
        // inset-anchored absolute child two levels down.
        let wrapper = try box(
            id: "w", w: 200, h: 200, rgb: (0, 0, 1),
            childrenJSON:
                #"{"id":"nested","name":"nested","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":20.0}},{"type":"Top","data":{"px":10.0}}]}"#)
        let split = FixedHoist.split(roots: [wrapper])
        // iOS side of the divergence: the box stays in the tree.
        XCTAssertEqual(split.flow[0].children?.map(\.id), ["nested"],
                       "EXPECTED DIVERGENCE: iOS keeps a nested absolute where Compose hoists it to the ICB — see this test's doc comment for the measured reason")
        XCTAssertTrue(split.hoisted.isEmpty)
        // …and it is NOT the transform clause doing it: no ancestor in this
        // tree establishes a transform containing block, so the wave-35 veto
        // is provably inert here and the divergence is the wave-17 clause.
        XCTAssertFalse(TransformContainingBlock.establishes(wrapper))
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
    /// frame, 390 wide, 600 min height, white, canvas-root overlay) at
    /// scale 1. wptCaptureMode is ON — the composed canvas only ever
    /// runs in WPT capture (name placeholders suppressed, content-box
    /// default), so the raster matches the production composed path.
    @MainActor
    private func renderComposed(_ roots: [IRComponent]) throws -> (px: [UInt8], w: Int, h: Int) {
        // The wave-17 split under test: flow half → padded VStack,
        // hoisted half → the canvas-root overlay, whose own `canvasFrame`
        // inset places it at the INITIAL CONTAINING BLOCK corner.
        let split = FixedHoist.split(roots: roots)
        let view = VStack(alignment: .leading, spacing: 0) {
            // Positional identity, same as the harness root loop.
            ForEach(Array(split.flow.enumerated()), id: \.offset) { _, root in
                // Wave 18 (RC1): mirror the harness — a kept no-inset
                // absolute root mounts behind the zero-report anchor so
                // it paints at its slot origin with no flow footprint.
                if FixedHoist.rendersInFlowAsStaticPosition(root) {
                    ComponentHost(component: root)
                        .modifier(StaticPositionAnchor())
                } else {
                    ComponentHost(component: root)
                }
            }
        }
        // The ref 358px content box, block-flow origin.
        .frame(maxWidth: 390 - 32, alignment: .topLeading)
        // The canvas frame (the ref's image-space pad, CANVAS_PAD_PX).
        .padding(WPTCanvas.canvasFramePx)
        // Full 390 width, 600 min height (the ref min-height:100vh floor).
        .frame(minWidth: 390, maxWidth: 390, minHeight: 600, alignment: .topLeading)
        // Natural height above the floor, like the harness canvas.
        .fixedSize(horizontal: false, vertical: true)
        // Corpus-v4 white canvas → "unpainted" is assertable.
        .background(Color.white)
        // The wave-17 overlay: attached OUTSIDE the padding, on the FULL
        // frame, then inset by `canvasFrame` to the ICB corner — the exact
        // production wiring (ComposedCaptureCanvas passes Self.padding to
        // both z-partitions). Wave 25 round 3 moved that corner from the
        // raw canvas (0,0) to the framed content corner (16,16), because
        // the ref's frame became an image-space translation that moves
        // abspos and in-flow ink alike.
        .overlay(alignment: .topLeading) {
            if !split.hoisted.isEmpty {
                FixedHoistOverlay(components: split.hoisted,
                                  canvasFrame: WPTCanvas.canvasFramePx)
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

    // MARK: - S1: root-level fixed → ICB (100, 0) = canvas (116, 16)

    /// A root `position: fixed; left: 100; top: 0` box must paint at ICB
    /// (100, 0) — F1: the viewport is the containing block — and reserve no
    /// flow space. Wave 25 round 3: the ICB corner is the FRAMED content
    /// corner, so that lands at canvas (116, 16). Through wave 24 it was
    /// canvas (100, 0), matching refs whose CSS body pad moved only in-flow
    /// content; the image-space frame moves the ref's fixed boxes too.
    @MainActor
    func testS1RootFixedAnchorsAtTheIcbCorner() throws {
        let img = try renderComposed([
            try box(id: "f", position: "FIXED", left: 100, top: 0,
                    w: 50, h: 50, rgb: (1, 0, 0)),
        ])
        // (118, 18): just inside the box's canvas (116, 16) corner.
        XCTAssertTrue(isRed(rgb(img, 118, 18)),
                      "root fixed left:100/top:0 must anchor at the ICB corner — canvas (116,16) under the wave-25 image-space frame")
        // (102, 2): the OLD wave-17 anchor (100,0) — must be white now, or
        // the overlay is still mounting at the raw canvas corner.
        XCTAssertTrue(isWhite(rgb(img, 102, 2)),
                      "canvas (100,0) must be white — the hoist origin is still the unframed canvas corner")
        // (114, 18): left of the box — proves it did not smear from 16.
        XCTAssertTrue(isWhite(rgb(img, 114, 18)),
                      "canvas left of the fixed box must stay white — the box is anchoring left of its declared inset")
        // (168, 68): past the 50×50 span from (116,16) — no runaway fill.
        XCTAssertTrue(isWhite(rgb(img, 168, 68)),
                      "past the fixed box's 50px span the canvas must stay white")
    }

    // MARK: - S2: root-level absolute → ICB (100, 0) = canvas (116, 16)

    /// Same anchor for a root ABSOLUTE box: with no positioned ancestor its
    /// containing block is the INITIAL containing block — the ref's render
    /// viewport, which the image-space frame places at canvas (16,16). So
    /// `left:100` lands at x=116, sharing the offset its in-flow siblings
    /// get (wave 17 measured x=100 against the pre-CAL-RC1 refs, where the
    /// CSS body pad moved in-flow content only).
    @MainActor
    func testS2RootAbsoluteAnchorsAtTheIcbCorner() throws {
        let img = try renderComposed([
            try box(id: "a", position: "ABSOLUTE", left: 100, top: 0,
                    w: 50, h: 50, rgb: (0, 0, 1)),
        ])
        // (118, 18): inside the canvas (116, 16) anchor (see S1 rationale).
        XCTAssertTrue(isBlue(rgb(img, 118, 18)),
                      "root absolute left:100/top:0 must anchor at the ICB corner — canvas (116,16), the same frame its in-flow siblings sit inside")
        // (114, 18) / (168, 68): outside the box on both sides — white.
        XCTAssertTrue(isWhite(rgb(img, 114, 18)))
        XCTAssertTrue(isWhite(rgb(img, 168, 68)))
    }

    // MARK: - S3: fixed child of a moved relative parent (the raster proof)

    /// THE diagnosed-bug pin: a fixed child (left:116, top:16) of an
    /// in-flow RELATIVE parent painted at canvas (116, 16) must land at ITS
    /// OWN viewport anchor — ICB (116, 16), i.e. canvas (132, 32) under the
    /// wave-25 frame — never at parent + offset = (232, 32). The parent
    /// must still paint at its relative slot.
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
        // (134, 34): inside the child's OWN anchor — ICB (116,16) = canvas
        // (132..162, 32..62) — and the child paints ABOVE the parent it
        // overlaps (fixed boxes paint over in-flow content, Appendix E
        // step 8).
        XCTAssertTrue(isRed(rgb(img, 134, 34)),
                      "the fixed child must paint at ITS OWN ICB anchor (116,16) = canvas (132,32) — viewport anchor, above the overlapping parent")
        // (120, 20): inside the parent (canvas 116..166, 16..66) but LEFT
        // of the child — the relative parent still paints at its moved
        // flow slot, which the frame does NOT move again (its 16px inset
        // is the flow Column's padding, already counted).
        XCTAssertTrue(isBlue(rgb(img, 120, 20)),
                      "the relative parent must still paint at flow origin + relative offset (116,16)")
        // (234, 34): where the COMPOUNDED bug would paint the child
        // (parent (116,16) + child inset (116,16) = (232,32)) — white.
        XCTAssertTrue(isWhite(rgb(img, 234, 34)),
                      "canvas at (232,32)+ must stay white — red here means the fixed child compounded parent position + inset (the diagnosed wave-17 bug)")
    }

    // MARK: - S4: absolute child anchors at its ancestor's padding box

    /// A hoisted absolute ANCESTOR at ICB (50, 40) — canvas (66, 56) under
    /// the wave-25 frame — with a 5px border and an absolute child at
    /// left:10/top:10: the child anchors at the ancestor's PADDING box
    /// (css-position-3 §3.1, the wave-8 overlay inset) → canvas
    /// x = 66 + 5 + 10 = 81, y = 56 + 5 + 10 = 71. The frame translates the
    /// hoisted ancestor; the child's padding-box basis is unchanged.
    @MainActor
    func testS4AbsoluteChildAnchorsAtAncestorPaddingBox() throws {
        // Ancestor: absolute root (hoisted, ICB anchor (50,40) = canvas
        // (66,56)), 60×60 content + 5px solid black border, blue background.
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
        // (83, 73): inside the child's (81, 71) anchor — padding-box basis
        // (66+5+10). A border-box anchor would start it at (76,66); a
        // canvas-anchored child would start at (10,10).
        XCTAssertTrue(isRed(rgb(img, 83, 73)),
                      "the absolute child must anchor at the ancestor's PADDING box: 66 + 5 border + 10 inset = 81 (css-position-3 §3.1)")
        // (68, 58): inside the ancestor's 5px border band — still black,
        // proving the ancestor itself anchored at ICB (50,40) = canvas
        // (66,56).
        XCTAssertTrue(isBlack(rgb(img, 68, 58)),
                      "the hoisted ancestor's border corner must sit at the ICB anchor (50,40) = canvas (66,56)")
        // (76, 66): inside the padding box but LEFT of the child's 81px
        // anchor — ancestor background, not child ink (no border-box or
        // canvas mis-anchor).
        XCTAssertTrue(isBlue(rgb(img, 76, 66)),
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
        // (218, 18): the fixed box itself at its viewport anchor — ICB
        // (200,0) = canvas (216,16) under the wave-25 frame.
        XCTAssertTrue(isRed(rgb(img, 218, 18)),
                      "the fixed box must still paint at its own ICB anchor (200,0) = canvas (216,16)")
    }

    // MARK: - Wave 18 RC1: the inset-aware hoist + the static-position class

    /// css-position-3 §3.1: an absolute box with ALL-AUTO insets sits at
    /// its STATIC position — the wave-17 canvas-origin hoist painted
    /// css-sizing abspos-001/002's square at (0,0) over the paragraph.
    /// Pure classification pins first (twin table of Compose's
    /// CanvasRootHoistTest RC1 block).
    func testRC1NoInsetAbsoluteRootStaysInFlow() throws {
        let noInset = try box(id: "a", position: "ABSOLUTE", w: 100, h: 100, rgb: (0, 0.5, 0))
        // Classified for the in-flow static-position mount…
        XCTAssertTrue(FixedHoist.rendersInFlowAsStaticPosition(noInset))
        // …but NEVER under a positioned ancestor (skeptic fix — the
        // Compose twin's parameter): such a box belongs to the wave-8/9
        // positioned-children machinery, not the static-position class
        // (the cross-native probe caught the natives disagreeing on the
        // css-sizing fit-content-percentage children without this).
        XCTAssertFalse(FixedHoist.rendersInFlowAsStaticPosition(
            noInset, hasPositionedAncestor: true))
        // …and split keeps it in the FLOW half (no canvas hoist).
        let split = FixedHoist.split(roots: [noInset])
        XCTAssertEqual(split.flow.map(\.id), ["a"])
        XCTAssertTrue(split.hoisted.isEmpty)
    }

    /// Any single inset keeps the wave-17 canvas hoist (mixed-axis pins
    /// the documented approximation: the auto axis anchors at the canvas
    /// origin), and every wave-17 css-position case — all inset-carrying
    /// — splits byte-identically to before (the regression guard).
    func testRC1InsetCarryingAbsoluteAndAllFixedStillHoist() throws {
        let leftOnly = try box(id: "l", position: "ABSOLUTE", left: 100, w: 50, h: 50, rgb: (0, 0, 1))
        XCTAssertFalse(FixedHoist.rendersInFlowAsStaticPosition(leftOnly))
        let noInsetFixed = try box(id: "f", position: "FIXED", w: 50, h: 50, rgb: (1, 0, 0))
        // Fixed keeps the always-hoist rule even with no inset — its
        // containing block IS the viewport (css-position-3 §3.2).
        XCTAssertFalse(FixedHoist.rendersInFlowAsStaticPosition(noInsetFixed))
        let split = FixedHoist.split(roots: [leftOnly, noInsetFixed])
        XCTAssertTrue(split.flow.isEmpty)
        XCTAssertEqual(split.hoisted.map(\.id), ["l", "f"])
    }

    /// Logical insets anchor exactly like physical ones — one wire
    /// decoder (PositionExtractor folds InsetInline/InsetBlock to
    /// physical in the LTR horizontal-tb normalization).
    func testRC1LogicalInsetCountsAsAnchor() throws {
        let logical = try component(
            #"{"id":"lg","name":"x","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"InsetInlineStart","data":{"px":24}}]}"#)
        XCTAssertFalse(FixedHoist.rendersInFlowAsStaticPosition(logical))
        XCTAssertTrue(FixedHoist.hasAnyInset(logical))
    }

    /// The raster proof (the abspos-001 geometry): a paragraph-analog
    /// block followed by a no-inset absolute square — the square must
    /// paint BELOW the block at the flow x (static position), NOT at the
    /// unpadded canvas origin, and must reserve no flow space for the
    /// sibling after it.
    @MainActor
    func testRC1StaticPositionPaintsInFlowSlotWithZeroFootprint() throws {
        let img = try renderComposed([
            // The "paragraph": a 40px-tall in-flow block.
            try box(id: "p", w: 100, h: 40, rgb: (0, 0, 1)),
            // The abspos-001 square: absolute, no insets, 100×100
            // (pure green — the raster helpers' primary thresholds).
            try box(id: "sq", position: "ABSOLUTE", w: 100, h: 100, rgb: (0, 1, 0)),
            // An in-flow sibling AFTER the square (S5 zero-footprint pin).
            try box(id: "after", w: 40, h: 20, rgb: (1, 0, 0)),
        ])
        // Geometry: pad 16 → blue block x16..116 y16..56; the square's
        // static position is the slot AFTER the block → green ink
        // x16..116 y56..156 (zero flow report); the red sibling starts at
        // the SAME y=56 (nothing was reserved) → x16..56 y56..76, painted
        // ABOVE the green where they overlap (later sibling, tree order).
        // (2,2): the unpadded canvas origin must stay WHITE — the square
        // no longer hoists there (the wave-17 overlap bug).
        XCTAssertTrue(isWhite(rgb(img, 2, 2)),
                      "no-inset absolute box must not anchor at the canvas origin")
        // (20,60): the red sibling owns the square's slot origin — the
        // square reserved NO flow space (S5; red would sit at y=156+ if
        // the square had kept a flow footprint).
        XCTAssertTrue(isRed(rgb(img, 20, 60)),
                      "the in-flow sibling must start where the square's slot began — the square reserved flow space")
        // (60,60): past red's right edge (x=56) — the square's own ink at
        // its static position, below the block.
        XCTAssertTrue(isGreen(rgb(img, 60, 60)),
                      "the square must paint below the block at its static position")
        // (60,140): deep in the square's overflow — still green, proving
        // the full 100px ink drew despite the 0×0 flow report.
        XCTAssertTrue(isGreen(rgb(img, 60, 140)),
                      "the square's overflowing ink must draw unclipped past the zero flow report")
    }
}
