//
//  Wave12BoxSizingBasisTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 12 — two confirmed iOS diagnoses, pinned:
//
//  1. CONTAINING-BLOCK BOX-SIZING AXIS (ContainingBlockBasis): the
//     wave-9 contentBox/paddingBox math subtracted the padding+border
//     bands from the DECLARED size unconditionally — but under an
//     effective `box-sizing: content-box` (css-sizing-3 §3, the CSS
//     initial value, explicit in WPT capture via
//     SizeApplierMath.effectiveBoxSizing) the declared size ALREADY IS
//     the content box. Two pixel-proven regressions pinned here:
//       • the css-break fragmentainer sliced at 118 instead of 120
//         (block-size:120 minus its 1px border pair);
//       • the flexbox abspos 100%×100% child rendered 70×80 (declared
//         100 minus the 20/10/5/15 border bands) instead of covering
//         the full 100×100 padding box.
//
//  2. INHERITED WRITING-MODE THREADING: `writing-mode` is
//     Inherited: yes (css-writing-modes-4 §3.1) but was absent from
//     InheritedText.inheritedTypes, AND the fragmentPlan call site read
//     it through the typography aggregate (nil whenever writing-mode is
//     the only typography signal — WritingModeApplier never flips
//     `touched`), so the vertical-mode bail + logOnce never fired.
//
//  Wire shapes below are pinned against LIVE converter output (JDK 21,
//  wave 12): sizes as typed `{"type":"length","px":N}` objects, border
//  widths as `{"px":N}`, keywords as bare SHOUTY strings — WritingMode
//  verified this wave: `{"type":"WritingMode","data":"VERTICAL_RL"}`.
//

import XCTest
import SwiftUI
// @testable: StyleBuilder / ContainingBlockBasis / ColumnsApplier /
// InheritedText / WritingModeExtractor are internal to the module.
@testable import StyleConverterRuntime

final class Wave12BoxSizingBasisTests: XCTestCase {

    // MARK: - wire builders (converter-pinned shapes)

    /// Typed length property, e.g. Width → {"type":"length","px":100}.
    private func len(_ type: String, _ v: Double) -> IRProperty {
        IRProperty(type: type, data: .object([
            "type": .string("length"), "px": .double(v)]))
    }

    /// Border-width-style px object, e.g. BorderTopWidth → {"px": 1}.
    private func px(_ type: String, _ v: Double) -> IRProperty {
        IRProperty(type: type, data: .object(["px": .double(v)]))
    }

    /// Bare keyword property, e.g. BorderTopStyle → "SOLID".
    private func kw(_ type: String, _ v: String) -> IRProperty {
        IRProperty(type: type, data: .string(v))
    }

    /// Build a ComponentStyle and optionally apply the renderer's WPT
    /// box-sizing fold (ComponentRenderer.styledContent: an UNDECLARED
    /// box-sizing defaults to content-box in WPT capture ONLY — the
    /// css-sizing-3 §3 initial value the WPT refs assume).
    private func style(_ props: [IRProperty], wptFold: Bool) -> ComponentStyle {
        var s = StyleBuilder.build(from: props)
        // Same fold the renderer applies before any basis math runs.
        s.size.boxSizing = SizeApplierMath.effectiveBoxSizing(
            declared: s.size.boxSizing, wptCaptureMode: wptFold)
        return s
    }

    // MARK: - 1a. The fragmentainer scenario (120 → 120 slices)

    /// The css-break fragmentainer wire: 470×120 multicol box with a
    /// 1px top/bottom border pair (SOLID so the band actually paints —
    /// `border-style: none` would have used width 0, CSS 2.1 §8.5.3).
    private var fragmentainerProps: [IRProperty] {
        [len("Width", 470), len("Height", 120),
         px("BorderTopWidth", 1), kw("BorderTopStyle", "SOLID"),
         px("BorderBottomWidth", 1), kw("BorderBottomStyle", "SOLID")]
    }

