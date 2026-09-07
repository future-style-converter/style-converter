//
//  BorderRadiusClipPolicyTests.swift
//  Retro R4 (A12#0 / A11#7) — border-radius must not clip descendants.
//
//  css-backgrounds-3 §4.3 (Corner Clipping): the curve clips the element's
//  own background (per background-clip) and, for descendants, only
//  "overflow values other than visible". BorderRadiusApplier applied
//  `.clipShape` unconditionally, so a rounded box cut its own label /
//  children at the border edge while web and Compose paint them in full:
//  visual-test BorderRadius_Uniform read "BORD" on iOS and "BORDERRADIUS
//  UNIFORM" on Android/web — 0 vs 204 ink pixels right of x=80, deltaE 0
//  (retro A12#0 zoom stacks). Ten ledger lines blamed "corner AA".
//
//  Two layers of pins:
//    1. the pure clip-policy truth table StyleBuilder feeds the applier;
//    2. a Catalyst ImageRenderer raster proving the applier itself no
//       longer clips in self-paint mode and still does in legacy mode
//       (property assertions only — see the swiftui-tests-catalyst note:
//       colour SPACE and exact bytes differ from the device).
//

import SwiftUI
import XCTest
@testable import StyleConverterRuntime

final class BorderRadiusClipPolicyTests: XCTestCase {

    // MARK: - fixture-shaped configs (fixtures/visual-test.json)

    /// Uniform circular radius on all four corners.
    private func radius(_ r: CGFloat) -> BorderRadiusConfig {
        var c = BorderRadiusConfig()
        c.topLeft = BorderRadiusCorner(uniform: r)
        c.topRight = BorderRadiusCorner(uniform: r)
        c.bottomRight = BorderRadiusCorner(uniform: r)
        c.bottomLeft = BorderRadiusCorner(uniform: r)
        return c
    }

    /// Four identical sides.
    private func uniform(_ style: BorderStyleValue?, width: CGFloat?) -> AllBordersConfig {
        var side = BorderSideConfig()
        side.width = width; side.style = style; side.color = .white
        var c = AllBordersConfig()
        c.top = side; c.end = side; c.bottom = side; c.start = side
        return c
    }

    // MARK: - truth table

    /// BorderRadius_Uniform / Pill / Mixed / Edge_VeryLargeRadius /
    /// Edge_InsetRoundShadow: radius + background-color + padding, no
    /// border, no overflow, no image layers → self-paint (no clip). These
    /// are the ten iOS ledger pairs the fix predicts stale.
    func testRoundedBoxWithVisibleOverflowSelfPaints() {
        XCTAssertFalse(BorderRadiusApplier.clipsDescendants(
            radius: radius(10), sides: nil, overflowClips: false, hasBackgroundLayers: false))
        // Edge_VeryLargeRadius: 9999px still just a radius.
        XCTAssertFalse(BorderRadiusApplier.clipsDescendants(
            radius: radius(9999), sides: nil, overflowClips: false, hasBackgroundLayers: false))
    }

    /// Avatar_Circle / Input_Field: uniform solid border with explicit
    /// width → BorderSideApplier's strokeBorder already follows the shape.
    func testUniformSolidBorderSelfPaints() {
        XCTAssertFalse(BorderRadiusApplier.clipsDescendants(
            radius: radius(30), sides: uniform(.solid, width: 3),
            overflowClips: false, hasBackgroundLayers: false))
    }

    /// `overflow: hidden|clip|scroll|auto` → css-backgrounds-3 §4.3 clips
    /// the content at the curved padding edge; the legacy clip IS the spec.
    func testOwnOverflowClipKeepsLegacyClip() {
        XCTAssertTrue(BorderRadiusApplier.clipsDescendants(
            radius: radius(10), sides: nil, overflowClips: true, hasBackgroundLayers: false))
    }

    /// Edge_GradientWithRadius: rectangular background-image layers would
    /// lose their corners without the clip — the documented residual both
    /// natives carry (its two ledger lines stay, and are NOT AA either).
    func testBackgroundLayersKeepLegacyClip() {
        XCTAssertTrue(BorderRadiusApplier.clipsDescendants(
            radius: radius(15), sides: nil, overflowClips: false, hasBackgroundLayers: true))
    }

    /// Per-side / 3D / style-only borders paint straight Canvas edges that
    /// only look rounded under the clip → legacy.
    func testNonSelfRoundingBordersKeepLegacyClip() {
        var perSide = AllBordersConfig()
        perSide.top.width = 3; perSide.top.style = .solid
        XCTAssertTrue(BorderRadiusApplier.clipsDescendants(
            radius: radius(10), sides: perSide, overflowClips: false, hasBackgroundLayers: false))
        XCTAssertTrue(BorderRadiusApplier.clipsDescendants(
            radius: radius(10), sides: uniform(.double, width: 6),
            overflowClips: false, hasBackgroundLayers: false))
        XCTAssertTrue(BorderRadiusApplier.clipsDescendants(
            radius: radius(10), sides: uniform(.solid, width: nil),
            overflowClips: false, hasBackgroundLayers: false))
        // Uniform dashed at a non-zero radius takes the rounded perimeter.
        XCTAssertFalse(BorderRadiusApplier.clipsDescendants(
            radius: radius(10), sides: uniform(.dashed, width: 2),
            overflowClips: false, hasBackgroundLayers: false))
    }

