//
//  MarginCollapseTests.swift
//  StyleConverterRuntimeTests
//
//  Lane IOS-COLLAPSE pins — the CSS 2.1 §8.3.1 margin-collapse emulation:
//    1. Static edge classification (positive px in, everything else bails).
//    2. The §8.3.1 fold (sibling max() collapse + hoist bookkeeping).
//    3. Hoist-band composition with the parent's own margin (max rule).
//    4. containerPlan gates (flex parents, padded/definite-height parents,
//       negative/auto/relative children → logged fallback).
//    5. ImageRenderer raster proof of the measured web geometry: three
//       100×40 bars with margin:20 in an unpadded 300px block parent →
//       parent band exactly 300×160 with the first bar FLUSH at its top
//       edge and bars at y-offsets 20/80/140 inside the hoisted region.
//
//  Every wire-shape literal below is copied from LIVE converter output
//  (scratchpad collapse-fixture run: `{"px":20.0}` margins, `"auto"`,
//  `{"original":{"v":1.5,"u":"EM"}}` for unresolved em) — never invented.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class MarginCollapseTests: XCTestCase {

    // MARK: - Helpers

    /// Decode an [IRProperty] from inline JSON — the production decode
    /// path (same convention as IOSTextLaneTests).
    private func props(_ json: String) -> [IRProperty] {
        try! JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    /// Decode an IRComponent from wire JSON — IRComponent is
    /// Decodable-only, and decoding keeps the test on converter bytes.
    private func component(_ json: String) throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    /// One 100×40 bar with `margin: 20px` (the live four-longhand
    /// expansion) and a distinct background color.
    private func barJSON(id: String, r: Double, g: Double, b: Double) -> String {
        """
        {"id":"\(id)","name":"\(id)",
         "properties":[
           {"type":"Width","data":{"type":"length","px":100.0}},
           {"type":"Height","data":{"type":"length","px":40.0}},
           {"type":"MarginTop","data":{"px":20.0}},
           {"type":"MarginRight","data":{"px":20.0}},
           {"type":"MarginBottom","data":{"px":20.0}},
           {"type":"MarginLeft","data":{"px":20.0}},
           {"type":"BackgroundColor","data":{"srgb":{"r":\(r),"g":\(g),"b":\(b)},"original":"#000000"}}
         ]}
        """
    }

    /// The measured-geometry tree: 300px-wide unpadded gray parent with
    /// three margin-20 bars (blue / green / red — the fixture palette).
    private func threeBarTree(extraParentProps: String = "") throws -> IRComponent {
        try component("""
        {"id":"collapse_three_bars-001","name":"Collapse_Three_Bars",
         "properties":[
           {"type":"Width","data":{"type":"length","px":300.0}},
           {"type":"BackgroundColor","data":{"srgb":{"r":0.2,"g":0.2,"b":0.2},"original":"#333333"}}\(extraParentProps)
         ],
         "children":[
           \(barJSON(id: "bar1", r: 0.20392156862745098, g: 0.596078431372549, b: 0.8588235294117647)),
           \(barJSON(id: "bar2", r: 0.1803921568627451, g: 0.8, b: 0.44313725490196076)),
           \(barJSON(id: "bar3", r: 0.9058823529411765, g: 0.2980392156862745, b: 0.23529411764705882))
         ]}
        """)
    }

    /// containerPlan against the plain StyleBuilder output — the same
    /// build the renderer folds env geometry into (none of the gates
    /// read the env-only fields, so the plain build is equivalent).
    private func plan(_ comp: IRComponent) -> MarginCollapse.Plan? {
        MarginCollapse.containerPlan(component: comp,
                                     style: StyleBuilder.build(from: comp.properties))
    }

    // MARK: - 1. Static edge classification

    /// Live px shape `{"px":20.0}` classifies; missing margins are the
    /// CSS initial 0; logical longhands feed the block axis.
    func testStaticEdgesFromLiveWireShapes() {
        // Both edges declared as absolute px.
        let e = MarginCollapse.staticVerticalEdges(props(
            #"[{"type":"MarginTop","data":{"px":20.0}},{"type":"MarginBottom","data":{"px":8.0}}]"#))
        XCTAssertEqual(e?.top, 20); XCTAssertEqual(e?.bottom, 8)
        // No margin longhand at all → (0, 0), still eligible.
        let zero = MarginCollapse.staticVerticalEdges(props("[]"))
        XCTAssertEqual(zero?.top, 0); XCTAssertEqual(zero?.bottom, 0)
        // Logical block-start (LTR-TB → top) — live shape from Spacing_C03.
        let logical = MarginCollapse.staticVerticalEdges(props(
            #"[{"type":"MarginBlockStart","data":{"px":12.0}}]"#))
        XCTAssertEqual(logical?.top, 12)
    }

    /// Negative px (live `{"px":-10.0}`), `"auto"`, and unresolved em
    /// (`{"original":{"v":1.5,"u":"EM"}}`) all bail — the §8.3.1
    /// negative/auto rules are out of scope, relative needs live context.
    func testDynamicEdgesBail() {
        XCTAssertNil(MarginCollapse.staticVerticalEdges(props(
            #"[{"type":"MarginTop","data":{"px":-10.0}}]"#)))
        XCTAssertNil(MarginCollapse.staticVerticalEdges(props(
            #"[{"type":"MarginTop","data":"auto"}]"#)))
        XCTAssertNil(MarginCollapse.staticVerticalEdges(props(
            #"[{"type":"MarginBottom","data":{"original":{"v":1.5,"u":"EM"}}}]"#)))
        // A HORIZONTAL auto stays eligible — §8.3.1 is vertical-only.
        let e = MarginCollapse.staticVerticalEdges(props(
            #"[{"type":"MarginLeft","data":"auto"},{"type":"MarginTop","data":{"px":5.0}}]"#))
        XCTAssertEqual(e?.top, 5)
    }

    // MARK: - 2. The §8.3.1 fold

    /// The lane's target geometry: 3 × (20, 20) with both hoists open →
    /// first child flush (0), interior gaps collapse to max(20,20)=20,
    /// both edge margins hoist out.
    func testFoldThreeEqualBars() {
        let p = MarginCollapse.fold(edges: [(20, 20), (20, 20), (20, 20)],
                                    parentOwn: (0, 0),
                                    hoistTopAllowed: true, hoistBottomAllowed: true)
        XCTAssertEqual(p.overrides, [MarginCollapseOverride(top: 0, bottom: 0),
                                     MarginCollapseOverride(top: 20, bottom: 0),
                                     MarginCollapseOverride(top: 20, bottom: 0)])
        XCTAssertEqual(p.hoistTop, 20)
        XCTAssertEqual(p.hoistBottom, 20)
    }

    /// Asymmetric margins take the max (§8.3.1 "the maximum of the
    /// adjoining margin widths"); closed gates keep edge margins inside.
    func testFoldAsymmetricAndClosedGates() {
        let p = MarginCollapse.fold(edges: [(10, 30), (5, 40)],
                                    parentOwn: (0, 0),
                                    hoistTopAllowed: false, hoistBottomAllowed: false)
        // First child keeps its full top (gate closed); interior gap is
        // max(30, 5) = 30 on the second child; last keeps its bottom.
        XCTAssertEqual(p.overrides, [MarginCollapseOverride(top: 10, bottom: 0),
                                     MarginCollapseOverride(top: 30, bottom: 40)])
        XCTAssertEqual(p.hoistTop, 0)
        XCTAssertEqual(p.hoistBottom, 0)
    }

    // MARK: - 3. Hoist-band composition

    /// The band is max(own, child) − own: the parent's own margin is
    /// already painted by its MarginApplier, only the excess escapes.
    func testHoistBandMaxComposition() {
        XCTAssertEqual(MarginCollapse.hoistBand(childEdge: 20, parentOwnEdge: nil), 20)
        XCTAssertEqual(MarginCollapse.hoistBand(childEdge: 20, parentOwnEdge: 0), 20)
        XCTAssertEqual(MarginCollapse.hoistBand(childEdge: 20, parentOwnEdge: 8), 12)
        XCTAssertEqual(MarginCollapse.hoistBand(childEdge: 20, parentOwnEdge: 25), 0)
        XCTAssertEqual(MarginCollapse.hoistBand(childEdge: 0, parentOwnEdge: 8), 0)
    }

    // MARK: - 4. containerPlan gates

    /// The three-bar tree produces the full measured-geometry plan.
    func testContainerPlanThreeBars() throws {
        let p = try XCTUnwrap(plan(try threeBarTree()))
        XCTAssertEqual(p.overrides, [MarginCollapseOverride(top: 0, bottom: 0),
                                     MarginCollapseOverride(top: 20, bottom: 0),
                                     MarginCollapseOverride(top: 20, bottom: 0)])
        XCTAssertEqual(p.hoistTop, 20)
        XCTAssertEqual(p.hoistBottom, 20)
    }

    /// Flex containers never collapse item margins (css-flexbox-1 §4).
    func testFlexParentGetsNoPlan() throws {
        let flex = try threeBarTree(extraParentProps:
            #",{"type":"Display","data":"FLEX"}"#)
        XCTAssertNil(plan(flex))
    }

    /// Parent top padding blocks the TOP hoist only (§8.3.1 adjoining
    /// needs "no top padding"): the first child keeps its full margin
    /// INSIDE, sibling collapse and the bottom hoist stay live.
    func testParentTopPaddingBlocksTopHoistOnly() throws {
        let padded = try threeBarTree(extraParentProps:
            #",{"type":"PaddingTop","data":{"px":10.0}}"#)
        let p = try XCTUnwrap(plan(padded))
        XCTAssertEqual(p.hoistTop, 0)
        XCTAssertEqual(p.overrides[0], MarginCollapseOverride(top: 20, bottom: 0))
        XCTAssertEqual(p.hoistBottom, 20)
    }

    /// A definite parent height pins the bottom edge (§8.3.1 requires
    /// computed height auto): last child keeps its bottom margin inside.
    func testDefiniteHeightBlocksBottomHoist() throws {
        let sized = try threeBarTree(extraParentProps:
            #",{"type":"Height","data":{"type":"length","px":200.0}}"#)
        let p = try XCTUnwrap(plan(sized))
        XCTAssertEqual(p.hoistTop, 20)
        XCTAssertEqual(p.hoistBottom, 0)
        XCTAssertEqual(p.overrides[2], MarginCollapseOverride(top: 20, bottom: 20))
    }

    /// A negative child margin bails the WHOLE container to the legacy
    /// sum path, with the once-per-container breadcrumb (house rule:
    /// no silent fallthroughs).
    func testNegativeChildMarginBailsAndLogs() throws {
        // Fresh dedupe set so this test is order-independent.
        PropertyTracker._resetForTests()
        let tree = try component("""
        {"id":"neg-parent-001","name":"NegParent",
         "properties":[{"type":"Width","data":{"type":"length","px":300.0}}],
         "children":[
           {"id":"neg-002","name":"neg",
            "properties":[{"type":"MarginTop","data":{"px":-10.0}}]}
         ]}
        """)
        XCTAssertNil(plan(tree))
        // The fallback key must have been consumed by containerPlan —
        // a second logOnce on it reports "already seen" (false).
        XCTAssertFalse(PropertyTracker.logOnce(
            key: "MarginCollapse.fallback.neg-parent-001", message: "probe"),
            "containerPlan must log the fallback breadcrumb once")
    }

    /// All-zero margins → nil plan (identity fold; keeps the exact
    /// legacy view tree for the margin-free corpus).
    func testMarginFreeChildrenGetNoPlan() throws {
        let tree = try component("""
        {"id":"plain-001","name":"Plain",
         "properties":[{"type":"Width","data":{"type":"length","px":300.0}}],
         "children":[
           {"id":"a-002","name":"a","properties":[{"type":"Height","data":{"type":"length","px":40.0}}]}
         ]}
        """)
        XCTAssertNil(plan(tree))
    }

    // MARK: - 5. Override application

    /// The child-side fold: used top/bottom replace declared, other
    /// sides pass through; nil override is identity.
    func testApplyingOverride() {
        // Margin-less child receiving a collapsed gap on its top.
        let m = MarginCollapse.applying(MarginCollapseOverride(top: 5, bottom: 7), to: nil)
        XCTAssertEqual(m?.top, .exact(px: 5))
        XCTAssertEqual(m?.bottom, .exact(px: 7))
        // Horizontal sides survive the vertical override untouched.
        var declared = MarginConfig()
        declared.left = .exact(px: 20); declared.top = .exact(px: 20)
        let folded = MarginCollapse.applying(MarginCollapseOverride(top: 0, bottom: 0), to: declared)
        XCTAssertEqual(folded?.left, .exact(px: 20))
        XCTAssertEqual(folded?.top, .exact(px: 0))
        // Nil override → declared config byte-identical.
        XCTAssertEqual(MarginCollapse.applying(nil, to: declared), declared)
    }

    // MARK: - 6. Raster proof (measured web geometry)

    /// Render through the same canvas contract the capture harness uses
    /// (CaptureCanvas: 358 content width, 16pt padding, 390 canvas,
    /// scale 1) and return an RGBA8 buffer — the EmptyFlexContainerTests
    /// render-pin pattern.
    @MainActor
    private func render(_ comp: IRComponent) throws -> (px: [UInt8], w: Int, h: Int) {
        // Mirror CaptureCanvas so the pin measures capture-pipeline truth.
        let view = ComponentRenderer(component: comp)
            .frame(maxWidth: 358, alignment: .topLeading)
            .padding(16)
            .frame(width: 390, alignment: .topLeading)
            .fixedSize(horizontal: false, vertical: true)
            .background(Color.white)
        // Scale 1 = one buffer pixel per logical point.
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

    /// Row range (min...max) of pixels matching an RGB predicate.
    private func rows(in img: (px: [UInt8], w: Int, h: Int),
                      where match: (UInt8, UInt8, UInt8) -> Bool) -> ClosedRange<Int>? {
        var lo = Int.max, hi = Int.min
        // Full scan — captures are small (390 × ~230), this is cheap.
        for y in 0..<img.h {
            for x in 0..<img.w {
                let i = (y * img.w + x) * 4
                if match(img.px[i], img.px[i + 1], img.px[i + 2]) {
                    lo = min(lo, y); hi = max(hi, y)
                }
            }
        }
        return lo <= hi ? lo...hi : nil
    }

    /// THE lane geometry pin: parent band 160px tall starting at canvas
    /// y = 36 (16 canvas padding + 20 hoisted top margin), first bar
    /// FLUSH with the parent's top edge (collapse-through), bars at
    /// 20/80/140 inside the hoisted region (canvas y 36/96/156).
    @MainActor
    func testThreeBarRasterMatchesWebGeometry() throws {
        let img = try render(try threeBarTree())
        // Parent band: the #333 gray (51,51,51) region. ±6 tolerance
        // absorbs colorspace conversion, none is expected at scale 1.
        let gray = try XCTUnwrap(rows(in: img) { r, g, b in
            (45...57).contains(Int(r)) && (45...57).contains(Int(g)) && (45...57).contains(Int(b))
        }, "parent background not found")
        // Band top: 16 (canvas pad) + 20 (hoisted margin) = 36 — the
        // hoisted region must be TRANSPARENT (white canvas), so no gray
        // may appear above it.
        XCTAssertEqual(gray.lowerBound, 36, "parent must start below the hoisted 20px band")
        // Band height: 3×40 bars + 2×20 collapsed gaps = 160 → rows 36…195.
        XCTAssertEqual(gray.count, 160, "parent band must be exactly 160px tall (collapsed)")
        // Bar 1 (#3498db blue): FLUSH at the parent's top edge — its own
        // 20px top margin escaped through the parent (§8.3.1).
        let blue = try XCTUnwrap(rows(in: img) { r, g, b in
            (30...80).contains(Int(r)) && (130...175).contains(Int(g)) && b > 200
        }, "first bar not found")
        XCTAssertEqual(blue.lowerBound, 36, "first bar must sit flush at the parent top edge")
        // Bar 2 (#2ecc71 green): one collapsed 20px gap below bar 1 →
        // canvas y = 36 + 40 + 20 = 96 (NOT 36+40+40 = 116, the old sum).
        let green = try XCTUnwrap(rows(in: img) { r, g, b in
            Int(r) < 80 && Int(g) > 180 && (90...140).contains(Int(b))
        }, "second bar not found")
        XCTAssertEqual(green.lowerBound, 96, "sibling gap must collapse to max(20,20) = 20")
        // Bar 3 (#e74c3c red): third slot at 36 + 2×60 = 156.
        let red = try XCTUnwrap(rows(in: img) { r, g, b in
            Int(r) > 200 && Int(g) < 110 && Int(b) < 90
        }, "third bar not found")
        XCTAssertEqual(red.lowerBound, 156, "third bar must sit at 140 inside the hoisted region")
    }

    // MARK: - 7. The unified collapse gate contract — S1–S12 pin table
    //
    // These twelve scenarios are the SHARED SCENARIO PIN TABLE of the
    // unified collapse gate contract: BOTH natives (this SwiftUI lane and
    // the Compose BlockMarginCollapse lane) must produce IDENTICAL expected
    // values here. Any divergence is the exact bug class the contract
    // exists to kill. All parents are 300px, unpadded/unbordered/position
    // static unless a scenario perturbs one aspect; child margins are
    // t/b px. Wire literals are live-converter shapes ({"px":N}).

    /// A block child with explicit top/bottom margins (px) and a 100×40
    /// box (a real height so it never self-collapses — B9 is off).
    private func marginChild(_ id: String, _ top: Double, _ bottom: Double) -> String {
        """
        {"id":"\(id)","name":"\(id)",
         "properties":[
           {"type":"Width","data":{"type":"length","px":100.0}},
           {"type":"Height","data":{"type":"length","px":40.0}},
           {"type":"MarginTop","data":{"px":\(top)}},
           {"type":"MarginBottom","data":{"px":\(bottom)}},
           {"type":"BackgroundColor","data":{"srgb":{"r":0.2,"g":0.6,"b":0.86},"original":"#3498db"}}
         ]}
        """
    }

    /// A 300px block parent (static/unpadded/unbordered unless `extra`
    /// perturbs it) with optional own top/bottom margin and the given
    /// children JSON.
    private func blockParent(_ id: String,
                             children: [String],
                             ownTop: Double? = nil,
                             ownBottom: Double? = nil,
                             extra: String = "") throws -> IRComponent {
        var props = #"{"type":"Width","data":{"type":"length","px":300.0}}"#
        if let t = ownTop { props += ",{\"type\":\"MarginTop\",\"data\":{\"px\":\(t)}}" }
        if let b = ownBottom { props += ",{\"type\":\"MarginBottom\",\"data\":{\"px\":\(b)}}" }
        props += extra
        return try component("""
        {"id":"\(id)","name":"\(id)",
         "properties":[\(props)],
         "children":[\(children.joined(separator: ","))]}
        """)
    }

    private func ov(_ t: CGFloat, _ b: CGFloat) -> MarginCollapseOverride {
        MarginCollapseOverride(top: t, bottom: b)
    }

    /// S1 — [A(0,20), B(20,0)]: interior gap collapses to max(20,20)=20 on
    /// B's used top, both edge margins are 0 so both hoist bands are 0.
    func testS1_interiorMaxEdgeMarginsZero() throws {
        let p = try XCTUnwrap(plan(try blockParent("s1",
            children: [marginChild("a", 0, 20), marginChild("b", 20, 0)])))
        XCTAssertEqual(p.overrides, [ov(0, 0), ov(20, 0)])
        XCTAssertEqual(p.hoistTop, 0)
        XCTAssertEqual(p.hoistBottom, 0)
    }

    /// S2 — [A(20,20), B(20,20), C(20,20)]: used tops [hoisted 0, 20, 20],
    /// both edges hoist 20/20, content offsets 0/60/120 (40px bars + one
    /// collapsed 20px gap each).
    func testS2_threeEqualBars() throws {
        let p = try XCTUnwrap(plan(try blockParent("s2",
            children: [marginChild("a", 20, 20), marginChild("b", 20, 20), marginChild("c", 20, 20)])))
        XCTAssertEqual(p.overrides, [ov(0, 0), ov(20, 0), ov(20, 0)])
        XCTAssertEqual(p.hoistTop, 20)
        XCTAssertEqual(p.hoistBottom, 20)
        // Content offsets from a spacing-0 VStack of 40px bars.
        var y: CGFloat = 0
        var offsets: [CGFloat] = []
        for o in p.overrides { y += o.top; offsets.append(y); y += 40 + o.bottom }
        XCTAssertEqual(offsets, [0, 60, 120])
    }

    /// S3 — [A(30,0), B(0,10)] parent own (0,0): the interior gap is
    /// max(0,0)=0, A's top 30 hoists and B's bottom 10 hoists → 30/10.
    func testS3_asymmetricEdgesHoist() throws {
        let p = try XCTUnwrap(plan(try blockParent("s3",
            children: [marginChild("a", 30, 0), marginChild("b", 0, 10)])))
        XCTAssertEqual(p.overrides, [ov(0, 0), ov(0, 0)])
        XCTAssertEqual(p.hoistTop, 30)
        XCTAssertEqual(p.hoistBottom, 10)
    }

    /// S4 — parent own margin (20,0), first child top 30: the hoist band is
    /// max(20,30) − 20 = 10 (the excess above the parent's own painted
    /// margin). THE double-count regression pin — the band is computed
    /// from the DECLARED parent margin, never the override-folded one.
    func testS4_hoistBandExcessOverParentOwn() throws {
        let p = try XCTUnwrap(plan(try blockParent("s4",
            children: [marginChild("a", 30, 20), marginChild("b", 20, 20)],
            ownTop: 20)))
        XCTAssertEqual(p.hoistTop, 10, "band = max(parentOwn 20, child 30) − 20 = 10")
    }

    /// S5 — parent padding-top 8: the TOP gate closes (no top hoist, first
    /// child keeps its full 20 inside), interior collapse + bottom hoist
    /// stay live.
    func testS5_paddingTopClosesTopGateOnly() throws {
        let p = try XCTUnwrap(plan(try blockParent("s5",
            children: [marginChild("a", 20, 20), marginChild("b", 20, 20)],
            extra: #",{"type":"PaddingTop","data":{"px":8.0}}"#)))
        XCTAssertEqual(p.overrides, [ov(20, 0), ov(20, 0)])
        XCTAssertEqual(p.hoistTop, 0)
        XCTAssertEqual(p.hoistBottom, 20)
    }

    /// S6 — parent overflow-x hidden establishes a BFC (css-overflow-3
    /// §2.1: the sibling axis computes to auto): BOTH gates close, no
    /// hoists, but interior sibling collapse still applies.
    func testS6_overflowXClosesBothGates() throws {
        let p = try XCTUnwrap(plan(try blockParent("s6",
            children: [marginChild("a", 20, 20), marginChild("b", 20, 20), marginChild("c", 20, 20)],
            extra: #",{"type":"OverflowX","data":"HIDDEN"}"#)))
        XCTAssertEqual(p.overrides, [ov(20, 0), ov(20, 0), ov(20, 20)])
        XCTAssertEqual(p.hoistTop, 0)
        XCTAssertEqual(p.hoistBottom, 0)
    }

    /// S7 — parent position:absolute is out of flow (G4): BOTH gates close,
    /// no hoists, interior collapse still applies.
    func testS7_absoluteParentClosesBothGates() throws {
        let p = try XCTUnwrap(plan(try blockParent("s7",
            children: [marginChild("a", 20, 20), marginChild("b", 20, 20), marginChild("c", 20, 20)],
            extra: #",{"type":"Position","data":"ABSOLUTE"}"#)))
        XCTAssertEqual(p.overrides, [ov(20, 0), ov(20, 0), ov(20, 20)])
        XCTAssertEqual(p.hoistTop, 0)
        XCTAssertEqual(p.hoistBottom, 0)
    }

    /// S8 — parent max-height 500px does NOT close the bottom gate (G5:
    /// only height/min-height/block-size/aspect-ratio do): both hoists open.
    func testS8_maxHeightKeepsBottomGateOpen() throws {
        let p = try XCTUnwrap(plan(try blockParent("s8",
            children: [marginChild("a", 20, 20), marginChild("b", 20, 20), marginChild("c", 20, 20)],
            extra: #",{"type":"MaxHeight","data":{"type":"length","px":500.0}}"#)))
        XCTAssertEqual(p.overrides, [ov(0, 0), ov(20, 0), ov(20, 0)])
        XCTAssertEqual(p.hoistTop, 20)
        XCTAssertEqual(p.hoistBottom, 20)
    }

    /// S9 — a `display: none` child bails the whole container (B1).
    func testS9_displayNoneChildBails() throws {
        let none = #"{"id":"g","name":"g","properties":[{"type":"Display","data":"NONE"},{"type":"MarginTop","data":{"px":20.0}}]}"#
        XCTAssertNil(plan(try blockParent("s9", children: [marginChild("a", 20, 20), none])))
    }

    /// S10 — a `float: left` child bails the whole container (B3).
    func testS10_floatedChildBails() throws {
        let floated = #"{"id":"f","name":"f","properties":[{"type":"Float","data":"LEFT"},{"type":"MarginTop","data":{"px":20.0}},{"type":"MarginBottom","data":{"px":20.0}},{"type":"Height","data":{"type":"length","px":40.0}}]}"#
        XCTAssertNil(plan(try blockParent("s10", children: [marginChild("a", 20, 20), floated])))
    }

    /// S11 — a child whose MEDIA bucket declares a margin longhand bails
    /// the whole container (B5): the bucket could restyle the very margin
    /// the static plan overrides.
    func testS11_mediaBucketMarginChildBails() throws {
        let bucketChild = """
        {"id":"m","name":"m",
         "properties":[
           {"type":"Width","data":{"type":"length","px":100.0}},
           {"type":"Height","data":{"type":"length","px":40.0}},
           {"type":"MarginTop","data":{"px":20.0}},
           {"type":"MarginBottom","data":{"px":20.0}}],
         "media":[{"query":"(max-width: 600px)",
                   "properties":[{"type":"MarginTop","data":{"px":40.0}}]}]}
        """
        XCTAssertNil(plan(try blockParent("s11", children: [marginChild("a", 20, 20), bucketChild])))
    }

    /// S12 — a middle child with no text/children, no explicit height, and
    /// BOTH vertical margins (20,20) is a self-collapsing candidate: the
    /// pairwise fold can't reproduce the n-ary max, so bail (B9).
    func testS12_selfCollapsingMiddleChildBails() throws {
        let selfCollapsing = #"{"id":"sc","name":"sc","properties":[{"type":"MarginTop","data":{"px":20.0}},{"type":"MarginBottom","data":{"px":20.0}}]}"#
        XCTAssertNil(plan(try blockParent("s12",
            children: [marginChild("a", 20, 20), selfCollapsing, marginChild("c", 20, 20)])))
    }

    // MARK: - 8. Contract bail coverage — B2 / B4 / B6 / B7 / B10

    /// B2 — an inline-level child (inline-block here) bails the container:
    /// §8.3.1 collapses block-level margins only.
    func testB2_inlineLevelChildBails() throws {
        let inlineBlock = #"{"id":"ib","name":"ib","properties":[{"type":"Display","data":"INLINE_BLOCK"},{"type":"MarginTop","data":{"px":20.0}},{"type":"MarginBottom","data":{"px":20.0}},{"type":"Height","data":{"type":"length","px":40.0}}]}"#
        XCTAssertNil(plan(try blockParent("b2", children: [marginChild("a", 20, 20), inlineBlock])))
    }

    /// B4 — an absolutely-positioned SIBLING bails the whole container
    /// (conservative parity: index-map alignment forbids skip-and-collapse).
    func testB4_absolutePositionedChildBails() throws {
        let abs = #"{"id":"ap","name":"ap","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"MarginTop","data":{"px":20.0}},{"type":"MarginBottom","data":{"px":20.0}},{"type":"Height","data":{"type":"length","px":40.0}}]}"#
        XCTAssertNil(plan(try blockParent("b4", children: [marginChild("a", 20, 20), abs])))
    }

    /// B6 — a parent whose selector bucket declares a gate-affecting
    /// longhand (padding here) bails: a hover restyle could flip a gate
    /// after the static plan baked it.
    func testB6_parentBucketGateAffectingBails() throws {
        let parent = try component("""
        {"id":"b6","name":"b6",
         "properties":[{"type":"Width","data":{"type":"length","px":300.0}}],
         "selectors":[{"condition":"hover",
                       "properties":[{"type":"PaddingTop","data":{"px":12.0}}]}],
         "children":[\(marginChild("a", 20, 20)),\(marginChild("b", 20, 20))]}
        """)
        XCTAssertNil(plan(parent))
    }

    /// B7 — a non-static parent own margin (auto) bails the whole
    /// container: the hoist max() composition needs a static own margin.
    func testB7_dynamicParentOwnMarginBails() throws {
        let parent = try component("""
        {"id":"b7","name":"b7",
         "properties":[{"type":"Width","data":{"type":"length","px":300.0}},
                       {"type":"MarginTop","data":"auto"}],
         "children":[\(marginChild("a", 20, 20)),\(marginChild("b", 20, 20))]}
        """)
        XCTAssertNil(plan(parent))
    }

    /// B10 — a FIRST child that is itself an eligible unpadded/unbordered
    /// block container WITH children bails: a grandchild edge margin would
    /// chain-collapse through two levels (single-level emulation can't).
    func testB10_nestedHoistChainFirstChildBails() throws {
        let nested = """
        {"id":"nest","name":"nest",
         "properties":[{"type":"MarginTop","data":{"px":20.0}},{"type":"MarginBottom","data":{"px":20.0}}],
         "children":[\(marginChild("gc", 20, 20))]}
        """
        XCTAssertNil(plan(try blockParent("b10", children: [nested, marginChild("b", 20, 20)])))
    }
}
