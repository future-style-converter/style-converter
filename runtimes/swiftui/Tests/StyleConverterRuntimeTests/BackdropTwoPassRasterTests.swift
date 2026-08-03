//
//  BackdropTwoPassRasterTests.swift
//  Lane BF-I — the VIEW-TREE half of the two-pass backdrop, measured
//  through ImageRenderer on a canvas that mirrors the harness's
//  CaptureCanvas (390-wide surface, `.backdropCanvasRoot()` at the
//  outermost position, ImageRenderer at scale 1).
//
//  The two passes are driven here exactly as
//  ScreenshotManager.renderBackdropTwoPass drives them, so this file pins
//  the contract the harness relies on without needing a simulator:
//
//    1. PASS A suppresses the backdrop element's paint but NOT its layout;
//    2. PASS B paints the filtered backdrop UNDER the element, so a
//       translucent box shows the inverted stage while an opaque one is
//       unchanged;
//    3. the backplate is clipped to the element's rounded border box;
//    4. THE IDENTITY GUARANTEE — with no pass published (`.disabled`, the
//       default on every baseline path), a component declaring
//       `backdrop-filter` renders byte-for-byte like the same component
//       with the property removed. That is the 327-pair protection stated
//       as a test, not as a comment.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class BackdropTwoPassRasterTests: XCTestCase {

    // MARK: - fixtures

    /// A stage-plus-box document root: a 120×60 GREEN strip with a 60×60
    /// child box on top of it. The child is translucent white (alpha 0.25)
    /// so whatever is painted under it — the stage, or the FILTERED stage —
    /// shows through and can be probed.
    ///
    /// `backdrop` is spliced in verbatim so the identity test can build the
    /// exact same tree with and without the property.
    private func stage(backdrop: String?) throws -> IRComponent {
        let bd = backdrop.map { ",\($0)" } ?? ""
        return try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"stage","name":"Stage","properties":[
          {"type":"Width","data":{"type":"length","px":120}},
          {"type":"Height","data":{"type":"length","px":60}},
          {"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.8,"b":0.2},
                                            "original":"green"}}],
         "children":[
          {"id":"glass","name":"Glass","properties":[
            {"type":"Width","data":{"type":"length","px":60}},
            {"type":"Height","data":{"type":"length","px":60}},
            {"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1,"a":0.25},
                                              "original":"rgba(255,255,255,.25)"}}\(bd)]}]}
        """.utf8))
    }

    /// `backdrop-filter: invert(100%)` on the live wire shape (an array of
    /// {fn, v} objects — FilterExtractor.parseList).
    private let invertProp =
        #"{"type":"BackdropFilter","data":[{"fn":"invert","v":100}]}"#

    // MARK: - canvas + passes (mirrors the harness)

    /// The capture surface: the component on a dark stage, at the canvas
    /// width, with the backdrop coordinate space named at the outermost
    /// position — the same three things CaptureCanvas does.
    @MainActor
    private func canvas(_ comp: IRComponent) -> some View {
        ComponentRenderer(component: comp)
            .frame(width: 200, height: 100, alignment: .topLeading)
            .background(Color(red: 0.1, green: 0.1, blue: 0.18))
            .backdropCanvasRoot()
    }

    /// One ImageRenderer pass at scale 1 under an explicit pass value.
    @MainActor
    private func render(_ comp: IRComponent, pass: BackdropPass) throws -> CGImage {
        let renderer = ImageRenderer(content: canvas(comp).environment(\.backdropPass, pass))
        renderer.scale = 1
        return try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
    }

    /// The full two-pass drive — identical in shape to
    /// ScreenshotManager.renderBackdropTwoPass.
    @MainActor
    private func renderTwoPass(_ comp: IRComponent) throws -> (a: CGImage, b: CGImage) {
        let a = try render(comp, pass: .sampling)
        let b = try render(comp, pass: .compositing(BackdropPlate(image: a, scale: 1)))
        return (a, b)
    }

    /// RGB at a pixel, read back through a fixed sRGB RGBX context so the
    /// probe adds no colour conversion of its own.
    private func rgb(_ image: CGImage, _ x: Int, _ y: Int) throws -> (Int, Int, Int) {
        let space = try XCTUnwrap(CGColorSpace(name: CGColorSpace.sRGB))
        let ctx = try XCTUnwrap(CGContext(
            data: nil, width: image.width, height: image.height, bitsPerComponent: 8,
            bytesPerRow: 0, space: space,
            bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue))
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        let base = try XCTUnwrap(ctx.data).bindMemory(
            to: UInt8.self, capacity: ctx.bytesPerRow * image.height)
        let i = y * ctx.bytesPerRow + x * 4
        return (Int(base[i]), Int(base[i + 1]), Int(base[i + 2]))
    }

    /// Raw bytes of a render, for byte-equality claims.
    private func bytes(_ image: CGImage) throws -> Data {
        try XCTUnwrap(image.dataProvider?.data as Data?)
    }

    // MARK: - 1. pass A suppresses paint, not layout

    /// In pass A the glass box's own paint is gone (the pixel under it is
    /// the bare green strip), while the strip it sits on — which declares
    /// no backdrop-filter — is untouched.
    @MainActor
    func testPassASuppressesOnlyTheBackdropElementsPaint() throws {
        let comp = try stage(backdrop: invertProp)
        let a = try render(comp, pass: .sampling)
        // (20,20) is inside the glass box: pass A must show pure stage green.
        let under = try rgb(a, 20, 20)
        XCTAssertEqual(under.1, 204, accuracy: 6, "glass paint not suppressed")
        XCTAssertEqual(under.0, 0, accuracy: 6)
        // (90,20) is the strip beside the box — unchanged either way.
        let beside = try rgb(a, 90, 20)
        XCTAssertEqual(beside.1, 204, accuracy: 6)
        // Layout is preserved: the strip still ends at x=120, so x=130 is
        // canvas stage, not green.
        XCTAssertLessThan((try rgb(a, 130, 20)).1, 80, "layout moved in pass A")
    }

    // MARK: - 2. pass B paints the filtered backdrop underneath

    /// With `invert(100%)`, the green stage under the glass becomes its
    /// complement before the translucent white box composites over it. The
    /// probe compares against the SAME pixel from the `.disabled` render,
    /// so it measures the lane's effect, not absolute colour management.
    @MainActor
    func testPassBInvertsTheBackdropUnderTheElement() throws {
        let comp = try stage(backdrop: invertProp)
        let plain = try render(comp, pass: .disabled)
        let composited = try renderTwoPass(comp).b
        let before = try rgb(plain, 20, 20)
        let after = try rgb(composited, 20, 20)
        // Green stage (low R, high G) → inverted (high R, low G).
        XCTAssertGreaterThan(after.0, before.0 + 60, "red channel not inverted")
        XCTAssertLessThan(after.1, before.1 - 60, "green channel not inverted")
        // Outside the glass box the strip is untouched — the backplate is
        // clipped to the element's own border box.
        let outside = try rgb(composited, 90, 20)
        XCTAssertEqual(outside.1, (try rgb(plain, 90, 20)).1, accuracy: 2)
    }

    /// An OPAQUE box over the same inverted backdrop is unchanged: the
    /// backplate is drawn UNDER the element's own background, exactly like
    /// a CSS backdrop, so opaque paint hides it completely.
    @MainActor
    func testOpaqueElementHidesItsOwnBackdrop() throws {
        let opaque = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"box","name":"Box","properties":[
          {"type":"Width","data":{"type":"length","px":60}},
          {"type":"Height","data":{"type":"length","px":60}},
          {"type":"BackgroundColor","data":{"srgb":{"r":0.2,"g":0.3,"b":0.9},
                                            "original":"blue"}},
          \(invertProp)]}
        """.utf8))
        let plain = try render(opaque, pass: .disabled)
        let composited = try renderTwoPass(opaque).b
        XCTAssertEqual(try bytes(plain), try bytes(composited),
                       "backplate leaked above the element's own background")
    }

    // MARK: - 3. rounded border box

    /// A fully-rounded (50%) glass box must not paint its backplate in the
    /// corner outside the ellipse — the corner pixel stays what the
    /// `.disabled` render put there.
    @MainActor
    func testBackplateIsClippedToTheRoundedBorderBox() throws {
        let rounded = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"stage","name":"Stage","properties":[
          {"type":"Width","data":{"type":"length","px":120}},
          {"type":"Height","data":{"type":"length","px":60}},
          {"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.8,"b":0.2},
                                            "original":"green"}}],
         "children":[
          {"id":"glass","name":"Glass","properties":[
            {"type":"Width","data":{"type":"length","px":60}},
            {"type":"Height","data":{"type":"length","px":60}},
            {"type":"BorderTopLeftRadius","data":{"type":"length","px":30}},
            {"type":"BorderTopRightRadius","data":{"type":"length","px":30}},
            {"type":"BorderBottomLeftRadius","data":{"type":"length","px":30}},
            {"type":"BorderBottomRightRadius","data":{"type":"length","px":30}},
            {"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1,"a":0.25},
                                              "original":"rgba(255,255,255,.25)"}},
            \(invertProp)]}]}
        """.utf8))
        let plain = try render(rounded, pass: .disabled)
        let composited = try renderTwoPass(rounded).b
        // (1,1) is the box's top-left corner, well outside a 30pt radius.
        XCTAssertEqual(try rgb(composited, 1, 1).0, try rgb(plain, 1, 1).0, accuracy: 3,
                       "backplate painted outside the rounded corner")
        // …while the centre still shows the inverted backdrop.
        XCTAssertGreaterThan(try rgb(composited, 30, 30).0,
                             try rgb(plain, 30, 30).0 + 60)
    }

    // MARK: - 4. blur, over an edge that really exists in the plate

    /// A translucent box with `backdrop-filter: blur(6px)` over a canvas
    /// whose background has a hard black→green edge at x = 30. The stage
    /// split lives in the CANVAS (as it does in the harness, where the
    /// capture canvas's own paint is part of the plate), so the sampled
    /// backdrop genuinely contains an edge to soften.
    ///
    /// Pin: the 4px-wide step across the edge collapses under the blur,
    /// while the `.disabled` render keeps it sharp.
    @MainActor
    func testBlurSoftensAnEdgeInTheSampledBackdrop() throws {
        let glass = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"glass","name":"Glass","properties":[
          {"type":"Width","data":{"type":"length","px":60}},
          {"type":"Height","data":{"type":"length","px":60}},
          {"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1,"a":0.25},
                                            "original":"rgba(255,255,255,.25)"}},
          {"type":"BackdropFilter","data":[{"fn":"blur","r":{"px":6}}]}]}
        """.utf8))
        // Canvas with the split stage — black left of x=30, green right.
        @MainActor func split(_ pass: BackdropPass) throws -> CGImage {
            let view = ComponentRenderer(component: glass)
                .frame(width: 200, height: 100, alignment: .topLeading)
                .background(alignment: .topLeading) {
                    HStack(spacing: 0) {
                        Color.black.frame(width: 30)
                        Color(red: 0, green: 0.8, blue: 0.2)
                    }
                }
                .backdropCanvasRoot()
                .environment(\.backdropPass, pass)
            let renderer = ImageRenderer(content: view)
            renderer.scale = 1
            return try XCTUnwrap(renderer.cgImage)
        }
        let plain = try split(.disabled)
        let blurred = try split(.compositing(BackdropPlate(image: try split(.sampling),
                                                           scale: 1)))
        // Green-channel step measured 2px either side of the edge.
        let sharpStep = abs((try rgb(plain, 32, 30)).1 - (try rgb(plain, 28, 30)).1)
        let softStep = abs((try rgb(blurred, 32, 30)).1 - (try rgb(blurred, 28, 30)).1)
        XCTAssertGreaterThan(sharpStep, 60, "the unblurred stage edge is not sharp")
        XCTAssertLessThan(softStep, sharpStep / 2,
                          "blur did not soften the sampled backdrop edge "
                          + "(sharp \(sharpStep) → soft \(softStep))")
        // Sanity: the dark side got brighter — green bled across the edge.
        XCTAssertGreaterThan((try rgb(blurred, 28, 30)).1,
                             (try rgb(plain, 28, 30)).1 + 10)
    }

    // MARK: - 4b. the element's own `filter` must not reach the backplate

    /// EXECUTED PROOF for wave-26 skeptic fix #3. A translucent box declares
    /// BOTH `backdrop-filter: invert(100%)` and `filter: invert(100%)`.
    ///
    /// filter-effects-2 §2: the filtered backdrop is painted UNDER the
    /// element's own paint and OUTSIDE the element's own `filter`. So the
    /// green stage under the box must come out INVERTED (magenta-ish, high R /
    /// low G) — inverted once by the backdrop chain, and not a second time.
    ///
    /// Before the fix, BackdropApplier was attached BEFORE the foreground
    /// loop, so `.colorInvert()` wrapped the backplate: invert∘invert is the
    /// identity, and the backdrop came back out plain green — a rendering that
    /// looks exactly like the lane never ran.
    @MainActor
    func testOwnFilterDoesNotReInvertTheBackplate() throws {
        // Same stage as `stage(backdrop:)`, plus `filter: invert(100%)` on the
        // glass box. `.colorInvert()` is SwiftUI's only invert, so the CSS
        // amount is 100% to keep the foreground mapping exact.
        let both = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"stage","name":"Stage","properties":[
          {"type":"Width","data":{"type":"length","px":120}},
          {"type":"Height","data":{"type":"length","px":60}},
          {"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.8,"b":0.2},
                                            "original":"green"}}],
         "children":[
          {"id":"glass","name":"Glass","properties":[
            {"type":"Width","data":{"type":"length","px":60}},
            {"type":"Height","data":{"type":"length","px":60}},
            {"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1,"a":0.25},
                                              "original":"rgba(255,255,255,.25)"}},
            {"type":"Filter","data":[{"fn":"invert","v":100}]},
            \(invertProp)]}]}
        """.utf8))
        // Reference: the SAME tree without `backdrop-filter`, rendered with
        // the lane disabled. Its pixel under the box is the element's own
        // filter applied to the UNfiltered backdrop.
        let ownFilterOnly = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"stage","name":"Stage","properties":[
          {"type":"Width","data":{"type":"length","px":120}},
          {"type":"Height","data":{"type":"length","px":60}},
          {"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.8,"b":0.2},
                                            "original":"green"}}],
         "children":[
          {"id":"glass","name":"Glass","properties":[
            {"type":"Width","data":{"type":"length","px":60}},
            {"type":"Height","data":{"type":"length","px":60}},
            {"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1,"a":0.25},
                                              "original":"rgba(255,255,255,.25)"}},
            {"type":"Filter","data":[{"fn":"invert","v":100}]}]}]}
        """.utf8))
        let reference = try render(ownFilterOnly, pass: .disabled)
        let composited = try renderTwoPass(both).b
        let before = try rgb(reference, 20, 20)
        let after = try rgb(composited, 20, 20)
        // The backdrop chain must still visibly change the pixel. A
        // double-apply would land back on `before` (invert∘invert == id), so
        // this is the assertion the pre-fix build failed.
        XCTAssertGreaterThan(abs(after.0 - before.0) + abs(after.1 - before.1), 60,
                             "own filter cancelled the backdrop invert "
                             + "(\(before) → \(after)) — the backplate is inside "
                             + "the foreground chain again")
    }

    // MARK: - 5. the identity guarantee (327-pair protection)

    /// With no pass published — the default on the bundled property-fixture
    /// capture path — a `backdrop-filter` element renders BYTE-IDENTICALLY
    /// to the same element without the property. The lane cannot move a
    /// committed dark-stage baseline.
    @MainActor
    func testDisabledPassRendersIdenticallyToNoBackdropProperty() throws {
        let withBackdrop = try render(try stage(backdrop: invertProp), pass: .disabled)
        let without = try render(try stage(backdrop: nil), pass: .disabled)
        XCTAssertEqual(try bytes(withBackdrop), try bytes(without),
                       "the backdrop modifier is not identity when disabled")
    }

    /// The same guarantee for a chain this lane does NOT execute (`sepia`):
    /// an entirely-unsupported chain plans to identity, so it neither
    /// suppresses paint in pass A nor draws a backplate in pass B.
    @MainActor
    func testUnsupportedOnlyChainStaysIdentityInBothPasses() throws {
        let sepia = #"{"type":"BackdropFilter","data":[{"fn":"sepia","v":100}]}"#
        let comp = try stage(backdrop: sepia)
        let plain = try render(comp, pass: .disabled)
        let passes = try renderTwoPass(comp)
        XCTAssertEqual(try bytes(plain), try bytes(passes.a),
                       "identity plan suppressed paint in pass A")
        XCTAssertEqual(try bytes(plain), try bytes(passes.b),
                       "identity plan drew a backplate in pass B")
    }
}
