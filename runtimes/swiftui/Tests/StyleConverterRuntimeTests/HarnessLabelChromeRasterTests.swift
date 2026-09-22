//
//  HarnessLabelChromeRasterTests.swift
//  Wave 51 PR (A) — the raster pins for the harness label as CHROME
//  (HarnessLabelChrome.swift; normative home docs/DYNAMIC_CAPTURE.md
//  section "Harness label chrome").
//
//  THE RULE. The block-font name label is drawn by the capture harness as a
//  SIBLING overlay over the whole capture root, at literal (8, 6) in the
//  CAPTURE FRAME, after the element's entire paint chain. So whatever the
//  element does to ITSELF — mix-blend-mode, opacity, transform, clip-path,
//  overflow clip — the band rows 6..12 × the glyph columns must be
//  BYTE-EQUAL to the plain leaf's band: same positions, same (174,174,180)
//  ink over the #1A1A2E stage (measured here), nothing else lit. Exactly one
//  label per capture, only for a childless, textless root, never in WPT.
//
//  Rendered on CaptureCanvasMirror — the byte-mirror of the harness
//  CaptureCanvas — with the chrome at the harness's exact mounting point.
//
//  MUTATIONS EXECUTED (wave 51, Catalyst ImageRenderer at scale 1; each
//  reverted byte-exact before the green run — lane notes hold the logs):
//   * chrome overlay moved onto the ComponentHost VIEW in the mirror chain
//     (CaptureCanvas.swift itself is not in this target): band ink 0 on
//     (a)–(f), all 178 "LABEL CHROME" pixels at the old footprint from
//     (24,22) — content origin (16,16) + (8,6) — and NOT multiplied / faded /
//     rotated / clipped, since a view-level overlay is outside the element's
//     style chain. The per-effect signatures live in that old footprint
//     (seen incidentally under the next mutation: clip-path kept 91 of 178,
//     multiply/opacity darkened them below the lit threshold).
//   * `BlockLabel` restored in ComponentRenderer's nameless-leaf branch →
//     178 (91 under clip) stray lit pixels from (8,6) inside the element.
//   * `guard !wptCaptureMode` dropped → testWptCaptureModePaintsNoLabel red
//     (178 px); `guard backdropPass != .sampling` dropped →
//     testSamplingPassPaintsNoLabel red (178 px).
//

import Foundation
import SwiftUI
import XCTest
@testable import StyleConverterRuntime

final class HarnessLabelChromeRasterTests: XCTestCase {
    typealias M = CaptureCanvasMirror

    /// "Label_Chrome" → "LABEL CHROME": 12 glyphs, 178 set bits. 'L' row 0
    /// = 10000, so frame (8,6) is ink and (9,6) is stage; 'L' row 6 = 11111,
    /// so (12,12) is ink — the two probes BlockLabelTests also pins.
    static let name = "Label_Chrome"
    /// Band = glyph rows 6..12 × cols 8 ..< 8 + n·ADVANCE (design §4).
    static let bandRows = 6...12
    static let bandCols = 8..<(8 + 12 * BlockFont.advance)
    /// rgba(237,237,237,179/255) over #1A1A2E — the shared ink byte; stage.
    static let ink = (174, 174, 180), stage = (0x1A, 0x1A, 0x2E)

