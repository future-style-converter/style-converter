//
//  CaptureColorSpaceTests.swift
//  StyleConverterRuntimeTests
//
//  Pins the colour space of what the capture path hands the comparison
//  pipeline — see Renderer/CaptureColorSpace.swift for the full rationale.
//
//  WHY these are raster tests and not pure-function tests: the promotion is
//  a property of the RASTERISER. `ImageRenderer` chooses its destination
//  colour space from the content, so nothing short of an actual render
//  reproduces it. The mechanism does reproduce under the Mac Catalyst
//  destination this suite runs on — measured, `uiImage` returns
//  `kCGColorSpaceExtendedSRGB` for the out-of-range view and
//  `kCGColorSpaceSRGB` for the in-range control — although the promoted
//  space differs from the iOS simulator's Display P3. That is exactly why
//  the production predicate is `isWideGamutRGB` and not an equality check
//  against P3: both flavours are wide-gamut, and only one of them is what
//  a device run produces.
//

import XCTest
import SwiftUI
import UIKit
@testable import StyleConverterRuntime

final class CaptureColorSpaceTests: XCTestCase {

    // MARK: - PNG chunk scanning

    /// Chunk types meaning "not plain sRGB, and honouring it needs a
    /// conversion no stage of this pipeline performs". Mirrors
    /// DISQUALIFYING_COLOR_CHUNKS in tools/visual/png-color-space.mjs; the
    /// two lists are one policy on either side of the device boundary.
    /// `sRGB` is deliberately absent — it asserts the very thing we want.
    private static let disqualifying: Set<String> = ["iCCP", "cICP"]

    /// Chunk types in file order. Layout after the 8-byte signature is,
    /// repeatedly: 4-byte big-endian length · 4-byte ASCII type · payload ·
    /// 4-byte CRC. CRCs are not validated — this scans a file we just
    /// produced, not hostile input.
    private func chunkTypes(_ data: Data) -> [String] {
        let bytes = [UInt8](data)
        guard bytes.count > 8 else { return [] }
        var types: [String] = []
        var offset = 8
        while offset + 12 <= bytes.count {          // length+type+CRC minimum
            let length = (UInt32(bytes[offset]) << 24)
                | (UInt32(bytes[offset + 1]) << 16)
                | (UInt32(bytes[offset + 2]) << 8)
                | UInt32(bytes[offset + 3])
            let type = String(bytes: bytes[(offset + 4)..<(offset + 8)],
                              encoding: .ascii) ?? "????"
            types.append(type)
            if type == "IEND" { break }             // terminates by spec
            let next = offset + 12 + Int(length)
            // Corrupt table — stop rather than read out of bounds or spin.
            if next <= offset || next > bytes.count { break }
            offset = next
        }
        return types
    }

    // MARK: - Fixtures

    /// #2ecc71 — the corpus's `Filter_Brightness` background, and the exact
    /// colour whose green channel leaves [0,1] under the filter below.
    private let corpusGreen = Color(red: 0.18, green: 0.80, blue: 0.44)

    /// The out-of-range view: `filter: brightness(1.5)` as the applier
    /// expresses it (additive +0.5), which is what promotes the render.
    @MainActor
    private func promotedRenderer(height: CGFloat = 60)
    -> ImageRenderer<some View> {
        let r = ImageRenderer(content: corpusGreen
            .frame(width: 120, height: height)
            .brightness(0.5))
        r.scale = 1.0
        return r
    }

    /// The same swatch with no filter — the shape 358 of the corpus's 359
    /// captures have.
    @MainActor
    private func plainRenderer(height: CGFloat = 60) -> ImageRenderer<some View> {
        let r = ImageRenderer(content: corpusGreen.frame(width: 120, height: height))
        r.scale = 1.0
        return r
    }

    // MARK: - The defect

    /// The precondition the whole fix rests on: `uiImage` really does
    /// promote here. If SwiftUI ever stops doing this, the tests below
    /// would pass vacuously — this one fails instead and says why.
    @MainActor
    func testImageRendererPromotesOutOfRangeRenders() throws {
        let promoted = try XCTUnwrap(promotedRenderer().uiImage)
        XCTAssertTrue(
            CaptureColorSpace.isPromoted(promoted),
            "ImageRenderer no longer promotes out-of-range renders — the "
            + "capture path's colour-space gate is now untested, not fixed.")

        let plain = try XCTUnwrap(plainRenderer().uiImage)
        XCTAssertFalse(CaptureColorSpace.isPromoted(plain),
                       "in-range render was promoted; the gate's fast path "
                       + "would stop applying to the whole corpus")
    }

