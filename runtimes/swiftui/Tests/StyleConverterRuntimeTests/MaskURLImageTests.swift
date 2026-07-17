//
//  MaskURLImageTests.swift
//  StyleConverterRuntimeTests
//
//  Lane UM-IOS: real url() masks. Three pinned contracts:
//   1. RESOLVER PATH — a fully percent-encoded data URI (the
//      converter-lowercasing-proof form the fixtures use) decodes to a
//      raster through the shared BackgroundURLImageResolver.
//   2. FAILURE SEMANTICS — unresolvable sources (SVG url(#fragment)
//      references, http(s) remotes) resolve to `.transparent`, the
//      css-masking-1 failed-load behavior (transparent black layer =
//      element hidden). The old placeholder painted a uniform 50%
//      mask; MaskURLResolution has no half-visible case AT ALL, so
//      this pin is structural as well as behavioral.
//   3. GEOMETRY DEFAULTS — undeclared mask-repeat TILES (initial
//      `repeat`), undeclared mask-position anchors top/left (initial
//      `0% 0%`, not the gradient-centric 0.5 config default), and
//      undeclared mask-size keeps intrinsic pixels (`auto`).
//

import Foundation
// UIKit: UIImage is the resolver's currency (Catalyst-hosted tests).
import UIKit
import XCTest
// @testable: MaskURLLayer and the geometry helpers are internal.
@testable import StyleConverterRuntime

final class MaskURLImageTests: XCTestCase {

    // ── Shared fixture: a tiny PNG as a percent-encoded data URI ─────

