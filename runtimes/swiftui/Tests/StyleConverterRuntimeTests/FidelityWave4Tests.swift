//
//  FidelityWave4Tests.swift
//  StyleConverterRuntimeTests
//
//  Pinning tests for the wave-4 iOS engine fix:
//    • skewX placement (css-transforms-1 §8): transform functions
//      compose about `transform-origin` (default 50% 50%), but the old
//      applier fed the raw shear matrix to `.projectionEffect`, which
//      anchors at the view's top-leading corner. That displaced
//      skewX(8deg) content by a constant +tan(8°)·h/2 ≈ +4.5px on a
//      64px-tall box — the measured iOS-vs-web drift on
//      Performance_Decorated (row extents web top/mid/bot 24/16/20 vs
//      iOS 28/20/24) and Svg_Decorated, while Android (Compose
//      graphicsLayer, centre pivot by default) matched web exactly.
//      The fix conjugates the shear about the anchor:
//      T(origin) · S · T(−origin), implemented in
//      TransformsMath.anchoredSkew and applied via the layout-neutral
//      SkewEffect GeometryEffect.
//

import XCTest
import SwiftUI
import CoreGraphics
@testable import StyleConverterRuntime

final class FidelityWave4Tests: XCTestCase {

    // MARK: - skewX about centre (the measured Decorated-row geometry)

    func testSkewXAboutCenterCancelsTopLeftDrift() {
        // skewX(8deg) on a 200×64 box (the Decorated-row geometry that
        // measured +4–5px right of web). Centre anchor → cy = 32.
        let m = TransformsMath.anchoredSkew(
            xDeg: 8, yDeg: 0, size: CGSize(width: 200, height: 64),
            anchor: .center)
        let t = tan(8 * CGFloat.pi / 180) // ≈ 0.14054
        // Linear part is the untouched CSS shear [1 t; 0 1] — the fix
        // may not change the shape, only where it lands.
        XCTAssertEqual(m.a, 1); XCTAssertEqual(m.b, 0)
        XCTAssertEqual(m.c, t, accuracy: 1e-12)
        XCTAssertEqual(m.d, 1)
        // Compensating translation = −tan(8°)·h/2 ≈ −4.497px — exactly
        // the rightward drift the wave-4 measurement flagged.
        XCTAssertEqual(m.tx, -t * 32, accuracy: 1e-12)
        XCTAssertEqual(m.tx, -4.4972, accuracy: 1e-3)
        XCTAssertEqual(m.ty, 0)
    }

    func testSkewXFixedPointIsTheAnchor() {
        // css-transforms-1 §8: the transform-origin is the fixed point
        // of the rendered transform. Map the centre of a 200×64 box
        // through the matrix — it must not move.
        let m = TransformsMath.anchoredSkew(
            xDeg: 8, yDeg: 0, size: CGSize(width: 200, height: 64),
            anchor: .center)
        let p = CGPoint(x: 100, y: 32).applying(m)
        XCTAssertEqual(p.x, 100, accuracy: 1e-9)
        XCTAssertEqual(p.y, 32, accuracy: 1e-9)
        // And the row extents become symmetric: the top edge shifts
        // −tan·32 and the bottom edge +tan·32 (web/Android behaviour),
        // instead of 0 / +tan·64 (the old top-left application).
        let top = CGPoint(x: 100, y: 0).applying(m)
        let bot = CGPoint(x: 100, y: 64).applying(m)
        let t = tan(8 * CGFloat.pi / 180)
        XCTAssertEqual(top.x, 100 - t * 32, accuracy: 1e-9)
        XCTAssertEqual(bot.x, 100 + t * 32, accuracy: 1e-9)
    }

    // MARK: - skewY symmetry + explicit non-centre anchors

    func testSkewYAboutCenterCompensatesHorizontally() {
        // skewY shears x into y, so its compensation rides on ty and
        // scales with the anchor's X pixel coordinate (cx = w/2 here).
        let m = TransformsMath.anchoredSkew(
            xDeg: 0, yDeg: 10, size: CGSize(width: 120, height: 40),
            anchor: .center)
        let t = tan(10 * CGFloat.pi / 180)
        // Linear part: [1 0; t 1] — pure Y shear.
        XCTAssertEqual(m.b, t, accuracy: 1e-12)
        XCTAssertEqual(m.c, 0)
        // Offset lands on ty only: −tan(10°)·cx = −tan(10°)·60.
        XCTAssertEqual(m.tx, 0)
        XCTAssertEqual(m.ty, -t * 60, accuracy: 1e-12)
        // Fixed point check at the centre.
        let p = CGPoint(x: 60, y: 20).applying(m)
        XCTAssertEqual(p.x, 60, accuracy: 1e-9)
        XCTAssertEqual(p.y, 20, accuracy: 1e-9)
    }

    func testSkewAboutTopLeadingReproducesRawShear() {
        // transform-origin: 0 0 → cy = cx = 0 → zero compensation. The
        // matrix degenerates to the raw CSS shear, proving the fix is a
        // pure re-anchoring (no shape change smuggled in).
        let m = TransformsMath.anchoredSkew(
            xDeg: 8, yDeg: 5, size: CGSize(width: 200, height: 64),
            anchor: UnitPoint(x: 0, y: 0))
        XCTAssertEqual(m.tx, 0)
        XCTAssertEqual(m.ty, 0)
        XCTAssertEqual(m.c, tan(8 * CGFloat.pi / 180), accuracy: 1e-12)
        XCTAssertEqual(m.b, tan(5 * CGFloat.pi / 180), accuracy: 1e-12)
    }

    // MARK: - SkewEffect wiring (GeometryEffect → TransformsMath)

    func testSkewEffectEmitsTheAnchoredMatrix() {
        // The GeometryEffect must hand its laid-out size straight to
        // anchoredSkew — same matrix, wrapped in a ProjectionTransform.
        let eff = SkewEffect(xDeg: 8, yDeg: 0, anchor: .center)
        let proj = eff.effectValue(size: CGSize(width: 200, height: 64))
        let expect = ProjectionTransform(TransformsMath.anchoredSkew(
            xDeg: 8, yDeg: 0, size: CGSize(width: 200, height: 64),
            anchor: .center))
        XCTAssertEqual(proj.m11, expect.m11); XCTAssertEqual(proj.m12, expect.m12)
        XCTAssertEqual(proj.m21, expect.m21); XCTAssertEqual(proj.m22, expect.m22)
        XCTAssertEqual(proj.m31, expect.m31); XCTAssertEqual(proj.m32, expect.m32)
    }

    func testZeroSkewIsIdentity() {
        // skew(0, 0) must be the identity for any size/anchor — the
        // no-op guard the extractor relies on when angles parse to 0.
        let m = TransformsMath.anchoredSkew(
            xDeg: 0, yDeg: 0, size: CGSize(width: 200, height: 64),
            anchor: .center)
        XCTAssertTrue(m.isIdentity)
    }
}