    // MARK: - The fix

    /// THE REGRESSION. Before the fix this capture encoded to a PNG
    /// carrying `iCCP` + `cICP`; pngjs discards both, so every ΔE/SSIM for
    /// the component would have been computed on wide-gamut pixels read as
    /// sRGB.
    @MainActor
    func testPromotedCaptureEncodesWithoutColorChunks() throws {
        let image = try XCTUnwrap(CaptureColorSpace.capture(promotedRenderer(),
                                                            scale: 1.0))
        let png = try XCTUnwrap(image.pngData())
        let offenders = chunkTypes(png).filter { Self.disqualifying.contains($0) }
        XCTAssertTrue(
            offenders.isEmpty,
            """
            Capture carries \(offenders.joined(separator: " + ")). The host \
            pipeline discards colour profiles rather than applying them, so \
            these pixels would be scored as sRGB and the numbers would be \
            quietly wrong. Fix the capture path, not this assertion.
            """)
    }

    /// Pinned separately from the chunk assertion because `isWideGamutRGB`
    /// is the property the fix keys on — without this, the chunk test could
    /// pass for the wrong reason.
    @MainActor
    func testPromotedCaptureIsNoLongerWideGamut() throws {
        let image = try XCTUnwrap(CaptureColorSpace.capture(promotedRenderer(),
                                                            scale: 1.0))
        XCTAssertFalse(CaptureColorSpace.isPromoted(image),
                       "capture is still in a wide-gamut space")
    }

    // MARK: - The blast radius

    /// The fix must be NARROW: an in-range capture has to come back as
    /// `uiImage`'s own bytes.
    ///
    /// HONEST LIMIT OF THIS TEST. It does NOT catch an unconditional sRGB
    /// re-render. Measured: replacing the gate with a bare
    /// `rasterizeSRGB` call still passes here, because on this Mac
    /// Catalyst destination the two rasterisers agree byte-for-byte. On
    /// the iOS simulator they do NOT — the same change moved 355 of 359
    /// captures in the control corpus, some by as much as 202/255 on a
    /// channel. So this pins the INTENT (identity on the fast path) and
    /// would catch a gate that mangles the common case outright, while the
    /// real guard against corpus-wide drift is the byte comparison against
    /// committed captures in a device harness run.
    @MainActor
    func testInRangeCaptureIsByteIdenticalToUIImage() throws {
        let captured = try XCTUnwrap(CaptureColorSpace.capture(plainRenderer(),
                                                               scale: 1.0))
        let reference = try XCTUnwrap(plainRenderer().uiImage)
        XCTAssertEqual(
            captured.pngData(), reference.pngData(),
            "in-range capture diverged from ImageRenderer.uiImage — the "
            + "colour-space fix must not re-rasterise the corpus")
    }

    /// Geometry is part of the contract. The re-render path builds its own
    /// CGContext, and a rounding policy differing from `uiImage`'s ceil
    /// silently drops the last pixel row — measured during the fix, five
    /// corpus captures came out exactly 1 px short under round-to-nearest.
    /// 60.3 is deliberately fractional: that is where the policies differ.
    @MainActor
    func testRerenderKeepsUIImageGeometryOnFractionalSizes() throws {
        let reference = try XCTUnwrap(plainRenderer(height: 60.3).uiImage?.cgImage)
        let actual = try XCTUnwrap(
            CaptureColorSpace.capture(promotedRenderer(height: 60.3),
                                      scale: 1.0)?.cgImage)
        XCTAssertEqual(actual.width, reference.width, "width drifted from uiImage")
        XCTAssertEqual(actual.height, reference.height, "height drifted from uiImage")
    }

    /// The hires probe path renders at 4×; the context must scale with it
    /// rather than silently capturing a 1× buffer.
    @MainActor
    func testRerenderHonoursRasterizationScale() throws {
        let r = promotedRenderer()
        r.scale = 4.0
        let image = try XCTUnwrap(CaptureColorSpace.capture(r, scale: 4.0))
        let cg = try XCTUnwrap(image.cgImage)
        XCTAssertEqual(cg.width, 480, "4× render did not produce a 4× buffer")
        XCTAssertEqual(cg.height, 240, "4× render did not produce a 4× buffer")
    }
}
