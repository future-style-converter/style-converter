//
//  WPTCaptureModeTests.swift
//  StyleConverterRuntimeTests
//
//  Pins the WPT-capture-mode placeholder suppression (WPTCaptureMode.swift +
//  ComponentRenderer.suppressesNamePlaceholder). The synthesized
//  component-NAME placeholder is a harness debug label the Chromium
//  browser-ref never paints; under the WPT capture flag the renderer must
//  drop it for nameless-empty leaves — WITHOUT ever suppressing real
//  element text (the IR `_text`/rawText channel). This mirrors the web
//  harness's ?wpt=1 empty-visible-text path.
//
//  Two layers of proof:
//    1. The pure static predicate — deterministic, no render surface
//       (exactly the style of FidelityWave3Tests' isOutOfFlow pins).
//    2. An end-to-end ImageRenderer pixel pass through the real capture
//       canvas (the EmptyFlexContainerTests approach), proving the name
//       glyphs actually vanish only in the nameless-empty WPT case and
//       that the flag defaults OFF keep the placeholder byte-for-byte.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class WPTCaptureModeTests: XCTestCase {

    // MARK: - IR helpers

    /// Decode from wire JSON — IRComponent is Decodable-only, and decoding
    /// keeps the fixtures on the exact byte shapes the converter emits.
    private func component(_ json: String) throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    /// A childless box with an explicit 120×40 size and a light (#eee)
    /// background: light bg ⇒ the placeholder's contrast pick is DARK text
    /// (PlaceholderLabel.resolvedColor: luminance > 0.6), so glyph ink
    /// reads clearly against the fill, and the explicit height pins the box
    /// size whether or not the label is present. `text` inserted verbatim.
    private func boxJSON(name: String, text: String? = nil) -> String {
        let textField = text.map { ",\"text\":\"\($0)\"" } ?? ""
        return """
        {"id":"t","name":"\(name)"\(textField),
         "properties":[
           {"type":"Width","data":{"type":"length","px":120.0}},
           {"type":"Height","data":{"type":"length","px":40.0}},
           {"type":"BackgroundColor","data":{"srgb":{"r":0.9333333333333333,"g":0.9333333333333333,"b":0.9333333333333333},"original":"#eeeeee"}}
         ]}
        """
    }

    // MARK: - Pure predicate pins (no render surface)

    /// Flag OFF is the product / baseline path — NEVER suppress, so every
    /// committed capture keeps its name placeholder exactly.
    func testPredicateFlagOffNeverSuppresses() throws {
        let nameless = try component(boxJSON(name: "background color rgb 001"))
        XCTAssertFalse(
            ComponentRenderer.suppressesNamePlaceholder(nameless, wptCaptureMode: false),
            "flag OFF must never suppress the placeholder (baseline behaviour)")
    }

    /// The target case: WPT mode ON + a nameless-empty leaf ⇒ suppress.
    func testPredicateNamelessEmptyInWptModeSuppresses() throws {
        let nameless = try component(boxJSON(name: "background color rgb 001"))
        XCTAssertTrue(
            ComponentRenderer.suppressesNamePlaceholder(nameless, wptCaptureMode: true),
            "WPT mode must suppress the synthesized name placeholder for a nameless-empty leaf")
    }

    /// Real element text (`text`) is actual content — NEVER suppressed,
    /// even in WPT mode. Web gives `_text` precedence over the empty-string
    /// suppression; so do we.
    func testPredicateRealTextInWptModeNotSuppressed() throws {
        let withText = try component(boxJSON(name: "css color 001", text: "Filler text"))
        XCTAssertFalse(
            ComponentRenderer.suppressesNamePlaceholder(withText, wptCaptureMode: true),
            "real element text must render even in WPT mode")
    }

    /// A component WITH children renders no synthesized name at all — there
    /// is nothing to suppress, WPT mode or not.
    func testPredicateWithChildrenNotSuppressed() throws {
        let parent = try component("""
        {"id":"p","name":"parent",
         "properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.9,"g":0.9,"b":0.9},"original":"#e6e6e6"}}],
         "children":[{"id":"c","name":"child","properties":[]}]}
        """)
        XCTAssertFalse(
            ComponentRenderer.suppressesNamePlaceholder(parent, wptCaptureMode: true),
            "a component with children paints no synthesized name to suppress")
    }

    // MARK: - Line-box calibration pins (TITAN Round 4 GAP 1, height half)

    /// Flag OFF (product + baseline): bare text stays on SwiftUI's natural
    /// metrics (nil) — no calibration line box, so every committed capture
    /// is byte-identical.
    func testEffectiveLineHeightOffKeepsNaturalMetrics() {
        XCTAssertNil(
            ComponentRenderer.effectiveLineHeight(declared: nil, wptCaptureMode: false),
            "flag OFF + no IR line-height must stay nil (natural metrics)")
    }

    /// Flag ON + no IR line-height: pin the ref line box (18px @16px) so the
    /// forced-Inter bar matches the browser-ref's default-font `<p>`.
    func testEffectiveLineHeightOnPinsRefLineBox() {
        XCTAssertEqual(
            ComponentRenderer.effectiveLineHeight(declared: nil, wptCaptureMode: true),
            ComponentRenderer.wptRefLineBoxPx,
            "flag ON + no IR line-height must pin the ref line box")
        XCTAssertEqual(ComponentRenderer.wptRefLineBoxPx, 18)
    }

    /// An IR-declared line-height ALWAYS wins (author > calibration), flag
    /// on or off — a test that sets its own line-height keeps it exactly.
    func testEffectiveLineHeightDefersToIR() {
        XCTAssertEqual(
            ComponentRenderer.effectiveLineHeight(declared: 30, wptCaptureMode: true), 30,
            "IR line-height must win over the ref-line-box pin in WPT mode")
        XCTAssertEqual(
            ComponentRenderer.effectiveLineHeight(declared: 30, wptCaptureMode: false), 30,
            "IR line-height must pass through unchanged with the flag off")
    }

    // MARK: - End-to-end pixel proof

    /// Render a component through the capture-canvas contract (390pt width,
    /// 16pt padding, top-leading, scale 1) with the WPT flag set as given,
    /// on a WHITE canvas so the only dark ink is placeholder/text glyphs.
    @MainActor
    private func darkPixelCount(_ comp: IRComponent, wpt: Bool) throws -> Int {
        // Mirror CaptureCanvas geometry; publish the WPT flag exactly the
        // way captureAllComponents does (`.environment(\.wptCaptureMode, …)`).
        let view = ComponentRenderer(component: comp)
            .environment(\.wptCaptureMode, wpt)
            .frame(maxWidth: 358, alignment: .topLeading)
            .padding(16)
            .frame(width: 390, alignment: .topLeading)
            .fixedSize(horizontal: false, vertical: true)
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
        // Glyph ink over the #eee box composites to ≈107/channel (sum≈321);
        // the white canvas is 765 and the box fill 714. A sum < 450 is
        // unambiguously glyph ink — count those pixels.
        var count = 0
        for i in stride(from: 0, to: buf.count, by: 4) {
            let sum = Int(buf[i]) + Int(buf[i + 1]) + Int(buf[i + 2])
            if sum < 450 { count += 1 }
        }
        return count
    }

    /// Nameless-empty leaf: flag OFF paints the name glyphs, flag ON drops
    /// them — the glyph ink must go from "present" to "none".
    @MainActor
    func testNamelessEmptyGlyphsVanishOnlyInWptMode() throws {
        let box = try component(boxJSON(name: "background color rgb 001"))
        let off = try darkPixelCount(box, wpt: false)
        let on  = try darkPixelCount(box, wpt: true)
        // Baseline path must still paint the placeholder glyphs.
        XCTAssertGreaterThan(off, 0,
            "flag OFF must keep painting the name placeholder (baseline behaviour)")
        // WPT path must leave a clean box — zero glyph ink.
        XCTAssertEqual(on, 0,
            "WPT mode must suppress the synthesized name placeholder entirely " +
            "— \(on) glyph pixels still painted")
    }

    /// Real element text survives WPT mode: the same box carrying `text`
    /// still paints glyphs with the flag ON.
    @MainActor
    func testRealTextStillRendersInWptMode() throws {
        let withText = try component(boxJSON(name: "css color 001", text: "Filler text"))
        let on = try darkPixelCount(withText, wpt: true)
        XCTAssertGreaterThan(on, 0,
            "real element text must still render in WPT mode (only the " +
            "synthesized NAME placeholder is suppressed)")
    }
}
