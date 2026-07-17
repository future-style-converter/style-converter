//
//  EmptyFlexContainerTests.swift
//  StyleConverterRuntimeTests
//
//  Regression pin for the 055 Flex_AlignCenter wave-2 regression: a
//  CHILDLESS flex container must not let align-items reposition the
//  harness PlaceholderLabel. css-flexbox-1 §4 defines flex items as the
//  container's in-flow children — an empty container has zero items, so
//  align-items/justify-content have nothing to act on, and the web
//  reference (apps/web-harness ComponentRenderer.tsx) rewrites
//  display→block for childless flex/grid containers so the label lays
//  out in normal block flow. Wave 2's CSSFlexLayout briefly treated the
//  placeholder as a flex item and vertically centred it inside the
//  declared 80px height (iOS-vs-baseline SSIM 0.90 < 0.95).
//
//  Pinned at the VIEW level with ImageRenderer (same API the capture
//  harness uses) because the routing decision lives in ComponentRenderer's
//  body — logic-level tests can't see which container branch a childless
//  component takes.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class EmptyFlexContainerTests: XCTestCase {

    // MARK: - Render helper

    /// Decode an IR component from wire JSON — IRComponent is
    /// Decodable-only (no memberwise init), and decoding keeps the test
    /// on the exact byte shapes the converter emits.
    private func component(_ json: String) throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    /// Render a component through the same canvas contract the capture
    /// harness uses (CaptureCanvas.swift: 390pt width, 16pt padding,
    /// top-leading anchor, scale 1) and return the RGBA8 pixel buffer
    /// plus dimensions. MainActor because ImageRenderer requires it.
    @MainActor
    private func render(_ comp: IRComponent) throws -> (px: [UInt8], w: Int, h: Int) {
        // Mirror CaptureCanvas so the pin measures capture-pipeline truth.
        let view = ComponentRenderer(component: comp)
            .frame(maxWidth: 358, alignment: .topLeading)
            .padding(16)
            .frame(width: 390, alignment: .topLeading)
            .fixedSize(horizontal: false, vertical: true)
            .background(Color.white)
        // ImageRenderer at scale 1 = 1 buffer pixel per logical point.
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        // cgImage (not uiImage) keeps the helper UIKit-free.
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        // Redraw into a known RGBA8 layout so byte comparisons are
        // format-stable regardless of the renderer's native format.
        let w = cg.width, h = cg.height
        var buf = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = try XCTUnwrap(CGContext(
            data: &buf, width: w, height: h, bitsPerComponent: 8,
            bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        return (buf, w, h)
    }

    /// Row range (min...max, in image coordinates) of pixels matching an
    /// RGB predicate — used to locate a coloured band inside a render.
    private func rows(in img: (px: [UInt8], w: Int, h: Int),
                      where match: (UInt8, UInt8, UInt8) -> Bool) -> ClosedRange<Int>? {
        var lo = Int.max, hi = Int.min
        // Scan every pixel; captures are small (390 × ~120) so this is cheap.
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

    // MARK: - IR fixtures (exact converter output shapes)

    /// The 055 Flex_AlignCenter IR verbatim: childless flex container
    /// with align-items:center, height 80, padding 10, grey background.
    private let flexAlignCenterJSON = """
    {"id":"t-flex","name":"Flex AlignCenter",
     "properties":[
       {"type":"Display","data":"FLEX"},
       {"type":"AlignItems","data":"CENTER"},
       {"type":"Height","data":{"type":"length","px":80.0}},
       {"type":"BackgroundColor","data":{"srgb":{"r":0.7411764705882353,"g":0.7647058823529411,"b":0.7803921568627451},"original":"#bdc3c7"}},
       {"type":"PaddingTop","data":{"px":10.0}},
       {"type":"PaddingRight","data":{"px":10.0}},
       {"type":"PaddingBottom","data":{"px":10.0}},
       {"type":"PaddingLeft","data":{"px":10.0}}
     ]}
    """

    /// The block-flow control: IDENTICAL box minus Display/AlignItems.
    /// Per the web harness's childless display→block rewrite these two
    /// must render pixel-identically. Same `name` → same placeholder run.
    private let blockControlJSON = """
    {"id":"t-block","name":"Flex AlignCenter",
     "properties":[
       {"type":"Height","data":{"type":"length","px":80.0}},
       {"type":"BackgroundColor","data":{"srgb":{"r":0.7411764705882353,"g":0.7647058823529411,"b":0.7803921568627451},"original":"#bdc3c7"}},
       {"type":"PaddingTop","data":{"px":10.0}},
       {"type":"PaddingRight","data":{"px":10.0}},
       {"type":"PaddingBottom","data":{"px":10.0}},
       {"type":"PaddingLeft","data":{"px":10.0}}
     ]}
    """

    /// Same flex container WITH one real flex item (60×20 red child, no
    /// container padding so the centring math is exact) — align-items
    /// must still centre actual children after the childless guard.
    private let flexWithChildJSON = """
    {"id":"t-flex-child","name":"FlexChild",
     "properties":[
       {"type":"Display","data":"FLEX"},
       {"type":"AlignItems","data":"CENTER"},
       {"type":"Width","data":{"type":"length","px":200.0}},
       {"type":"Height","data":{"type":"length","px":80.0}},
       {"type":"BackgroundColor","data":{"srgb":{"r":0.7411764705882353,"g":0.7647058823529411,"b":0.7803921568627451},"original":"#bdc3c7"}}
     ],
     "children":[
       {"id":"t-red","name":"Red",
        "properties":[
          {"type":"Width","data":{"type":"length","px":60.0}},
          {"type":"Height","data":{"type":"length","px":20.0}},
          {"type":"BackgroundColor","data":{"srgb":{"r":1.0,"g":0.0,"b":0.0},"original":"#ff0000"}}
        ]}
     ]}
    """

    // MARK: - The pins

    /// 055 regression pin: childless flex + align-items:center + definite
    /// height renders EXACTLY like the same box in block flow — the
    /// placeholder stays at the top, never vertically centred.
    @MainActor
    func testChildlessFlexAlignCenterRendersAsBlock() throws {
        // Render both variants in the same process — deterministic
        // rasterisation makes byte equality a fair assertion.
        let flex = try render(try component(flexAlignCenterJSON))
        let block = try render(try component(blockControlJSON))
        // Identical canvas geometry first (fail fast with a clear message).
        XCTAssertEqual(flex.w, block.w, "canvas widths diverged")
        XCTAssertEqual(flex.h, block.h, "canvas heights diverged")
        // Pixel-identical renders — any align-items influence on the
        // childless container (the 055 regression) shows up here. Compare
        // via a differing-byte COUNT (not array equality) so a failure
        // prints one number instead of two megabyte-scale buffers.
        let diffBytes = zip(flex.px, block.px).lazy.filter { $0 != $1 }.count
        XCTAssertEqual(diffBytes, 0,
                       "childless flex container must ignore align-items " +
                       "and render in block flow (web display→block parity) " +
                       "— \(diffBytes) bytes differ from the block control")
    }

    /// Belt-and-braces on the same render: the placeholder glyphs sit in
    /// the TOP portion of the 80px box (block flow), not centred. The
    /// block-font label (applier campaign) paints the FIXED
    /// rgba(237,237,237,0.7) ink — lighter than the #bdc3c7 fill but
    /// darker than the white canvas, so the probe hunts that middle band.
    @MainActor
    func testChildlessFlexPlaceholderSitsAtTop() throws {
        let img = try render(try component(flexAlignCenterJSON))
        // Container band: canvas pad 16 → box rows 16..<96 (height 80).
        // Label ink over #bdc3c7 composites to ≈(223,225,226) — sum ≈ 673
        // — strictly between the box fill (583) and the white canvas
        // (765); box edges are integer-aligned at scale 1, so no
        // fractional-coverage blend pixel can fall inside the band.
        let textRows = try XCTUnwrap(
            rows(in: img) { r, g, b in (640..<720).contains(Int(r) + Int(g) + Int(b)) },
            "no placeholder glyphs found in render")
        // Box top = 16 (canvas padding). Block flow puts the label run
        // right after the 10px container padding (+ the label's fixed 6px
        // top inset → ink starts at row ≈32); glyph ink must start well
        // above the vertical middle (16 + 40 = 56). The regression
        // rendered the run vertically centred — comfortably caught.
        XCTAssertLessThan(textRows.lowerBound, 40,
                          "placeholder vertically displaced — align-items " +
                          "leaked into the childless flex container")
    }

    /// Inverse guard: with a REAL child, align-items:center must still
    /// centre it — proves the childless fix didn't disable flex alignment.
    @MainActor
    func testFlexWithChildStillCentersChild() throws {
        let img = try render(try component(flexWithChildJSON))
        // The red child band (pure red fill, tolerant match).
        let red = try XCTUnwrap(
            rows(in: img) { r, g, b in r > 200 && g < 80 && b < 80 },
            "red flex item not found in render")
        // Container rows 16..<96 (pad 16, height 80, no padding declared).
        // Centred 20px child → rows 46...65 (offset (80−20)/2 = 30).
        XCTAssertEqual(red.lowerBound, 46, accuracy: 2,
                       "flex child not vertically centred (top edge)")
        XCTAssertEqual(red.upperBound, 65, accuracy: 2,
                       "flex child not vertically centred (bottom edge)")
    }
}

/// Integer accuracy overload — XCTAssertEqual(_:_:accuracy:) is
/// FloatingPoint-only; this keeps the row assertions readable.
private func XCTAssertEqual(_ a: Int, _ b: Int, accuracy: Int,
                            _ message: String, file: StaticString = #filePath,
                            line: UInt = #line) {
    XCTAssertLessThanOrEqual(abs(a - b), accuracy, message, file: file, line: line)
}