    /// Effective content-box (the WPT fold): the declared block-size IS
    /// the content box — the basis must be 120, NOT 120 − 2 (the pre-fix
    /// value that sliced every fragment 2px short).
    func testContentBoxBasisPassesDeclaredThroughUnderContentBox() {
        let s = style(fragmentainerProps, wptFold: true)
        // Block axis: declared 120 passes through untouched.
        XCTAssertEqual(ContainingBlockBasis.contentBox(style: s, vertical: true), 120,
                       "content-box: declared block-size IS the content box")
        // Inline axis: no horizontal bands declared — 470 either way.
        XCTAssertEqual(ContainingBlockBasis.contentBox(style: s, vertical: false), 470,
                       "content-box: declared inline-size IS the content box")
    }

    /// An EXPLICIT `box-sizing: border-box` keeps the wave-9 subtraction
    /// even in WPT capture (a declared keyword always wins the fold).
    func testExplicitBorderBoxKeepsBandSubtraction() {
        let s = style(fragmentainerProps + [kw("BoxSizing", "BORDER_BOX")],
                      wptFold: true)
        // 120 − (1 + 1) border pair = 118 — the border-box content box.
        XCTAssertEqual(ContainingBlockBasis.contentBox(style: s, vertical: true), 118,
                       "border-box: content box = declared − painted bands")
    }

    /// UNSET box-sizing outside WPT capture stays the frozen border-box
    /// status quo (the dark-stage corpus is captured against the web
    /// harness's `* { box-sizing: border-box }` reset) — the wave-9
    /// subtraction must stay byte-identical there.
    func testUnsetOutsideWptKeepsBorderBoxStatusQuo() {
        let s = style(fragmentainerProps, wptFold: false)
        // nil box-sizing → the pre-wave-12 arithmetic, unchanged.
        XCTAssertEqual(ContainingBlockBasis.contentBox(style: s, vertical: true), 118,
                       "unset/non-WPT: the frozen border-box subtraction stays")
    }

    /// End-to-end plan pin: with the fixed 120 basis, a 240-tall child
    /// slices into two FULL 120px fragments (css-break-3 §4). Pre-fix
    /// the 118 basis produced 118px bands and a −118 translate.
    func testFragmentPlanSlicesAtFullBlockSize() throws {
        let s = style(fragmentainerProps, wptFold: true)
        // The container config: column-count 3 (the §2 multicol gate).
        var cols = ColumnsConfig()
        cols.count = 3
        // The SAME basis lane the renderer's call site feeds fragmentPlan
        // (childCB / childCBH via flexContentSize → ContainingBlockBasis).
        let plan = try XCTUnwrap(ColumnsApplier.fragmentPlan(
            columns: cols, verticalWritingMode: false, siblingCount: 1,
            contentWidthPx: ContainingBlockBasis.contentBox(style: s, vertical: false),
            contentHeightPx: ContainingBlockBasis.contentBox(style: s, vertical: true),
            gapPx: 10,
            // The overflowing child declares an explicit 240px block-size.
            childProperties: [len("Height", 240)],
            ctx: s.spacing.context))
        // H is the full declared block-size — the 120→120 pin.
        XCTAssertEqual(plan.columnBlockSizePx, 120, "H = declared 120, not 118")
        // ceil(240 / 120) = 2 fragments, third column stays empty.
        XCTAssertEqual(plan.fragments.count, 2, "C=240 over H=120 → 2 fragments")
        // Fragment 1 shows the [120, 240) band: full-height clip, −120
        // translate — pre-fix these were 118 and −118 (a visible seam).
        XCTAssertEqual(plan.fragments[1].clipRect.height, 120,
                       "fragment bands are full 120px slices")
        XCTAssertEqual(plan.fragments[1].translate.height, -120,
                       "fragment 1 exposes [120, 240) — sliced at 120")
    }

    // MARK: - 1b. The abspos padding-box scenario (70×80 → 100×100)

    /// The flexbox-abspos wire: a positioned 100×100 ancestor with the
    /// asymmetric 20(left)/10(right)/5(top)/15(bottom) border set of the
    /// measured WPT scenario (all SOLID so every band paints).
    private var borderedAncestorProps: [IRProperty] {
        [kw("Position", "RELATIVE"), len("Width", 100), len("Height", 100),
         px("BorderLeftWidth", 20), kw("BorderLeftStyle", "SOLID"),
         px("BorderRightWidth", 10), kw("BorderRightStyle", "SOLID"),
         px("BorderTopWidth", 5), kw("BorderTopStyle", "SOLID"),
         px("BorderBottomWidth", 15), kw("BorderBottomStyle", "SOLID")]
    }

