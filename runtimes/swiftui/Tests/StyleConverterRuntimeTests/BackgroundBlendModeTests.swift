//
//  BackgroundBlendModeTests.swift
//  IOS-BBM lane — the real background-blend-mode compositor.
//
//  Pins the four pieces of the lane:
//    1. the wire → config path (BlendModeExtractor + the StyleBuilder
//       gate activeBackgroundBlendModes) against IR shapes copied
//       VERBATIM from the live converter run on
//       fixtures/properties/color/background-blend-mode.json
//       (BackgroundBlendMode = array of UPPERCASE strings,
//       BackgroundImage = array of gradient objects, BackgroundColor =
//       srgb blob — wave-5 lesson: never assume wire shapes);
//    2. the SCREEN arithmetic — CSS Compositing 1 §10.1.2
//       screen(Cb,Cs) = Cb + Cs − Cb·Cs composited §5.1 as
//       Co = (1−αs)·Cb + αs·screen(Cb,Cs) over the opaque backdrop —
//       measured at the raster level via ImageRenderer, the same
//       capture path the harness uses. Backdrop AND source are sampled
//       from probe renders (not assumed) so colour management applies
//       equally to measurement and expectation;
//    3. byte-stability of the `normal` control — all-normal mode lists
//       must keep the legacy chained-.background paint path so every
//       unblended baseline is untouched;
//    4. extractor index alignment — an unmapped keyword falls back to
//       `.normal` IN PLACE (logged once) instead of being dropped and
//       shifting later layers onto the wrong modes (§2.7 pairing).
//

import XCTest
import SwiftUI
// @testable: extractors, StyleBuilder, and the appliers are internal.
@testable import StyleConverterRuntime

final class BackgroundBlendModeTests: XCTestCase {

    // MARK: - live-converter-pinned IR fixtures