    /// A `w`×`h` `#222222` leaf plus `extra` declarations and an optional
    /// `tail` (children / text) — converter wire shapes, one per line.
    private func leaf(_ extra: String = "", w: Int = 120, h: Int = 40, tail: String = "") throws -> IRComponent {
        let json = """
        {"id":"t","name":"\(Self.name)","properties":[
          {"type":"Width","data":{"type":"length","px":\(w).0}},
          {"type":"Height","data":{"type":"length","px":\(h).0}},
          {"type":"BackgroundColor","data":{"srgb":{"r":0.13333333333333333,"g":0.13333333333333333,"b":0.13333333333333333},"original":"#222222"}}\(extra)
        ]\(tail)}
        """
        return try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    /// The (b)–(f) effect declarations, exactly as the converter emits them.
    static let multiply = #",{"type":"MixBlendMode","data":"MULTIPLY"}"#
    static let opacity  = #",{"type":"Opacity","data":{"alpha":0.5}}"#
    static let rotate   = #",{"type":"Transform","data":{"type":"functions","list":[{"fn":"rotate","a":{"deg":15.0}}]}},{"type":"MarginTop","data":{"px":40.0}}"#
    static let clip     = #",{"type":"ClipPath","data":{"type":"circle","original":{"v":50.0,"u":"PERCENT"}}}"#
    static let overflow = #",{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"}"#
    static let kid      = #","children":[{"id":"k","name":"kid","properties":[{"type":"Width","data":{"type":"length","px":20.0}},{"type":"Height","data":{"type":"length","px":20.0}}]}]"#

    /// The in-flow harness canvas WITH the chrome, as CaptureCanvas mounts it.
    @MainActor private func render(_ c: IRComponent) throws -> RasterImage {
        try M.raster(M.flowCanvasView(c, withLabelChrome: true))
    }

    /// "Lit" = every channel ≥ 160: over the stage and over a #222 box only
    /// label ink qualifies (design §4 L-set: ink over ANY paint is ≥ 166).
    private func isLit(_ p: (Int, Int, Int)) -> Bool { p.0 >= 160 && p.1 >= 160 && p.2 >= 160 }

    /// Lit pixels inside (true) / outside (false) the band.
    private func lit(_ img: RasterImage, inBand: Bool) -> Int {
        var n = 0
        for y in 0..<img.h { for x in 0..<img.w
            where (Self.bandRows.contains(y) && Self.bandCols.contains(x)) == inBand && isLit(M.rgb(img, x, y)) { n += 1 } }
        return n
    }

    /// First lit pixel (row-major) for failure messages — names WHERE stray
    /// ink landed (the ComponentHost-overlay mutation puts it at (24,22)).
    private func firstLit(_ img: RasterImage) -> String {
        for y in 0..<img.h { for x in 0..<img.w where isLit(M.rgb(img, x, y)) { return "(\(x),\(y))" } }
        return "none"
    }

    /// The band's RGB bytes, row-major — compared whole between variants.
    private func bandBytes(_ img: RasterImage) -> [Int] {
        var out: [Int] = []
        for y in Self.bandRows { for x in Self.bandCols { let p = M.rgb(img, x, y); out += [p.0, p.1, p.2] } }
        return out
    }

    /// Shared assertions: ink present in the band, none outside it, and the
    /// whole band byte-equal to the plain leaf (a) when `plain` is given.
    @MainActor
    private func assertBand(_ img: RasterImage, plain: RasterImage? = nil, _ label: String) {
        XCTAssertEqual(img.w, 390, "\(label): frame width")
        if let plain { XCTAssertEqual(bandBytes(img), bandBytes(plain), "\(label): band rows 6..12 must be byte-equal to the plain leaf") }
        XCTAssertGreaterThan(lit(img, inBand: true), 0, "\(label): no label ink in the band (first lit: \(firstLit(img)))")
        XCTAssertEqual(lit(img, inBand: false), 0, "\(label): ink-coloured pixels outside the band (first: \(firstLit(img)))")
    }

    // MARK: - (a) the plain leaf: position, colour, containment

    @MainActor
    func testPlainLeafPaintsTheLabelAtFrameOriginInInkColour() throws {
        let img = try render(try leaf())
        // 'L' row 0 = 10000 → (8,6) ink, (9,6) stage; row 6 → (12,12) ink. ±1
        // per channel on the ink byte (Catalyst raster, in-gamut sRGB).
        for (x, y) in [(8, 6), (12, 12)] {
            let p = M.rgb(img, x, y)
            XCTAssertTrue(abs(p.0 - 174) <= 1 && abs(p.1 - 174) <= 1 && abs(p.2 - 180) <= 1, "(\(x),\(y)) is \(p), not (174,174,180)±1")
        }
        XCTAssertTrue(M.rgb(img, 9, 6) == Self.stage, "(9,6) is \(M.rgb(img, 9, 6)), must be the bare stage — glyph shifted or smeared?")
        // Every lit band pixel is the shared ink byte — no other colour lit.
        for y in Self.bandRows { for x in Self.bandCols where isLit(M.rgb(img, x, y)) {
            let p = M.rgb(img, x, y)
            XCTAssertTrue(abs(p.0 - 174) <= 1 && abs(p.1 - 174) <= 1 && abs(p.2 - 180) <= 1, "band ink at (\(x),\(y)) is \(p)")
        } }
        assertBand(img, "(a) plain 120×40 #222222 leaf")
        // The element itself is untouched: the #222 box still starts at (16,16).
        XCTAssertTrue(M.rgb(img, 20, 20) == (0x22, 0x22, 0x22), "box fill at (20,20) is \(M.rgb(img, 20, 20))")
    }

    // MARK: - (b)–(f) the element's own effects never reach the chrome

    @MainActor func testMultiplyLeafBandEqualsPlain() throws {
        try assertBand(render(leaf(Self.multiply)), plain: render(leaf()), "(b) mix-blend-mode: multiply") }
    @MainActor func testOpacityLeafBandEqualsPlain() throws {
        try assertBand(render(leaf(Self.opacity)), plain: render(leaf()), "(c) opacity 0.5") }
    @MainActor func testRotatedLeafBandEqualsPlain() throws {
        try assertBand(render(leaf(Self.rotate)), plain: render(leaf()), "(d) rotate(15deg) + margin-top 40") }
    @MainActor func testClipPathLeafBandEqualsPlain() throws {
        try assertBand(render(leaf(Self.clip)), plain: render(leaf()), "(e) clip-path circle(50%)") }
    @MainActor func testOverflowHiddenLeafBandEqualsPlain() throws {
        try assertBand(render(leaf(Self.overflow, w: 100, h: 50)), plain: render(leaf()), "(f) overflow hidden 100×50") }

    // MARK: - (g) + the negative pins: when the chrome must NOT paint

    /// Defence-in-depth guard (HarnessLabelChrome header): the pass-A plate
    /// render publishes `.sampling` and must never carry label ink.
    @MainActor
    func testSamplingPassPaintsNoLabel() throws {
        let img = try M.raster(M.flowCanvasView(try leaf(), withLabelChrome: true).environment(\.backdropPass, .sampling))
        XCTAssertEqual(lit(img, inBand: true) + lit(img, inBand: false), 0, "label ink baked into a backdrop sampling pass")
    }

    /// The REAL negative pin (design C47): the flag is read from the
    /// environment the harness publishes on the canvas, so TITAN/WPT/inbox
    /// captures stay label-free exactly as before PR (A).
    @MainActor
    func testWptCaptureModePaintsNoLabel() throws {
        let img = try M.raster(M.flowCanvasView(try leaf(), withLabelChrome: true).environment(\.wptCaptureMode, true))
        XCTAssertEqual(lit(img, inBand: true) + lit(img, inBand: false), 0, "WPT capture must paint no label chrome")
    }

    /// Containers are unlabelled — the composed children ARE the content.
    @MainActor
    func testRootWithChildrenPaintsNoLabel() throws {
        let img = try render(try leaf(tail: Self.kid))
        XCTAssertEqual(lit(img, inBand: true) + lit(img, inBand: false), 0, "a root with children must carry no label")
    }

    // MARK: - Pure predicate + geometry pins (no render surface)

    func testShowsLabelPredicate() throws {
        let plain = try leaf(), kid = try leaf(tail: Self.kid)
        let text = try leaf(tail: #","text":"x""#), empty = try leaf(tail: #","text":"""#)
        XCTAssertTrue(HarnessLabelChrome.showsLabel(plain, wptCaptureMode: false, backdropPass: .disabled))
        XCTAssertTrue(HarnessLabelChrome.showsLabel(empty, wptCaptureMode: false, backdropPass: .disabled), "\"\" is 'extracted, was empty' — not content")
        XCTAssertFalse(HarnessLabelChrome.showsLabel(kid, wptCaptureMode: false, backdropPass: .disabled), "children")
        XCTAssertFalse(HarnessLabelChrome.showsLabel(text, wptCaptureMode: false, backdropPass: .disabled), "real text")
        XCTAssertFalse(HarnessLabelChrome.showsLabel(plain, wptCaptureMode: true, backdropPass: .disabled), "wpt")
        XCTAssertFalse(HarnessLabelChrome.showsLabel(plain, wptCaptureMode: false, backdropPass: .sampling), "sampling")
        // The PLAIN name, `_` → space (design C51); case is BlockLabel's job.
        XCTAssertEqual(HarnessLabelChrome.labelText(for: plain), "Label Chrome")
    }

    /// Truncation against the FRAME (design §2): 62 glyphs at 390, 39 at
    /// CAPTURE_WIDTH=250, one at 22, none below 22 — never the element width.
    func testTruncationIsAgainstTheFrameWidth() {
        XCTAssertEqual(BlockLabelLayout.truncatedCount(100, componentWidth: 390), 62)
        XCTAssertEqual(BlockLabelLayout.truncatedCount(100, componentWidth: 250), 39)
        XCTAssertEqual(BlockLabelLayout.truncatedCount(100, componentWidth: 22), 1)
        XCTAssertEqual(BlockLabelLayout.truncatedCount(100, componentWidth: 21), 0)
    }
}