    /// Effective content-box: the declared 100×100 IS the content box,
    /// and with zero padding the PADDING box equals it — the abspos
    /// `100%×100%` child must resolve 100×100 (css-position-3 §3.1),
    /// not 70×80 (declared minus the border bands — the pre-fix render).
    func testPaddingBoxBasisUnderContentBoxIsDeclaredPlusPadding() {
        let s = style(borderedAncestorProps, wptFold: true)
        // Horizontal: pre-fix 100 − (20+10) = 70; fixed: 100.
        XCTAssertEqual(ContainingBlockBasis.paddingBox(style: s, vertical: false), 100,
                       "content-box abspos basis: full 100, not declared − borders")
        // Vertical: pre-fix 100 − (5+15) = 80; fixed: 100.
        XCTAssertEqual(ContainingBlockBasis.paddingBox(style: s, vertical: true), 100,
                       "content-box abspos basis: full 100, not declared − borders")
    }

    /// EXPLICIT border-box keeps the wave-9 padding-box arithmetic —
    /// declared border box minus the painted borders (70×80 is CORRECT
    /// when the author really declared border-box sizing).
    func testPaddingBoxBasisUnderExplicitBorderBox() {
        let s = style(borderedAncestorProps + [kw("BoxSizing", "BORDER_BOX")],
                      wptFold: true)
        // 100 − (20 + 10) = 70 — the border-box padding box.
        XCTAssertEqual(ContainingBlockBasis.paddingBox(style: s, vertical: false), 70)
        // 100 − (5 + 15) = 80.
        XCTAssertEqual(ContainingBlockBasis.paddingBox(style: s, vertical: true), 80)
    }

    /// A PADDED content-box ancestor: the padding box WRAPS the declared
    /// content (declared + padding band), keeping the basis in agreement
    /// with the wave-8 overlay anchor (frame − border band = declared +
    /// padding), while the in-flow content basis passes declared through.
    func testPaddedContentBoxAncestorBases() {
        let s = style([len("Width", 100), len("Height", 100),
                       px("PaddingTop", 8), px("PaddingRight", 8),
                       px("PaddingBottom", 8), px("PaddingLeft", 8)],
                      wptFold: true)
        // In-flow basis: the declared size IS the content box.
        XCTAssertEqual(ContainingBlockBasis.contentBox(style: s, vertical: false), 100)
        XCTAssertEqual(ContainingBlockBasis.contentBox(style: s, vertical: true), 100)
        // Abspos basis: content + the 8px padding band on both edges.
        XCTAssertEqual(ContainingBlockBasis.paddingBox(style: s, vertical: false), 116)
        XCTAssertEqual(ContainingBlockBasis.paddingBox(style: s, vertical: true), 116)
    }

    // MARK: - raster harness (shared by 1b end-to-end + lane 2)