    // Concise IRValue.object builder (same helper family as
    // ColorBackgroundTests).
    private static func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }

    /// The BBM_* component property list exactly as the live converter
    /// emits it (scratchpad run, 2026-07-18): width/height 180×80,
    /// background-color #ef4444, one 45deg blue→transparent linear
    /// gradient layer, and — when `mode` is non-nil — a one-entry
    /// BackgroundBlendMode array of the UPPERCASE keyword.
    private static func bbmProps(mode: String?) -> [IRProperty] {
        var p: [IRProperty] = [
            // {"type":"length","px":180.0} — the live Width shape.
            IRProperty(type: "Width", data: obj([
                "type": .string("length"), "px": .double(180)])),
            IRProperty(type: "Height", data: obj([
                "type": .string("length"), "px": .double(80)])),
            // #ef4444 normalized to sRGB floats by the converter.
            IRProperty(type: "BackgroundColor", data: obj([
                "srgb": obj(["r": .double(0.9372549019607843),
                             "g": .double(0.26666666666666666),
                             "b": .double(0.26666666666666666)]),
                "original": .string("#ef4444")])),
            // linear-gradient(45deg, #3b82f6, transparent) — stop
            // positions are null (unresolved) on the wire.
            IRProperty(type: "BackgroundImage", data: .array([obj([
                "type": .string("linear-gradient"),
                "angle": obj(["deg": .double(45.0)]),
                "stops": .array([
                    obj(["color": obj([
                            "srgb": obj(["r": .double(0.23137254901960785),
                                         "g": .double(0.5098039215686274),
                                         "b": .double(0.9647058823529412)]),
                            "original": .string("#3b82f6")]),
                         "position": .null]),
                    obj(["color": obj([
                            "srgb": obj(["r": .double(0.0), "g": .double(0.0),
                                         "b": .double(0.0), "a": .double(0.0)]),
                            "original": .string("transparent")]),
                         "position": .null]),
                ])])])),
        ]
        // The blend keyword rides as a one-entry UPPERCASE string array.
        if let mode = mode {
            p.append(IRProperty(type: "BackgroundBlendMode",
                                data: .array([.string(mode)])))
        }
        return p
    }

    // MARK: - raster plumbing (mirrors BorderImagePaintOrderTests)

    /// Rasterize at @1x the way the harness captures.
    @MainActor
    private func render<V: View>(_ view: V, size: CGSize) -> UIImage? {
        let renderer = ImageRenderer(content: view.frame(width: size.width,
                                                        height: size.height))
        renderer.scale = 1
        return renderer.uiImage
    }

    /// RGBA at a pixel, normalized 0–1, RAW as stored (premultiplied when
    /// the backing CGImage says so). Byte-order decoding matches
    /// BorderImagePaintOrderTests — alphaInfo × byteOrder32Little.
    private func rgbaAt(_ img: UIImage, _ x: Int, _ y: Int)
        -> (r: CGFloat, g: CGFloat, b: CGFloat, a: CGFloat) {
        guard let cg = img.cgImage,
              let data = cg.dataProvider?.data as Data? else { return (-1, -1, -1, -1) }
        let bpr = cg.bytesPerRow, bpp = cg.bitsPerPixel / 8
        let idx = y * bpr + x * bpp
        guard bpp == 4, idx + 3 < data.count else { return (-1, -1, -1, -1) }
        // Raw component bytes in memory order.
        let c0 = CGFloat(data[idx]) / 255, c1 = CGFloat(data[idx + 1]) / 255
        let c2 = CGFloat(data[idx + 2]) / 255, c3 = CGFloat(data[idx + 3]) / 255
        // Alpha slot from alphaInfo; little-endian storage reverses it.
        let alphaFirst = [.premultipliedFirst, .first, .noneSkipFirst].contains(cg.alphaInfo)
        let little = cg.bitmapInfo.contains(.byteOrder32Little)
        switch (alphaFirst, little) {
        case (true, false):  return (c1, c2, c3, c0)  // ARGB
        case (true, true):   return (c2, c1, c0, c3)  // BGRA
        case (false, false): return (c0, c1, c2, c3)  // RGBA
        case (false, true):  return (c3, c2, c1, c0)  // ABGR
        }
    }

    /// Un-premultiplied colour at a pixel — divides by alpha when the
    /// backing store is premultiplied (ImageRenderer's default), so the
    /// screen() arithmetic runs on straight source values like the spec.
    private func straightRGBA(_ img: UIImage, _ x: Int, _ y: Int)
        -> (r: CGFloat, g: CGFloat, b: CGFloat, a: CGFloat) {
        let p = rgbaAt(img, x, y)
        guard let cg = img.cgImage else { return p }
        let premul = [.premultipliedFirst, .premultipliedLast].contains(cg.alphaInfo)
        guard premul, p.a > 0, p.a < 1 else { return p }
        return (p.r / p.a, p.g / p.a, p.b / p.a, p.a)
    }

    // MARK: - 1. wire → config → gate

    /// SCREEN wire shape reaches the gate as [.screen]; NORMAL and a
    /// blend-less component both keep the gate closed (legacy path).
    func testGateOpensForScreenAndStaysClosedForNormal() {
        // screen: extractor maps the UPPERCASE keyword, gate passes it.
        let screen = StyleBuilder.build(from: Self.bbmProps(mode: "SCREEN"))
        XCTAssertEqual(screen.blend?.background, [.screen],
                       "SCREEN wire keyword must extract to [.screen]")
        XCTAssertEqual(StyleBuilder.activeBackgroundBlendModes(screen), [.screen],
                       "gate must open: non-normal mode + gradient layer present")
        // normal: extracted faithfully but the gate must NOT route the
        // compositor — normal blends nothing and the legacy path must
        // stay byte-stable.
        let normal = StyleBuilder.build(from: Self.bbmProps(mode: "NORMAL"))
        XCTAssertEqual(normal.blend?.background, [.normal])
        XCTAssertEqual(StyleBuilder.activeBackgroundBlendModes(normal), [],
                       "all-normal list must keep the legacy paint path")
        // no image layers → nothing to blend even with a non-normal mode.
        let noImage = StyleBuilder.build(from: [
            IRProperty(type: "BackgroundColor", data: Self.bbmProps(mode: nil)[2].data),
            IRProperty(type: "BackgroundBlendMode", data: .array([.string("SCREEN")])),
        ])
        XCTAssertEqual(StyleBuilder.activeBackgroundBlendModes(noImage), [],
                       "colour-only element has no layer stack to blend (§3.2)")
    }

    // MARK: - 2. the screen() arithmetic raster pin

    /// Center pixel of the blended BBM_screen render must equal the CSS
    /// §10.1.2/§5.1 arithmetic — Co = (1−αs)·Cb + αs·(Cb + Cs − Cb·Cs) —
    /// within 2/255 per channel. Backdrop (Cb) and source (Cs, αs) are
    /// MEASURED from probe renders through the same ImageRenderer
    /// pipeline, so colour management cancels out of the comparison.
    @MainActor
    func testScreenBlendMatchesCssArithmeticAtCenter() throws {
        let size = CGSize(width: 180, height: 80)
        let cx = 90, cy = 40
        let style = StyleBuilder.build(from: Self.bbmProps(mode: "SCREEN"))
        // Probe 1 — backdrop: the background-colour fill alone.
        let bgView = Color.clear.engineBackgroundColor(style.color)
        let bgImg = try XCTUnwrap(render(bgView, size: size), "backdrop probe render failed")
        let cb = straightRGBA(bgImg, cx, cy)
        XCTAssertGreaterThan(cb.a, 0.99, "backdrop must be opaque red")
        // Probe 2 — source: the gradient layer alone, unblended, over
        // transparent (empty blendModes → legacy path by construction).
        let srcView = Color.clear.engineBackgroundImage(style.backgroundImage)
        let srcImg = try XCTUnwrap(render(srcView, size: size), "source probe render failed")
        let cs = straightRGBA(srcImg, cx, cy)
        // Mid-gradient the blue→transparent ramp must be semi-opaque —
        // otherwise the pin would sit at a degenerate point where screen
        // equals normal.
        XCTAssertGreaterThan(cs.a, 0.15, "gradient center should be semi-transparent, not gone")
        XCTAssertLessThan(cs.a, 0.9, "gradient center should be semi-transparent, not solid")
        // System under test — the REAL wiring: full applyStyle over the
        // built style (gate → BackgroundImageApplier → compositor).
        let blended = try XCTUnwrap(render(Color.clear.applyStyle(style), size: size),
                                    "blended render failed")
        let got = straightRGBA(blended, cx, cy)
        XCTAssertGreaterThan(got.a, 0.99, "blend over an opaque backdrop must stay opaque")
        // Expected: §5.1 simple alpha compositing with §10.1.2 screen as
        // the mixing function, backdrop opaque.
        func screen(_ b: CGFloat, _ s: CGFloat) -> CGFloat { b + s - b * s }
        let want = (r: (1 - cs.a) * cb.r + cs.a * screen(cb.r, cs.r),
                    g: (1 - cs.a) * cb.g + cs.a * screen(cb.g, cs.g),
                    b: (1 - cs.a) * cb.b + cs.a * screen(cb.b, cs.b))
        // The lane's acceptance bar: 2/255 per channel.
        let tol: CGFloat = 2.0 / 255.0
        XCTAssertEqual(got.r, want.r, accuracy: tol, "screen red channel off CSS arithmetic")
        XCTAssertEqual(got.g, want.g, accuracy: tol, "screen green channel off CSS arithmetic")
        XCTAssertEqual(got.b, want.b, accuracy: tol, "screen blue channel off CSS arithmetic")
        // Screen never darkens (§10.1.2: result ≥ max(Cb,Cs) per channel)
        // — a cheap direction pin that fails loudly if the compositor
        // accidentally blends against the transparent canvas instead of
        // the red fill (the exact historic failure mode).
        XCTAssertGreaterThanOrEqual(got.r + tol, cb.r, "screen must not darken the backdrop")
    }

    // MARK: - 3. the normal-mode byte-stability control

    /// BBM_normal (explicit `background-blend-mode: normal`) must render
    /// BYTE-IDENTICALLY to the same component without the property — the
    /// gate keeps all-normal lists on the legacy paint path, so no
    /// unblended fixture can regress.
    @MainActor
    func testNormalModeControlIsByteStable() throws {
        let size = CGSize(width: 180, height: 80)
        // Same component, with and without the NORMAL blend entry — both
        // rendered through the REAL applyStyle chain.
        let withNormal = StyleBuilder.build(from: Self.bbmProps(mode: "NORMAL"))
        let without = StyleBuilder.build(from: Self.bbmProps(mode: nil))
        let imgA = try XCTUnwrap(render(Color.clear.applyStyle(withNormal), size: size))
        let imgB = try XCTUnwrap(render(Color.clear.applyStyle(without), size: size))
        // Compare the raw backing bytes — stricter than SSIM, exactly
        // "no regression on unblended fixtures".
        let dataA = try XCTUnwrap(imgA.cgImage?.dataProvider?.data as Data?)
        let dataB = try XCTUnwrap(imgB.cgImage?.dataProvider?.data as Data?)
        XCTAssertEqual(dataA, dataB,
                       "background-blend-mode: normal must not change a single byte vs the blend-less render")
    }

    // MARK: - 4. extractor alignment + Compose parity

    /// An unmapped keyword must fall back to `.normal` IN PLACE — the old
    /// compactMap dropped it and shifted every later mode onto the wrong
    /// layer (css-backgrounds-3 §2.7 pairs modes to layers by index).
    func testExtractorKeepsIndexAlignmentOnUnknownKeyword() {
        let cfg = BlendModeExtractor.extract(from: [
            IRProperty(type: "BackgroundBlendMode", data: .array([
                .string("SCREEN"), .string("NOT_A_MODE"), .string("MULTIPLY")])),
        ])
        XCTAssertEqual(cfg?.background, [.screen, .normal, .multiply],
                       "unknown keyword must hold its slot as .normal, not shift the list")
    }

    /// The fixture's four keywords (and the hyphenated spellings Compose
    /// tolerates) map name-for-name with Compose's BlendModeMapping so
    /// the two natives blend identically.
    func testKeywordMappingAgreesWithCompose() {
        XCTAssertEqual(BlendModeExtractor.mapBlend("NORMAL"), BlendMode.normal)
        XCTAssertEqual(BlendModeExtractor.mapBlend("MULTIPLY"), BlendMode.multiply)
        XCTAssertEqual(BlendModeExtractor.mapBlend("SCREEN"), BlendMode.screen)
        XCTAssertEqual(BlendModeExtractor.mapBlend("OVERLAY"), BlendMode.overlay)
        // Hyphen tolerance mirrors Compose's normalize step.
        XCTAssertEqual(BlendModeExtractor.mapBlend("color-dodge"), BlendMode.colorDodge)
        XCTAssertEqual(BlendModeExtractor.mapBlend("plus-lighter"), BlendMode.plusLighter)
    }
}