    /// No radius → the modifier is identity in both modes; the gate
    /// answers false so nothing downstream ever installs a clip.
    func testNoRadiusNeverClips() {
        XCTAssertFalse(BorderRadiusApplier.clipsDescendants(
            radius: nil, sides: nil, overflowClips: true, hasBackgroundLayers: true))
        XCTAssertFalse(BorderRadiusApplier.clipsDescendants(
            radius: BorderRadiusConfig(), sides: nil, overflowClips: true, hasBackgroundLayers: true))
    }

    // MARK: - raster (Catalyst ImageRenderer, property assertions only)

    /// Pure channel green / purple — not the system palette colours, whose
    /// channels sit far from 0/1 (BorderImagePaintOrderTests' lesson).
    private let green = Color(red: 0, green: 1, blue: 0)
    private let purple = Color(red: 0.6, green: 0.35, blue: 0.7)

    /// A 40×24 rounded box (radius 12) whose 100×8 child overflows to
    /// x=100 — the shape of BorderRadius_Uniform's clipped label. The fill
    /// is the shape-aware paint ColorApplier uses, the child is NOT clipped
    /// by anything but the applier under test. Rendered on a 120×24
    /// leading-aligned canvas so the overflow has room to show.
    private func roundedBox(clips: Bool) -> some View {
        let r = radius(12)
        return Rectangle().fill(green)
            .frame(width: 100, height: 8)
            .frame(width: 40, height: 24, alignment: .topLeading)
            .background(BorderRadiusShape(radius: r).fill(purple))
            .modifier(BorderRadiusApplier(config: r, clipsDescendants: clips))
            .frame(width: 120, height: 24, alignment: .topLeading)
    }

    @MainActor
    private func render<V: View>(_ view: V) -> UIImage? {
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        return renderer.uiImage
    }

    /// RGBA at a pixel (0–1), byte-order decoded from the CGImage's
    /// alphaInfo × byteOrder32Little (mirrors BorderImagePaintOrderTests).
    private func rgba(_ img: UIImage, _ x: Int, _ y: Int) -> (r: CGFloat, g: CGFloat, b: CGFloat, a: CGFloat) {
        guard let cg = img.cgImage, let data = cg.dataProvider?.data as Data? else { return (-1, -1, -1, -1) }
        let bpr = cg.bytesPerRow, bpp = cg.bitsPerPixel / 8
        let idx = y * bpr + x * bpp
        guard bpp == 4, idx + 3 < data.count else { return (-1, -1, -1, -1) }
        let c0 = CGFloat(data[idx]) / 255, c1 = CGFloat(data[idx + 1]) / 255
        let c2 = CGFloat(data[idx + 2]) / 255, c3 = CGFloat(data[idx + 3]) / 255
        let alphaFirst = [.premultipliedFirst, .first, .noneSkipFirst].contains(cg.alphaInfo)
        let little = cg.bitmapInfo.contains(.byteOrder32Little)
        switch (alphaFirst, little) {
        case (true, false):  return (c1, c2, c3, c0)
        case (true, true):   return (c2, c1, c0, c3)
        case (false, false): return (c0, c1, c2, c3)
        case (false, true):  return (c3, c2, c1, c0)
        }
    }

    /// The fix, rendered: in self-paint mode the overflowing child paints
    /// past the rounded box (x=90 is green), the fill is still present
    /// (x=20,y=20 purple) and still ROUNDED without any clip (the corner
    /// pixel is transparent) — the shape-aware fill owns the rounding, so
    /// the clip bought nothing but the cut label.
    @MainActor
    func testSelfPaintModeLeavesOverflowingChildUnclipped() throws {
        let img = try XCTUnwrap(render(roundedBox(clips: false)))
        let past = rgba(img, 90, 4)
        XCTAssertGreaterThan(past.a, 0.9, "child must paint past the curve (css-backgrounds-3 §4.3)")
        XCTAssertGreaterThan(past.g, 0.8, "…and it is the green child, not a fill leak")
        XCTAssertLessThan(past.r, 0.2)
        let fill = rgba(img, 20, 20)
        XCTAssertGreaterThan(fill.a, 0.9, "the rounded fill still paints")
        XCTAssertGreaterThan(fill.b, 0.5, "…in the fill colour")
        let corner = rgba(img, 0, 23)
        XCTAssertLessThan(corner.a, 0.1, "corner stays transparent WITHOUT a clip — the fill is shaped")
    }

    /// The legacy branch still clips (overflow ≠ visible keeps taking it):
    /// the same child is cut at the border box.
    @MainActor
    func testLegacyModeStillClipsDescendants() throws {
        let img = try XCTUnwrap(render(roundedBox(clips: true)))
        let past = rgba(img, 90, 4)
        XCTAssertLessThan(past.a, 0.1, "legacy clip must cut the child at the curve")
        // Inside the box the child (rows 0–8) is untouched in both modes.
        let inside = rgba(img, 20, 4)
        XCTAssertGreaterThan(inside.g, 0.8)
    }
}