    /// 4×4 opaque PNG, fully percent-encoded with lowercase hex — the
    /// only data-URI form that survives the converter's value
    /// lowercasing (see BackgroundURLImage.swift header), and exactly
    /// what fixtures/properties/effects/mask-image.json ships.
    private func tinyPNGDataURI() -> String {
        // Render at explicit 1× so the byte payload is deterministic.
        let fmt = UIGraphicsImageRendererFormat()
        fmt.scale = 1
        let img = UIGraphicsImageRenderer(size: CGSize(width: 4, height: 4),
                                          format: fmt).image { ctx in
            // Any opaque fill works — the pin is decode success, not pixels.
            UIColor.red.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 4, height: 4))
        }
        // PNG-encode then percent-encode EVERY byte (lowercase hex).
        let payload = img.pngData()!.map { String(format: "%%%02x", $0) }.joined()
        return "data:image/png," + payload
    }

    // ── 1. Resolver path ─────────────────────────────────────────────

    func testDataURIResolvesToARaster() {
        // The shared background decoder must hand the mask a UIImage.
        guard case .raster(let img) = MaskURLLayer.resolve(tinyPNGDataURI(),
                                                           mode: .matchSource) else {
            return XCTFail("percent-encoded data URI must resolve to .raster")
        }
        // Intrinsic size survives the round-trip (auto-size currency).
        XCTAssertEqual(img.size.width * img.scale, 4)
        XCTAssertEqual(img.size.height * img.scale, 4)
    }

    // ── 2. Failure semantics ─────────────────────────────────────────

    func testSVGFragmentReferenceResolvesTransparentNotHalfVisible() {
        // url(#id) needs an SVG document model we don't have — the spec
        // failed-load result is a transparent black layer, NOT the old
        // uniform 50% placeholder.
        XCTAssertEqual(MaskURLLayer.resolve("#fadeMask", mode: .matchSource),
                       .transparent,
                       "unresolvable url(#fragment) must hide the element")
    }

    func testRemoteHTTPResolvesTransparent() {
        // http(s) is a defined no-op (capture determinism) — same
        // transparent-black failure semantics as any unresolvable source.
        XCTAssertEqual(MaskURLLayer.resolve("https://example.com/mask.png",
                                            mode: .matchSource),
                       .transparent,
                       "remote url() must hide the element, not half-show it")
    }

    // ── 3. Geometry defaults ─────────────────────────────────────────

    func testDefaultRepeatTilesBothAxes() {
        // Initial mask-repeat is `repeat` (css-masking-1): the config
        // default must map to a repeat/repeat lattice.
        let layer = MaskURLLayer.repeatLayer(.repeatBoth)
        let modes = BackgroundImageGeometry.repeatModes(layer)
        XCTAssertEqual(modes.x, .repeat, "default mask-repeat must tile x")
        XCTAssertEqual(modes.y, .repeat, "default mask-repeat must tile y")
        // And the lattice actually COVERS the box: a 24×24 tile over
        // 160×80 must reach both far edges (CSS tiles infinitely and
        // painters clip to the box).
        let plan = BackgroundImageGeometry.placement(
            imageSize: CGSize(width: 24, height: 24),
            box: CGSize(width: 160, height: 80),
            size: MaskURLLayer.sizeLayer(.auto),
            positionX: nil, positionY: nil, repeatLayer: layer)
        let rects = BackgroundImageGeometry.tileRects(placement: plan,
                                                      box: CGSize(width: 160, height: 80))
        XCTAssertGreaterThanOrEqual(rects.map(\.maxX).max() ?? 0, 160,
                                    "tiling must cover the full width")
        XCTAssertGreaterThanOrEqual(rects.map(\.maxY).max() ?? 0, 80,
                                    "tiling must cover the full height")
    }

    func testUndeclaredPositionAnchorsTopLeft() {
        // No mask-position on the wire → the css-masking-1 initial
        // `0% 0%`. The config's 0.5 default is gradient-only; the url
        // adapters must emit nil axes (geometry initial = 0 offset).
        let cfg = MaskConfig() // positionDeclared defaults false
        let axes = MaskURLLayer.positionAxes(cfg)
        XCTAssertNil(axes.x, "undeclared position must not center x")
        XCTAssertNil(axes.y, "undeclared position must not center y")
        // The resulting anchor is flush top/left.
        let origin = BackgroundImageGeometry.origin(
            tile: CGSize(width: 24, height: 24),
            box: CGSize(width: 160, height: 80), x: axes.x, y: axes.y)
        XCTAssertEqual(origin, .zero, "initial 0% 0% anchors at the origin")
    }

    func testDeclaredCenterPositionMapsToPercentAnchors() {
        // Authored `mask-position: center` → 50% per axis → the §3.6
        // free-space identity centers the anchor tile.
        var cfg = MaskConfig()
        cfg.position = MaskPositionValue(x: 0.5, y: 0.5)
        cfg.positionDeclared = true // the extractor sets this on parse
        let axes = MaskURLLayer.positionAxes(cfg)
        XCTAssertEqual(axes.x, .percent(50))
        XCTAssertEqual(axes.y, .percent(50))
        let origin = BackgroundImageGeometry.origin(
            tile: CGSize(width: 24, height: 24),
            box: CGSize(width: 160, height: 80), x: axes.x, y: axes.y)
        // (160−24)/2 = 68, (80−24)/2 = 28 — centered free space.
        XCTAssertEqual(origin, CGPoint(x: 68, y: 28))
    }

    func testAutoSizeKeepsIntrinsicPixels() {
        // Initial mask-size `auto` = intrinsic dimensions (§3.9).
        let tile = BackgroundImageGeometry.tileSize(
            imageSize: CGSize(width: 24, height: 24),
            box: CGSize(width: 160, height: 80),
            size: MaskURLLayer.sizeLayer(.auto))
        XCTAssertEqual(tile, CGSize(width: 24, height: 24))
    }

    func testExplicitFractionSizeMapsBackToPercent() {
        // MaskExtractor stores percents as unit fractions (0.5 = 50%);
        // the adapter must scale back to the geometry's CSS-percent
        // dimension, resolving against the box axis.
        let layer = MaskURLLayer.sizeLayer(
            .explicit(width: nil, height: nil, widthPercent: 0.5, heightPercent: 0.25))
        XCTAssertEqual(layer, .explicit(w: .percent(50), h: .percent(25)))
    }

    // ── Extractor: the declared-position flag ────────────────────────

    func testExtractorFlagsDeclaredPositionAndResetsLoneAxis() {
        // A lone `mask-position-x: 100%` declaration: the flag flips
        // AND the undeclared y axis resets to its 0% initial instead of
        // silently keeping the gradient-centric 0.5 default.
        let cfg = MaskExtractor.extract(from: [IRProperty(
            type: "MaskPositionX",
            data: .object(["percentage": .double(100)]))])
        XCTAssertEqual(cfg?.positionDeclared, true, "longhand must set the flag")
        XCTAssertEqual(cfg?.position.x, 1.0, "declared axis carries its fraction")
        XCTAssertEqual(cfg?.position.y, 0.0, "undeclared axis falls to the 0% initial")
        // And a config with no position declaration keeps the flag off.
        let none = MaskExtractor.extract(from: [IRProperty(
            type: "MaskRepeat",
            data: .object(["type": .string("app.NoRepeat")]))])
        XCTAssertEqual(none?.positionDeclared, false)
    }
}