    /// Render on a white canvas at scale 1 and return the RGB bytes at
    /// (x, y) — the same CGContext harness as FragmentGeometryTests.
    @MainActor
    private func pixel(_ comp: IRComponent, wpt: Bool,
                       x: Int, y: Int) throws -> (r: UInt8, g: UInt8, b: UInt8) {
        // topLeading pin so probe coordinates are canvas coordinates.
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

    /// End-to-end raster pin of scenario 1b: in WPT capture the painted
    /// ancestor frame is 130×120 (content 100×100 + the border bands,
    /// SizeApplier.inflatedAxis), the wave-8 overlay anchors the abspos
    /// child at the padding-box corner (20, 5), and — with the fixed
    /// basis — the 100%×100% child covers the FULL 100×100 padding box.
    @MainActor
    func testAbsposChildCoversPaddingBoxUnderContentBox() throws {
        // Nested wire (the runtime decodes v1 nested children directly):
        // the bordered ancestor in red, the abspos child in blue.
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"p","name":"BorderedAncestor",
         "properties":[
           {"type":"Position","data":"RELATIVE"},
           {"type":"Width","data":{"type":"length","px":100.0}},
           {"type":"Height","data":{"type":"length","px":100.0}},
           {"type":"BorderLeftWidth","data":{"px":20.0}},
           {"type":"BorderLeftStyle","data":"SOLID"},
           {"type":"BorderLeftColor","data":{"srgb":{"r":0.0,"g":1.0,"b":0.0},"original":"#00ff00"}},
           {"type":"BorderRightWidth","data":{"px":10.0}},
           {"type":"BorderRightStyle","data":"SOLID"},
           {"type":"BorderRightColor","data":{"srgb":{"r":0.0,"g":1.0,"b":0.0},"original":"#00ff00"}},
           {"type":"BorderTopWidth","data":{"px":5.0}},
           {"type":"BorderTopStyle","data":"SOLID"},
           {"type":"BorderTopColor","data":{"srgb":{"r":0.0,"g":1.0,"b":0.0},"original":"#00ff00"}},
           {"type":"BorderBottomWidth","data":{"px":15.0}},
           {"type":"BorderBottomStyle","data":"SOLID"},
           {"type":"BorderBottomColor","data":{"srgb":{"r":0.0,"g":1.0,"b":0.0},"original":"#00ff00"}},
           {"type":"BackgroundColor","data":{"srgb":{"r":1.0,"g":0.0,"b":0.0},"original":"#ff0000"}}
         ],
         "children":[
           {"id":"c","name":"AbsChild",
            "properties":[
              {"type":"Position","data":"ABSOLUTE"},
              {"type":"Top","data":{"px":0.0}},
              {"type":"Left","data":{"px":0.0}},
              {"type":"Width","data":{"type":"percentage","value":100.0}},
              {"type":"Height","data":{"type":"percentage","value":100.0}},
              {"type":"BackgroundColor","data":{"srgb":{"r":0.0,"g":0.0,"b":1.0},"original":"#0000ff"}}
            ]}
         ]}
        """.utf8))
        // Padding box spans x ∈ [20, 120), y ∈ [5, 105) in canvas coords.
        // Center: blue both pre- and post-fix (sanity).
        let center = try pixel(comp, wpt: true, x: 70, y: 55)
        XCTAssertGreaterThan(center.b, 192, "abspos child paints at the center")
        // Right band (x = 115 < 120): pre-fix the 70-wide child ended at
        // x = 90 and the parent's red showed — post-fix it must be blue.
        let right = try pixel(comp, wpt: true, x: 115, y: 55)
        XCTAssertGreaterThan(right.b, 192, "child covers the padding box's right band")
        XCTAssertLessThan(right.r, 64, "no parent red in the right band")
        // Bottom band (y = 100 < 105): pre-fix the 80-tall child ended
        // at y = 85 — post-fix blue.
        let bottom = try pixel(comp, wpt: true, x: 70, y: 100)
        XCTAssertGreaterThan(bottom.b, 192, "child covers the padding box's bottom band")
        XCTAssertLessThan(bottom.r, 64, "no parent red in the bottom band")
        // The right BORDER band (x ∈ [120, 130)) stays child-free — the
        // containing block is the padding box, never the border box, so
        // the explicit GREEN border must still show at x = 125 (a child
        // overshooting to the border box would paint it blue).
        let border = try pixel(comp, wpt: true, x: 125, y: 55)
        XCTAssertGreaterThan(border.g, 192, "the border band keeps its green stroke")
        XCTAssertLessThan(border.b, 64, "child must not overshoot into the border band")
    }

    // MARK: - 2. Inherited writing-mode threading

    /// `writing-mode` flows parent → child (css-writing-modes-4 §3.1
    /// Inherited: yes): the InheritedText channel must carry it, and the
    /// merged list must expose it to WritingModeExtractor — the exact
    /// read the fragmentPlan call site now performs.
    func testWritingModeInheritsThroughMergedList() {
        // The ancestor's declaration, converter-pinned wire shape.
        let parent = [kw("WritingMode", "VERTICAL_RL"), len("Width", 470)]
        // The channel filter must now include WritingMode (Width must not
        // flow — layout properties never inherit).
        let flowing = InheritedText.inheritable(from: parent)
        XCTAssertEqual(flowing.map(\.type), ["WritingMode"],
                       "WritingMode inherits; Width must not")
        // A multicol container that declares NO writing mode of its own
        // sees the ancestor's vertical mode in its merged list.
        let merged = InheritedText.merge(
            own: [len("Height", 120)],
            inherited: flowing)
        XCTAssertTrue(WritingModeExtractor.extract(from: merged)?.isVertical == true,
                      "the inherited vertical mode reaches the merged read")
        // Own declaration WINS over the inherited one (cascade: own
        // properties block inheritance — InheritedText.merge contract).
        let ownWins = InheritedText.merge(
            own: [kw("WritingMode", "HORIZONTAL_TB")], inherited: flowing)
        XCTAssertFalse(WritingModeExtractor.extract(from: ownWins)?.isVertical == true,
                       "an own horizontal-tb must beat the inherited vertical mode")
    }

    /// The threaded flag actually fires the fragmentPlan bail + its
    /// logOnce breadcrumb (no silent fallthrough) — the pure composition
    /// of the inheritance read and the wave-10 gate.
    func testInheritedVerticalModeBailsFragmentPlan() throws {
        // Fresh dedupe set so THIS test observes the breadcrumb.
        PropertyTracker._resetForTests()
        // The merged container list: ancestor vertical mode + own multicol.
        let merged = InheritedText.merge(
            own: [len("Height", 120)],
            inherited: InheritedText.inheritable(
                from: [kw("WritingMode", "VERTICAL_RL")]))
        // The call-site read (wave 12): extractor over the merged list.
        let vertical = WritingModeExtractor.extract(from: merged)?.isVertical == true
        // A geometry that WOULD fragment (C=240 > H=120) must bail.
        var cols = ColumnsConfig()
        cols.count = 3
        XCTAssertNil(ColumnsApplier.fragmentPlan(
            columns: cols, verticalWritingMode: vertical, siblingCount: 1,
            contentWidthPx: 470, contentHeightPx: 120, gapPx: 10,
            childProperties: [len("Height", 240)], ctx: SpacingContext()),
            "inherited vertical writing-mode: blocked-platform — no fragmentation")
        // The bail logged its breadcrumb: a fresh logOnce on the same key
        // reports NOT-first.
        XCTAssertFalse(PropertyTracker.logOnce(
            key: "multicol-fragment-vertical-writing", message: "probe"),
            "the vertical-writing bail must have logged its breadcrumb")
    }

    /// End-to-end raster pin: a multicol container nested under a
    /// vertical-writing ANCESTOR renders its overflowing child
    /// UNFRAGMENTED (the bail path) — column 1 stays empty and the tail
    /// overflows below the container, exactly the pre-wave-10 geometry.
    @MainActor
    func testInheritedVerticalModeRendersUnfragmented() throws {
        // Fresh dedupe set: the render itself must emit the breadcrumb.
        PropertyTracker._resetForTests()
        // Wrapper declares the vertical mode; the 470×120 count-3
        // multicol container holds one 150×350 red child.
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"w","name":"VerticalAncestor",
         "properties":[
           {"type":"WritingMode","data":"VERTICAL_RL"}
         ],
         "children":[
           {"id":"m","name":"MulticolParent",
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
                 {"type":"Height","data":{"type":"length","px":350.0}},
                 {"type":"BackgroundColor","data":{"srgb":{"r":1.0,"g":0.0,"b":0.0},"original":"#ff0000"}}
               ]}
            ]}
         ]}
        """.utf8))
        // Column 1's slot (x=200): a FRAGMENTED render paints the child's
        // [120, 240) band here (FragmentGeometryTests S1) — the bail must
        // leave it white (the unfragmented child is only 150 wide).
        let col1 = try pixel(comp, wpt: true, x: 200, y: 5)
        XCTAssertGreaterThan(col1.r, 192, "column 1 slot stays unpainted (white)")
        XCTAssertGreaterThan(col1.g, 192, "column 1 slot stays unpainted (white)")
        XCTAssertGreaterThan(col1.b, 192, "column 1 slot stays unpainted (white)")
        // Below the container (y=150 > H=120): the unfragmented 350-tall
        // child overflows and paints red — a fragmented render clips
        // every band to H and leaves this white.
        let below = try pixel(comp, wpt: true, x: 75, y: 150)
        XCTAssertGreaterThan(below.r, 192, "unfragmented tail paints below the row")
        XCTAssertLessThan(below.b, 64, "unfragmented tail paints red, not blue/white")
        // And the bail surfaced its breadcrumb during the render (the
        // repo's no-silent-fallthrough rule).
        XCTAssertFalse(PropertyTracker.logOnce(
            key: "multicol-fragment-vertical-writing", message: "probe"),
            "the render must have logged the vertical-writing bail")
    }
}
